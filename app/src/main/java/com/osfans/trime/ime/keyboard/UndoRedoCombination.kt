// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
// SPDX-License-Identifier: LGPL-2.1-or-later

package com.osfans.trime.ime.keyboard

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Ported from Fcitx5 for Android sendCombinationKeyEvents (LGPL-2.1-or-later).
 * Linux evdev scancodes match its generated ScancodeMapping for these three keys.
 * Send directly to the editor so the IME hook cannot consume the combination.
 */
internal fun sendUndoRedoCombination(
    connection: InputConnection,
    redo: Boolean,
) {
    val eventTime = SystemClock.uptimeMillis()
    val metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or
        (if (redo) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0)
    fun send(action: Int, code: Int, scanCode: Int, meta: Int = 0) {
        connection.sendKeyEvent(
            KeyEvent(
                eventTime,
                if (action == KeyEvent.ACTION_DOWN) eventTime else SystemClock.uptimeMillis(),
                action,
                code,
                0,
                meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                scanCode,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            ),
        )
    }
    send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 29)
    if (redo) send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 42)
    send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 44, metaState)
    send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 44, metaState)
    if (redo) send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 42)
    send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 29)
}
