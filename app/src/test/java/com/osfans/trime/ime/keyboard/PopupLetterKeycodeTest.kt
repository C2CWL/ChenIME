// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PopupLetterKeycodeTest :
    StringSpec({
        "every popup letter preserves its case in composition" {
            for (letter in ('A'..'Z') + ('a'..'z')) {
                popupLetterKeycode(letter.toString(), true) shouldBe letter.code
                popupLetterKeycode(letter.toString(), false) shouldBe null
            }
        }
        "symbols unicode letters and multi-character commits retain action routing" {
            for (commit in listOf("", "!", "'", "1", "é", "中", "AB", "{Shift+a}")) {
                popupLetterKeycode(commit, true) shouldBe null
                popupLetterKeycode(commit, false) shouldBe null
            }
        }
    })
