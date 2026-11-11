/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.Context
import android.view.ViewGroup
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

open class CompactCandidateViewAdapter(
    val scope: ThemeScope,
) : BaseQuickAdapter<CandidateProto, CandidateViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items.getOrNull(position).hashCode().toLong()

    var total: Int = -1
        private set

    var highlightedIdx: Int = -1
        private set

    var layoutMinWidth: Int = 0
        private set

    var layoutFlexGrow: Float = 0f
        private set

    var itemWidths: List<Int> = emptyList()
        private set

    var lastItemEllipsized: Boolean = false
        private set

    fun updateLayoutParams(minWidth: Int, flexGrow: Float) {
        layoutMinWidth = minWidth
        layoutFlexGrow = flexGrow
    }

    fun updateItemWidths(widths: List<Int>, lastEllipsized: Boolean = false) {
        itemWidths = widths
        lastItemEllipsized = lastEllipsized
    }

    fun updateCandidates(
        data: Array<CandidateProto>,
        total: Int,
        highlightedIndex: Int,
    ) {
        super.submitList(data.toList(), null)
        this.total = total
        this.highlightedIdx = highlightedIndex
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        val ui = CandidateItemUi(context, scope)
        ui.root.apply {
            minimumWidth = dp(40)
            layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
        item: CandidateProto?,
    ) {
        item ?: return
        val isHighlighted = position == highlightedIdx
        holder.ui.update(item, isHighlighted)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = position // unused
        val root = holder.ui.root
        val params = root.layoutParams as FlexboxLayoutManager.LayoutParams
        val displayWidth = itemWidths.getOrNull(position)
        val ellipsized = lastItemEllipsized && position == itemWidths.lastIndex
        val minWidth = (if (displayWidth != null) minOf(layoutMinWidth, displayWidth) else layoutMinWidth).coerceAtLeast(0)
        val flexGrow = if (ellipsized) 0f else layoutFlexGrow
        val maxWidth = displayWidth ?: Int.MAX_VALUE
        val width = displayWidth ?: root.wrapContent
        val minimum = if (displayWidth != null) minOf(root.dp(40), displayWidth) else root.dp(40)
        if (root.minimumWidth != minimum) root.minimumWidth = minimum
        if (params.minWidth != minWidth || params.flexGrow != flexGrow || params.maxWidth != maxWidth || params.width != width) {
            params.minWidth = minWidth
            params.flexGrow = flexGrow
            params.maxWidth = maxWidth
            params.width = width
            root.layoutParams = params
        }
    }
}
