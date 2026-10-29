// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyLabelCaseTest :
    StringSpec({
        "lowercase default still displays shifted uppercase" {
            keyLabelForDisplay("q", uppercase = false, shifted = false) shouldBe "q"
            keyLabelForDisplay("Q", uppercase = false, shifted = true) shouldBe "Q"
        }
        "height percentage applies to portrait and landscape and clamps restored values" {
            for (height in listOf(250, 200)) {
                scaleKeyboardHeight(height, 100) shouldBe height
                scaleKeyboardHeight(height, 70) shouldBe (height * 0.7f).toInt()
                scaleKeyboardHeight(height, 150) shouldBe (height * 1.5f).toInt()
                scaleKeyboardHeight(height, 0) shouldBe scaleKeyboardHeight(height, 70)
                scaleKeyboardHeight(height, 999) shouldBe scaleKeyboardHeight(height, 150)
            }
        }
        "overrides derived and explicit theme letter labels in either direction" {
            for (letter in 'a'..'z') {
                for (label in listOf(letter.toString(), letter.uppercase())) {
                    keyLabelForDisplay(label, uppercase = true, shifted = false) shouldBe letter.uppercase()
                    keyLabelForDisplay(label, uppercase = false, shifted = false) shouldBe letter.toString()
                }
            }
        }
        "preserves shifted and caps lock labels regardless of preference" {
            for (uppercase in listOf(true, false)) {
                for (label in listOf("A", "a", "!")) {
                    keyLabelForDisplay(label, uppercase, shifted = true) shouldBe label
                }
            }
        }
        "preserves non ASCII and multi character labels" {
            for (label in listOf("", "Enter", "Q~", "ZF", "1", "⌫", "中", "é")) {
                for (uppercase in listOf(true, false)) {
                    keyLabelForDisplay(label, uppercase, shifted = false) shouldBe label
                }
            }
        }
    })
