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

        given("no measured width yet") {
            then("the window stays open so the first publish cannot hide candidates") {
                compactCandidateInitialWindow(emptyList(), barWidth = 200, separatorWidth = separator) shouldBe null
                compactCandidateScrollWindow(widths, scrollOffset = 0, barWidth = 0, separatorWidth = separator) shouldBe null
            }
        }
    })
