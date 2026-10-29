// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class EnglishModeTest :
    BehaviorSpec({
        given("Mode_switch on the Chinese schema") {
            then("only a non-ASCII alphabet layout enters English completion") {
                EnglishMode.targetSchema("rime_frost", false, false) shouldBe "chen_eng"
                EnglishMode.targetSchema("rime_frost", true, false) shouldBe null
                EnglishMode.targetSchema("rime_frost", false, true) shouldBe null
                EnglishMode.targetSchema("rime_frost", true, true) shouldBe null
            }
        }

        given("English completion or an unrelated schema") {
            then("English always returns to Chinese, including from forced ASCII panels") {
                for (ascii in listOf(false, true)) {
                    for (forced in listOf(false, true)) {
                        EnglishMode.targetSchema("chen_eng", ascii, forced) shouldBe "rime_frost"
                        EnglishMode.targetSchema("other", ascii, forced) shouldBe null
                    }
                }
            }
        }

        given("cached toggle labels and automatic capitalization") {
            then("English is displayed without enabling the engine ASCII option") {
                EnglishMode.isEnglish("chen_eng", false) shouldBe true
                EnglishMode.isEnglish("rime_frost", true) shouldBe true
                EnglishMode.isEnglish("rime_frost", false) shouldBe false
                EnglishMode.optionEnabled("ascii_mode", false, "chen_eng") shouldBe true
                EnglishMode.optionEnabled("ascii_mode", false, "rime_frost") shouldBe false
                EnglishMode.optionEnabled("ascii_mode", true, "rime_frost") shouldBe true
                EnglishMode.optionEnabled("ascii_punct", false, "chen_eng") shouldBe false
                EnglishMode.optionEnabled("custom", true, "chen_eng") shouldBe true
            }
        }

        given("returning from temporary number and symbol keyboards") {
            then("English letters always compose even if the saved panel mode was ASCII") {
                for (saved in listOf(false, true)) {
                    val state = KeyboardAsciiState()
                    val panel = state.select(true, saved)
                    EnglishMode.keyboardAsciiMode("chen_eng", true, panel) shouldBe true
                    val letters = state.select(false, panel)
                    EnglishMode.keyboardAsciiMode("chen_eng", false, letters) shouldBe false
                    EnglishMode.keyboardAsciiMode("rime_frost", false, letters) shouldBe saved
                }
            }
        }

        given("a language change while a temporary panel has saved ASCII") {
            then("returning to letters cannot restore the old ASCII mode") {
                val state = KeyboardAsciiState()
                state.select(true, true) shouldBe true
                state.reset()
                state.select(true, true) shouldBe true
                state.select(false, true) shouldBe false
            }
        }

        given("schema notifications") {
            then("only Chinese-English transitions keep the current keyboard") {
                EnglishMode.keepsKeyboard("rime_frost", "chen_eng") shouldBe true
                EnglishMode.keepsKeyboard("chen_eng", "rime_frost") shouldBe true
                EnglishMode.keepsKeyboard("chen_eng", "other") shouldBe false
                EnglishMode.keepsKeyboard("other", "rime_frost") shouldBe false
                EnglishMode.keepsKeyboard("rime_frost", "rime_frost") shouldBe false
            }
        }
    })
