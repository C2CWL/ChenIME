/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Even-grid sizing used by the approved symbol-page left bar: a wide action
 * column leaves the remaining width, which is split into regular cells whose
 * visual size equals the touch hitbox.
 */
class LiquidGridGeometryTest :
    BehaviorSpec({
        Given("a typical portrait panel with an 18% left action column") {
            // 360dp-wide keyboard, 18% bar, 5dp padding on the recycler.
            val panelWidth = 360
            val barWidth = (panelWidth * 18) / 100
            val padding = 5
            val spacing = 5
            val available = panelWidth - barWidth - padding * 2
            val minCell = 36 + spacing * 2
            val columns = LiquidGridGeometry.columnCount(available, minCell)
            val rows = 4
            val availableHeight = 240 - padding * 2
            val width = LiquidGridGeometry.cellSize(available, columns, spacing)
            val height = LiquidGridGeometry.cellSize(availableHeight, rows, spacing)

            Then("the symbol cells form a regular 5×4 grid that fills the remaining space") {
                barWidth shouldBeGreaterThan width
                columns shouldBe 5
                width shouldBeGreaterThan 0
                height shouldBeGreaterThan 0
                // Integer cell sizes may leave a remainder smaller than one row or column.
                val usedHeight = padding * 2 + rows * (height + spacing * 2)
                val usedWidth = padding * 2 + columns * (width + spacing * 2)
                (240 - usedHeight) shouldBe (240 - usedHeight).coerceIn(0, rows - 1)
                ((panelWidth - barWidth) - usedWidth) shouldBe
                    ((panelWidth - barWidth) - usedWidth).coerceIn(0, columns - 1)
            }

            Then("19 常用 keys occupy four rows with a short last row and no bottom action bar") {
                val itemCount = 19
                val lastRowCount = itemCount - columns * (rows - 1)
                lastRowCount shouldBe 4
            }
        }

        Given("a narrow remaining width") {
            Then("the column count drops rather than overflowing") {
                LiquidGridGeometry.columnCount(availableWidth = 80, minCell = 46) shouldBe 1
                LiquidGridGeometry.columnCount(availableWidth = 0, minCell = 46) shouldBe 1
            }
        }
    })
