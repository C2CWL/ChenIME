// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled.window

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.view.Gravity
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Slide
import androidx.transition.Transition
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.candidateSpan
import com.osfans.trime.ime.window.BoardWindow
import org.kodein.di.DI
import splitties.dimensions.dp

class FlexboxUnrolledCandidateWindow(di: DI) : BaseUnrolledCandidateWindow(di) {
    private var gridWidth = 0
    private val textPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = theme.style.candidateTextSize * context.resources.displayMetrics.scaledDensity
            typeface = theme.fonts.candidate
        }
    }

    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply { slideEdge = Gravity.TOP }

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(scope) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder = super.onCreateViewHolder(parent, viewType).apply {
                itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(theme.style.run { candidateViewHeight + commentHeight }).coerceAtLeast(context.dp(44)),
                )
            }

            override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager by lazy {
        GridLayoutManager(context, 6).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val candidate = adapter.peek(position)?.candidate ?: return 1
                    val textWidth = textPaint.measureText(candidate.text)
                    val commentWidth = textPaint.measureText(candidate.comment)
                    val width = when (theme.style.commentPosition) {
                        com.osfans.trime.data.theme.model.GeneralStyle.CommentPosition.RIGHT -> textWidth + commentWidth
                        else -> maxOf(textWidth, commentWidth)
                    }
                    return candidateSpan(width + context.dp(theme.style.candidatePadding * 2), gridWidth / 6f)
                }
            }
        }
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout = UnrolledCandidateLayout(context, scope).apply {
        recyclerView.apply {
            adapter = this@FlexboxUnrolledCandidateWindow.adapter
            layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                gridWidth = right - left
                if (right - left != oldRight - oldLeft) requestLayout()
            }
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                private val paint = Paint()
                override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    paint.color = scope.colors.candidateSeparatorColor
                    paint.strokeWidth = context.dp(0.5f).coerceAtLeast(1f)
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChildAt(i)
                        canvas.drawLine(child.right.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat(), paint)
                        canvas.drawLine(child.left.toFloat(), child.bottom.toFloat(), child.right.toFloat(), child.bottom.toFloat(), paint)
                    }
                }
            })
        }
    }
}
