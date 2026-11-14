// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import com.osfans.trime.core.CandidateProto
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

        given("compact candidate content at the same length") {
            val ma = listOf(
                CandidateProto("吗", "", ""),
                CandidateProto("慢", "*", ""),
                CandidateProto("没", "*", ""),
                CandidateProto("名", "*", ""),
                CandidateProto("买", "*", ""),
                CandidateProto("忙", "*", ""),
            )
            val da = listOf(
                CandidateProto("打", "", ""),
                CandidateProto("大", "", ""),
                CandidateProto("答", "", ""),
                CandidateProto("达", "", ""),
                CandidateProto("搭", "", ""),
                CandidateProto("DAH", "", ""),
            )

            then("different text of the same length is not treated as a no-op") {
                shouldSkipCompactCandidateUpdate(ma, da, 6, 6, 0, 0) shouldBe false
            }

            then("an identical republish is still skipped") {
                shouldSkipCompactCandidateUpdate(ma, ma.toList(), 6, 6, 0, 0) shouldBe true
            }

            then("a highlight-only change is not skipped") {
                shouldSkipCompactCandidateUpdate(ma, ma, 6, 6, 0, 1) shouldBe false
            }
        }
    })
