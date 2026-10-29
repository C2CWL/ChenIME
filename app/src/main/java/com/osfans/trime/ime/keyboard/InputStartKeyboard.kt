// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Only numeric editor classes select a number pad; flags and variations do not. */
internal fun inputStartKeyboard(
    inputType: Int,
    restarting: Boolean = false,
    currentKeyboardId: String = "default",
): String = if (restarting) {
    currentKeyboardId
} else {
    when (inputType and 0x0f) {
        // TYPE_CLASS_NUMBER, PHONE, DATETIME
        2, 3, 4 -> "number"

        else -> "default"
    }
}

/** A panel's forced ASCII mode must never replace the user's letter mode. */
internal class KeyboardAsciiState {
    private var savedMode: Boolean? = null

    /** An explicit language change starts a new non-ASCII letter mode. */
    fun reset() {
        savedMode = false
    }

    fun select(temporary: Boolean, currentMode: Boolean): Boolean = if (temporary) {
        if (savedMode == null) savedMode = currentMode
        true
    } else {
        val restored = savedMode ?: currentMode
        savedMode = null
        restored
    }
}

/** Resolve implicit returns and ASCII requests without guessing from map order. */
internal fun retainedKeyboard(
    request: String,
    current: String,
    locked: Boolean,
    lastLocked: String,
    asciiKeyboard: String,
    keyboardIds: Collection<String>,
): String = if (request == ".ascii") {
    asciiKeyboard.takeIf { it.isNotEmpty() && it in keyboardIds } ?: current
} else {
    if (locked) current else lastLocked
}
