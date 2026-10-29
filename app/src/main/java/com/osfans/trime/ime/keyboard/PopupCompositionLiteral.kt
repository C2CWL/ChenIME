// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Only literal digits/punctuation qualify; macros, shortcuts and letters keep their routing. */
internal fun popupCompositionLiteral(commit: String, text: String, keyValue: Int?): String? {
    val literal = when {
        commit.isNotEmpty() -> commit
        text.isNotEmpty() -> text
        keyValue != null && keyValue in 0x21..0x7e -> keyValue.toChar().toString()
        else -> return null
    }
    val char = literal.singleOrNull() ?: return null
    return literal.takeIf { !char.isLetter() && !char.isWhitespace() && !char.isISOControl() }
}
