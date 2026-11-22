/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

/**
 * Decides which candidates get a real view. Everything outside the returned range stays a
 * fixed-width spacer, so the row can still scroll to its full width without measuring a
 * ConstraintLayout for every off-screen candidate.
 *
 * A null result means the whole row fits and every item should be real.
 */
fun compactCandidateInitialWindow(
    widths: List<Int>,
    barWidth: Int,
    separatorWidth: Int,
): IntRange? {
    if (widths.isEmpty() || barWidth <= 0) return null
    val last = fittingEnd(widths, 0, barWidth, separatorWidth)
    return if (last == widths.lastIndex) null else 0..last
}

/**
 * Real views cover the current viewport plus one bar of prefetch on each side. The prefetch
 * is bound while the row is moving, so a fling does not reveal an unbound spacer.
 */
fun compactCandidateScrollWindow(
    widths: List<Int>,
    scrollOffset: Int,
    barWidth: Int,
    separatorWidth: Int,
): IntRange? {
    if (widths.isEmpty() || barWidth <= 0) return null
    val offset = scrollOffset.coerceAtLeast(0)
    val first = itemAt(widths, offset, separatorWidth)
    val last = fittingEnd(widths, first, barWidth, separatorWidth)
    val prefetchStart = itemAt(widths, (offset - barWidth).coerceAtLeast(0), separatorWidth)
    val prefetchEnd = fittingEnd(widths, last, barWidth, separatorWidth)
    val start = minOf(first, prefetchStart)
    val end = maxOf(last, prefetchEnd)
    return if (start == 0 && end == widths.lastIndex) null else start..end
}

private fun itemAt(widths: List<Int>, offset: Int, separatorWidth: Int): Int {
    var cursor = 0
    for (index in widths.indices) {
        val next = cursor + widths[index] + if (index == widths.lastIndex) 0 else separatorWidth
        if (offset < next) return index
        cursor = next
    }
    return widths.lastIndex
}

/** Last item whose leading edge still starts inside [span] px from [start]. */
private fun fittingEnd(widths: List<Int>, start: Int, span: Int, separatorWidth: Int): Int {
    var used = 0
    var end = start
    for (index in start..widths.lastIndex) {
        val gap = if (index == start) 0 else separatorWidth
        val leading = used + gap
        if (index != start && leading >= span) break
        used = leading + widths[index]
        end = index
    }
    return end
}
