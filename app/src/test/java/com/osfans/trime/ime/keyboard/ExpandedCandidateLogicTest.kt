// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.keyboard

import com.osfans.trime.ime.candidates.unrolled.candidateSpan
import com.osfans.trime.ime.candidates.unrolled.isSingleCharacter
import com.osfans.trime.ime.candidates.unrolled.preeditSyllables
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
            preeditSyllables("xi'an xi") shouldBe listOf("xi", "an")
            preeditSyllables("nikan") shouldBe listOf("nikan")
            preeditSyllables("你 kan 123") shouldBe listOf("kan")
            preeditSyllables(null) shouldBe emptyList()
        }
    })
