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
    private val single = button("单字", "仅显示单字") { onSingleCharacterToggle?.invoke() }.apply { textSize = 16f }
    private val strokeKeys = mutableListOf<TextView>()
    private val strokeSummary = TextView(context)
    private val strokeUndo = button("⌫", "撤回一笔") { onStrokeChange?.invoke(strokePrefix.dropLast(1)) }
    private val strokeClear = button("清空", "清空笔画") { onStrokeChange?.invoke("") }
    var onStrokeChange: ((String) -> Unit)? = null
    var strokePrefix = ""
        private set
    private val actions = mutableListOf<TextView>()
    var onSingleCharacterToggle: (() -> Unit)? = null
    var onSyllableToggle: ((Int, List<String>) -> Unit)? = null
    var activeSyllable: Int? = null
        set(value) {
            field = value
            refreshColors()
        }
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
        addView(left, LayoutParams(0, LayoutParams.MATCH_PARENT, .14f))
        val center = LinearLayout(context).apply { orientation = VERTICAL }
        center.addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        center.addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                strokeSummary.apply {
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.START
                    setPadding(dp(8), 0, dp(4), 0)
                }
                addView(strokeSummary, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                strokeUndo.textSize = 18f
                strokeClear.textSize = 14f
                addView(strokeUndo, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
                addView(strokeClear, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(44)),
        )
        center.addView(
            LinearLayout(context).apply {
                setPadding(dp(2), dp(2), dp(2), dp(4))
                listOf("一" to "横", "丨" to "竖", "丿" to "撇", "丶" to "点或捺", "乙" to "折").forEachIndexed { index, (label, description) ->
                    val key = button(label, description) {
                        onStrokeChange?.invoke(strokePrefix + ('1' + index))
                    }
                    key.textSize = 24f
                    strokeKeys.add(key)
                    addView(
                        key,
                        LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                            marginStart = dp(2)
                            marginEnd = dp(2)
                        },
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(48)),
        )
        addView(center, LayoutParams(0, LayoutParams.MATCH_PARENT, .70f))
        listOf("↑" to "上一页", "↓" to "下一页", "⌫" to "退格", "单字" to "单字").forEachIndexed { index, (label, description) ->
            val view = if (index == 3) {
                single
            } else {
                button(label, description) {
                    when (index) {
                        0 -> recyclerView.smoothScrollBy(0, -recyclerView.height)
                        1 -> recyclerView.smoothScrollBy(0, recyclerView.height)
                        2 -> onCompositionKey?.invoke("BackSpace")
                    }
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
        addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, .16f))
        updateStrokePrefix("")
    }

    fun updateStrokePrefix(prefix: String) {
        strokePrefix = prefix
        strokeSummary.text = if (prefix.isEmpty()) "笔画筛选" else prefix.map { "一丨丿丶乙"[it - '1'] }.joinToString(" ")
        strokeSummary.contentDescription = "已选笔画：${strokeSummary.text}"
        strokeUndo.isEnabled = prefix.isNotEmpty()
        strokeClear.isEnabled = prefix.isNotEmpty()
        strokeUndo.alpha = if (prefix.isEmpty()) .35f else 1f
        strokeClear.alpha = strokeUndo.alpha
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
            val view = (syllables.getChildAt(index) as? TextView) ?: button(syllable, "筛选 $syllable") {}.also {
                syllables.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
            }
            if (view.text.toString() != syllable) view.text = syllable
            view.contentDescription = "筛选 $syllable"
            view.setOnClickListener { onSyllableToggle?.invoke(index, displayedSyllables) }
        }
        refreshColors()
    }

    fun resetPosition() = recyclerView.scrollToPosition(0)

    fun refreshColors() {
        background = scope.decorDrawable("candidate_background", "candidate_border_color", dp(scope.theme.style.candidateBorder), dp(scope.theme.style.candidateBorderRound))
        val colors = scope.colors
        for (i in 0 until syllables.childCount) {
            val covered = activeSyllable?.let { i <= it } == true
            (syllables.getChildAt(i) as TextView).apply {
                setTextColor(if (covered) colors.hilitedCandidateTextColor else colors.candidateTextColor)
                background = roundedRippleDrawable(colors.hilitedCandidateBackColor, dp(8f), if (covered) colors.hilitedCandidateBackColor else colors.candidateBackground)
            }
        }
        single.setTextColor(if (singleCharacter) colors.hilitedCandidateTextColor else colors.candidateTextColor)
        single.background = roundedRippleDrawable(colors.hilitedCandidateBackColor, dp(8f), if (singleCharacter) colors.hilitedCandidateBackColor else colors.candidateBackground)
        strokeSummary.setTextColor(colors.candidateTextColor)
        (strokeKeys + strokeUndo + strokeClear).forEach { view ->
            view.setTextColor(colors.keyTextColor)
            view.background = roundedRippleDrawable(colors.hilitedKeyBackColor, dp(8f), colors.keyBackColor)
        }
        actions.filter { it !== single }.forEach { view ->
            view.setTextColor(colors.keyTextColor)
            view.background = roundedRippleDrawable(colors.hilitedKeyBackColor, dp(100f), colors.keyBackColor)
        }
        recyclerView.invalidateItemDecorations()
    }
}
