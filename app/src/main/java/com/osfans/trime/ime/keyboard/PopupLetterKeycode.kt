// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Literal popup letters use their case-sensitive Rime keysym during composition. */
internal fun popupLetterKeycode(commit: String, isComposing: Boolean): Int? = commit.singleOrNull()?.takeIf { isComposing && (it in 'A'..'Z' || it in 'a'..'z') }?.code
