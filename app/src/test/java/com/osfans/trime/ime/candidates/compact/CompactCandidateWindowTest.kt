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

        given("a fresh publish") {
            val short = listOf(100, 100, 100)
            then("a stale offset from a shorter row never hides every candidate") {
                // The fresh row cannot own offset 660. Clamping to its real end keeps the
                // candidates on both edges real until the view reports its corrected offset.
                compactCandidatePublishWindow(
                    widths = short,
                    forceStart = false,
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe null
            }

            then("an explicit reset binds from the start even at a live offset") {
                compactCandidatePublishWindow(
                    widths = List(8) { 100 },
                    forceStart = true,
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 0..1
            }

            then("typing after an idle right swipe keeps the visible candidates real") {
                // The swipe stopped at offset 660 with the bar idle. The next candidate batch
                // must not collapse the window to the first screen, which would leave every
                // currently visible position as a spacer and blank the whole bar.
                compactCandidatePublishWindow(
                    widths = List(8) { 100 },
                    forceStart = false,
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 4..7
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

            then("a swipe to the real end keeps the last candidates real") {
                // Five 100px items plus five 10px separators make 550px of content;
                // the bar shows 200px, so the real end offset is 350. Counting only
                // four separators made 350 look stale and blanked the whole bar.
                compactCandidateVisibleWindow(
                    widths = widths,
                    scrollOffset = 350,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 1..4
            }

            then("an offset past the end keeps the row fully bound") {
                compactCandidateVisibleWindow(
                    widths = listOf(100, 100, 100),
                    scrollOffset = 660,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe null
            }

            then("a few pixels past the end still keeps the last candidates real") {
                compactCandidateVisibleWindow(
                    widths = widths,
                    scrollOffset = 360,
                    barWidth = 200,
                    separatorWidth = separator,
                ) shouldBe 1..4
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

        given("a row whose reported scroll offset is stuck at zero") {
            val row = List(4) { 200 }

            then("a laid-out child reconstructs the live offset") {
                // Item 2 starts at 2 * 200 + 2 * 10 = 420 inside the content. With 16px of
                // padding, a child left edge of -64 means the row is scrolled by 500px.
                compactCandidateScrollOffset(
                    widths = row,
                    separatorWidth = 10,
                    paddingLeft = 16,
                    adapterPosition = 2,
                    childLeft = -64,
                ) shouldBe 500
                compactCandidateScrollOffset(
                    widths = row,
                    separatorWidth = 10,
                    paddingLeft = 16,
                    adapterPosition = 0,
                    childLeft = -484,
                ) shouldBe 500
            }

            then("an unbound child is ignored") {
                compactCandidateScrollOffset(
                    widths = row,
                    separatorWidth = 10,
                    paddingLeft = 16,
                    adapterPosition = -1,
                    childLeft = -64,
                ) shouldBe null
            }
        }

        given("no measured width yet") {
            then("the window stays open so the first publish cannot hide candidates") {
                compactCandidateInitialWindow(emptyList(), barWidth = 200, separatorWidth = separator) shouldBe null
                compactCandidateScrollWindow(widths, scrollOffset = 0, barWidth = 0, separatorWidth = separator) shouldBe null
            }
        }
    })
