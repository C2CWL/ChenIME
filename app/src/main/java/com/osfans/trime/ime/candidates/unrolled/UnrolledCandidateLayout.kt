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
    private val frequency = button("词频", "按词频显示全部候选") { onResetFilters?.invoke() }
    private val single = button("单字", "仅显示单字") { onSingleCharacterToggle?.invoke() }
    private val handwriting = button("手写找字", "手写筛选当前拼音候选") { onHandwriting?.invoke() }
    private val strokeToggle = button("首笔画 ›", "展开笔画筛选") { setStrokeExpanded(!strokeExpanded) }.apply { textSize = 13f }
    private val strokeRow = LinearLayout(context)
    private val strokeKeys = mutableListOf<TextView>()
    private val strokeUndo = button("⌫", "撤回一笔，长按清空笔画") { onStrokeChange?.invoke(strokePrefix.dropLast(1)) }
    private val actions = mutableListOf<TextView>()
    private val leftRail = LinearLayout(context).apply { orientation = VERTICAL }
    private var strokeExpanded = false
    private var displayedSyllables: List<String> = emptyList()
    private val rowHeight = dp(expandedRowHeightDp(scope.theme.style.candidateViewHeight, scope.theme.style.commentHeight))
    var onResetFilters: (() -> Unit)? = null
    var onHandwriting: (() -> Unit)? = null
    var onStrokeChange: ((String) -> Unit)? = null
    var onSingleCharacterToggle: (() -> Unit)? = null
    var onSyllableToggle: ((Int, List<String>) -> Unit)? = null
    var onCompositionKey: ((String) -> Unit)? = null
    var strokePrefix = ""
        private set
    var activeSyllable: Int? = null
        set(value) {
            field = value
            refreshColors()
        }
    var singleCharacter = false
        set(value) {
            field = value
            refreshColors()
        }

    init {
        id = R.id.unrolled_candidate_view
        orientation = VERTICAL
        // A rounded outline can punch a hole at the bottom-left rail and let a key show through.
        clipToOutline = false
        addView(
            LinearLayout(context).apply {
                setPadding(dp(6), dp(3), dp(6), dp(3))
                listOf(frequency, single, handwriting).forEach {
                    addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(40)),
        )
        val body = LinearLayout(context)
        leftRail.addView(ScrollView(context).apply { addView(syllables) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        leftRail.addView(strokeToggle, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
        body.addView(leftRail, LayoutParams(dp(64), LayoutParams.MATCH_PARENT))
        val center = LinearLayout(context).apply { orientation = VERTICAL }
        center.addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        listOf("一" to "横", "丨" to "竖", "丿" to "撇", "丶" to "点或捺", "折" to "折").forEachIndexed { index, (label, description) ->
            val key = button(label, description) { onStrokeChange?.invoke(strokePrefix + ('1' + index)) }
            key.textSize = 22f
            strokeKeys.add(key)
            strokeRow.addView(key, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        strokeUndo.setOnLongClickListener {
            onStrokeChange?.invoke("")
            true
        }
        strokeRow.addView(strokeUndo, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
        center.addView(strokeRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
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
        setStrokeExpanded(false)
        updateStrokePrefix("")
    }

    fun setStrokeExpanded(expanded: Boolean) {
        strokeExpanded = expanded
        strokeRow.visibility = if (expanded) VISIBLE else GONE
        strokeToggle.text = if (expanded) "首笔画 ‹" else "首笔画 ›"
        strokeToggle.contentDescription = if (expanded) "收起并取消笔画筛选" else "展开笔画筛选"
        if (!expanded && strokePrefix.isNotEmpty()) onStrokeChange?.invoke("")
        refreshColors()
    }

    fun updateStrokePrefix(prefix: String) {
        strokePrefix = prefix
        strokeToggle.text = if (prefix.isEmpty()) {
            if (strokeExpanded) "首笔画 ‹" else "首笔画 ›"
        } else {
            prefix.map { "一丨丿丶折"[it - '1'] }.joinToString("")
        }
        strokeUndo.isEnabled = prefix.isNotEmpty()
        strokeUndo.alpha = if (prefix.isEmpty()) .35f else 1f
        refreshColors()
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
            if (singleCharacter) add(single)
            if (!singleCharacter && activeSyllable == null && strokePrefix.isEmpty()) add(frequency)
            if (strokeExpanded) add(strokeToggle)
            strokePrefix.lastOrNull()?.let { add(strokeKeys[it - '1']) }
            activeSyllable?.let { end -> for (i in 0..end) (syllables.getChildAt(i) as? TextView)?.let(::add) }
        }
        val views = listOf(frequency, single, handwriting, strokeToggle, strokeUndo) + strokeKeys + actions +
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
