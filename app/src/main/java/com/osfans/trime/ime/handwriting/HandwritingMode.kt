/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

/**
 * Two placements share one pad:
 * [FindChar] is opened from the expanded candidates 「手写」 button (above 「单字」)
 * and inserts one looked-up character.
 * [Full] is opened from 「更多」 and stays open for character-by-character input.
 */
enum class HandwritingMode {
    FindChar,
    Full,
    ;

    val actions: List<HandwritingAction>
        get() = when (this) {
            FindChar -> listOf(
                HandwritingAction.Back,
                HandwritingAction.Clear,
                HandwritingAction.Backspace,
                HandwritingAction.Confirm,
            )

            Full -> listOf(
                HandwritingAction.Back,
                HandwritingAction.Clear,
                HandwritingAction.Space,
                HandwritingAction.Backspace,
                HandwritingAction.Enter,
            )
        }
}

enum class HandwritingAction {
    Back,
    Clear,
    Space,
    Backspace,
    Confirm,
    Enter,
}
