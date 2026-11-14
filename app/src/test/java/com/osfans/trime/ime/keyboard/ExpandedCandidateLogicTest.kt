// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.keyboard

import com.osfans.trime.ime.candidates.compact.layoutCompactCandidateBar
import com.osfans.trime.ime.candidates.unrolled.candidateLayoutWidth
import com.osfans.trime.ime.candidates.unrolled.candidateSpan
import com.osfans.trime.ime.candidates.unrolled.expandedGridColumns
import com.osfans.trime.ime.candidates.unrolled.expandedGridGapPx
import com.osfans.trime.ime.candidates.unrolled.expandedMinCellWidth
import com.osfans.trime.ime.candidates.unrolled.expandedPillMargin
import com.osfans.trime.ime.candidates.unrolled.expandedPillWidth
import com.osfans.trime.ime.candidates.unrolled.expandedRowHeightDp
import com.osfans.trime.ime.candidates.unrolled.isSingleCharacter
import com.osfans.trime.ime.candidates.unrolled.layoutExpandedCandidateGrid
import com.osfans.trime.ime.candidates.unrolled.preeditSyllables
import com.osfans.trime.ime.candidates.unrolled.syllableInputEnd
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ExpandedCandidateLogicTest :
    StringSpec({
        "spans round up measured width and stay within the grid" {
            candidateSpan(49f, 50f) shouldBe 1
            candidateSpan(51f, 50f) shouldBe 2
            candidateSpan(100f, 50f) shouldBe 2
            candidateSpan(999f, 50f) shouldBe 6
            candidateSpan(99f, 0f) shouldBe 1
        }
        "expanded widths include the star and the right-side comment gap" {
            candidateLayoutWidth(30f, "*", 20f, true, 3f) shouldBe 53f
            candidateLayoutWidth(30f, "", 20f, true, 3f) shouldBe 30f
            candidateLayoutWidth(30f, "hint", 20f, true, 3f) shouldBe 53f
            candidateLayoutWidth(30f, "hint", 40f, false, 3f) shouldBe 40f
            candidateLayoutWidth(30f, "*", 40f, false, 3f) shouldBe 40f
            // A text-only width fits 64px, but the drawn star and pill insets do not.
            val width = candidateLayoutWidth(40f, "*", 12f, true, 3f) + 16f + 8f
            candidateSpan(width, 64f) shouldBe 2
        }
        "expanded rows match the taller of the theme row and the 44dp minimum" {
            expandedRowHeightDp(34, 12) shouldBe 46
            expandedRowHeightDp(28, 12) shouldBe 44
            expandedRowHeightDp(40, 12) shouldBe 52
        }
        "single character filter counts Unicode code points" {
            isSingleCharacter("你") shouldBe true
            isSingleCharacter("𠀀") shouldBe true
            isSingleCharacter("你好") shouldBe false
            isSingleCharacter("") shouldBe false
        }
        "syllables use only explicit engine boundaries and discard non-pinyin" {
            preeditSyllables("ni kan") shouldBe listOf("ni", "kan")
            preeditSyllables("xi'an xi") shouldBe listOf("xi", "an", "xi")
            preeditSyllables("nikan") shouldBe listOf("nikan")
            preeditSyllables("你 kan 123") shouldBe listOf("kan")
            preeditSyllables("拼yin") shouldBe listOf("yin")
            preeditSyllables("ma ma") shouldBe listOf("ma", "ma")
            preeditSyllables(null) shouldBe emptyList()
        }
        "syllable ends align against raw segment input" {
            syllableInputEnd("pinyin", 0, listOf("pin", "yin"), 0, emptyList()) shouldBe 3
            syllableInputEnd("pinyin", 0, listOf("pin", "yin"), 1, emptyList()) shouldBe 6
            syllableInputEnd("xi'an", 0, listOf("xi", "an"), 1, emptyList()) shouldBe 5
            syllableInputEnd("ma ma", 0, listOf("ma", "ma"), 1, emptyList()) shouldBe 5
            syllableInputEnd("pinyin", 3, preeditSyllables("拼yin"), 0, emptyList()) shouldBe 6
            syllableInputEnd("LV", 0, listOf("lü"), 0, emptyList()) shouldBe 2
        }
        "fallback uses distinct sorted candidate ends and clamps" {
            syllableInputEnd("py", 0, listOf("pin", "yin"), 0, listOf(2, 1, 1, 0)) shouldBe 1
            syllableInputEnd("py", 0, listOf("pin", "yin"), 1, listOf(1, 1)) shouldBe 1
            syllableInputEnd("py", 0, listOf("pin"), 0, emptyList()) shouldBe null
        }
        "grid column count grows only while each cell stays at least the minimum width" {
            expandedGridColumns(300f, 80f) shouldBe 3
            expandedGridColumns(300f, 50f) shouldBe 6
            expandedGridColumns(300f, 40f) shouldBe 6
            expandedGridColumns(0f, 50f) shouldBe 6
            expandedGridColumns(200f, 0f) shouldBe 6
        }
        "a filled pill occupies its span minus a uniform gap, so two columns equal two cells plus a gutter" {
            expandedPillMargin(0) shouldBe 0
            expandedPillMargin(1) shouldBe 1
            expandedPillMargin(8) shouldBe 4
            expandedGridGapPx(0f, 0) shouldBe 0
            expandedGridGapPx(4f, 4) shouldBe 4
            expandedGridGapPx(0.5f, 1) shouldBe 2
            val gap = 8f
            val cell = 50f
            val one = expandedPillWidth(cell, gap)
            val two = expandedPillWidth(2 * cell, gap)
            one shouldBe 42f
            two shouldBe 92f
            two shouldBe one + gap + one
        }
        "short words stay in one column and typical four-character words take one or two" {
            val twoChar = 40f
            val padding = 16f
            val gap = 8f
            val cell = expandedMinCellWidth(twoChar, padding, gap)
            cell shouldBe 64f
            // 哈* includes the annotation width
            val haStar = candidateLayoutWidth(20f, "*", 12f, commentOnRight = true) + padding + gap
            candidateSpan(haStar, cell) shouldBe 1
            // 蛤蛤
            candidateSpan(twoChar + padding, cell) shouldBe 1
            // 好啊好啊 (four characters, about two "汉字")
            candidateSpan(twoChar * 2 + padding, cell) shouldBe 2
        }
        "equal-width words share one cell size and even spacing, even on a short last row" {
            // 哈哈 / 蛤蛤 / haha / 哈 / 蛤 / 铪 all fit one cell when the cell is wide enough
            val cells = layoutExpandedCandidateGrid(List(8) { 40f }, gridWidth = 300f, columns = 6)
            cells.map { it.width }.distinct() shouldBe listOf(50f)
            cells.take(6).map { it.row } shouldBe listOf(0, 0, 0, 0, 0, 0)
            cells.drop(6).map { it.row } shouldBe listOf(1, 1)
            cells.drop(6).map { it.left } shouldBe listOf(0f, 50f)
            cells.drop(6).map { it.width } shouldBe listOf(50f, 50f)
            cells.zipWithNext().filter { it.first.row == it.second.row }.forEach { (a, b) ->
                b.left shouldBe a.left + a.width
                b.column shouldBe a.column + a.span
            }
        }
        "a long word takes whole columns, leaves no hole, and is never dropped" {
            // 隘谷, AGPL3.0, Google Antigravity, 昂贵 on a 3-column row
            val cells = layoutExpandedCandidateGrid(
                itemWidths = listOf(80f, 90f, 180f, 70f, 70f, 70f),
                gridWidth = 300f,
                columns = 3,
            )
            cells.map { it.index } shouldBe listOf(0, 1, 2, 3, 4, 5)
            // 80 and 90 fit one column; 180 needs two and does not fit the leftover,
            // so the previous cell expands to fill the hole and the long word starts the next row.
            cells[0].span shouldBe 1
            cells[1].span shouldBe 2
            cells[1].row shouldBe 0
            cells[1].column shouldBe 1
            cells[1].width shouldBe 200f
            cells[2].span shouldBe 2
            cells[2].row shouldBe 1
            cells[2].column shouldBe 0
            cells[2].width shouldBe 200f
            cells[3].row shouldBe 1
            cells[3].column shouldBe 2
            cells[3].span shouldBe 1
            cells.groupBy { it.row }.values.forEach { row ->
                val occupied = row.sumOf { it.span }
                if (row.last().index != cells.last().index) occupied shouldBe 3 else (occupied in 1..3) shouldBe true
                var column = 0
                row.forEach { cell ->
                    cell.column shouldBe column
                    cell.left shouldBe column * 100f
                    cell.width shouldBe cell.span * 100f
                    column += cell.span
                    (column <= 3) shouldBe true
                }
            }
        }
        "a word wider than the grid still occupies one full row" {
            val cells = layoutExpandedCandidateGrid(listOf(999f, 40f), gridWidth = 240f, columns = 4)
            cells[0].span shouldBe 4
            cells[0].width shouldBe 240f
            cells[0].row shouldBe 0
            cells[1].row shouldBe 1
            cells[1].span shouldBe 1
            cells.map { it.index } shouldBe listOf(0, 1)
        }
        "compact bar shows an overlong candidate in the remaining space instead of skipping it" {
            val items = layoutCompactCandidateBar(
                desiredWidths = listOf(60f, 70f, 200f, 50f),
                barWidth = 200f,
                separatorWidth = 0f,
            )
            items.map { it.index } shouldBe listOf(0, 1, 2)
            items[0].width shouldBe 60f
            items[1].width shouldBe 70f
            items[2].width shouldBe 70f
            items[2].ellipsized shouldBe true
            items.sumOf { it.width.toDouble() } shouldBe 200.0
        }
        "a compact-bar candidate wider than the bar is still shown" {
            val items = layoutCompactCandidateBar(listOf(500f, 40f), barWidth = 180f)
            items.map { it.index } shouldBe listOf(0)
            items[0].width shouldBe 180f
            items[0].ellipsized shouldBe true
        }
        "compact bar keeps later words when earlier ones fit" {
            val items = layoutCompactCandidateBar(listOf(40f, 40f, 40f, 40f), barWidth = 150f, separatorWidth = 10f)
            items.map { it.index } shouldBe listOf(0, 1, 2)
            items.map { it.ellipsized } shouldBe listOf(false, false, false)
            items.map { it.width } shouldBe listOf(40f, 40f, 40f)
        }
    })
