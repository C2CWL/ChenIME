/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.theme.CornerRadiusOverrides
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.baselineToBaselineOf
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

/** Gap (dp) between candidate text and a right-side comment in [CandidateItemUi]. */
internal const val CANDIDATE_COMMENT_GAP_DP = 1

class CandidateItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme
        get() = scope.theme

    private val textSize = theme.style.candidateTextSize
    private val commentSize = theme.style.commentTextSize

    // Read at use time so a scheme switch re-binds rows with the new colors.
    private val textColor: Int get() = scope.colors.candidateTextColor
    private val commentColor: Int get() = scope.colors.commentTextColor
    private val hlCommentColor: Int get() = scope.colors.hilitedCommentTextColor
    private val hlTextColor: Int get() = scope.colors.hilitedCandidateTextColor
    private val hlBackColor: Int get() = scope.colors.hilitedCandidateBackColor

    private val commentPosition = theme.style.commentPosition
    private val commentVerticalBias = theme.style.commentVerticalBias
    private val candidateTextVerticalBias = theme.style.candidateTextVerticalBias

    // Candidates need TextView's measured baseline and ellipsis; the key-label
    // canvas transform can shift recycled candidate text outside its bounds.
    private val text =
        view(::TextView) {
            id = View.generateViewId()
            this.textSize = this@CandidateItemUi.textSize
            typeface = theme.fonts.candidate
            isSingleLine = true
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
        }

    private val comment =
        view(::TextView) {
            id = View.generateViewId()
            this.textSize = commentSize
            typeface = theme.fonts.comment
            isSingleLine = true
            gravity = gravityCenter
            ellipsize = TextUtils.TruncateAt.END
        }

    private val content = constraintLayout {
        horizontalPadding = dp(theme.style.candidatePadding)
        when (commentPosition) {
            GeneralStyle.CommentPosition.RIGHT -> {
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        centerVertically()
                        startOfParent()
                        endToStartOf(comment)
                        constrainedWidth = true
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        startToEndOf(text, ctx.dp(CANDIDATE_COMMENT_GAP_DP))
                        endOfParent()
                        baselineToBaselineOf(text)
                        constrainedWidth = true
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
            }

            GeneralStyle.CommentPosition.TOP -> {
                add(
                    text,
                    lParams(wrapContent, matchConstraints) {
                        centerHorizontally()
                        bottomOfParent()
                        topToBottomOf(comment)
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, matchConstraints) {
                        matchConstraintPercentHeight = 0.4f
                        topOfParent()
                        centerHorizontally()
                        bottomToTopOf(text)
                    },
                )
            }

            GeneralStyle.CommentPosition.OVERLAY -> {
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = candidateTextVerticalBias
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = commentVerticalBias
                    },
                )
            }
        }
    }

    /**
     * candidate long press feedback is handled by `showCandidateActionMenu`
     */
    override val root = view(::GestureFrame) {
        add(
            content,
            lParams(wrapContent, dp(theme.style.candidateViewHeight)) {
                gravity = gravityCenter
            },
        )
    }

    /**
     * Stretch the visible pill to the full span, inset by [marginPx] on every
     * side so neighboring pills share one uniform gap. Text stays centered and
     * ellipsizes instead of shrinking. The compact bar leaves this unset.
     */
    fun setFillSpan(marginPx: Int) {
        val layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
            val inset = marginPx.coerceAtLeast(0)
            setMargins(inset, inset, inset, inset)
        }
        content.layoutParams = layoutParams
        text.ellipsize = TextUtils.TruncateAt.END
        comment.ellipsize = TextUtils.TruncateAt.END
        (text.layoutParams as ConstraintLayout.LayoutParams).let { params ->
            params.constrainedWidth = true
            if (commentPosition != GeneralStyle.CommentPosition.RIGHT) {
                params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            text.layoutParams = params
        }
        (comment.layoutParams as ConstraintLayout.LayoutParams).let { params ->
            params.constrainedWidth = true
            comment.layoutParams = params
        }
    }

    private var backgroundColor: Int? = null
    private var backgroundContentColor: Int? = null
    private var backgroundCornerRadius: Float? = null

    @SuppressLint("UseKtx")
    fun update(
        item: CandidateProto,
        highlighted: Boolean,
    ) {
        val tColor = if (highlighted) hlTextColor else textColor
        val cColor = if (highlighted) hlCommentColor else commentColor
        val cornerRadius = ctx.dp(CornerRadiusOverrides.current().bar(theme.style.candidateCornerRadius))
        // Spacing > 0 opts into pill chips: unselected rows use key back color as chip fill.
        val contentColor = when {
            highlighted -> hlBackColor
            theme.style.candidateSpacing > 0f -> scope.colors.keyBackColor
            else -> Color.TRANSPARENT
        }

        val rippleColor = hlBackColor
        if (backgroundColor != rippleColor ||
            backgroundContentColor != contentColor ||
            backgroundCornerRadius != cornerRadius
        ) {
            content.background = roundedRippleDrawable(rippleColor, cornerRadius, contentColor)
            backgroundColor = rippleColor
            backgroundContentColor = contentColor
            backgroundCornerRadius = cornerRadius
        }
        if (!text.text.contentEquals(item.text)) text.text = item.text
        if (text.currentTextColor != tColor) text.setTextColor(tColor)

        val commentText = item.comment
        if (!comment.text.contentEquals(commentText)) comment.text = commentText
        if (comment.currentTextColor != cColor) comment.setTextColor(cColor)
        comment.isVisible = commentText.isNotEmpty()
    }
}
