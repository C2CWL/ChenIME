/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Hitboxes for the approved number-page vertical 返回 span and the editor
 * left-column + 4×3 grid. Coordinates are relative to a 1000×400 keyboard.
 */
class KeyboardGeometryTest :
    BehaviorSpec({
        fun spec(
            index: Int,
            width: Float,
            rowSpan: Int = 1,
        ) = KeyLayoutSpec(
            index = index,
            widthWeight = width,
            rowHeightHint = 0,
            rowSpan = rowSpan,
            clickable = true,
        )

        fun keyAt(
            placement: KeyboardPlacement,
            index: Int,
        ): PlacedKey = placement.keys.single { it.index == index }

        fun assertNoOverlap(keys: List<PlacedKey>) {
            for (i in keys.indices) {
                for (j in i + 1 until keys.size) {
                    val a = keys[i]
                    val b = keys[j]
                    val overlapX = a.x < b.x + b.width && b.x < a.x + a.width
                    val overlapY = a.y < b.y + b.height && b.y < a.y + a.height
                    (overlapX && overlapY) shouldBe false
                }
            }
        }

        fun owner(
            keys: List<PlacedKey>,
            x: Int,
            y: Int,
        ): Int? = keys.singleOrNull { x >= it.x && x < it.x + it.width && y >= it.y && y < it.y + it.height }?.index

        Given("a regular 4×5 pad with no spans") {
            val specs = (0 until 20).map { spec(it, 20f) }
            val placement =
                KeyboardGeometry.layout(
                    specs = specs,
                    allowedWidth = 1000,
                    keyboardHeight = 400,
                    defaultKeyHeight = 100,
                    maxColumns = 30,
                )

            Then("it fills four equal rows and does not overlap") {
                placement.keys.size shouldBe 20
                placement.height shouldBe 400
                placement.keys.forEach { it.width shouldBe 200 }
                placement.keys.forEach { it.height shouldBe 100 }
                keyAt(placement, 0).x shouldBe 0
                keyAt(placement, 4).x shouldBe 800
                keyAt(placement, 5).y shouldBe 100
                keyAt(placement, 19).y shouldBe 300
                assertNoOverlap(placement.keys)
            }
        }

        Given("the approved number page with a vertical double-height 返回") {
            // row1 + 1 2 3 BS; row2 - 4 5 6 space; row3 返回(span 2) 7 8 9 enter;
            // row4 / 0 . @
            val specs =
                listOf(
                    spec(0, 20f),
                    spec(1, 20f),
                    spec(2, 20f),
                    spec(3, 20f),
                    spec(4, 20f),
                    spec(5, 20f),
                    spec(6, 20f),
                    spec(7, 20f),
                    spec(8, 20f),
                    spec(9, 20f),
                    spec(10, 20f, rowSpan = 2),
                    spec(11, 20f),
                    spec(12, 20f),
                    spec(13, 20f),
                    spec(14, 20f),
                    spec(15, 20f),
                    spec(16, 20f),
                    spec(17, 20f),
                    spec(18, 20f),
                )
            val placement =
                KeyboardGeometry.layout(
                    specs = specs,
                    allowedWidth = 1000,
                    keyboardHeight = 400,
                    defaultKeyHeight = 100,
                    maxColumns = 30,
                )

            Then("返回 spans rows 3–4 in column 1 and slash sits in the former 符 slot") {
                placement.keys.size shouldBe 19
                placement.height shouldBe 400
                val back = keyAt(placement, 10)
                back.x shouldBe 0
                back.y shouldBe 200
                back.width shouldBe 200
                back.height shouldBe 200
                back.rowSpan shouldBe 2
                back.row shouldBe 2
                back.column shouldBe 0

                val slash = keyAt(placement, 15)
                slash.x shouldBe 200
                slash.y shouldBe 300
                slash.width shouldBe 200
                slash.height shouldBe 100

                val zero = keyAt(placement, 16)
                val two = keyAt(placement, 2)
                val five = keyAt(placement, 7)
                val eight = keyAt(placement, 12)
                zero.x shouldBe two.x
                zero.x shouldBe five.x
                zero.x shouldBe eight.x
                zero.y shouldBe 300
                two.x shouldBe 400

                keyAt(placement, 14).x shouldBe 800
                keyAt(placement, 14).y shouldBe 200
                keyAt(placement, 18).x shouldBe 800
                keyAt(placement, 18).y shouldBe 300

                assertNoOverlap(placement.keys)
            }

            Then("touch centers belong to exactly one key, including the tall 返回") {
                owner(placement.keys, 100, 250) shouldBe 10
                owner(placement.keys, 100, 350) shouldBe 10
                owner(placement.keys, 300, 350) shouldBe 15
                owner(placement.keys, 500, 350) shouldBe 16
                owner(placement.keys, 500, 50) shouldBe 2
                owner(placement.keys, 300, 250) shouldBe 11
                owner(placement.keys, 100, 50) shouldBe 0
                // no hole in the bottom-left of 返回
                owner(placement.keys, 50, 399) shouldBe 10
                owner(placement.keys, 199, 399) shouldBe 10
                owner(placement.keys, 200, 399) shouldBe 15
            }
        }

        Given("the approved editor grid with a reserved left column") {
            val specs = (0 until 12).map { spec(it, 21.5f) }
            val placement =
                KeyboardGeometry.layout(
                    specs = specs,
                    allowedWidth = 1000,
                    keyboardHeight = 400,
                    defaultKeyHeight = 100,
                    maxColumns = 30,
                    reservedLeadingWeight = 14f,
                )
            val leftWidth = 140
            val leftHeights = KeyboardGeometry.scaleHeights(List(4) { 100 }, 400)
            val leftKeys =
                leftHeights.mapIndexed { i, h ->
                    PlacedKey(
                        index = 100 + i,
                        row = i,
                        column = 0,
                        x = 0,
                        y = leftHeights.take(i).sum(),
                        width = leftWidth,
                        height = h,
                        rowSpan = 1,
                    )
                }

            Then("the grid is 4×3 beside a 14% left band, with no blank slots") {
                placement.keys.size shouldBe 12
                placement.height shouldBe 400
                keyAt(placement, 0).x shouldBe 140
                keyAt(placement, 0).y shouldBe 0
                keyAt(placement, 3).x + keyAt(placement, 3).width shouldBe 1000
                keyAt(placement, 4).y shouldBe keyAt(placement, 0).height
                keyAt(placement, 8).y + keyAt(placement, 8).height shouldBe 400
                placement.keys.forEach { it.width shouldBeGreaterThan 0 }
                placement.keys.forEach { it.height shouldBeGreaterThan 0 }
                assertNoOverlap(placement.keys)
                assertNoOverlap(leftKeys)
                assertNoOverlap(leftKeys + placement.keys)
            }

            Then("left-column and grid hitboxes do not share a cell") {
                leftHeights.sum() shouldBe 400
                owner(leftKeys + placement.keys, 70, 50) shouldBe 100
                owner(leftKeys + placement.keys, 70, 350) shouldBe 103
                owner(leftKeys + placement.keys, 200, 50) shouldBe 0
                owner(leftKeys + placement.keys, 990, 390) shouldBe 11
                // boundary between left column and first grid key
                owner(leftKeys + placement.keys, 139, 50) shouldBe 100
                owner(leftKeys + placement.keys, 140, 50) shouldBe 0
            }
        }
    })
