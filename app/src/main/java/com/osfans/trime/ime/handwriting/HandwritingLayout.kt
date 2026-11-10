/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.c2cwl.chenime.R
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding
import splitties.views.imageDrawable

@SuppressLint("ViewConstructor")
class HandwritingLayout(
    context: android.content.Context,
    private val scope: ThemeScope,
    mode: HandwritingMode,
    private val onAction: (HandwritingAction) -> Unit,
) : LinearLayout(context) {
    var mode: HandwritingMode = mode
        private set
    val canvasView = HandwritingCanvasView(context).apply {
        contentDescription = context.getString(R.string.handwriting_canvas)
    }
    private val actionViews = mutableListOf<View>()
    private val actionRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        orientation = VERTICAL
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        addView(
            canvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = pad },
        )
        addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rebuildActions()
        refreshColors()
    }

    fun bindMode(newMode: HandwritingMode) {
        if (mode == newMode && actionViews.isNotEmpty()) return
        mode = newMode
        rebuildActions()
        refreshColors()
    }

    private fun rebuildActions() {
        actionRow.removeAllViews()
        actionViews.clear()
        val gap = dp(8)
        mode.actions.forEachIndexed { index, action ->
            val button = actionButton(action)
            actionViews.add(button)
            actionRow.addView(
                button,
                LayoutParams(0, dp(48), weightFor(action)).apply {
                    if (index > 0) marginStart = gap
                },
            )
        }
    }

    private fun weightFor(action: HandwritingAction): Float = when (action) {
        HandwritingAction.Confirm, HandwritingAction.Enter, HandwritingAction.Space -> 1.3f
        else -> 1f
    }

    private fun actionButton(action: HandwritingAction): View {
        val label = actionLabel(action)
        val icon = actionIcon(action)
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = gravityCenter
            horizontalPadding = dp(4)
            contentDescription = label
            isClickable = true
            isFocusable = true
            if (icon != 0) {
                add(
                    imageView {
                        imageDrawable = context.getDrawable(icon)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    lParams(dp(22), dp(22)),
                )
            } else {
                add(
                    textView {
                        text = label
                        textSize = 16f
                        gravity = gravityCenter
                    },
                    lParams(wrapContent, wrapContent),
                )
            }
            setOnClickListener { onAction(action) }
        }
    }

    private fun actionLabel(action: HandwritingAction): String = context.getString(
        when (action) {
            HandwritingAction.Back -> R.string.handwriting_back
            HandwritingAction.Clear -> R.string.handwriting_clear
            HandwritingAction.Space -> R.string.handwriting_space
            HandwritingAction.Backspace -> R.string.handwriting_backspace
            HandwritingAction.Confirm -> R.string.handwriting_confirm
            HandwritingAction.Enter -> R.string.handwriting_enter
        },
    )

    private fun actionIcon(action: HandwritingAction): Int = when (action) {
        HandwritingAction.Backspace -> R.drawable.ic_baseline_backspace_24
        HandwritingAction.Enter -> R.drawable.ic_baseline_keyboard_return_24
        else -> 0
    }

    fun refreshColors() {
        val colors = scope.colors
        setBackgroundColor(colors.keyboardBackColor)
        canvasView.wellColor = colors.offKeyBackColor
        val well = canvasView.wellColor
        val wellLum = Color.red(well) + Color.green(well) + Color.blue(well)
        canvasView.inkColor = if (wellLum < 384) {
            colors.keyTextColor
        } else {
            colors.keyTextColor
        }
        mode.actions.forEachIndexed { index, action ->
            val view = actionViews[index] as LinearLayout
            val accent = action == HandwritingAction.Confirm || action == HandwritingAction.Enter
            val fill = if (accent) colors.hilitedKeyBackColor else colors.offKeyBackColor
            val text = if (accent) colors.hilitedKeyTextColor else colors.keyTextColor
            view.background = roundedRippleDrawable(colors.hilitedKeyBackColor, dp(16f), fill)
            for (i in 0 until view.childCount) {
                when (val child = view.getChildAt(i)) {
                    is TextView -> child.setTextColor(text)
                    is ImageView -> child.setColorFilter(text)
                }
            }
        }
    }
}
