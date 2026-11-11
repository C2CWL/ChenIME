// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.keyboard

import com.osfans.trime.ime.candidates.unrolled.candidateLayoutWidth
import com.osfans.trime.ime.candidates.unrolled.candidateSpan
import com.osfans.trime.ime.candidates.unrolled.expandedRowHeightDp
import com.osfans.trime.ime.candidates.unrolled.isSingleCharacter
import com.osfans.trime.ime.candidates.unrolled.preeditSyllables
import com.osfans.trime.ime.candidates.unrolled.spanCommentWidth
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
        "a lone user-dict star does not add to the span width" {
            spanCommentWidth("", 12f) shouldBe 0f
            spanCommentWidth("*", 20f) shouldBe 0f
            spanCommentWidth("er", 20f) shouldBe 20f
            candidateLayoutWidth(30f, "*", 20f, commentOnRight = true) shouldBe 30f
            candidateLayoutWidth(30f, "", 20f, commentOnRight = true) shouldBe 30f
            candidateLayoutWidth(30f, "hint", 20f, commentOnRight = true) shouldBe 50f
            candidateLayoutWidth(30f, "hint", 40f, commentOnRight = false) shouldBe 40f
            candidateLayoutWidth(30f, "*", 40f, commentOnRight = false) shouldBe 30f
            // Single character plus star still fits one 50px cell after 14dp-style padding.
            val width = candidateLayoutWidth(18f, "*", 20f, commentOnRight = true) + 28f
            candidateSpan(width, 50f) shouldBe 1
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
    })
