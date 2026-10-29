/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.popup

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.toIconName
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class PopupEntryUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val keyHeight: Int,
    radius: Float,
) : Ui {
    private val theme: Theme get() = scope.theme

    private val previewBackColor get() = runCatching { scope.color("preview_back_color") }.getOrDefault(scope.colors.popupBackColor)
    private val previewTextColor get() = runCatching { scope.color("preview_text_color") }.getOrDefault(scope.colors.popupTextColor)

    var capsule = false
        set(value) {
            field = value
            textView.layoutParams = textView.layoutParams.apply { height = if (value) ctx.dp(36) else keyHeight }
            textView.textSize = if (value) 16f else theme.style.popupTextSize
        }

    var lastShowTime = -1L

    val textView = view(::AutoScaleTextView) {
        distinctLetterLabels = true
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = theme.style.popupTextSize
        gravity = gravityCenter
        setTextColor(previewTextColor)
        typeface = theme.fonts.popup
    }

    val imageView = view(::AppCompatImageView) {
        visibility = android.view.View.GONE
    }

    private val popupBackground = GradientDrawable().apply {
        cornerRadius = radius
        setColor(previewBackColor)
    }

    override val root = constraintLayout {
        background = popupBackground
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(2f)
        add(
            textView,
            lParams(wrapContent, keyHeight) {
                topOfParent()
                centerHorizontally()
            },
        )
        add(
            imageView,
            lParams(wrapContent, keyHeight) {
                topOfParent()
                centerHorizontally()
            },
        )
    }

    /** Re-applies the scheme colors, as pooled popups outlive scheme switches. */
    fun refreshColors() {
        popupBackground.setColor(previewBackColor)
        textView.setTextColor(previewTextColor)
        imageView.drawable?.colorFilter =
            PorterDuffColorFilter(previewTextColor, PorterDuff.Mode.SRC_IN)
    }

    fun setText(text: String) {
        if (text.isIconFont) {
            imageView.setImageDrawable(
                IconicsDrawable(ctx, text.toIconName()).apply {
                    sizeDp = theme.style.popupTextSize.toInt()
                    colorFilter = PorterDuffColorFilter(previewTextColor, PorterDuff.Mode.SRC_IN)
                },
            )
            imageView.isVisible = true
            textView.isVisible = false
        } else {
            textView.text = text
            textView.isVisible = true
            imageView.isVisible = false
        }
    }
}
