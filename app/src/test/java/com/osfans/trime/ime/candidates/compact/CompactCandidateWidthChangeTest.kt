// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CompactCandidateWidthChangeTest :
    BehaviorSpec({
        given("measured compact candidate widths") {
            then("an identical row has no changed range") {
                compactCandidateWidthChangeRange(listOf(40, 40, 40), listOf(40, 40, 40)) shouldBe null
            }

            then("only the moved rows are reported") {
                compactCandidateWidthChangeRange(
                    listOf(40, 40, 40, 40, 40),
                    listOf(40, 52, 52, 40, 40),
                ) shouldBe 1..2
            }

            then("a trailing change is reported alone") {
                compactCandidateWidthChangeRange(listOf(40, 40, 40), listOf(40, 40, 61)) shouldBe 2..2
            }

            then("every row changing reports the whole row") {
                compactCandidateWidthChangeRange(listOf(40, 40), listOf(50, 51)) shouldBe 0..1
            }

            then("a size change is left to the size plan") {
                compactCandidateWidthChangeRange(listOf(40, 40), listOf(40)) shouldBe null
                compactCandidateWidthChangeRange(emptyList(), emptyList()) shouldBe null
            }
        }
    })
