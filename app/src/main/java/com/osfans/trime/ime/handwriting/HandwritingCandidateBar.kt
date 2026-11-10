/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

@SuppressLint("ViewConstructor")
internal class HandwritingCandidateBar(
    context: Context,
    private val scope: ThemeScope,
    private val onPick: (HandwritingCandidate) -> Unit,
) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private var items: List<HandwritingCandidate> = emptyList()
    private var selected = 0
    private var emptyMessage: String? = null

    init {
        isHorizontalScrollBarEnabled = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        val pad = dp(8)
        setPadding(pad, dp(6), pad, dp(6))
    }

    val selectedCandidate: HandwritingCandidate?
        get() = items.getOrNull(selected)

    fun setCandidates(candidates: List<HandwritingCandidate>, select: Int = 0, emptyMessage: String? = null) {
        items = candidates
        this.emptyMessage = emptyMessage
        selected = select.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
        rebuild()
    }

    fun refreshColors() {
        rebuild()
    }

    private fun rebuild() {
        row.removeAllViews()
        val colors = scope.colors
        val gap = dp(8)
        val height = dp(40)
        if (items.isEmpty()) {
            emptyMessage?.let { message ->
                row.addView(
                    TextView(context).apply {
                        text = message
                        gravity = gravityCenter
                        horizontalPadding = dp(14)
                        setTextColor(colors.candidateTextColor)
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, height),
                )
            }
        }
        items.forEachIndexed { index, item ->
            val selectedItem = index == selected
            val chip = TextView(context).apply {
                text = item.text
                textSize = 18f
                gravity = gravityCenter
                horizontalPadding = dp(14)
                setTextColor(if (selectedItem) colors.hilitedCandidateTextColor else colors.candidateTextColor)
                background = roundedRippleDrawable(
                    colors.hilitedCandidateBackColor,
                    dp(12f),
                    if (selectedItem) colors.hilitedCandidateBackColor else colors.keyBackColor,
                )
                setOnClickListener {
                    selected = index
                    rebuild()
                    onPick(item)
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, height).apply {
                if (index > 0) marginStart = gap
            }
            row.addView(chip, lp)
        }
    }
}
