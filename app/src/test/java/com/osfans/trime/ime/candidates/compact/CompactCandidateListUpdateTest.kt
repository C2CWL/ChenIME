// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CompactCandidateListUpdateTest :
    BehaviorSpec({
        given("a compact candidate list size change") {
            then("an identical size only rebinds existing rows") {
                planCompactCandidateListNotify(5, 5) shouldBe CompactCandidateListNotify(changeCount = 5)
            }

            then("growing the row rebinds the old items then inserts the new ones") {
                planCompactCandidateListNotify(3, 5) shouldBe
                    CompactCandidateListNotify(changeCount = 3, insertStart = 3, insertCount = 2)
            }

            then("shrinking the row rebinds the remaining items then removes the tail") {
                planCompactCandidateListNotify(5, 2) shouldBe
                    CompactCandidateListNotify(changeCount = 2, removeStart = 2, removeCount = 3)
            }

            then("filling an empty row only inserts") {
                planCompactCandidateListNotify(0, 4) shouldBe
                    CompactCandidateListNotify(changeCount = 0, insertStart = 0, insertCount = 4)
            }

            then("clearing the row only removes") {
                planCompactCandidateListNotify(4, 0) shouldBe
                    CompactCandidateListNotify(changeCount = 0, removeStart = 0, removeCount = 4)
            }

            then("an already empty row is a no-op") {
                planCompactCandidateListNotify(0, 0).isNoOp shouldBe true
            }

            then("negative sizes are rejected") {
                shouldThrow<IllegalArgumentException> { planCompactCandidateListNotify(-1, 0) }
                shouldThrow<IllegalArgumentException> { planCompactCandidateListNotify(0, -2) }
            }
        }
    })
