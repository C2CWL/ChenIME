/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * Click rules for toolbar actions that must not fire from a stray or
 * neighboring touch, especially the voice button next to Hide.
 */
object DeliberateClick {

    const val HIDE_DEBOUNCE_MS = 400L

    /** True when the finger is still inside the view at ACTION_UP. */
    fun isReleaseInside(x: Float, y: Float, width: Int, height: Int): Boolean = width > 0 && height > 0 && x >= 0f && y >= 0f && x < width && y < height

    /**
     * Whether a click should run. [requireReleaseInside] cancels a lift that
     * started on this view but ended outside it (a slide onto a neighbor).
     */
    fun shouldFireClick(
        gestureStarted: Boolean,
        requireReleaseInside: Boolean,
        upX: Float,
        upY: Float,
        width: Int,
        height: Int,
    ): Boolean {
        if (!gestureStarted) return false
        if (requireReleaseInside && !isReleaseInside(upX, upY, width, height)) return false
        return true
    }

    /**
     * A press in the slop strip beside Hide is treated as a missed Hide tap.
     * At most one third of the button is reserved so the rest stays tappable.
     */
    fun isNearHideEdge(
        downX: Float,
        width: Int,
        slopPx: Float,
        hideOnRight: Boolean,
    ): Boolean {
        if (slopPx <= 0f || width <= 0) return false
        val slop = slopPx.coerceAtMost(width / 3f)
        return if (hideOnRight) downX >= width - slop else downX <= slop
    }

    /** Ignore a voice click that arrives just after Hide already ran. */
    fun isWithinHideDebounce(
        lastHideUptimeMs: Long,
        nowMs: Long,
        windowMs: Long = HIDE_DEBOUNCE_MS,
    ): Boolean = lastHideUptimeMs > 0L && nowMs >= lastHideUptimeMs && nowMs - lastHideUptimeMs < windowMs
}
