// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyLabelCaseTest :
    StringSpec({
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
