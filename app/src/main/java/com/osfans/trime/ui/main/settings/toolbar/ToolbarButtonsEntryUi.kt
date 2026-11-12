/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.toolbar

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.resources.color
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledDimenPxSize
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.textAppearance

class ToolbarButtonsEntryUi(override val ctx: Context) : Ui {

    val dragHandle = view(::TextView) {
        text = "≡"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(color(R.color.settings_text))
        contentDescription = ctx.getString(R.string.toolbar_button_drag)
    }

    val nameText = view(::TextView) {
        textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        setTextColor(color(R.color.settings_text))
        isClickable = false
    }

    val moveUp = view(::ImageButton) {
        setImageResource(R.drawable.ic_baseline_arrow_drop_down_24)
        rotation = 180f
        background = null
        contentDescription = ctx.getString(R.string.toolbar_button_move_up)
        imageTintList = android.content.res.ColorStateList.valueOf(ctx.color(R.color.settings_text))
    }

    val moveDown = view(::ImageButton) {
        setImageResource(R.drawable.ic_baseline_arrow_drop_down_24)
        background = null
        contentDescription = ctx.getString(R.string.toolbar_button_move_down)
        imageTintList = android.content.res.ColorStateList.valueOf(ctx.color(R.color.settings_text))
    }

    val toggle = SwitchCompat(ctx).apply {
        contentDescription = ctx.getString(R.string.toolbar_buttons)
    }

    override val root: View = constraintLayout {
        layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent).apply {
            bottomMargin = dp(8)
        }
        background = roundedRippleDrawable(
            color(R.color.settings_divider),
            dp(20).toFloat(),
            color(R.color.settings_surface),
        )
        minHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeight)
        val pad = styledDimenPxSize(android.R.attr.listPreferredItemPaddingStart)

        add(
            dragHandle,
            lParams(dp(32), matchConstraints) {
                startOfParent(pad)
                centerVertically()
            },
        )
        add(
            toggle,
            lParams(wrapContent, wrapContent) {
                endOfParent(pad)
                centerVertically()
            },
        )
        add(
            moveDown,
            lParams(dp(36), dp(36)) {
                before(toggle)
                centerVertically()
            },
        )
        add(
            moveUp,
            lParams(dp(36), dp(36)) {
                before(moveDown)
                centerVertically()
            },
        )
        add(
            nameText,
            lParams(matchConstraints, wrapContent) {
                after(dragHandle, pad)
                before(moveUp)
                centerVertically()
            },
        )
    }
}
