// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.diagnostics

/**
 * Pairs a key touch with the candidate update that touch caused, and records
 * the stages in between so a later log can show where time went.
 *
 * The clock starts on a key-down, records key-up / dispatch / rime / main /
 * layout / draw as they happen, and yields a duration when that list is laid
 * out. A later touch replaces an unused stamp, so idle time between taps is
 * never counted. Hide/show clears any in-flight stamp so a layout after the
 * window comes back cannot inherit a touch from the previous session.
 *
 * [PathTiming.totalMs] is measured from key-up when one was recorded, falling
 * back to key-down otherwise. Physical hold time (key-down to key-up) is a
 * user gesture, not software latency, so it is reported separately as
 * [PathTiming.downToUpMs] and never folded into the slow-path threshold.
 *
 * All timestamps are [android.os.SystemClock.uptimeMillis] values. A stage
 * that was not marked is reported as -1 and omitted from [PathTiming.details].
 */
class TouchToCandidatesClock {
    private var touchAt = 0L
    private var touchUpAt = 0L
    private var dispatchAt = 0L
    private var rimeStartAt = 0L
    private var rimeDoneAt = 0L
    private var mainReceiveAt = 0L
    private var layoutAt = 0L
    private var awaitingCandidateUpdate = false
    private var awaitingLayout = false
    private var awaitingDraw = false

    @Synchronized
    fun onTouchDown(uptimeMs: Long) {
        clearLocked()
        touchAt = uptimeMs
        awaitingCandidateUpdate = true
    }

    @Synchronized
    fun onTouchUp(uptimeMs: Long) {
        if (touchAt == 0L) return
        touchUpAt = uptimeMs
    }

    @Synchronized
    fun onDispatched(uptimeMs: Long) {
        if (touchAt == 0L) return
        dispatchAt = uptimeMs
    }

    @Synchronized
    fun onRimeStarted(uptimeMs: Long) {
        if (touchAt == 0L) return
        rimeStartAt = uptimeMs
    }

    @Synchronized
    fun onRimeFinished(uptimeMs: Long) {
        if (touchAt == 0L) return
        rimeDoneAt = uptimeMs
    }

    @Synchronized
    fun onCandidateUpdate(empty: Boolean, uptimeMs: Long = 0L) {
        if (empty || !awaitingCandidateUpdate || touchAt == 0L) {
            if (empty) clearLocked()
            return
        }
        awaitingCandidateUpdate = false
        awaitingLayout = true
        mainReceiveAt = uptimeMs
    }

    /** @return elapsed ms from key-up (or key-down when no up was seen), or null if this layout does not belong to one. */
    @Synchronized
    internal fun onCandidatesLaidOut(nowUptimeMs: Long): PathTiming? {
        if (!awaitingLayout || touchAt == 0L) return null
        layoutAt = nowUptimeMs
        awaitingLayout = false
        awaitingDraw = true
        return snapshotLocked(drawAt = 0L, includeDraw = false)
    }

    /**
     * Marks the frame that first draws the laid-out candidates.
     * @return the same path with [PathTiming.layoutToDrawMs] filled in, or null if this draw
     * is not waiting for one.
     */
    @Synchronized
    internal fun onFrameDrawn(nowUptimeMs: Long): PathTiming? {
        if (!awaitingDraw || touchAt == 0L || layoutAt == 0L) return null
        awaitingDraw = false
        val timing = snapshotLocked(drawAt = nowUptimeMs, includeDraw = true)
        // Keep the pairing finished: a later layout must not reuse this touch.
        clearLocked()
        return timing
    }

    @Synchronized
    fun onWindowTransition() {
        clearLocked()
    }

    @Synchronized
    fun hasPendingTouch(): Boolean = touchAt != 0L && (awaitingCandidateUpdate || awaitingLayout)

    private fun snapshotLocked(drawAt: Long, includeDraw: Boolean): PathTiming {
        val touch = touchAt
        // Physical hold time is a user gesture, not software latency. Start the
        // slow-path clock at key-up when we saw one, falling back to key-down.
        val start = touchUpAt.takeIf { it > 0L } ?: touch
        return PathTiming(
            totalMs = (layoutAt - start).coerceAtLeast(0L),
            touchToDispatchMs = stage(touch, dispatchAt),
            downToUpMs = stage(touch, touchUpAt),
            upToDispatchMs = stage(touchUpAt, dispatchAt),
            dispatchToRimeMs = stage(dispatchAt, rimeStartAt),
            rimeMs = stage(rimeStartAt, rimeDoneAt),
            rimeToMainMs = stage(rimeDoneAt, mainReceiveAt),
            mainToLayoutMs = stage(mainReceiveAt, layoutAt),
            layoutToDrawMs = if (includeDraw) stage(layoutAt, drawAt) else -1L,
        )
    }

    private fun clearLocked() {
        touchAt = 0L
        touchUpAt = 0L
        dispatchAt = 0L
        rimeStartAt = 0L
        rimeDoneAt = 0L
        mainReceiveAt = 0L
        layoutAt = 0L
        awaitingCandidateUpdate = false
        awaitingLayout = false
        awaitingDraw = false
    }

    companion object {
        /** Elapsed ms between two marks, or -1 if either was not recorded. */
        private fun stage(startMs: Long, endMs: Long): Long = if (startMs == 0L || endMs == 0L) -1L else (endMs - startMs).coerceAtLeast(0L)
    }
}

/**
 * Sub-timings of one touch-to-candidates path. Missing stages are -1 and are
 * left out of [details], so the exported log never invents a number.
 */
internal data class PathTiming(
    val totalMs: Long,
    val touchToDispatchMs: Long = -1L,
    val downToUpMs: Long = -1L,
    val upToDispatchMs: Long = -1L,
    val dispatchToRimeMs: Long = -1L,
    val rimeMs: Long = -1L,
    val rimeToMainMs: Long = -1L,
    val mainToLayoutMs: Long = -1L,
    val layoutToDrawMs: Long = -1L,
) {
    fun details(): String = buildString {
        appendStage("touch_to_dispatch_ms", touchToDispatchMs)
        appendStage("down_to_up_ms", downToUpMs)
        appendStage("up_to_dispatch_ms", upToDispatchMs)
        appendStage("dispatch_to_rime_ms", dispatchToRimeMs)
        appendStage("rime_ms", rimeMs)
        appendStage("rime_to_main_ms", rimeToMainMs)
        appendStage("main_to_layout_ms", mainToLayoutMs)
        appendStage("layout_to_draw_ms", layoutToDrawMs)
    }.trim()

    private fun StringBuilder.appendStage(name: String, value: Long) {
        if (value < 0L) return
        if (isNotEmpty()) append(' ')
        append(name).append('=').append(value)
    }
}
