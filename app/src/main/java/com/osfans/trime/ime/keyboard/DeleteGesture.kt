// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Keeps every step of a composing delete gesture away from the editor. */
internal class DeleteGesture {
    private var clearedComposition = false

    fun consume(isComposing: Boolean): Boolean {
        clearedComposition = clearedComposition || isComposing
        return clearedComposition
    }
}
