// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.compact

internal data class CompactBarItem(
    val index: Int,
    val width: Float,
    val ellipsized: Boolean,
)

/**
 * Width of one compact-bar candidate's text and comment, not including cell padding.
 *
 * A user-dict "*" or sentence "∞" still counts: those marks are drawn beside
 * the text, and omitting them makes the cell too narrow, clipping both the
 * glyph and the mark. [commentGap] is the space between text and a right-side
 * comment, matching [com.osfans.trime.ime.candidates.CANDIDATE_COMMENT_GAP_DP].
 */
internal fun compactCandidateContentWidth(
    textWidth: Float,
    comment: String,
    measuredCommentWidth: Float,
    commentOnRight: Boolean,
    commentGap: Float = 0f,
): Float {
    val text = textWidth.coerceAtLeast(0f)
    if (comment.isEmpty()) return text
    val commentWidth = measuredCommentWidth.coerceAtLeast(0f)
    return if (commentOnRight) {
        text + commentGap.coerceAtLeast(0f) + commentWidth
    } else {
        maxOf(text, commentWidth)
    }
}

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
