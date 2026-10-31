/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * User corner radius overrides in dp, one per keyboard region.
 *
 * A negative value ([FOLLOW_THEME]) keeps the radius the theme declares, so the
 * default settings leave every theme looking exactly as written.
 */
data class CornerRadiusOverrides(
    val key: Int = FOLLOW_THEME,
    val panel: Int = FOLLOW_THEME,
    val bar: Int = FOLLOW_THEME,
    val popup: Int = FOLLOW_THEME,
) {
    /** Key backgrounds; [themeRadius] is the radius the key resolved from the theme. */
    fun key(themeRadius: Float): Float = resolve(key, themeRadius)

    /** Top corners of the whole keyboard panel. Themes declare none, so it is square by default. */
    fun panel(): Float = resolve(panel, 0f)

    /** Candidate highlights and toolbar buttons in the candidate bar. */
    fun bar(themeRadius: Float): Float = resolve(bar, themeRadius)

    /** Key previews, long-press popups, the 更多 panel, symbol and clipboard items, floating candidates. */
    fun popup(themeRadius: Float): Float = resolve(popup, themeRadius)

    companion object {
        const val FOLLOW_THEME = -1
        const val MAX_RADIUS = 32

        fun isOverridden(value: Int): Boolean = value >= 0

        /** Returns [themeRadius] unless [override] is set; overrides are clamped to `0..MAX_RADIUS`. */
        fun resolve(
            override: Int,
            themeRadius: Float,
        ): Float = if (isOverridden(override)) override.coerceAtMost(MAX_RADIUS).toFloat() else themeRadius

        /** The overrides currently saved in settings. */
        fun current(): CornerRadiusOverrides = ThemeManager.prefs.cornerRadiusOverrides()
    }
}
