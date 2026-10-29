// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Local modifier state; never changes Rime's ASCII mode or commits text. */
internal data class ShiftState(val shifted: Boolean = false, val locked: Boolean = false) {
    fun press(lock: Boolean): ShiftState = when {
        lock -> ShiftState(shifted = true, locked = true)
        locked || shifted -> ShiftState()
        else -> ShiftState(shifted = true)
    }

    fun afterKey(): ShiftState = if (locked) this else ShiftState()
}

/** Preserve composing bindings except when a locked Shift must be available to unlock. */
internal fun useComposingAction(composing: Boolean, isShift: Boolean, locked: Boolean): Boolean = composing && !(isShift && locked)
