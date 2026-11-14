// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import com.osfans.trime.ime.candidates.unrolled.candidateLayoutWidth
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CompactCandidateWidthTest :
    BehaviorSpec({
        given("compact-bar candidate content width") {
            then("it includes a user-dict star and the gap beside the text") {
                // Both layouts must include the annotation actually drawn.
                candidateLayoutWidth(30f, "*", 12f, commentOnRight = true) shouldBe 42f

                compactCandidateContentWidth(30f, "*", 12f, commentOnRight = true, commentGap = 3f) shouldBe 45f
                compactCandidateContentWidth(30f, "∞", 16f, commentOnRight = true, commentGap = 3f) shouldBe 49f
                compactCandidateContentWidth(30f, "hint", 20f, commentOnRight = true, commentGap = 3f) shouldBe 53f
            }

            then("an empty comment does not add a gap") {
                compactCandidateContentWidth(30f, "", 20f, commentOnRight = true, commentGap = 3f) shouldBe 30f
                compactCandidateContentWidth(30f, "", 20f, commentOnRight = false, commentGap = 3f) shouldBe 30f
            }

            then("a top or overlay comment uses the wider of text and comment") {
                compactCandidateContentWidth(30f, "*", 12f, commentOnRight = false, commentGap = 3f) shouldBe 30f
                compactCandidateContentWidth(30f, "hint", 40f, commentOnRight = false, commentGap = 3f) shouldBe 40f
            }

            then("a measured pill for 哈 plus a star is wider than the text alone") {
                // Matches the compact-bar report: 哈 was clipped on the left and
                // the comment was also cut, because the allocated width covered
                // only the character and padding.
                val text = 36f
                val star = 10f
                val gap = 3f
                val padding = 14f
                val allocated = compactCandidateContentWidth(text, "*", star, commentOnRight = true, commentGap = gap) + padding
                val expanded = candidateLayoutWidth(text, "*", star, commentOnRight = true, commentGap = gap) + padding
                allocated shouldBe 63f
                expanded shouldBe allocated
            }
        }
    })
