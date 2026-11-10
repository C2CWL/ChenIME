/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import kotlin.math.abs

/** Input for [KeyboardGeometry.layout]: one YAML key after width-weight resolution. */
internal data class KeyLayoutSpec(
    val index: Int,
    val widthWeight: Float,
    val rowHeightHint: Int,
    val rowSpan: Int = 1,
    val clickable: Boolean = true,
)

/** Resulting hitbox of a clickable key after wrapping, scaling and row spans. */
internal data class PlacedKey(
    val index: Int,
    val row: Int,
    val column: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rowSpan: Int,
)

internal data class LayoutSpacer(
    val x: Int,
    val width: Int,
    val row: Int,
)

internal data class KeyboardPlacement(
    val keys: List<PlacedKey>,
    val height: Int,
    val minWidth: Int,
    val lastRow: Int,
    val spacers: List<LayoutSpacer>,
)

/**
 * Pure keyboard geometry: left-to-right wrap, row-height scaling, optional
 * reserved leading column, and vertical [KeyLayoutSpec.rowSpan]. Extracted so
 * hitboxes can be unit-tested without an Android [android.content.Context].
 */
internal object KeyboardGeometry {
    const val MAX_TOTAL_WEIGHT = 100

    fun scaleHeights(
        rawHeights: List<Int>,
        target: Int,
    ): List<Int> {
        if (rawHeights.isEmpty()) return emptyList()
        val sum = rawHeights.sum().coerceAtLeast(1)
        val scaled = MutableList(rawHeights.size) { 0 }
        var remain = target
        val scale = target.toFloat() / sum
        for (i in 0 until rawHeights.lastIndex) {
            val h = (rawHeights[i] * scale).toInt()
            scaled[i] = h
            remain -= h
        }
        scaled[rawHeights.lastIndex] = remain
        return scaled
    }

    fun layout(
        specs: List<KeyLayoutSpec>,
        allowedWidth: Int,
        keyboardHeight: Int,
        defaultKeyHeight: Int,
        maxColumns: Int,
        reservedLeadingWeight: Float = 0f,
        isSplit: Boolean = false,
        splitRatio: Float = 0f,
        expandKeypressArea: Boolean = false,
    ): KeyboardPlacement {
        if (specs.isEmpty()) {
            return KeyboardPlacement(emptyList(), keyboardHeight, 0, 0, emptyList())
        }

        val reservedLeadingPx = (reservedLeadingWeight * allowedWidth / MAX_TOTAL_WEIGHT).toInt()

        val rowWidthTotalWeight = mutableListOf<Float>()
        val rowRawHeight = mutableListOf<Int>()

        var x = reservedLeadingPx
        var column = 0
        var rowHeight = defaultKeyHeight
        var totalKeyWidth = reservedLeadingWeight

        for (key in specs) {
            val widthPx = (key.widthWeight * allowedWidth / MAX_TOTAL_WEIGHT).toInt()
            if (column >= maxColumns || x + widthPx > allowedWidth) {
                rowWidthTotalWeight.add(totalKeyWidth)
                rowRawHeight.add(rowHeight)
                x = reservedLeadingPx
                column = 0
                totalKeyWidth = reservedLeadingWeight
            }
            if (column == 0) {
                // A spanning key must not inflate the current row; later rows add the extra height.
                rowHeight =
                    if (key.rowSpan <= 1 && key.rowHeightHint > 0) key.rowHeightHint else defaultKeyHeight
            }
            totalKeyWidth += key.widthWeight
            if (key.clickable) {
                column++
            }
            x += widthPx
        }
        rowWidthTotalWeight.add(totalKeyWidth)
        rowRawHeight.add(rowHeight)

        val rowHeightScaled = scaleHeights(rowRawHeight, keyboardHeight)
        val rows = rowHeightScaled.size
        val oneWeightWidthPx =
            allowedWidth.toFloat() / (MAX_TOTAL_WEIGHT * (1 + if (isSplit) splitRatio else 0f))

        val reserved = Array(rows) { mutableListOf<Pair<Int, Int>>() }

        var xPos = reservedLeadingPx
        var yPos = 0
        var row = 0
        column = 0
        var rowWeightAccumulo = reservedLeadingWeight
        var currentRowHeight = rowHeightScaled[0]
        var splitInserted = false
        var minWidth = 0
        val spacers = mutableListOf<LayoutSpacer>()
        val placed = mutableListOf<PlacedKey>()

        fun skipReserved(
            start: Int,
            rowIdx: Int,
        ): Int {
            var cur = start
            val ranges = reserved.getOrNull(rowIdx) ?: return cur
            var moved = true
            while (moved) {
                moved = false
                for ((from, until) in ranges) {
                    if (cur in from until until) {
                        cur = until
                        moved = true
                    }
                }
            }
            return cur
        }

        fun startNextRow() {
            xPos = reservedLeadingPx
            yPos += currentRowHeight
            row++
            column = 0
            rowWeightAccumulo = reservedLeadingWeight
            splitInserted = false
            currentRowHeight = rowHeightScaled[row]
            xPos = skipReserved(xPos, row)
        }

        for (spec in specs) {
            var widthPx = (spec.widthWeight * oneWeightWidthPx).toInt()

            if (column >= maxColumns || xPos + widthPx > allowedWidth) {
                startNextRow()
            }
            xPos = skipReserved(xPos, row)
            if (column > 0 && xPos + widthPx > allowedWidth) {
                startNextRow()
            }

            rowWeightAccumulo += spec.widthWeight
            val totalWeight = rowWidthTotalWeight[row]
            if (isSplit && !splitInserted && rowWeightAccumulo > totalWeight * 0.5f) {
                splitInserted = true
                val gap = (totalWeight * splitRatio * oneWeightWidthPx).toInt()
                if (spec.widthWeight > 20f) {
                    widthPx += gap
                } else {
                    if (expandKeypressArea) spacers.add(LayoutSpacer(xPos, gap, row))
                    xPos += gap
                }
            }

            if (!spec.clickable) {
                if (expandKeypressArea) spacers.add(LayoutSpacer(xPos, widthPx, row))
                xPos += widthPx
                continue
            }

            val span = spec.rowSpan.coerceIn(1, (rows - row).coerceAtLeast(1))
            var keyHeight = 0
            for (r in row until row + span) {
                keyHeight += rowHeightScaled[r]
            }

            val rightGap = abs(allowedWidth - xPos - widthPx)
            val keyWidth = if (rightGap <= allowedWidth / 100) allowedWidth - xPos else widthPx

            placed.add(
                PlacedKey(
                    index = spec.index,
                    row = row,
                    column = column,
                    x = xPos,
                    y = yPos,
                    width = keyWidth,
                    height = keyHeight,
                    rowSpan = span,
                ),
            )

            if (span > 1) {
                val occupied = xPos to (xPos + keyWidth)
                for (r in row + 1 until row + span) {
                    reserved[r].add(occupied)
                }
            }

            column++
            xPos += keyWidth
            if (xPos > minWidth) {
                minWidth = xPos
            }
        }

        return KeyboardPlacement(
            keys = placed,
            height = rowHeightScaled.sum(),
            minWidth = minWidth,
            lastRow = (rows - 1).coerceAtLeast(0),
            spacers = spacers,
        )
    }
}
