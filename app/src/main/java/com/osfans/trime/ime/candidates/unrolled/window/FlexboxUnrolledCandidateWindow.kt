// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled.window

import android.view.Gravity
import android.view.ViewGroup
import androidx.transition.Slide
import androidx.transition.Transition
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.expandedGridGapPx
import com.osfans.trime.ime.candidates.unrolled.expandedPillMargin
import com.osfans.trime.ime.candidates.unrolled.expandedRowHeightDp
import com.osfans.trime.ime.window.BoardWindow
import org.kodein.di.DI
import splitties.dimensions.dp

/**
 * Expanded candidate panel: pills keep their measured content width and wrap
 * onto the next row, so no candidate is ever squeezed into an ellipsis and no
 * span/column arithmetic has to be recomputed on every page change.
 */
class FlexboxUnrolledCandidateWindow(di: DI) : BaseUnrolledCandidateWindow(di) {
    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply { slideEdge = Gravity.TOP }

    private fun gridGapPx(): Int = expandedGridGapPx(
        theme.style.candidateSpacing,
        context.dp(theme.style.candidateSpacing).toInt(),
    )

    private fun rowHeightPx(): Int = context.dp(
        theme.style.run { expandedRowHeightDp(candidateViewHeight, commentHeight) },
    )

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(scope) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder = super.onCreateViewHolder(parent, viewType).apply {
                val margin = expandedPillMargin(gridGapPx())
                itemView.layoutParams = FlexboxLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    rowHeightPx(),
                ).apply {
                    setMargins(margin, margin, margin, margin)
                    // Pills never shrink: an overflowing row wraps instead of ellipsizing.
                    flexShrink = 0f
                }
            }

            override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout = UnrolledCandidateLayout(context, scope).apply {
        recyclerView.apply {
            adapter = this@FlexboxUnrolledCandidateWindow.adapter
            layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
        }
    }
}
