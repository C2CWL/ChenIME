// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import com.osfans.trime.ime.core.TrimeInputMethodService
import timber.log.Timber

/** Serialized charset switching shared by the keyboard key and schema switch tile. */
internal fun TrimeInputMethodService.switchCharset(option: String, keyboardWindow: KeyboardWindow) {
    if (option.isEmpty()) return
    val forcedAscii = keyboardWindow.isForcedAsciiLayout
    postRimeJob {
        val enabled = getRuntimeOption(option)
        val schema = statusCached.schemaId
        val target = if (option == "ascii_mode") {
            EnglishMode.targetSchema(schema, enabled, forcedAscii)
        } else {
            null
        }
        if (target != null) {
            if (target == EnglishMode.SCHEMA && statusCached.isComposing) {
                getRawInput().takeIf { it.isNotEmpty() }?.let {
                    commitText(it)
                    clearComposition()
                }
            } else {
                commitComposition()
            }
            if (selectSchema(target)) {
                keyboardWindow.resetLetterAsciiMode()
                setKeyboardAsciiMode(forcedAscii)
            } else {
                Timber.w("Failed to select schema %s from %s", target, schema)
                if (target == EnglishMode.SCHEMA) {
                    setRuntimeOption("ascii_mode", true)
                }
            }
        } else if (option == "ascii_mode" && schema == EnglishMode.CHINESE_SCHEMA) {
            commitComposition()
            keyboardWindow.resetLetterAsciiMode()
            setKeyboardAsciiMode(false)
        } else {
            val composing = statusCached.isComposing
            setRuntimeOption(option, !enabled)
            if (option == "ascii_mode" && composing) {
                getRawInput().takeIf { it.isNotEmpty() }?.let {
                    commitText(it)
                    clearComposition()
                }
            }
        }
    }
}
