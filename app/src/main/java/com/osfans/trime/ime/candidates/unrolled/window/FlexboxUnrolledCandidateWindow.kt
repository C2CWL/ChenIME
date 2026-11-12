// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled.window

import android.graphics.Paint
import android.text.TextPaint
import android.view.Gravity
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Slide
import androidx.transition.Transition
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.unrolled.EXPANDED_GRID_MAX_COLUMNS
import com.osfans.trime.ime.candidates.unrolled.ExpandedGridCell
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.candidateLayoutWidth
import com.osfans.trime.ime.candidates.unrolled.expandedGridColumns
import com.osfans.trime.ime.candidates.unrolled.expandedGridGapPx
import com.osfans.trime.ime.candidates.unrolled.expandedMinCellWidth
import com.osfans.trime.ime.candidates.unrolled.expandedPillMargin
import com.osfans.trime.ime.candidates.unrolled.expandedRowHeightDp
import com.osfans.trime.ime.candidates.unrolled.layoutExpandedCandidateGrid
import com.osfans.trime.ime.window.BoardWindow
import org.kodein.di.DI
import splitties.dimensions.dp

class FlexboxUnrolledCandidateWindow(di: DI) : BaseUnrolledCandidateWindow(di) {
    private var gridWidth = 0
    private var placements: List<ExpandedGridCell> = emptyList()
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

    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply { slideEdge = Gravity.TOP }

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(scope) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder = super.onCreateViewHolder(parent, viewType).apply {
                ui.setFillSpan(expandedPillMargin(gridGapPx()))
                itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(theme.style.run { expandedRowHeightDp(candidateViewHeight, commentHeight) }),
                )
            }

            override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager by lazy {
        GridLayoutManager(context, EXPANDED_GRID_MAX_COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = placements.getOrNull(position)?.span ?: 1
            }
        }
    }

    private fun gridGapPx(): Int = expandedGridGapPx(
        theme.style.candidateSpacing,
        context.dp(theme.style.candidateSpacing).toInt(),
    )

    private fun recomputePlacements() {
        val width = gridWidth
        if (width <= 0) {
            placements = emptyList()
            return
        }
        val padding = context.dp(theme.style.candidatePadding * 2)
        val gap = gridGapPx()
        val commentOnRight = theme.style.commentPosition == GeneralStyle.CommentPosition.RIGHT
        val itemWidths = List(adapter.itemCount) { position ->
            val candidate = adapter.peek(position)?.candidate ?: return@List 0f
            candidateLayoutWidth(
                textPaint.measureText(candidate.text),
                candidate.comment,
                commentPaint.measureText(candidate.comment),
                commentOnRight,
            ) + padding
        }
        val minCellWidth = expandedMinCellWidth(textPaint.measureText("汉字"), padding.toFloat(), gap.toFloat())
        val columns = expandedGridColumns(width.toFloat(), minCellWidth)
        if (layoutManager.spanCount != columns) {
            layoutManager.spanCount = columns
        }
        placements = layoutExpandedCandidateGrid(itemWidths, width.toFloat(), columns)
        layoutManager.spanSizeLookup.invalidateSpanIndexCache()
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout = UnrolledCandidateLayout(context, scope).apply {
        recyclerView.apply {
            adapter = this@FlexboxUnrolledCandidateWindow.adapter
            layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
            fun relayoutGrid() {
                recomputePlacements()
                requestLayout()
            }
            this@FlexboxUnrolledCandidateWindow.adapter.registerAdapterDataObserver(
                object : RecyclerView.AdapterDataObserver() {
                    override fun onChanged() = relayoutGrid()
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = relayoutGrid()
                    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = relayoutGrid()
                    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = relayoutGrid()
                    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = relayoutGrid()
                },
            )
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                val newWidth = right - left
                if (newWidth != gridWidth || right - left != oldRight - oldLeft) {
                    gridWidth = newWidth
                    recomputePlacements()
                    if (right - left != oldRight - oldLeft) requestLayout()
                }
            }
        }
    }
}
