// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CompactCandidateWindowTest :
    BehaviorSpec({
        val widths = listOf(100, 100, 100, 100, 100)
        val separator = 10

        given("a row that still fits the bar") {
            then("every candidate stays a real view") {
                compactCandidateInitialWindow(widths, barWidth = 1000, separatorWidth = separator) shouldBe null
                compactCandidateScrollWindow(widths, scrollOffset = 0, barWidth = 1000, separatorWidth = separator) shouldBe null
            }
        }

        given("five candidates and a bar that shows the first two") {
            then("typing binds only the items that start inside the bar") {
                // Item 0 occupies 0..100, the separator ends at 110, item 1 occupies 110..210.
                // Item 2 starts at 220, past the 200px bar, so it stays a spacer.
                compactCandidateInitialWindow(widths, barWidth = 200, separatorWidth = separator) shouldBe 0..1
            }

            then("a scroll prefetches one bar ahead and keeps one bar behind") {
                // Offset 220 starts at item 2 (its span is 220..320). One bar behind reaches
                // item 0, and one bar ahead reaches the last item of this short row.
                compactCandidateScrollWindow(widths, scrollOffset = 220, barWidth = 200, separatorWidth = separator) shouldBe null
            }
        }

        given("a long row scrolled near its end") {
            val long = List(8) { 100 }
            then("the leading candidates stay spacers") {
                // Offset 660 starts at item 6. One bar behind starts at item 4; the tail fits,
                // so items 0..3 stay spacers.
                compactCandidateScrollWindow(long, scrollOffset = 660, barWidth = 200, separatorWidth = separator) shouldBe 4..7
            }
        }

        given("a clear gesture that left the scroll state behind") {
            val short = listOf(100, 100, 100)
            then("a stale offset from the previous composition binds from the start") {
                // Clear empties the row but the scroll listener can still report scrolling
                // with the old offset. The fresh three-candidate row is 320px wide, so an
                // offset of 660 cannot belong to it and must not hide candidates as spacers.
                compactCandidatePublishWindow(
                    widths = short,
                    scrolling = true,
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 0..1
            }

            then("an offset that still fits keeps the live scroll window") {
                compactCandidatePublishWindow(
                    widths = List(8) { 100 },
                    scrolling = true,
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 4..7
            }

            then("an idle bar ignores the offset") {
                compactCandidatePublishWindow(
                    widths = List(8) { 100 },
                    scrolling = false,
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe compactCandidateInitialWindow(List(8) { 100 }, 200, separator)
            }
        }

        given("a horizontal swipe that stopped mid-row") {
            then("the idle correction keeps the visible candidates real") {
                // A right swipe can stop with offset 660 still reported. Scrolling is already
                // false, but this offset came from a real scroll event, so items 4..7 must
                // stay real instead of being replaced by spacers at the start.
                compactCandidateVisibleWindow(
                    widths = List(8) { 100 },
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 4..7
            }

            then("an offset left over from a shorter row falls back to the start") {
                compactCandidateVisibleWindow(
                    widths = listOf(100, 100, 100),
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 0..1
            }

            then("swiping all the way back restores the first candidates") {
                compactCandidateVisibleWindow(
                    widths = List(8) { 100 },
                    scrollOffset = 0,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 0..1
            }

            then("a fast right swipe promotes the items that have just entered") {
                // A fling can move several bars before the next frame. The window at the new
                // offset must cover the candidates now on screen, not only the ones that were
                // visible when the gesture started.
                compactCandidateVisibleWindow(
                    widths = List(8) { 100 },
                    scrollOffset = 440,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 2..6
            }

            then("a half-hidden first item still covers the candidate entering on the right") {
                // Item 0 is 50px off the left edge, so the viewport's right edge is at 250 and
                // item 2 (leading edge 220) is already on screen. Measuring a full bar from
                // item 0's leading edge would stop at item 1 and leave item 2 as a blank spacer.
                compactCandidateVisibleWindow(
                    widths = widths,
                    scrollOffset = 50,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 0..3
            }
        }

        given("no measured width yet") {
            then("the window stays open so the first publish cannot hide candidates") {
                compactCandidateInitialWindow(emptyList(), barWidth = 200, separatorWidth = separator) shouldBe null
                compactCandidateScrollWindow(widths, scrollOffset = 0, barWidth = 0, separatorWidth = separator) shouldBe null
            }
        }
    })
