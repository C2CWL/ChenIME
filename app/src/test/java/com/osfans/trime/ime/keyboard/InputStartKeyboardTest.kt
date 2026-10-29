// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputStartKeyboardTest :
    StringSpec({
        "text variations and flags always start with letters" {
            for (variation in 0..15) {
                inputStartKeyboard(1 or (variation shl 4)) shouldBe "default"
                inputStartKeyboard(1 or (variation shl 4) or 0x80000) shouldBe "default"
            }
        }
        "number editor and repeated starts restore both user modes" {
            for (userMode in listOf(false, true)) {
                val state = KeyboardAsciiState()
                state.select(false, userMode) shouldBe userMode
                state.select(true, userMode) shouldBe true
                state.select(true, true) shouldBe true
                state.select(false, true) shouldBe userMode
                // A later explicit user toggle is preserved on the next text start.
                state.select(false, !userMode) shouldBe !userMode
            }
        }
        for (trigger in listOf("Send", "backspace", "clear_all", "select-all/delete", "restore_cleared")) {
            "$trigger restart preserves the selected pad and language" {
                for (pad in listOf("default", "number", "editor")) {
                    for (inputType in 0..15) {
                        inputStartKeyboard(inputType, restarting = true, currentKeyboardId = pad) shouldBe pad
                    }
                    for (mode in listOf(false, true)) {
                        val state = KeyboardAsciiState()
                        val before = state.select(pad != "default", mode)
                        // Restart retains the pad, so no attach/mode change is dispatched.
                        state.select(pad != "default", before) shouldBe before
                    }
                }
            }
        }
        "new focus selects numbers only for numeric classes from either pad" {
            for (pad in listOf("default", "number")) {
                for (inputType in 0..15) {
                    inputStartKeyboard(inputType, restarting = false, currentKeyboardId = pad) shouldBe
                        if (inputType in 2..4) "number" else "default"
                }
            }
        }
        "null input after send starts with letters" {
            inputStartKeyboard(0) shouldBe "default"
        }
        "only numeric phone and datetime classes select numbers" {
            for (inputClass in 0..15) {
                inputStartKeyboard(inputClass or 0x1000) shouldBe
                    if (inputClass in 2..4) "number" else "default"
            }
        }
    })
