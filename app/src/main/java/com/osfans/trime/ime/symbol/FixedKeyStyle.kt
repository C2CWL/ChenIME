// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import com.osfans.trime.data.theme.model.TextKeyboard

/** Theme keys resolved again by LiquidItemUi on each color-scheme refresh. */
internal data class FixedKeyStyle(
    val background: String = "key_back_color",
    val text: String = "key_text_color",
    val border: String = "key_border_color",
    val pressedBackground: String = "hilited_key_back_color",
    val pressedText: String = "hilited_key_text_color",
    val pressedBorder: String = "hilited_key_border_color",
    val bold: Boolean = false,
)

internal fun fixedKeyStyle(name: String, enter: TextKeyboard.TextKey? = null): FixedKeyStyle = when (name) {
    "liquid_keyboard_exit", "BackSpace" -> FixedKeyStyle(
        background = "off_key_back_color",
        text = "off_key_text_color",
        border = "off_key_border_color",
        pressedBackground = "hilited_off_key_back_color",
        pressedText = "hilited_off_key_text_color",
        pressedBorder = "hilited_off_key_border_color",
        bold = name == "liquid_keyboard_exit",
    )

    "Return", "Return1", "Return2" -> FixedKeyStyle(
        background = enter?.keyBackColor?.takeIf { it.isNotBlank() } ?: "on_key_back_color",
        text = enter?.keyTextColor?.takeIf { it.isNotBlank() } ?: "on_key_text_color",
        bold = true,
    )

    else -> FixedKeyStyle()
}
