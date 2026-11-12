/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.util.diagnostics

import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * Android-free pieces of the lag diagnostics, kept here so they can be unit tested on the JVM.
 *
 * Every persisted line has the shape `<date> <time> [<thread>] <category> <details>`, where details
 * may carry `dur_ms=<n>` and the word `SLOW`. No typed text, candidates or key codes are ever written.
 */
internal object LagLine {
    const val SLOW = "SLOW"

    private val pattern = Regex("""^(\S+ \S+) \[([^\]]*)] (\S+) ?(.*)$""")
    private val duration = Regex("""\bdur_ms=(\d+)""")

    data class Parsed(val time: String, val thread: String, val category: String, val details: String) {
        val durationMs: Long? get() = duration.find(details)?.groupValues?.get(1)?.toLongOrNull()
        val slow: Boolean get() = details.split(' ').contains(SLOW)
    }

    fun format(time: String, thread: String, category: String, details: String): String {
        // keep one event per line, whatever a caller passes in
        val safeDetails = details.replace('\n', ' ').replace('\r', ' ').trim()
        return if (safeDetails.isEmpty()) "$time [$thread] $category" else "$time [$thread] $category $safeDetails"
    }

    fun parse(line: String): Parsed? = pattern.find(line)?.destructured?.let { (time, thread, category, details) ->
        Parsed(time, thread, category, details)
    }

    fun durationDetails(durationMs: Long, slowMs: Long, extra: String = ""): String = buildString {
        append("dur_ms=").append(durationMs)
        if (durationMs >= slowMs) append(' ').append(SLOW)
        if (extra.isNotBlank()) append(' ').append(extra.trim())
    }
}

/** Two-file capped log: when `lag.log` reaches [maxBytes] it becomes `lag.log.1` and the oldest file is dropped. */
internal class LagLogStore(private val dir: File, private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val current get() = File(dir, CURRENT)
    private val previous get() = File(dir, PREVIOUS)

    @Synchronized
    fun append(lines: List<String>) {
        if (lines.isEmpty()) return
        dir.mkdirs()
        if (current.length() >= maxBytes) {
            previous.delete()
            current.renameTo(previous)
        }
        FileOutputStream(current, true).bufferedWriter(Charsets.UTF_8).use { out ->
            lines.forEach {
                out.write(it)
                out.write("\n")
            }
        }
    }

    @Synchronized
    fun readAll(): List<String> = listOf(previous, current).filter { it.isFile }.flatMap { it.readLines(Charsets.UTF_8) }

    @Synchronized
    fun sizeBytes(): Long = listOf(previous, current).filter { it.isFile }.sumOf { it.length() }

    @Synchronized
    fun clear() {
        current.delete()
        previous.delete()
    }

    companion object {
        const val CURRENT = "lag.log"
        const val PREVIOUS = "lag.log.1"
        const val DEFAULT_MAX_BYTES = 1_000_000L
    }
}

/** Bounded sample of durations (newest [capacity] kept) for p50 / p95 / max. */
internal class DurationStats(private val capacity: Int = 2_000) {
    private val samples = ArrayDeque<Long>()
    var count = 0L
        private set
    var max = 0L
        private set

    @Synchronized
    fun add(value: Long) {
        count++
        if (value > max) max = value
        samples.addLast(value)
        if (samples.size > capacity) samples.removeFirst()
    }

    @Synchronized
    fun percentile(p: Double): Long = percentileOf(samples.toList(), p)

    @Synchronized
    fun reset() {
        samples.clear()
        count = 0
        max = 0
    }

    fun describe(name: String): String = if (count == 0L) {
        "$name=0"
    } else {
        "$name=$count ${name}_p50_ms=${percentile(0.50)} ${name}_p95_ms=${percentile(0.95)} ${name}_max_ms=$max"
    }

