// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.keyboard

import com.osfans.trime.ime.candidates.unrolled.candidateSpan
import com.osfans.trime.ime.candidates.unrolled.isSingleCharacter
import com.osfans.trime.ime.candidates.unrolled.preeditSyllables
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
