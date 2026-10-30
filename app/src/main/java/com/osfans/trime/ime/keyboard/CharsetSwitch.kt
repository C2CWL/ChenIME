// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import com.osfans.trime.core.RimeApi
import com.osfans.trime.ime.core.TrimeInputMethodService
import timber.log.Timber

/** Serialized charset switching shared by the keyboard key and schema switch tile. */
internal fun TrimeInputMethodService.switchCharset(option: String, keyboardWindow: KeyboardWindow) {
    if (option.isEmpty()) return
    val forcedAscii = keyboardWindow.isForcedAsciiLayout
    postRimeJob {
        val switch: suspend RimeApi.() -> Unit = {
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
                    showLanguageTip(EnglishMode.languageTip(target))
                } else {
                    Timber.w("Failed to select schema %s from %s", target, schema)
                    if (target == EnglishMode.SCHEMA) {
                        setRuntimeOption("ascii_mode", true)
                        showLanguageTip("英文")
                    }
                }
            } else if (option == "ascii_mode" && schema == EnglishMode.CHINESE_SCHEMA) {
                commitComposition()
                keyboardWindow.resetLetterAsciiMode()
                setKeyboardAsciiMode(false)
                showLanguageTip("中文")
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
        // Only these schemas use explicit language tips; preserve other option feedback.
        if (option == "ascii_mode" && statusCached.schemaId in setOf(EnglishMode.SCHEMA, EnglishMode.CHINESE_SCHEMA)) {
            withQuietAsciiSwitch(switch)
        } else {
            switch()
        }
    }
}
