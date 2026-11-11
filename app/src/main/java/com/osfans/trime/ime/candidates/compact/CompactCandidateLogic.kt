// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.compact

internal data class CompactBarItem(
    val index: Int,
    val width: Float,
    val ellipsized: Boolean,
)

/**
 * Pack compact-bar candidates onto a single row.
 * A candidate that is wider than the remaining space is still shown, shrunk
 * (to be ellipsized or auto-scaled) into that space, and later items wait
 * for the expanded panel. Long items are never skipped, and they must not
 * hide the candidates after them by wrapping off the row.
 */
internal fun layoutCompactCandidateBar(
    desiredWidths: List<Float>,
    barWidth: Float,
    separatorWidth: Float = 0f,
): List<CompactBarItem> {
    if (barWidth <= 0f || desiredWidths.isEmpty()) return emptyList()
    val items = ArrayList<CompactBarItem>()
    var used = 0f
    for ((index, desired) in desiredWidths.withIndex()) {
        val content = desired.coerceAtLeast(0f)
        val separator = if (items.isEmpty()) 0f else separatorWidth.coerceAtLeast(0f)
        val remaining = barWidth - used - separator
        if (remaining <= 0f) break
        val ellipsized = content > remaining
        val width = if (ellipsized) remaining else content
        if (width <= 0f) break
        items += CompactBarItem(index = index, width = width, ellipsized = ellipsized)
        used += separator + width
        if (ellipsized) break
    }
    return items
}
