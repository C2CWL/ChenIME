/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.widget.TextView
import androidx.core.text.PrecomputedTextCompat
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.setThemeLabel
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class LiquidItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    val mainText = view(::TextView) {
        gravity = gravityCenter
        isSingleLine = true
        isClickable = false
        isFocusable = false
        background = null
        textSize = theme.style.keyTextSize
        typeface = theme.fonts.key
        setPaddingDp(8, 4, 8, 4)
        setTextColor(scope.colors.keyTextColor)
    }

    private val content = constraintLayout {
        background = scope.decorDrawable(
            "key_back_color",
            "key_border_color",
            dp(theme.style.keyBorder),
            dp(theme.style.roundCorner),
        )
        add(
            mainText,
            lParams(wrapContent, wrapContent) {
                centerInParent()
            },
        )
    }

    override val root = view(::GestureFrame) {
        add(content, lParams(matchParent, matchParent))
    }

    private var label = ""
    private var fallback = ""

    fun setLabel(value: String, fallback: String = "", prepared: PrecomputedTextCompat? = null) {
        if (label == value && this.fallback == fallback) return
        label = value
        this.fallback = fallback
        if (prepared != null) {
            mainText.text = prepared
            mainText.contentDescription = value
        } else {
            mainText.setThemeLabel(value, fallback)
        }
    }

    /** Restyles the item after a scheme switch. */
    fun refreshColors() {
        mainText.setTextColor(scope.colors.keyTextColor)
        mainText.setThemeLabel(label, fallback)
        content.background = scope.decorDrawable(
            "key_back_color",
            "key_border_color",
            ctx.dp(theme.style.keyBorder),
            ctx.dp(theme.style.roundCorner),
        )
    }
}
