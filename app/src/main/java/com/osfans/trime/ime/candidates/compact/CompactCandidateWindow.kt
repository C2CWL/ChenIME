// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

/**
 * Adapter positions that should keep a full candidate view.
 *
 * A NOWRAP Flexbox row measures every child, including ones the bar clips.
 * A full candidate is a ConstraintLayout, so measuring all 16 on every key
 * costs 10-60ms. Positions outside this window stay in the list as
 * fixed-width spacers: scroll range, clicks and the expanded panel still
 * use the original positions, but the layout pass does not build them.
 *
 * [slack] items on each side are included so the next scroll step already
 * has a real view. Unknown bounds bind everything.
 */
internal fun compactCandidateFullWindow(
    firstVisible: Int,
    lastVisible: Int,
    itemCount: Int,
    slack: Int = 1,
): IntRange {
    if (itemCount <= 0) return IntRange.EMPTY
    val lastIndex = itemCount - 1
    val first = firstVisible.coerceIn(0, lastIndex)
    val last = lastVisible.coerceIn(first, lastIndex)
    val pad = slack.coerceAtLeast(0)
    return (first - pad).coerceAtLeast(0)..(last + pad).coerceAtMost(lastIndex)
}

/** How many leading items fit before the row must scroll. */
internal fun compactCandidateInitialWindow(
    widths: List<Int>,
    barWidth: Int,
    separator: Int,
    slack: Int = 1,
): IntRange {
    if (widths.isEmpty()) return IntRange.EMPTY
    if (barWidth <= 0) return widths.indices
    val gap = separator.coerceAtLeast(0)
    var used = 0
    var last = 0
    for (index in widths.indices) {
        val next = used + widths[index].coerceAtLeast(0)
        if (index > 0 && next > barWidth) break
        used = next + gap
        last = index
    }
    return compactCandidateFullWindow(0, last, widths.size, slack)
}
