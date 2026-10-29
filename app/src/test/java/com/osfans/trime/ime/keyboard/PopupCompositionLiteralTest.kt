// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PopupCompositionLiteralTest :
    StringSpec({
        "literal digits and symbols bypass key processors for commit text and send actions" {
            for (char in ('!'..'~').filterNot { it.isLetter() }) {
                val literal = char.toString()
                popupCompositionLiteral(literal, "", null) shouldBe literal
                popupCompositionLiteral("", literal, null) shouldBe literal
                popupCompositionLiteral("", "", char.code) shouldBe literal
            }
        }
        "iflytek unicode punctuation and pinyin apostrophe remain raw input" {
            for (literal in listOf("“", "”", "、", "'")) {
                popupCompositionLiteral(literal, "", null) shouldBe literal
            }
        }
        "letters macros whitespace and functional keys retain existing routing" {
            for (literal in listOf("a", "H", "中", "", "AB", "(){Left}", "{Control+a}", " ", "\n")) {
                popupCompositionLiteral(literal, "", null) shouldBe null
                popupCompositionLiteral("", literal, null) shouldBe null
            }
            for (key in listOf(0, 0x20, 0xff08, 0xff0d, 'H'.code, 'a'.code)) {
                popupCompositionLiteral("", "", key) shouldBe null
            }
        }
        "commit and text preserve action precedence over send" {
            popupCompositionLiteral("H", "!", '1'.code) shouldBe null
            popupCompositionLiteral("!", "H", '1'.code) shouldBe "!"
            popupCompositionLiteral("", "(){Left}", '1'.code) shouldBe null
        }
    })
