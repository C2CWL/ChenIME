/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/** Keeps font roles resolved until the theme, preferences, or font files change. */
internal class ResolvedFontCache<T : Any> {
    private val values = mutableMapOf<String, T>()

    fun get(role: String, resolve: () -> T): T = values.getOrPut(role, resolve)

    fun invalidate() {
        values.clear()
    }
}
