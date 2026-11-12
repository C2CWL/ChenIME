/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.text.TextPaint
import android.view.ContextThemeWrapper
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.unrolled.candidateLayoutWidth
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxVerticalDecoration
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.util.InputLatency
import com.osfans.trime.util.diagnostics.LagDiagnostics
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.max

class CompactCandidateDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val context: ContextThemeWrapper by instance()
    private val rime: RimeSession by instance()
    private val scope: ThemeScope by instance()
    private val inputView: InputView by instance()
    private val bar: InputBarDelegate by instance()

    private val theme: Theme
        get() = scope.theme

    private val fillStyle by AppPrefs.defaultInstance().keyboard.horizontalCandidateMode

    private val maxSpanCountPref by lazy {
        AppPrefs.defaultInstance().keyboard.run {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                maxSpanCount
            } else {
                maxSpanCountLandscape
            }
        }
    }

    private var layoutMinWidth = 0
    private var layoutFlexGrow = 0f
    private var pendingCandidates: Array<CandidateProto> = emptyArray()
    private var pendingTotal = -1
    private var pendingHighlighted = -1

    private val textPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = theme.style.candidateTextSize * context.resources.displayMetrics.scaledDensity
            typeface = theme.fonts.candidate
        }
    }
    private val commentPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = theme.style.commentTextSize * context.resources.displayMetrics.scaledDensity
            typeface = theme.fonts.comment
        }
    }
    private val widthCache = CandidateWidthCache()

    /**
     * (for [CompactCandidateMode.AUTO_FILL] only)
     * Second layout pass is needed when:
     * [^1] total candidates count < maxSpanCount && [^2] RecyclerView cannot display all of them
     * In that case, displayed candidates should be stretched evenly (by setting flexGrow to 1.0f).
     */
    private var secondLayoutPassNeeded = false
    private var secondLayoutPassDone = false

    // This is a batched layout delay, not a per-key or screen-presentation timestamp.
    private var candidateUpdateAt = 0L

    private val _unrolledCandidateOffset =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()

    fun refreshUnrolled(childCount: Int) {
        _unrolledCandidateOffset.tryEmit(childCount)
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (adapter.total == childCount),
        )
    }

    /** Restyles the compact list after a scheme switch; visible rows re-bind. */
    fun refreshColors() {
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        adapter.notifyDataSetChanged()
    }

    val adapter by lazy {
        CompactCandidateViewAdapter(scope).apply {
            setOnItemClickListener { _, _, position ->
                rime.launchOnReady { it.selectCandidate(position, global = true) }
            }
            setOnItemLongClickListener { _, view, position ->
                inputView.showCandidateActionMenu(position, items[position].text, view, global = true)
                true
            }
        }
    }

    fun updateLayoutParams(minWidth: Int, flexGrow: Float) {
        layoutMinWidth = minWidth
        layoutFlexGrow = flexGrow
    }

    val layoutManager by lazy {
        object : FlexboxLayoutManager(context) {
            override fun canScrollHorizontally(): Boolean = false

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                val cnt = this.childCount
                if (secondLayoutPassNeeded) {
                    if (cnt < adapter.itemCount) {
                        // [^2] RecyclerView can't display all candidates
                        // update LayoutParams in onLayoutCompleted would trigger another
                        // onLayoutCompleted, skip the second one to avoid infinite loop
                        if (secondLayoutPassDone) return
                        secondLayoutPassDone = true
                        for (i in 0 until cnt) {
                            getChildAt(i)!!.updateLayoutParams<LayoutParams> {
                                flexGrow = 1f
                            }
                        }
                    } else {
                        secondLayoutPassNeeded = false
                    }
                }
                refreshUnrolled(cnt)
                if (candidateUpdateAt != 0L) {
                    InputLatency.log("candidate_batch_layout_us=%d", (System.nanoTime() - candidateUpdateAt) / 1_000)
                    LagDiagnostics.duration("candidate_layout", candidateUpdateAt, minMs = 16)
                    LagDiagnostics.candidatesLaidOut(getChildAt(0)?.parent as? View)
                    candidateUpdateAt = 0L
                }
            }
        }
    }

    private val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.style.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = scope.colors.candidateSeparatorColor
        }
    }

    val view by lazy {
        context.recyclerView(R.id.candidate_view) {
            itemAnimator = null
            isFocusable = false
            isFocusableInTouchMode = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                defaultFocusHighlightEnabled = false
            }
            adapter = this@CompactCandidateDelegate.adapter
            layoutManager = this@CompactCandidateDelegate.layoutManager
            addItemDecoration(FlexboxVerticalDecoration(separatorDrawable))
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                val w = right - left
                val oldw = oldRight - oldLeft
                if (fillStyle == CompactCandidateMode.AUTO_FILL && w > 0) {
                    val maxSpanCount = maxSpanCountPref.getValue()
                    layoutMinWidth = w / maxSpanCount - separatorDrawable.intrinsicWidth
                    this@CompactCandidateDelegate.adapter.updateLayoutParams(layoutMinWidth, layoutFlexGrow)
                }
                if (pendingCandidates.isNotEmpty() && w != oldw) {
                    publishCompactCandidates()
                }
            }
        }
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        val (total, highlighted, candidates) = data

        val maxSpanCount = maxSpanCountPref.getValue()

        when (fillStyle) {
            CompactCandidateMode.NEVER_FILL -> {
                layoutMinWidth = 0
                layoutFlexGrow = 0f
                secondLayoutPassNeeded = false
            }

            CompactCandidateMode.AUTO_FILL -> {
                layoutMinWidth = view.width / maxSpanCount - separatorDrawable.intrinsicWidth
                layoutFlexGrow = if (candidates.size < maxSpanCount) 0f else 1f
                // [^1] total candidates count < maxSpanCount
                secondLayoutPassNeeded = candidates.size < maxSpanCount
                secondLayoutPassDone = false
            }

            CompactCandidateMode.ALWAYS_FILL -> {
                layoutMinWidth = 0
                layoutFlexGrow = 1f
                secondLayoutPassNeeded = false
            }
        }

        if (InputLatency.enabled && candidateUpdateAt == 0L && candidates.isNotEmpty()) {
            candidateUpdateAt = System.nanoTime()
        }
        if (!InputLatency.enabled || candidates.isEmpty()) candidateUpdateAt = 0L
        adapter.updateLayoutParams(layoutMinWidth, layoutFlexGrow)
        pendingCandidates = candidates
        pendingTotal = total
        pendingHighlighted = highlighted
        publishCompactCandidates()
    }

    private fun measureCandidateWidth(candidate: CandidateProto): Float {
        val padding = context.dp(theme.style.candidatePadding * 2).toFloat()
        val commentOnRight = theme.style.commentPosition == GeneralStyle.CommentPosition.RIGHT
        return widthCache.getOrMeasure(
            candidate.text,
            candidate.comment,
            textPaint.textSize,
            commentPaint.textSize,
            padding,
            commentOnRight,
        ) {
            candidateLayoutWidth(
                textPaint.measureText(candidate.text),
                candidate.comment,
                commentPaint.measureText(candidate.comment),
                commentOnRight,
            ) + padding
        }
    }

    private fun publishCompactCandidates() {
        val candidates = pendingCandidates
        if (candidates.isEmpty()) {
            adapter.updateItemWidths(emptyList())
            adapter.updateCandidates(candidates, pendingTotal, pendingHighlighted)
            refreshUnrolled(0)
            return
        }
        val barWidth = view.width
        if (barWidth <= 0) {
            adapter.updateItemWidths(emptyList())
            adapter.updateCandidates(candidates, pendingTotal, pendingHighlighted)
            return
        }
        val packed = layoutCompactCandidateBar(
            candidates.map(::measureCandidateWidth),
            barWidth.toFloat(),
            separatorDrawable.intrinsicWidth.toFloat(),
        )
        val shown = candidates.take(packed.size)
        val lastEllipsized = packed.lastOrNull()?.ellipsized == true
        if (lastEllipsized) secondLayoutPassNeeded = false
        val widthsChanged = adapter.updateItemWidths(
            packed.map { it.width.toInt().coerceAtLeast(1) },
            lastEllipsized,
        )
        val dataChanged = adapter.updateCandidates(shown.toTypedArray(), pendingTotal, pendingHighlighted)
        if (widthsChanged && !dataChanged) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
        if (dataChanged || widthsChanged) {
            refreshUnrolled(shown.size)
        }
    }
}
