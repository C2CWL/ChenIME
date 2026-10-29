// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp

@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(context: Context, private val scope: ThemeScope) : LinearLayout(context) {
    val recyclerView = RecyclerView(context)
    private val syllables = LinearLayout(context).apply { orientation = VERTICAL }
    private val left = LinearLayout(context).apply { orientation = VERTICAL }
    private val right = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
    }
    private val single = button("单字", "单字") { onSingleCharacterToggle?.invoke() }
    private val actions = mutableListOf<TextView>()
    var onSingleCharacterToggle: (() -> Unit)? = null
    var onCompositionKey: ((String) -> Unit)? = null
    var singleCharacter = false
        set(value) {
            field = value
            refreshColors()
        }

    init {
        id = R.id.unrolled_candidate_view
        orientation = HORIZONTAL
        clipToOutline = true
        left.addView(ScrollView(context).apply { addView(syllables) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        left.addView(single, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(left, LayoutParams(0, LayoutParams.MATCH_PARENT, .15f))
        addView(recyclerView, LayoutParams(0, LayoutParams.MATCH_PARENT, .70f))
        listOf("↑" to "上一页", "↓" to "下一页", "⌫" to "退格", "↵" to "回车").forEachIndexed { index, (label, description) ->
            val view = button(label, description) {
                when (index) {
                    0 -> recyclerView.smoothScrollBy(0, -recyclerView.height)
                    1 -> recyclerView.smoothScrollBy(0, recyclerView.height)
                    2 -> onCompositionKey?.invoke("BackSpace")
                    3 -> onCompositionKey?.invoke("Return")
                }
            }
            actions.add(view)
            val slot = FrameLayout(context).apply {
                addView(view, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
                addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                    val size = minOf(dp(48), r - l - dp(4), b - t - dp(8)).coerceAtLeast(1)
                    if (view.layoutParams.width != size) {
                        view.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                    }
                }
            }
            right.addView(slot, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, .15f))
        refreshColors()
    }

    private fun button(label: String, description: String, action: () -> Unit) = TextView(context).apply {
        text = label
        contentDescription = description
        textSize = 20f
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private var displayedSyllables: List<String> = emptyList()

    fun updatePreedit(preedit: String?) {
        val labels = preeditSyllables(preedit)
        if (labels == displayedSyllables) return
        displayedSyllables = labels
        while (syllables.childCount > labels.size) syllables.removeViewAt(syllables.childCount - 1)
        labels.forEachIndexed { index, syllable ->
            val view = (syllables.getChildAt(index) as? TextView) ?: button(syllable, syllable) {}.also {
                syllables.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
            }
            if (view.text.toString() != syllable) view.text = syllable
            view.contentDescription = syllable
            view.setTextColor(scope.colors.candidateTextColor)
        }
    }

    fun resetPosition() = recyclerView.scrollToPosition(0)

    fun refreshColors() {
        background = scope.decorDrawable("candidate_background", "candidate_border_color", dp(scope.theme.style.candidateBorder), dp(scope.theme.style.candidateBorderRound))
        val colors = scope.colors
        for (i in 0 until syllables.childCount) (syllables.getChildAt(i) as TextView).setTextColor(colors.candidateTextColor)
        single.setTextColor(if (singleCharacter) colors.hilitedCandidateTextColor else colors.candidateTextColor)
        single.background = roundedRippleDrawable(colors.hilitedCandidateBackColor, dp(8f), if (singleCharacter) colors.hilitedCandidateBackColor else colors.candidateBackground)
        actions.forEachIndexed { index, view ->
            view.setTextColor(if (index == 3) colors.hilitedKeyTextColor else colors.keyTextColor)
            view.background = roundedRippleDrawable(colors.hilitedKeyBackColor, dp(100f), if (index == 3) colors.hilitedKeyBackColor else colors.keyBackColor)
        }
        recyclerView.invalidateItemDecorations()
    }
}
