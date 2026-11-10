/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

/**
 * Even-grid sizing for the liquid symbol panel. The approved left-bar layout
 * fills the remaining width with a regular column count and matches row height
 * to the stacked action keys so visual bounds stay equal to touch hitboxes.
 */
internal object LiquidGridGeometry {
    const val PREFERRED_COLUMNS = 5

    fun columnCount(
        availableWidth: Int,
        minCell: Int,
        preferred: Int = PREFERRED_COLUMNS,
    ): Int {
        if (availableWidth <= 0) return 1
        val maxFit = (availableWidth / minCell.coerceAtLeast(1)).coerceAtLeast(1)
        return minOf(preferred.coerceAtLeast(1), maxFit)
    }

    fun cellSize(
        available: Int,
        count: Int,
        spacing: Int,
    ): Int = (available / count.coerceAtLeast(1) - spacing * 2).coerceAtLeast(1)
}
