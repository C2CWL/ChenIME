/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.toolbar

import android.content.Context
import com.c2cwl.chenime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.KeyboardWindow

fun toolbarButtonLabel(context: Context, action: String, theme: Theme?): String {
    val res = when (action) {
        "menu_keyboard" -> R.string.toolbar_button_menu
        "Hide" -> R.string.toolbar_button_hide
        "VOICE_ASSIST" -> R.string.toolbar_button_voice
        "Ifly_symbols", "liquid_keyboard_emoji" -> R.string.toolbar_button_symbols
        "clipboard_window" -> R.string.toolbar_button_clipboard
        "Keyboard_editor" -> R.string.toolbar_button_editor
        "redo" -> R.string.toolbar_button_redo
        "undo" -> R.string.toolbar_button_undo
        else -> 0
    }
    if (res != 0) return context.getString(res)
    val fromTheme = runCatching {
        theme?.resolveAction(action)?.getLabel(KeyboardWindow.currentKeyboard)
    }.getOrNull()
    return fromTheme?.takeIf { it.isNotBlank() } ?: action
}
