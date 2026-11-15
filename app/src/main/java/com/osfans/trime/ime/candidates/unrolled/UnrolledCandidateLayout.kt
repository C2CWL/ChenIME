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
    private val actions = mutableListOf<TextView>()
    private val leftRail = LinearLayout(context).apply { orientation = VERTICAL }
    private var displayedSyllables: List<String> = emptyList()
    private val rowHeight = dp(expandedRowHeightDp(scope.theme.style.candidateViewHeight, scope.theme.style.commentHeight))
    var onSyllableToggle: ((Int, List<String>) -> Unit)? = null
    var onCompositionKey: ((String) -> Unit)? = null
    var activeSyllable: Int? = null
        set(value) {
            field = value
            refreshColors()
        }
    init {
        id = R.id.unrolled_candidate_view
        orientation = VERTICAL
        // A rounded outline can punch a hole at the bottom-left rail and let a key show through.
        clipToOutline = false
        val body = LinearLayout(context)
        leftRail.addView(ScrollView(context).apply { addView(syllables) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(leftRail, LayoutParams(dp(64), LayoutParams.MATCH_PARENT))
        val center = LinearLayout(context).apply { orientation = VERTICAL }
        center.addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(center, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        val right = LinearLayout(context).apply { orientation = VERTICAL }
        listOf("↑" to "上一页", "↓" to "下一页", "⌫" to "退格").forEachIndexed { index, (label, description) ->
            val view = button(label, description) {
                when (index) {
                    0 -> recyclerView.smoothScrollBy(0, -recyclerView.height)
                    1 -> recyclerView.smoothScrollBy(0, recyclerView.height)
                    2 -> onCompositionKey?.invoke("BackSpace")
                }
            }
            actions.add(view)
            right.addView(
                FrameLayout(context).apply { addView(view, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.CENTER)) },
                LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        body.addView(right, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun button(label: String, description: String, action: () -> Unit) = TextView(context).apply {
        text = label
        contentDescription = description
        textSize = 15f
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.START
        isFocusable = true
        setOnClickListener { action() }
    }

    fun updatePreedit(preedit: String?) {
        val labels = preeditSyllables(preedit)
        if (labels == displayedSyllables) return
        displayedSyllables = labels
        while (syllables.childCount > labels.size) syllables.removeViewAt(syllables.childCount - 1)
        labels.forEachIndexed { index, syllable ->
            val view = (syllables.getChildAt(index) as? TextView) ?: button(syllable, "筛选 $syllable") {}.also {
                syllables.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
            }
            view.text = syllable
            view.contentDescription = "筛选 $syllable"
            view.setOnClickListener { onSyllableToggle?.invoke(index, displayedSyllables) }
        }
        refreshColors()
    }

    fun resetPosition() = recyclerView.scrollToPosition(0)

    fun refreshColors() {
        background = scope.decorDrawable("candidate_background", "candidate_border_color", dp(scope.theme.style.candidateBorder), dp(scope.theme.style.candidateBorderRound))
        val colors = scope.colors
        leftRail.setBackgroundColor(colors.candidateBackground)
        val selected = buildSet {
            activeSyllable?.let { end -> for (i in 0..end) (syllables.getChildAt(i) as? TextView)?.let(::add) }
        }
        val views = actions +
            (0 until syllables.childCount).map { syllables.getChildAt(it) as TextView }
        views.forEach { view ->
            val highlighted = view in selected
            view.isSelected = highlighted
            view.setTextColor(if (highlighted) colors.hilitedCandidateTextColor else colors.candidateTextColor)
            view.background = roundedRippleDrawable(colors.hilitedCandidateBackColor, dp(6f), if (highlighted) colors.hilitedCandidateBackColor else colors.candidateBackground)
        }
        recyclerView.invalidateItemDecorations()
    }
}
