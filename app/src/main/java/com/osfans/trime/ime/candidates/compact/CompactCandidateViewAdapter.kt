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
import com.osfans.trime.util.diagnostics.LagDiagnostics
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

    // Cumulative while diagnostics is enabled; compare deltas between layout records.
    internal var diagnosticBinds = 0L
        private set
    internal var diagnosticUpdates = 0L
        private set
    internal var diagnosticCreates = 0L
        private set
    internal var diagnosticInvalidations = 0L
        private set

    internal var diagnosticSequence = 0L

    internal inline fun trace(stage: String, details: () -> String) {
        CompactCandidateTrace.log(this, diagnosticSequence, stage, details)
    }

    init {
        registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onChanged() = trace("notify_all") { "items=$itemCount" }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = trace("notify_change") { "start=$positionStart count=$itemCount payload=${payload != null}" }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = trace("notify_insert") { "start=$positionStart count=$itemCount" }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = trace("notify_remove") { "start=$positionStart count=$itemCount" }
        })
    }

    private var bindGeneration = 0

    /** Forces the next bind to rewrite text and colors. */
    fun invalidateBindCache() {
        bindGeneration++
        if (LagDiagnostics.enabled) diagnosticInvalidations++
    }

    /** @return true if the stored widths changed. */
    fun updateItemWidths(widths: List<Int>, lastEllipsized: Boolean = false): Boolean {
        if (itemWidths == widths && lastItemEllipsized == lastEllipsized) return false
        itemWidths = widths
        lastItemEllipsized = lastEllipsized
        return true
    }

    /**
     * @return true if the list, total or highlight actually changed and a
     * [submitList] ran. Identical updates (a second Flexbox pass, a no-op
     * republish) skip the adapter rebind.
     *
     * Same-size content changes must use [submitList], which on this adapter
     * calls [notifyDataSetChanged]. Per-index [set] only issues
     * [notifyItemChanged] with a null payload. This adapter also uses
     * [setHasStableIds] and [getItemId] from the candidate hash, so
     * notifyItemChanged can leave the old Flexbox rows on screen after the
     * composition/preedit has already changed.
     */
    fun updateCandidates(
        data: Array<CandidateProto>,
        total: Int,
        highlightedIndex: Int,
    ): Boolean {
        val newItems = data.toList()
        if (shouldSkipCompactCandidateUpdate(
                items,
                newItems,
                this.total,
                total,
                this.highlightedIdx,
                highlightedIndex,
            )
        ) {
            trace("submit_skip") { "items=$itemCount total=$total highlight=$highlightedIndex" }
            return false
        }
        trace("submit_begin") {
            "old=$itemCount new=${newItems.size} fp=${CompactCandidateTrace.fingerprint(newItems)} total=$total highlight=$highlightedIndex"
        }
        super.submitList(newItems, null)
        trace("submit_end") { "items=$itemCount fp=${CompactCandidateTrace.fingerprint(items)}" }
        this.total = total
        this.highlightedIdx = highlightedIndex
        return true
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        if (LagDiagnostics.enabled) diagnosticCreates++
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
        if (LagDiagnostics.enabled) diagnosticBinds++
        val isHighlighted = position == highlightedIdx
        if (position < 12) {
            trace("bind") {
                "holder=${System.identityHashCode(holder)} position=$position cached_position=${holder.idx} " +
                    "generation=$bindGeneration cached_generation=${holder.bindGeneration} " +
                    "cache_text_match=${holder.text == item.text} cache_comment_match=${holder.comment == item.comment} " +
                    "ui_text_match=${holder.ui.displayedText == item.text} ui_comment_match=${holder.ui.displayedComment == item.comment}"
            }
        }
        // Flexbox lays this row out on every key. Skip the text, color and
        // background work when the recycled row already shows this candidate.
        if (holder.bindGeneration != bindGeneration || holder.idx != position || holder.text != item.text || holder.comment != item.comment || holder.highlighted != isHighlighted) {
            if (LagDiagnostics.enabled) diagnosticUpdates++
            holder.ui.update(item, isHighlighted)
            holder.text = item.text
            holder.comment = item.comment
            holder.idx = position
            holder.highlighted = isHighlighted
            holder.bindGeneration = bindGeneration
        }
        if (position < 12) {
            trace("bind_end") {
                "holder=${System.identityHashCode(holder)} position=$position text_match=${holder.ui.displayedText == item.text} " +
                    "comment_match=${holder.ui.displayedComment == item.comment} highlighted=$isHighlighted"
            }
        }
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
        if (params.minWidth != minWidth || params.flexGrow != flexGrow || params.flexShrink != 0f || params.maxWidth != maxWidth || params.width != width) {
            params.minWidth = minWidth
            params.flexGrow = flexGrow
            // The bar scrolls: an item keeps its natural width instead of shrinking
            // into an ellipsis when the row is full.
            params.flexShrink = 0f
            params.maxWidth = maxWidth
            params.width = width
            root.layoutParams = params
        }
    }
}
