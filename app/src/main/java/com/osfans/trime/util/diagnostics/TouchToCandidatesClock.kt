// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.diagnostics

/**
 * Pairs a key touch with the candidate update that touch caused.
 *
 * The clock starts on a key-down, moves to "awaiting layout" when a candidate
 * list update arrives after that touch, and yields a duration when that list
 * is laid out. A later touch replaces an unused stamp, so idle time between
 * taps is never counted. Hide/show clears any in-flight stamp so a layout
 * after the window comes back cannot inherit a touch from the previous session.
 */
class TouchToCandidatesClock {
    private var pendingTouchUptime = 0L
    private var awaitingCandidateUpdate = false
    private var awaitingLayout = false

    @Synchronized
    fun onTouchDown(uptimeMs: Long) {
        pendingTouchUptime = uptimeMs
        awaitingCandidateUpdate = true
        awaitingLayout = false
    }

    @Synchronized
    fun onCandidateUpdate(empty: Boolean) {
        if (empty || !awaitingCandidateUpdate || pendingTouchUptime == 0L) {
            if (empty) clearLocked()
            return
        }
        awaitingCandidateUpdate = false
        awaitingLayout = true
    }

    /** @return elapsed ms from the paired touch, or null if this layout does not belong to one. */
    @Synchronized
    fun onCandidatesLaidOut(nowUptimeMs: Long): Long? {
        if (!awaitingLayout || pendingTouchUptime == 0L) return null
        val started = pendingTouchUptime
        clearLocked()
        return (nowUptimeMs - started).coerceAtLeast(0L)
    }

    @Synchronized
    fun onWindowTransition() {
        clearLocked()
    }

    @Synchronized
    fun hasPendingTouch(): Boolean = pendingTouchUptime != 0L

    private fun clearLocked() {
        pendingTouchUptime = 0L
        awaitingCandidateUpdate = false
        awaitingLayout = false
    }
}
