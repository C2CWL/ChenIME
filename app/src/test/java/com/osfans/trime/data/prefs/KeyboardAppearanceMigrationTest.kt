// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy

class KeyboardAppearanceMigrationTest :
    StringSpec({
        "repairs saved defaults once and preserves subsequent toggles" {
            val values = mutableMapOf<String, Boolean>()
            val keys = listOf("hide_key_symbol", "hide_key_hint", "uppercase_key_labels")
            keys.forEach { values[it] = true }
            val editor = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "putBoolean" -> {
                        values[args!![0] as String] = args[1] as Boolean
                        proxy
                    }

                    "apply" -> null

                    else -> error(method.name)
                }
            } as SharedPreferences.Editor
            val shared = Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getBoolean" -> values[args!![0] as String] ?: args[1]
                    "edit" -> editor
                    else -> error(method.name)
                }
            } as SharedPreferences
            migrateKeyboardAppearance(shared)
            keys.forEach { values[it] shouldBe false }
            values["show_key_popup"] shouldBe null
            values["show_key_popup"] = true
            keys.forEach { values[it] = true }
            migrateKeyboardAppearance(shared)
            keys.forEach { values[it] shouldBe true }
            values["show_key_popup"] shouldBe true
            // An older installation must also retain an explicitly disabled preview.
            values["show_key_popup"] = false
            values["keyboard_preview_defaults_v1"] = false
            migrateKeyboardAppearance(shared)
            values["show_key_popup"] shouldBe false
            keys.forEach { values[it] shouldBe true }
        }
    })
