/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.util.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.Keep
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.util.DeviceInfo
import timber.log.Timber
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 卡顿诊断: an opt-in recorder for where the keyboard spends its time.
 *
 * While enabled it writes timing events to a capped file in app storage (it survives process
 * restarts, so a slow cold open is still in the report), watches the main thread for stalls and
 * captures their stack, and counts dropped frames while the keyboard is visible. It never records
 * typed text, candidates or key codes. Disabled, every hook is a single volatile read.
 */
object LagDiagnostics {
    /** A single operation at or above this is marked SLOW. */
    const val SLOW_MS = 50L

    /** Main-thread blocking calls into Rime at or above this are logged with their call site. */
    const val MAIN_RIME_CALL_MS = 16L

    private const val SLOW_KEY_MS = 30L
    private const val SLOW_RIME_JOB_MS = 100L
    private const val SLOW_TOUCH_TO_CANDIDATES_MS = 100L
    private const val WATCHDOG_TICK_MS = 50L
    private const val WATCHDOG_INTERVAL_MS = 100L
    private const val STACK_DEPTH = 30

    @Volatile
    @JvmStatic
    var enabled = false
        private set

    private var store: LagLogStore? = null
    private var appContext: Context? = null

    private val writer = Executors.newSingleThreadExecutor { Thread(it, "lag-diagnostics").apply { isDaemon = true } }
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // per keyboard-visible session
    private val keyStats = DurationStats()
    private val touchToCandidates = DurationStats()
    private val frameStats = FrameStats()
    private val stallCount = AtomicInteger()
    private val lastTouchDownUptime = AtomicLong(0)
    private var sessionStartUptime = 0L
    private var sessionStartCpuMs = 0L
    private var hiddenAtCpuMs = -1L
    private var hiddenAtUptime = 0L

    private val inputViewBuilds = AtomicInteger()
    private var firstInputViewShown = false
    private var startInputViewAt = 0L

    @Volatile
    private var watchdog: Thread? = null

    @Volatile
    private var frameMonitorRunning = false

    // ---- setup -----------------------------------------------------------------------------

