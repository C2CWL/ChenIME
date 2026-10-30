// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Policy shared by serialized mode changes and cached UI state. */
internal object EnglishMode {
    const val CHINESE_SCHEMA = "rime_frost"
    const val SCHEMA = "chen_eng"

    fun isEnglish(schemaId: String, asciiMode: Boolean): Boolean = schemaId == SCHEMA || asciiMode

    fun languageTip(schemaId: String): String = if (schemaId == SCHEMA) "英文" else "中文"

    fun optionEnabled(option: String, enabled: Boolean, schemaId: String): Boolean = enabled || (option == "ascii_mode" && schemaId == SCHEMA)

    fun targetSchema(schemaId: String, asciiMode: Boolean, forcedAscii: Boolean): String? = when {
        schemaId == SCHEMA -> CHINESE_SCHEMA
        schemaId == CHINESE_SCHEMA && !asciiMode && !forcedAscii -> SCHEMA
        else -> null
    }

    fun keyboardAsciiMode(schemaId: String, temporary: Boolean, restoredMode: Boolean): Boolean = if (schemaId == SCHEMA && !temporary) false else restoredMode

    fun keepsKeyboard(previous: String, current: String): Boolean = (previous == CHINESE_SCHEMA && current == SCHEMA) ||
        (previous == SCHEMA && current == CHINESE_SCHEMA)
}
