// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** Source-level guard: constructing the Android service requires an instrumented runtime. */
class KeyboardVisibilityPrefsTest :
    BehaviorSpec({
        Given("keyboard symbol and hint visibility preferences") {
            val source = File("src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt").readText()
            fun preferenceList(name: String) = source.substringAfter("private val $name:")
                .substringAfter("arrayOf(").substringBefore(")")

            When("either preference changes") {
                Then("it redraws keys instead of recreating the saved keyboard ASCII state") {
                    listOf("hideKeySymbol", "hideKeyHint").forEach { pref ->
                        preferenceList("recreateInputViewPrefs").contains("prefs.keyboard.$pref") shouldBe false
                        preferenceList("redrawKeyboardPrefs").contains("prefs.keyboard.$pref") shouldBe true
                    }
                }
            }
        }
    })
