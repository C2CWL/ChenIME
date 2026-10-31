/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.c2cwl.chenime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum
import com.osfans.trime.data.prefs.PreferenceDelegateOwner

class ThemePrefs(
    sharedPrefs: SharedPreferences,
) : PreferenceDelegateOwner(sharedPrefs, R.string.theme) {
    val selectedTheme =
        string(
            R.string.selected_theme,
            SELECTED_THEME,
            "iflytek.trime",
            R.string.selected_theme_summary,
        )

    val normalModeColor =
        string(
            R.string.normal_mode_color,
            NORMAL_MODE_COLOR,
            "default",
            R.string.normal_mode_color_summary,
        )

    enum class NavbarBackground(
        override val stringRes: Int,
    ) : PreferenceDelegateEnum {
        NONE(R.string.navbar_bkg_none),
        COLOR_ONLY(R.string.navbar_bkg_color_only),
        FULL(R.string.navbar_bkg_full),
    }

    val navbarBackground =
        enum(
            R.string.navbar_background,
            NAVBAR_BACKGROUND,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                NavbarBackground.FULL
            } else {
                NavbarBackground.COLOR_ONLY
            },
            enableUiOn = { Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM },
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                sharedPreferences.edit {
                    remove(this@apply.key)
                }
            }
        }

    val followSystemDayNight =
        switch(
            R.string.follow_system_day_night_color,
            FOLLOW_SYSTEM_DAY_NIGHT,
            true,
        )

    val keyCornerRadius = cornerRadius(R.string.key_corner_radius, KEY_CORNER_RADIUS)
    val panelCornerRadius = cornerRadius(R.string.panel_corner_radius, PANEL_CORNER_RADIUS)
    val barCornerRadius = cornerRadius(R.string.bar_corner_radius, BAR_CORNER_RADIUS)
    val popupCornerRadius = cornerRadius(R.string.popup_corner_radius, POPUP_CORNER_RADIUS)

    /** Every corner radius preference; the input method rebuilds its views when one changes. */
    val cornerRadiusPrefs get() = listOf(keyCornerRadius, panelCornerRadius, barCornerRadius, popupCornerRadius)

    fun cornerRadiusOverrides() = CornerRadiusOverrides(
        key = keyCornerRadius.getValue(),
        panel = panelCornerRadius.getValue(),
        bar = barCornerRadius.getValue(),
        popup = popupCornerRadius.getValue(),
    )

    private fun cornerRadius(
        @StringRes title: Int,
        key: String,
    ) = int(
        title,
        key,
        CornerRadiusOverrides.FOLLOW_THEME,
        CornerRadiusOverrides.FOLLOW_THEME,
        CornerRadiusOverrides.MAX_RADIUS,
        "dp",
        defaultLabel = R.string.follow_theme,
        useMinAsDefault = true,
    )

    companion object {
        const val KEY_CORNER_RADIUS = "corner_radius_key"
        const val PANEL_CORNER_RADIUS = "corner_radius_panel"
        const val BAR_CORNER_RADIUS = "corner_radius_bar"
        const val POPUP_CORNER_RADIUS = "corner_radius_popup"
        const val SELECTED_THEME = "selected_theme"
        const val NORMAL_MODE_COLOR = "normal_mode_color"
        const val FOLLOW_SYSTEM_DAY_NIGHT = "follow_system_day_night"
        const val NAVBAR_BACKGROUND = "navbar_background"
    }
}
