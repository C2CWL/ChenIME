// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

/**
 * How many compact-bar candidates should be bound.
 *
 * The compact row is one line tall, so Flexbox already clips anything that
 * wraps. Binding the whole Rime page still makes Flexbox measure every item
 * (AutoScale + ConstraintLayout), which is the 20–60ms main-thread cost.
 * This keeps the visible prefix plus the first overflowing item (that item
 * is clipped today; after PR #28 it is shrunk into the leftover space).
 *
 * When [barWidth] is not yet known, the full list is returned so the first
 * layout still has data, matching the previous behavior.
 */
internal fun compactCandidateBindLimit(
    widths: List<Int>,
    barWidth: Int,
    separator: Int,
): Int {
    if (barWidth <= 0 || widths.isEmpty()) return widths.size
    var used = 0
    val gap = separator.coerceAtLeast(0)
    widths.forEachIndexed { index, raw ->
        val width = raw.coerceAtLeast(0)
        used += (if (index == 0) 0 else gap) + width
        if (used > barWidth) return index + 1
    }
    return widths.size
}
