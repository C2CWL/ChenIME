// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FixedKeyStyleTest :
    BehaviorSpec({
        Given("the liquid fixed function keys") {
            When("styling backspace") {
                Then("it uses functional off-key colors and is not bold") {
                    val style = fixedKeyStyle("BackSpace")
                    style.background shouldBe "off_key_back_color"
                    style.text shouldBe "off_key_text_color"
                    style.pressedBackground shouldBe "hilited_off_key_back_color"
                    style.pressedText shouldBe "hilited_off_key_text_color"
                    style.bold shouldBe false
                }
            }
            When("styling the exit key") {
                Then("it matches return accent colors and is bold") {
                    // Approved symbol-page layout: 返回 at the bottom of the left
                    // action column uses the same accent treatment as 回车.
                    fixedKeyStyle("liquid_keyboard_exit") shouldBe fixedKeyStyle("Return")
                    fixedKeyStyle("liquid_keyboard_exit").bold shouldBe true
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
                        for (name in listOf("liquid_keyboard_exit", "Return", "Return1", "Return2")) {
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
                        for (name in listOf("liquid_keyboard_exit", "Return")) {
                            fixedKeyStyle(name, enter).background shouldBe "on_key_back_color"
                            fixedKeyStyle(name, enter).text shouldBe "on_key_text_color"
                        }
                    }
                }
            }
        }
    })
