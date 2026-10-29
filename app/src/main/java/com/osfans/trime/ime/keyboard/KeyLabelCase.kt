// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Overrides theme label casing only for display, preserving the existing shifted label. */
internal fun keyLabelForDisplay(
    label: String,
    uppercase: Boolean,
    shifted: Boolean,
): String {
    if (shifted || label.length != 1 || (label[0] !in 'a'..'z' && label[0] !in 'A'..'Z')) return label
    return if (uppercase) label.uppercase() else label.lowercase()
}
