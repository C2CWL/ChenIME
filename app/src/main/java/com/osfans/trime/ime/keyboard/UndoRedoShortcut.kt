// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent

internal enum class UndoRedoShortcut {
    UNDO,
    REDO,
}

internal fun undoRedoShortcut(code: Int, mask: Int): UndoRedoShortcut? {
    val ctrl = mask and KeyEvent.META_CTRL_MASK != 0
    val otherModifiers = mask and (KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK or KeyEvent.META_SYM_ON)
    if (!ctrl || otherModifiers != 0) return null
    return when (code) {
        KeyEvent.KEYCODE_Y -> UndoRedoShortcut.REDO
        KeyEvent.KEYCODE_Z -> if (mask and KeyEvent.META_SHIFT_MASK != 0) UndoRedoShortcut.REDO else UndoRedoShortcut.UNDO
        else -> null
    }
}