    companion object {
        /** Nearest-rank percentile; 0 for an empty list. */
        fun percentileOf(values: List<Long>, p: Double): Long {
            if (values.isEmpty()) return 0
            val sorted = values.sorted()
            val rank = ceil(p.coerceIn(0.0, 1.0) * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}

/**
 * Main-thread watchdog bookkeeping. A heartbeat is posted to the main looper; if it is not
 * acknowledged within [thresholdMs] the main thread is stalled and is reported exactly once.
 */
internal class StallDetector(private val thresholdMs: Long) {
    private var postedAt = -1L
    private var reported = false

    val waiting: Boolean get() = postedAt >= 0

    fun posted(nowMs: Long) {
        postedAt = nowMs
        reported = false
    }

    /** @return how long the main thread had been blocked, the first time it crosses the threshold. */
    fun check(nowMs: Long): Long? {
        if (postedAt < 0 || reported) return null
        val waited = nowMs - postedAt
        if (waited < thresholdMs) return null
        reported = true
        return waited
    }

    /** @return total stall length if this heartbeat had been reported as a stall, else null. */
    fun acknowledged(atMs: Long): Long? {
        if (postedAt < 0) return null
        val total = atMs - postedAt
        val wasStalled = reported
        postedAt = -1
        reported = false
        return if (wasStalled) total else null
    }
}

/** Counts Choreographer frame intervals while the keyboard is visible. */
internal class FrameStats {
    var frames = 0L
        private set
    var janky = 0L
        private set
    var severe = 0L
        private set
    var maxMs = 0L
        private set

    /** @return true when this single frame is long enough to be worth its own log line. */
    fun record(intervalNs: Long, refreshPeriodNs: Long): Boolean {
        if (intervalNs <= 0) return false
        frames++
        val ms = intervalNs / 1_000_000
        if (ms > maxMs) maxMs = ms
        // more than two refresh periods means at least one frame was visibly dropped
        if (refreshPeriodNs > 0 && intervalNs > refreshPeriodNs * 2) janky++
        if (ms >= SEVERE_MS) severe++
        return ms >= LOG_FRAME_MS
    }

    fun reset() {
        frames = 0
        janky = 0
        severe = 0
        maxMs = 0
    }

    fun describe(): String = "frames=$frames janky_frames=$janky frames_over_${SEVERE_MS}ms=$severe max_frame_ms=$maxMs"

    companion object {
        const val LOG_FRAME_MS = 50L
        const val SEVERE_MS = 100L
    }
}

/** Builds the human-readable summary that heads an exported report. */
internal object LagReport {
    private data class Bucket(val count: Int, val slow: Int, val durations: List<Long>)

    fun summarize(lines: List<String>, topN: Int = 15): String = buildString {
        val parsed = lines.mapNotNull(LagLine::parse)
        appendLine("记录条数: ${parsed.size}")
        if (parsed.isEmpty()) {
            appendLine("（没有记录。请先在 设置 → 高级 打开“卡顿诊断”，复现卡顿后再导出。）")
            return@buildString
        }
        appendLine("时间范围: ${parsed.first().time} ~ ${parsed.last().time}")
        val stalls = parsed.filter { it.category == "main_stall" }
        appendLine("主线程卡死(≥${MAIN_STALL_MS}ms)次数: ${stalls.size}")
        appendLine()
        appendLine("按类别（耗时类）: 次数 / 慢次数 / p50 / p95 / 最大 (ms)")
        parsed.filter { it.durationMs != null }
            .groupBy { it.category }
            .mapValues { (_, items) -> Bucket(items.size, items.count { it.slow }, items.map { it.durationMs!! }) }
            .entries
            .sortedByDescending { it.value.durations.max() }
            .forEach { (category, b) ->
                appendLine(
                    "  %-28s %5d / %4d / %5d / %5d / %6d".format(
                        category,
                        b.count,
                        b.slow,
                        DurationStats.percentileOf(b.durations, 0.50),
                        DurationStats.percentileOf(b.durations, 0.95),
                        b.durations.max(),
                    ),
                )
            }
        val slowest = parsed.filter { it.durationMs != null }.sortedByDescending { it.durationMs }.take(topN)
        if (slowest.isNotEmpty()) {
            appendLine()
            appendLine("最慢的 ${slowest.size} 次:")
            slowest.forEach { appendLine("  ${it.time} [${it.thread}] ${it.category} ${it.details}") }
        }
    }

    const val MAIN_STALL_MS = 300L
}
