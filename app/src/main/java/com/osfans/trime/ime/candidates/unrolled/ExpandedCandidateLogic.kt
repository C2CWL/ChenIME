// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import kotlin.math.ceil

internal const val EXPANDED_GRID_MAX_COLUMNS = 6

/**
 * Uniform inset on each side of a pill. Two neighboring pills then sit [gap]
 * apart both horizontally and vertically.
 */
internal fun expandedPillMargin(gap: Int): Int = if (gap <= 0) 0 else (gap / 2).coerceAtLeast(1)

/**
 * Visible pill width inside a span of [spanWidth], after the same [gap] used
 * between every pair of pills. A two-column pill is then exactly two
 * one-column pills plus one gutter: (2 * cell) - gap = 2 * (cell - gap) + gap.
 */
internal fun expandedPillWidth(spanWidth: Float, gap: Float): Float = (spanWidth - gap).coerceAtLeast(0f)

/**
 * Minimum column width so a two-character word (such as 蛤蛤) fits in one
 * column after inner padding and the inter-pill gap. Typical four-character
 * words then take one or two columns.
 */
internal fun expandedMinCellWidth(
    twoCharTextWidth: Float,
    innerPadding: Float,
    gap: Float,
): Float = (twoCharTextWidth + innerPadding + gap).coerceAtLeast(1f)

internal fun expandedGridGapPx(spacingDp: Float, spacingPx: Int): Int = if (spacingDp <= 0f) 0 else maxOf(spacingPx, 2)

internal fun expandedGridColumns(
    gridWidth: Float,
    minCellWidth: Float,
    maxColumns: Int = EXPANDED_GRID_MAX_COLUMNS,
): Int {
    val upper = maxColumns.coerceAtLeast(1)
    if (gridWidth <= 0f || minCellWidth <= 0f) return upper
    return (gridWidth / minCellWidth).toInt().coerceIn(1, upper)
}

internal fun candidateSpan(width: Float, cellWidth: Float, columns: Int = EXPANDED_GRID_MAX_COLUMNS): Int = if (cellWidth <= 0f) 1 else ceil(width / cellWidth).toInt().coerceIn(1, columns)

internal data class ExpandedGridCell(
    val index: Int,
    val row: Int,
    val column: Int,
    val span: Int,
    val left: Float,
    val width: Float,
)

/**
 * Place candidates on a uniform column grid.
 * Long items take a whole number of columns. If the next item needs more
 * columns than remain, the leftover is given to the previous item so the
 * row has no hole, and the long item starts the next row. Nothing is dropped.
 */
internal fun layoutExpandedCandidateGrid(
    itemWidths: List<Float>,
    gridWidth: Float,
    columns: Int = EXPANDED_GRID_MAX_COLUMNS,
): List<ExpandedGridCell> {
    require(columns > 0)
    val cellWidth = if (gridWidth <= 0f) 0f else gridWidth / columns
    val cells = ArrayList<ExpandedGridCell>(itemWidths.size)
    var row = 0
    var column = 0
    itemWidths.forEachIndexed { index, itemWidth ->
        var span = candidateSpan(itemWidth, cellWidth, columns)
        val remaining = columns - column
        if (span > remaining && column > 0) {
            val prev = cells.last()
            if (prev.row == row) {
                val filledSpan = prev.span + remaining
                cells[cells.lastIndex] = prev.copy(
                    span = filledSpan,
                    width = filledSpan * cellWidth,
                )
            }
            row++
            column = 0
            span = candidateSpan(itemWidth, cellWidth, columns)
        }
        cells += ExpandedGridCell(
            index = index,
            row = row,
            column = column,
            span = span,
            left = column * cellWidth,
            width = span * cellWidth,
        )
        column += span
        if (column >= columns) {
            row++
            column = 0
        }
    }
    return cells
}

/** A lone user-dict star is drawn as a badge and must not steal a grid column. */
internal const val USER_DICTIONARY_MARK = "*"

internal fun spanCommentWidth(comment: String, measuredCommentWidth: Float): Float = if (comment.isEmpty() || comment == USER_DICTIONARY_MARK) 0f else measuredCommentWidth

internal fun candidateLayoutWidth(
    textWidth: Float,
    comment: String,
    measuredCommentWidth: Float,
    commentOnRight: Boolean,
): Float {
    val commentWidth = spanCommentWidth(comment, measuredCommentWidth)
    return if (commentOnRight) textWidth + commentWidth else maxOf(textWidth, commentWidth)
}

internal fun expandedRowHeightDp(candidateViewHeight: Int, commentHeight: Int): Int = (candidateViewHeight + commentHeight).coerceAtLeast(44)

internal fun isSingleCharacter(text: String): Boolean = text.codePointCount(0, text.length) == 1

/** Only expose boundaries supplied by the engine; retain repeated syllables. */
internal fun preeditSyllables(preedit: String?): List<String> = preedit.orEmpty()
    .split(Regex("[^a-zA-ZüÜāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+"))
    .filter { it.isNotEmpty() }

/** Positions are raw input byte offsets; pinyin raw input is ASCII. */
internal fun syllableInputEnd(raw: String, start: Int, syllables: List<String>, index: Int, candidateEnds: List<Int>): Int? {
    if (start !in 0..raw.length || index !in syllables.indices) return null
    fun normalized(c: Char) = if (c.lowercaseChar() == 'ü') 'v' else c.lowercaseChar()
    var position = start
    var aligned = true
    for (syllable in syllables.take(index + 1)) {
        for (letter in syllable) {
            while (position < raw.length && (raw[position] == '\'' || raw[position] == ' ')) position++
            if (position >= raw.length || normalized(raw[position]) != normalized(letter)) {
                aligned = false
                break
            }
            position++
        }
        if (!aligned) break
    }
    if (aligned) return position
    val ends = candidateEnds.filter { it > start }.distinct().sorted()
    return ends.getOrNull(index.coerceAtMost(ends.lastIndex).coerceAtLeast(0))
}
