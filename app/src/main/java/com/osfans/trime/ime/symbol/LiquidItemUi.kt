/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
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

    val mainText: TextView = view(::LiquidLabelView) {
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

    internal var fixedKeyStyle: FixedKeyStyle? = null

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
        val style = fixedKeyStyle
        if (style != null) {
            mainText.setTypeface(theme.fonts.key, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
            mainText.isDuplicateParentStateEnabled = true
            content.isDuplicateParentStateEnabled = true
            mainText.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                    intArrayOf(scope.color(style.pressedText), scope.color(style.text)),
                ),
            )
            content.background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    scope.decorDrawable(
                        style.pressedBackground,
                        style.pressedBorder,
                        ctx.dp(theme.style.keyBorder),
                        ctx.dp(theme.style.roundCorner),
                    ),
                )
                addState(
                    intArrayOf(),
                    scope.decorDrawable(
                        style.background,
                        style.border,
                        ctx.dp(theme.style.keyBorder),
                        ctx.dp(theme.style.roundCorner),
                    ),
                )
            }
            mainText.setThemeLabel(label, fallback)
            return
        }
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

/** ImageSpan drawables do not inherit TextView's stateful text colors. */
private class LiquidLabelView(context: Context) : AppCompatTextView(context) {
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val label = text as? Spanned ?: return
        label.getSpans(0, label.length, ImageSpan::class.java).forEach {
            it.drawable.colorFilter = PorterDuffColorFilter(currentTextColor, PorterDuff.Mode.SRC_IN)
        }
        invalidate()
    }
}
