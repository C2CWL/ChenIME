/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.Gravity
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.bar.ToolbarButtonLayout
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view

class ButtonsBarUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val onButtonClick: ((String) -> Unit)? = null,
) : Ui {
    private val theme: Theme get() = scope.theme

    override val root = view(::LinearLayout) {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val firstButton: ToolButton?
        get() = root.children.filterIsInstance<ToolButton>().firstOrNull {
            // The yaml Hide button, if it is currently shown.
            (it.tag as? String) == ToolbarButtonLayout.HIDE_ACTION
        }

    private val defaultButtonSize = theme.style.run { candidateViewHeight + commentHeight }

    private var lastActions: List<String> = emptyList()

    private fun toolButton(
        buttonConfig: ToolBar.Button,
        voiceBesideHide: Boolean,
        hideOnRight: Boolean,
    ): ToolButton = ToolButton(ctx, buttonConfig, scope).apply {
        tag = buttonConfig.action
        if (buttonConfig.action == ToolbarButtonLayout.VOICE_ACTION) {
            // Lift must stay on this button; a press in the slop strip
            // beside Hide is treated as Hide so a near-miss does not switch IME.
            requireReleaseInside = true
            setOnClickListener {
                val slop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
                val action = if (voiceBesideHide && lastDownIsNearHideEdge(slop, hideOnRight)) {
                    ToolbarButtonLayout.HIDE_ACTION
                } else {
                    buttonConfig.action
                }
                onButtonClick?.invoke(action)
            }
        } else {
            setOnClickListener { onButtonClick?.invoke(buttonConfig.action) }
        }
        val longPressAction = buttonConfig.longPressAction
        if (longPressAction.isNotEmpty()) {
            setOnLongClickListener {
                onButtonClick?.invoke(longPressAction)
                true
            }
        }
    }

    /**
     * Rebuilds the row so [actions] share the bar width evenly, with a gap
     * between neighboring hit areas. No-op when the actions are unchanged.
     */
    fun rebuild(actions: List<String>) {
        if (actions == lastActions && root.childCount == actions.size) return
        lastActions = actions
        root.removeAllViews()
        val gap = ctx.dp(theme.toolBar.buttonSpacing)
        val voiceBesideHide = ToolbarButtonLayout.isVoiceBesideHide(actions)
        val hideOnRight = ToolbarButtonLayout.hideIsOnRightOfVoice(actions)
        actions.forEachIndexed { index, action ->
            val config = configFor(action) ?: return@forEachIndexed
            val button = toolButton(config, voiceBesideHide, hideOnRight)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                // Half-gap on each side so neighboring buttons do not touch and
                // the space between every pair is the same.
                marginStart = if (index == 0) 0 else gap / 2
                marginEnd = if (index == actions.lastIndex) 0 else gap / 2
            }
            root.addView(button, lp)
        }
    }

    private fun configFor(action: String): ToolBar.Button? {
        val primary = theme.toolBar.primaryButton
        if (primary?.action == action) return primary
        return theme.toolBar.buttons.find { it.action == action }
    }

    internal fun getButtonSize(config: ToolBar.Button?, customDefaultSize: Int = defaultButtonSize): Pair<Int, Int> {
        val sizeList = config?.size?.takeIf { it.size == 2 } ?: List(2) { customDefaultSize }
        val (width, height) = sizeList

        val finalWidth = if (width >= 0) ctx.dp(width) else width
        val finalHeight = if (height >= 0) ctx.dp(height) else height

        return finalWidth to finalHeight
    }

    fun updateStyle(option: String, enabled: Boolean) {
        root.children.forEach { (it as ToolButton).updateStyle(option, enabled) }
    }

    /** Restyles the toolbar buttons after a scheme switch. */
    fun refreshColors() {
        root.children.forEach { (it as ToolButton).refreshColors() }
    }
}
