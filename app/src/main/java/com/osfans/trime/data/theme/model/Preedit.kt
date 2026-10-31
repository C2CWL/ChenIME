/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
data class Preedit(
    val horizontalPadding: Int = 8,
    /** Gap (dp) below the preedit row so a slightly high tap on a candidate is not a preedit tap. */
    val bottomMargin: Int = 8,
    /**
     * Tapping the preedit moves Rime's caret. Off by default: the tap offset is measured in
     * the displayed preedit (syllable spaces, converted segments), not in the raw input, and a
     * mid-input caret makes later letters and BackSpace edit the middle of the code.
     */
    val tapToMoveCursor: Boolean = false,
    val topStartRadius: Float = 0f,
    val topEndRadius: Float = 0f,
    val alpha: Float = 0.8f,
    val foreground: Foreground = Foreground(),
) {

    @Serializable
    data class Foreground(
        val fontSize: Float = 16f,
    )
}
