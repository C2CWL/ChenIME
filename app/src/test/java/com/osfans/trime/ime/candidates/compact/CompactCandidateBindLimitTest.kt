// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CompactCandidateBindLimitTest :
    BehaviorSpec({
        given("a compact candidate bind limit") {
            then("it keeps the first overflowing item so later packing can shrink it") {
                compactCandidateBindLimit(listOf(40, 40, 40, 40, 40), barWidth = 100, separator = 0) shouldBe 3
                compactCandidateBindLimit(listOf(40, 40, 40), barWidth = 120, separator = 0) shouldBe 3
            }

            then("separators count toward the used width") {
                compactCandidateBindLimit(listOf(40, 40, 40, 40), barWidth = 90, separator = 10) shouldBe 2
            }

            then("an unknown bar width binds every item, as before the first layout") {
                compactCandidateBindLimit(listOf(40, 40, 40), barWidth = 0, separator = 0) shouldBe 3
                compactCandidateBindLimit(emptyList(), barWidth = 100, separator = 0) shouldBe 0
            }

            then("a single item wider than the bar is still bound") {
                compactCandidateBindLimit(listOf(300, 40, 40), barWidth = 100, separator = 4) shouldBe 1
            }
        }
    })
