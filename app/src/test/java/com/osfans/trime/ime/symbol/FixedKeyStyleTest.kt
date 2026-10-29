// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FixedKeyStyleTest :
    BehaviorSpec({
        Given("the liquid fixed function keys") {
            When("styling return and backspace") {
                Then("both use functional colors and only return is bold") {
                    for (name in listOf("liquid_keyboard_exit", "BackSpace")) {
                        val style = fixedKeyStyle(name)
                        style.background shouldBe "off_key_back_color"
                        style.text shouldBe "off_key_text_color"
                        style.pressedBackground shouldBe "hilited_off_key_back_color"
                        style.pressedText shouldBe "hilited_off_key_text_color"
                        style.bold shouldBe (name == "liquid_keyboard_exit")
                    }
                }
            }
            When("styling space or an unrelated key") {
                Then("ordinary key colors and weight are retained") {
                    fixedKeyStyle("space1") shouldBe FixedKeyStyle()
                    fixedKeyStyle("custom") shouldBe FixedKeyStyle()
                }
            }
        }
        Given("main keyboard enter color overrides in either bundled theme") {
            for ((background, text) in listOf("benter" to "tenter", "accent" to "on_key_text_color")) {
                When("enter uses $background and $text") {
                    Then("all return variants reuse those keys with pressed feedback") {
                        val enter = TextKeyboard.TextKey(keyBackColor = background, keyTextColor = text)
                        for (name in listOf("Return", "Return1", "Return2")) {
                            val style = fixedKeyStyle(name, enter)
                            style.background shouldBe background
                            style.text shouldBe text
                            style.pressedBackground shouldBe "hilited_key_back_color"
                            style.pressedText shouldBe "hilited_key_text_color"
                            style.bold shouldBe true
                        }
                    }
                }
            }
            When("no enter override is configured") {
                Then("theme accent fallbacks are used") {
                    for (enter in listOf(null, TextKeyboard.TextKey())) {
                        fixedKeyStyle("Return", enter).background shouldBe "on_key_back_color"
                        fixedKeyStyle("Return", enter).text shouldBe "on_key_text_color"
                    }
                }
            }
        }
    })
