// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences

/** Repair saved visibility defaults once, before preference delegates or views are created. */
internal fun migrateKeyboardAppearance(shared: SharedPreferences) {
    val marker = "keyboard_appearance_defaults_v2"
    if (shared.getBoolean(marker, false)) return
    // SwitchPreference can persist its initial default when the settings screen is opened.
    // That value cannot be distinguished from an intentional selection in v0.1.1.
    shared.edit()
        .putBoolean("hide_key_symbol", false)
        .putBoolean("hide_key_hint", false)
        .putBoolean("uppercase_key_labels", false)
        .putBoolean(marker, true)
        .apply()
}
