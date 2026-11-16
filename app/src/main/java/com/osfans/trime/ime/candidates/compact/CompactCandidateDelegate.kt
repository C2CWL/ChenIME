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
import com.google.android.flexbox.FlexWrap
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
import com.osfans.trime.ime.candidates.CANDIDATE_COMMENT_GAP_DP
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
import kotlin.math.ceil
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
    private var diagnosticLayoutSequence = -1L
    private val diagnosticSnapshot: Runnable = Runnable {
        adapter.trace("settled") {
            val checked = minOf(view.childCount, 12)
            val mismatches = (0 until checked).count { index ->
                val child = view.getChildAt(index)
                val holder = view.getChildViewHolder(child) as? com.osfans.trime.ime.candidates.CandidateViewHolder
                val expected = holder?.let { adapter.items.getOrNull(it.bindingAdapterPosition) }
                holder == null || expected == null || holder.ui.displayedText != expected.text ||
                    holder.ui.displayedComment != expected.comment
            }
            "layout_seq=$diagnosticLayoutSequence items=${adapter.itemCount} children=${view.childCount} " +
                "checked=$checked mismatches=$mismatches attached=${view.isAttachedToWindow} " +
                "shown=${view.isShown} requested=${view.isLayoutRequested} " +
                "binds=${adapter.diagnosticBinds} updates=${adapter.diagnosticUpdates}"
        }
    }

    private val _unrolledCandidateOffset =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()

    fun refreshUnrolled(childCount: Int) {
        val emitted = _unrolledCandidateOffset.tryEmit(childCount)
        adapter.trace("unrolled_offset") { "offset=$childCount emitted=$emitted" }
    }

    private fun updateUnrollButton(overflows: Boolean) {
        adapter.trace("unroll_button") { "overflows=$overflows" }
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to !overflows,
        )
    }

    /** Restyles the compact list after a scheme switch; visible rows re-bind. */
    fun refreshColors() {
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        // Drop the bind cache so a scheme switch repaints text that did not change.
        adapter.invalidateBindCache()
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
            init {
                // One scrolling row: candidates keep their natural width instead of
                // being packed into the bar and ellipsized at the end.
                flexWrap = FlexWrap.NOWRAP
            }

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
                val startedAt = LagDiagnostics.start()
                adapter.trace("layout_begin") {
                    "state_items=${state.itemCount} adapter_items=${adapter.itemCount} children=$childCount pre_layout=${state.isPreLayout} structure_changed=${state.didStructureChange()}"
                }
                try {
                    super.onLayoutChildren(recycler, state)
                } finally {
                    if (startedAt != 0L) {
                        LagDiagnostics.duration(
                            "candidate_layout_work",
                            startedAt,
                            extra = "work_us=${(System.nanoTime() - startedAt) / 1_000} " +
                                "items=${adapter.itemCount} children=$childCount pre_layout=${state.isPreLayout} " +
                                "binds_total=${adapter.diagnosticBinds} updates_total=${adapter.diagnosticUpdates} " +
                                "creates_total=${adapter.diagnosticCreates} invalidations_total=${adapter.diagnosticInvalidations}",
                        )
                    }
                }
            }

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                val cnt = this.childCount
                if (LagDiagnostics.enabled) diagnosticLayoutSequence = adapter.diagnosticSequence
                adapter.trace("layout_complete") { "children=$cnt items=${adapter.itemCount} pre_layout=${state?.isPreLayout}" }
                if (LagDiagnostics.enabled) {
                    for (i in 0 until minOf(cnt, 12)) {
                        val child = getChildAt(i) ?: continue
                        val holder = (child.parent as? RecyclerView)?.getChildViewHolder(child)
                            as? com.osfans.trime.ime.candidates.CandidateViewHolder ?: continue
                        val position = holder.bindingAdapterPosition
                        val expected = adapter.items.getOrNull(position)
                        adapter.trace("child") {
                            "child=$i holder=${System.identityHashCode(holder)} position=$position bound_position=${holder.idx} " +
                                "expected=${expected != null} text_match=${expected != null && holder.ui.displayedText == expected.text} " +
                                "comment_match=${expected != null && holder.ui.displayedComment == expected.comment} " +
                                "cache_match=${expected != null && holder.text == expected.text && holder.comment == expected.comment} " +
                                "bounds=${child.left},${child.top},${child.right},${child.bottom} requested=${child.isLayoutRequested}"
                        }
                    }
                }
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
        if (LagDiagnostics.enabled) {
            adapter.diagnosticSequence++
            adapter.trace("receive") {
                "count=${candidates.size} total=$total highlight=$highlighted fp=${CompactCandidateTrace.fingerprint(candidates.toList())}"
            }
        }

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

        if (LagDiagnostics.enabled && candidateUpdateAt == 0L && candidates.isNotEmpty()) {
            candidateUpdateAt = System.nanoTime()
        }
        if (!LagDiagnostics.enabled || candidates.isEmpty()) candidateUpdateAt = 0L
        adapter.updateLayoutParams(layoutMinWidth, layoutFlexGrow)
        pendingCandidates = candidates
        pendingTotal = total
        pendingHighlighted = highlighted
        publishCompactCandidates()
    }

    private fun measureCandidateWidth(candidate: CandidateProto): Float {
        val padding = context.dp(theme.style.candidatePadding * 2).toFloat()
        val commentOnRight = theme.style.commentPosition == GeneralStyle.CommentPosition.RIGHT
        val commentGap = if (commentOnRight && candidate.comment.isNotEmpty()) {
            context.dp(CANDIDATE_COMMENT_GAP_DP).toFloat()
        } else {
            0f
        }
        return widthCache.getOrMeasure(
            candidate.text,
            candidate.comment,
            textPaint.textSize,
            commentPaint.textSize,
            padding,
            commentOnRight,
            (textPaint.typeface?.hashCode() ?: 0) xor (commentPaint.typeface?.hashCode() ?: 0),
        ) {
            compactCandidateContentWidth(
                textPaint.measureText(candidate.text),
                candidate.comment,
                commentPaint.measureText(candidate.comment),
                commentOnRight,
                commentGap,
            ) + padding
        }
    }

    private fun publishCompactCandidates() {
        val startedAt = LagDiagnostics.start()
        try {
            adapter.trace("publish_begin") {
                "incoming=${pendingCandidates.size} width=${view.width} attached=${view.isAttachedToWindow} visibility=${view.visibility} requested=${view.isLayoutRequested} computing=${view.isComputingLayout}"
            }
            val candidates = pendingCandidates
            if (candidates.isEmpty()) {
                adapter.updateItemWidths(emptyList())
                adapter.updateCandidates(candidates, pendingTotal, pendingHighlighted)
                refreshUnrolled(0)
                updateUnrollButton(overflows = false)
                return
            }
            val barWidth = view.width
            if (barWidth <= 0) {
                adapter.updateItemWidths(emptyList())
                adapter.updateCandidates(candidates, pendingTotal, pendingHighlighted)
                return
            }
            // Every candidate stays in the bar at its natural width: the row scrolls
            // horizontally, so nothing has to be packed, shrunk or ellipsized.
            val widths = candidates.map { ceil(measureCandidateWidth(it)).toInt().coerceAtLeast(1) }
            val widthsChanged = adapter.updateItemWidths(widths, lastEllipsized = false)
            val dataChanged = adapter.updateCandidates(candidates, pendingTotal, pendingHighlighted)
            if (widthsChanged && !dataChanged) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
            // The bar now scrolls instead of packing, so the unrolled panel can no
            // longer learn its size from the packed layout: refresh it from the data.
            if (dataChanged || widthsChanged) {
                refreshUnrolled(candidates.size)
            }
            // The row no longer clips, so "more candidates exist" is the width that
            // does not fit, not a truncated item count. The expand button follows that.
            val separator = separatorDrawable.intrinsicWidth
            val contentWidth = widths.sum() + separator * (widths.size - 1).coerceAtLeast(0)
            updateUnrollButton(contentWidth > barWidth)
        } finally {
            if (LagDiagnostics.enabled) {
                view.removeCallbacks(diagnosticSnapshot)
                view.postDelayed(diagnosticSnapshot, 250)
            }
            if (startedAt != 0L) {
                LagDiagnostics.duration(
                    "candidate_publish",
                    startedAt,
                    extra = "work_us=${(System.nanoTime() - startedAt) / 1_000} " +
                        "incoming=${pendingCandidates.size} shown=${adapter.itemCount} bar_width=${view.width}",
                )
            }
        }
    }
}
