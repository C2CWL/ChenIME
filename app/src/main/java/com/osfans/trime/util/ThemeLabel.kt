// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

/** An icon name and readable text for accessibility or a missing icon. */
data class ThemeLabel(val iconName: String?, val text: String)

fun parseThemeLabel(label: String, fallback: String = ""): ThemeLabel {
    if (!label.startsWith("ic@")) return ThemeLabel(null, label)
    val name = label.removePrefix("ic@")
    val text = fallback.takeIf { it.isNotBlank() && !it.startsWith("ic@") } ?: when (name) {
        "keyboard_return" -> "回车"
        "backspace_outline" -> "退格"
        "undo" -> "撤销"
        "redo" -> "重做"
        "keyboard_close" -> "收起"
        "arrow-left", "arrow_left" -> "返回"
        else -> "按键"
    }
    return ThemeLabel(name.takeIf { it.isNotBlank() }?.let { "cmd_$it" }, text)
}
