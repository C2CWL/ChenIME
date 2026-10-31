/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import androidx.annotation.DrawableRes

class SwitchOptionEntry(
    val label: String,
    @param:DrawableRes
    val icon: Int,
    val type: Type,
) {
    enum class Type {
        SchemaList,
        UpdateConfig,
        Keyboard,
        ThemeList,
        InputMethodPicker,
    }
}
