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
 * Chooses the window for a fresh publish. Only an explicit [forceStart] (a clear gesture or a
 * new composition) binds the first screen. Otherwise the view's offset is authoritative even
 * after the gesture became idle: a swipe can stop mid-row and the next typed character
 * publishes at that offset. Falling back to the first screen here turns every visible
 * candidate into a spacer, leaving the bar blank until the next scroll.
 */
fun compactCandidatePublishWindow(
    widths: List<Int>,
    forceStart: Boolean,
    scrollOffset: Int,
    barWidth: Int,
    separatorWidth: Int,
): IntRange? {
    if (widths.isEmpty() || barWidth <= 0) return null
    return if (forceStart) {
        compactCandidateInitialWindow(widths, barWidth, separatorWidth)
    } else {
        compactCandidateVisibleWindow(widths, scrollOffset, barWidth, separatorWidth)
    }
}

/**
 * Chooses the window for a real scroll callback. The offset came from the view, so it stays
 * authoritative after the gesture becomes idle. Falling back to the initial window here is
 * what leaves visible positions as spacers when a right swipe stops mid-row.
 *
 * Real views cover the current viewport plus one bar of prefetch on each side, so a fling
 * does not reveal an unbound spacer.
 */
fun compactCandidateVisibleWindow(
    widths: List<Int>,
    scrollOffset: Int,
    barWidth: Int,
    separatorWidth: Int,
): IntRange? {
    if (widths.isEmpty() || barWidth <= 0) return null
    if (scrollOffset <= 0) return compactCandidateInitialWindow(widths, barWidth, separatorWidth)
    val contentWidth = widths.sum() + separatorWidth * (widths.size - 1).coerceAtLeast(0)
    val maxScroll = (contentWidth - barWidth).coerceAtLeast(0)
    return if (scrollOffset > maxScroll) {
        compactCandidateInitialWindow(widths, barWidth, separatorWidth)
    } else {
        compactCandidateScrollWindow(widths, scrollOffset, barWidth, separatorWidth)
    }
}

fun compactCandidateScrollWindow(
    widths: List<Int>,
    scrollOffset: Int,
    barWidth: Int,
    separatorWidth: Int,
): IntRange? {
    if (widths.isEmpty() || barWidth <= 0) return null
    val offset = scrollOffset.coerceAtLeast(0)
    val first = itemAt(widths, offset, separatorWidth)
    // The anchor item may already be partly past the measured edge. A full bar counted
    // from its leading edge stops short, and the candidate sliding in on the right stays
    // a spacer — the blank slot in the middle of the row.
    val last = fittingEnd(
        widths,
        first,
        coveredSpan(widths, first, offset, barWidth, separatorWidth),
        separatorWidth,
    )
    val prefetchStart = itemAt(widths, (offset - barWidth).coerceAtLeast(0), separatorWidth)
    val prefetchEnd = fittingEnd(widths, last, barWidth, separatorWidth)
    val start = minOf(first, prefetchStart)
    val end = maxOf(last, prefetchEnd)
    return if (start == 0 && end == widths.lastIndex) null else start..end
}

/** Content x of [index]'s leading edge, including separators before it. */
private fun leadingEdge(widths: List<Int>, index: Int, separatorWidth: Int): Int {
    if (index <= 0) return 0
    var edge = 0
    for (i in 0 until index) {
        edge += widths[i] + separatorWidth
    }
    return edge
}

/**
 * How far [fittingEnd] must look, starting at [anchor], to reach [distance] px past [edge].
 * [edge] is the viewport's left edge and [anchor] is the item containing it, so the part of
 * that item already off-screen has to be added back. Otherwise the candidate entering on the
 * right stays outside the window.
 */
private fun coveredSpan(
    widths: List<Int>,
    anchor: Int,
    edge: Int,
    distance: Int,
    separatorWidth: Int,
): Int = distance + (edge - leadingEdge(widths, anchor, separatorWidth)).coerceAtLeast(0)

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