    /** Called from Application.onCreate once preferences are available. */
    fun init(context: Context) {
        val ctx = context.applicationContext ?: context
        appContext = ctx
        store = LagLogStore(File(ctx.filesDir, "diagnostics"))
        val pref = AppPrefs.defaultInstance().keyboard.inputLatencyLogging
        enabled = pref.getValue()
        pref.registerOnChangeListener(prefListener)
        if (enabled) {
            val sinceProcessStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            } else {
                -1L
            }
            log("process_start", "pid=${Process.myPid()} app_create_after_ms=$sinceProcessStart ${memorySnapshot()}")
        }
    }

    // Kept as a field: PreferenceDelegate holds listeners weakly.
    @Keep
    private val prefListener = PreferenceDelegate.OnChangeListener<Boolean> { _, value ->
        val wasEnabled = enabled
        enabled = value
        if (value && !wasEnabled) log("diagnostics_on", memorySnapshot())
        if (!value && wasEnabled) {
            stopMonitors()
            writeLine("diagnostics_off", "")
        }
    }

    // ---- recording -------------------------------------------------------------------------

    fun log(category: String, details: String = "") {
        if (!enabled) return
        writeLine(category, details)
    }

    private fun writeLine(category: String, details: String) {
        val target = store ?: return
        val wallMs = System.currentTimeMillis()
        val thread = Thread.currentThread().name
        writer.execute {
            runCatching {
                target.append(listOf(LagLine.format(timeFormat.format(Date(wallMs)), thread, category, details)))
            }.onFailure { Timber.w(it, "Failed to write lag diagnostics") }
        }
    }

    /** Logs the time since [startNs] (from [System.nanoTime]) if it is at least [minMs]. */
    fun duration(category: String, startNs: Long, minMs: Long = 0, slowMs: Long = SLOW_MS, extra: String = "") {
        if (!enabled || startNs == 0L) return
        val ms = (System.nanoTime() - startNs) / 1_000_000
        if (ms >= minMs) writeLine(category, LagLine.durationDetails(ms, slowMs, extra))
    }

    /** `0` when disabled, so callers can skip work cheaply: `val t = LagDiagnostics.start()`. */
    fun start(): Long = if (enabled) System.nanoTime() else 0L

    inline fun <T> measure(category: String, minMs: Long = 0, extra: String = "", block: () -> T): T {
        val startedAt = start()
        try {
            return block()
        } finally {
            duration(category, startedAt, minMs, extra = extra)
        }
    }

    /** A blocking `rime.run {}`; logged with its caller only when it held the main thread. */
    fun rimeCallFinished(startNs: Long) {
        if (!enabled || startNs == 0L || Looper.myLooper() != Looper.getMainLooper()) return
        val ms = (System.nanoTime() - startNs) / 1_000_000
        if (ms < MAIN_RIME_CALL_MS) return
        writeLine("main_rime_call", LagLine.durationDetails(ms, SLOW_MS, "caller=${callSite()}"))
    }

    /** [name] is only evaluated for a slow job. */
    fun rimeJobFinished(queuedMs: Long, runNs: Long, name: () -> String) {
        if (!enabled) return
        val runMs = runNs / 1_000_000
        if (queuedMs < SLOW_RIME_JOB_MS && runMs < SLOW_RIME_JOB_MS) return
        val job = runCatching(name).getOrDefault("unknown")
        if (queuedMs >= SLOW_RIME_JOB_MS) writeLine("rime_queue_wait", LagLine.durationDetails(queuedMs, SLOW_RIME_JOB_MS, "job=$job"))
        if (runMs >= SLOW_RIME_JOB_MS) writeLine("rime_job", LagLine.durationDetails(runMs, SLOW_RIME_JOB_MS, "job=$job"))
    }

    fun keyProcessed(totalUs: Long, engineUs: Long, responseUs: Long, idleGapUs: Long) {
        if (!enabled) return
        val ms = totalUs / 1_000
        keyStats.add(ms)
        if (ms >= SLOW_KEY_MS) {
            writeLine(
                "key_process",
                LagLine.durationDetails(ms, SLOW_MS, "engine_ms=${engineUs / 1_000} response_ms=${responseUs / 1_000} idle_gap_ms=${idleGapUs / 1_000}"),
            )
        }
    }

    fun touchDown(eventUptimeMs: Long) {
        if (!enabled) return
        lastTouchDownUptime.compareAndSet(0, eventUptimeMs)
        val age = SystemClock.uptimeMillis() - eventUptimeMs
        if (age >= SLOW_MS) writeLine("touch_dispatch", LagLine.durationDetails(age, SLOW_MS, "note=touch_reached_keyboard_late"))
    }

    fun candidatesLaidOut() {
        if (!enabled) return
        val down = lastTouchDownUptime.getAndSet(0)
        if (down == 0L) return
        val ms = SystemClock.uptimeMillis() - down
        touchToCandidates.add(ms)
        if (ms >= SLOW_TOUCH_TO_CANDIDATES_MS) writeLine("touch_to_candidates", LagLine.durationDetails(ms, SLOW_TOUCH_TO_CANDIDATES_MS))
    }

    fun inputViewBuilt(startNs: Long, reason: String) {
        if (!enabled || startNs == 0L) return
        val ms = (System.nanoTime() - startNs) / 1_000_000
        writeLine("input_view_build", LagLine.durationDetails(ms, SLOW_MS, "n=${inputViewBuilds.incrementAndGet()} reason=$reason"))
    }

    fun startInputView() {
        if (!enabled) return
        startInputViewAt = SystemClock.uptimeMillis()
    }

    // ---- keyboard shown / hidden -------------------------------------------------------------

    fun onWindowShown(decorView: View) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        val cpu = Process.getElapsedCpuTime()
        val first = !firstInputViewShown
        firstInputViewShown = true
        val background = if (hiddenAtCpuMs >= 0) {
            " cpu_while_hidden_ms=${cpu - hiddenAtCpuMs} hidden_for_s=${(now - hiddenAtUptime) / 1_000}"
        } else {
            ""
        }
        writeLine("window_shown", "first=$first$background ${memorySnapshot()}")
        sessionStartUptime = now
        sessionStartCpuMs = cpu
        keyStats.reset()
        touchToCandidates.reset()
        frameStats.reset()
        stallCount.set(0)
        val openedAt = startInputViewAt.takeIf { it > 0 } ?: now
        awaitFirstDraw(decorView, openedAt, first)
        startMonitors()
    }

    fun onWindowHidden() {
        if (!enabled) {
            stopMonitors()
            return
        }
        stopMonitors()
        val now = SystemClock.uptimeMillis()
        val cpu = Process.getElapsedCpuTime()
        hiddenAtCpuMs = cpu
        hiddenAtUptime = now
        if (sessionStartUptime == 0L) return
        val wallMs = now - sessionStartUptime
        val cpuMs = cpu - sessionStartCpuMs
        val cpuPercent = if (wallMs > 0) cpuMs * 100 / wallMs else 0
        writeLine(
            "session",
            "shown_s=${wallMs / 1_000} cpu_ms=$cpuMs cpu_pct=$cpuPercent ${keyStats.describe("keys")} " +
                "${touchToCandidates.describe("touch_to_cand")} ${frameStats.describe()} main_stalls=${stallCount.get()}",
        )
        sessionStartUptime = 0L
    }

    private fun awaitFirstDraw(decorView: View, openedAt: Long, first: Boolean) {
        val observer = decorView.viewTreeObserver
        if (!observer.isAlive) return
        val listener = object : ViewTreeObserver.OnDrawListener {
            var done = false

            override fun onDraw() {
                if (done) return
                done = true
                val ms = SystemClock.uptimeMillis() - openedAt
                writeLine("open_to_first_draw", LagLine.durationDetails(ms, SLOW_MS * 2, "first=$first"))
                // a draw listener cannot remove itself while drawing
                mainHandler.post { if (decorView.viewTreeObserver.isAlive) decorView.viewTreeObserver.removeOnDrawListener(this) }
            }
        }
        observer.addOnDrawListener(listener)
        decorView.invalidate()
    }

    // ---- monitors --------------------------------------------------------------------------

    private fun startMonitors() {
        startFrameMonitor()
        startWatchdog()
    }

    @Synchronized
    private fun stopMonitors() {
        frameMonitorRunning = false
        watchdog?.interrupt()
        watchdog = null
    }

    private fun startFrameMonitor() {
        if (frameMonitorRunning || Looper.myLooper() != Looper.getMainLooper()) return
        frameMonitorRunning = true
        val period = refreshPeriodNs()
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            var last = 0L

            override fun doFrame(frameTimeNanos: Long) {
                if (!frameMonitorRunning || !enabled) return
                if (last != 0L) {
                    val interval = frameTimeNanos - last
                    if (frameStats.record(interval, period)) {
                        writeLine("frame", LagLine.durationDetails(interval / 1_000_000, FrameStats.SEVERE_MS, "refresh_ms=${"%.1f".format(Locale.US, period / 1e6)}"))
                    }
                }
                last = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        })
    }

    @Synchronized
    private fun startWatchdog() {
        if (watchdog != null) return
        val mainThread = Looper.getMainLooper().thread
        val thread = Thread({
            val detector = StallDetector(LagReport.MAIN_STALL_MS)
            val ackAt = AtomicLong(-1)
            try {
                while (!Thread.currentThread().isInterrupted && enabled) {
                    ackAt.set(-1)
                    detector.posted(SystemClock.uptimeMillis())
                    mainHandler.post { ackAt.set(SystemClock.uptimeMillis()) }
                    while (true) {
                        Thread.sleep(WATCHDOG_TICK_MS)
                        val ack = ackAt.get()
                        if (ack >= 0) {
                            detector.acknowledged(ack)?.let { total ->
                                writeLine("main_stall_end", LagLine.durationDetails(total, LagReport.MAIN_STALL_MS))
                            }
                            break
                        }
                        detector.check(SystemClock.uptimeMillis())?.let { waited ->
                            stallCount.incrementAndGet()
                            writeLine("main_stall", "waited_ms=$waited stack=${formatStack(mainThread.stackTrace)}")
                        }
                    }
                    Thread.sleep(WATCHDOG_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
            } finally {
                synchronized(this) { if (watchdog === Thread.currentThread()) watchdog = null }
            }
        }, "lag-watchdog").apply { isDaemon = true }
        watchdog = thread
        thread.start()
    }

    private fun refreshPeriodNs(): Long {
        @Suppress("DEPRECATION")
        val rate = runCatching {
            (appContext?.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay?.refreshRate
        }.getOrNull()?.takeIf { it > 1f } ?: 60f
        return (1e9 / rate).toLong()
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun formatStack(stack: Array<StackTraceElement>): String = stack.take(STACK_DEPTH).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

    private fun callSite(): String {
        val frame = Throwable().stackTrace.firstOrNull { el ->
            val c = el.className
            !c.startsWith("com.osfans.trime.util.diagnostics") &&
                !c.startsWith("com.osfans.trime.daemon.RimeDaemon") &&
                !c.startsWith("kotlin") &&
                !c.startsWith("java.")
        } ?: return "unknown"
        return "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
    }

    private fun memorySnapshot(): String {
        val rt = Runtime.getRuntime()
        val javaUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return "java_heap_mb=$javaUsedMb/${rt.maxMemory() / (1024 * 1024)} native_heap_mb=$nativeMb threads=${Thread.activeCount()}"
    }

    // ---- export / clear ------------------------------------------------------------------------

    fun storedBytes(): Long = store?.sizeBytes() ?: 0L

    /** Waits for pending writes, then writes the full report. Call off the main thread. */
    fun export(context: Context, out: Writer) {
        flush()
        val lines = store?.readAll().orEmpty()
        val now = Date()
        val tz = TimeZone.getDefault()
        out.write("ChenIME 卡顿诊断报告\n")
        out.write("导出时间: ${timeFormat.format(now)} (${tz.id}, UTC${offset(tz, now)})\n")
        out.write("诊断开关: ${if (enabled) "开" else "关"}\n")
        out.write("说明: 只记录耗时、内存、线程栈等，不记录输入内容、候选词或按键。\n")
        out.write("      dur_ms=耗时毫秒，SLOW=超过阈值；main_stall=主线程卡住≥${LagReport.MAIN_STALL_MS}ms，并附当时的主线程调用栈。\n\n")
        out.write(DeviceInfo.get(context))
        out.write("--------- 当前状态\n")
        out.write("${memorySnapshot()}\n")
        out.write("process_cpu_ms=${Process.getElapsedCpuTime()} process_uptime_s=${processUptimeSeconds()}\n")
        context.getSystemService(Context.ACTIVITY_SERVICE).let { it as? ActivityManager }?.let { am ->
            val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            out.write("device_avail_mem_mb=${info.availMem / (1024 * 1024)} low_memory=${info.lowMemory} memory_class_mb=${am.memoryClass}\n")
        }
        out.write("\n--------- 摘要\n")
        out.write(LagReport.summarize(lines))
        out.write("\n--------- 明细（旧 → 新）\n")
        lines.forEach {
            out.write(it)
            out.write("\n")
        }
    }

    fun clear() {
        flush()
        store?.clear()
        inputViewBuilds.set(0)
        if (enabled) log("diagnostics_cleared", memorySnapshot())
    }

    private fun flush() {
        runCatching { writer.submit {}.get() }
    }

    private fun processUptimeSeconds(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()) / 1_000
    } else {
        -1
    }

    private fun offset(tz: TimeZone, at: Date): String {
        val minutes = tz.getOffset(at.time) / 60_000
        val sign = if (minutes >= 0) "+" else "-"
        val abs = kotlin.math.abs(minutes)
        return if (abs % 60 == 0) "$sign${abs / 60}" else "$sign${abs / 60}:${"%02d".format(abs % 60)}"
    }
}
