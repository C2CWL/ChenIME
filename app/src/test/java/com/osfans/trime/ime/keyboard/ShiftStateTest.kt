// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ShiftStateTest :
    StringSpec({
        "composing separator yields only to locked Shift" {
            for (composing in listOf(false, true)) {
                for (isShift in listOf(false, true)) {
                    for (locked in listOf(false, true)) {
                        useComposingAction(composing, isShift, locked) shouldBe
                            (composing && (!isShift || !locked))
                    }
                }
            }
            val unlocked = ShiftState(shifted = true, locked = true).press(lock = false)
            useComposingAction(true, true, unlocked.locked) shouldBe true
        }
        "single tap is consumed by the next key" {
            val shifted = ShiftState().press(lock = false)
            shifted shouldBe ShiftState(shifted = true)
            shifted.afterKey() shouldBe ShiftState()
        }
        "double tap locks after the first immediate single tap" {
            val locked = ShiftState().press(lock = false).press(lock = true)
            locked shouldBe ShiftState(shifted = true, locked = true)
            locked.afterKey().afterKey() shouldBe locked
            locked.press(lock = false) shouldBe ShiftState()
        }
        "long press locks from either unshifted or one-shot state" {
            for (state in listOf(ShiftState(), ShiftState(shifted = true))) {
                val locked = state.press(lock = true)
                locked.afterKey() shouldBe ShiftState(shifted = true, locked = true)
                locked.press(lock = false) shouldBe ShiftState()
            }
        }
        "shifted letter text resolves direct uppercase ASCII" {
            for (offset in 0..25) {
                val code = android.view.KeyEvent.KEYCODE_A + offset
                val expected = ('A'.code + offset).toChar().toString()
                shiftedLetterText(code) shouldBe expected
            }
            shiftedLetterText(android.view.KeyEvent.KEYCODE_SPACE) shouldBe null
            shiftedLetterText(android.view.KeyEvent.KEYCODE_0) shouldBe null
        }
    })
