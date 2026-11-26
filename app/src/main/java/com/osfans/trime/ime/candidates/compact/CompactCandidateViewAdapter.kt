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
    // Rows are rebound by position. Content-derived stable ids change on every
    // composition update, so RecyclerView cannot match the previous row.

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

    /**
     * Inclusive range that gets a real candidate view. Positions outside it keep a
     * fixed-width spacer, so Flexbox does not measure a ConstraintLayout for every
     * off-screen candidate. Null means every item is real: before the first window,
     * or while the row still fits the bar.
     */
    var fullWindow: IntRange? = null
        private set

    /** True while the bar is being dragged or flung, including the layout that scroll causes. */
    var scrolling: Boolean = false

    fun showsFullCandidate(position: Int): Boolean {
        val window = fullWindow
        return window == null || position in window
    }

    /**
     * @return true when some position changed between showing its candidate and
     * keeping a fixed-width placeholder. Equal ranges compare by value.
     */
    fun updateFullWindow(window: IntRange?): Boolean {
        if (fullWindow == window) return false
        val previous = fullWindow
        fullWindow = window
        if (itemCount == 0) return false
        notifyWindowMembership(previous, window)
        return true
    }

    /**
     * Every position uses the same view type. Rebind only the positions that
     * entered or left the visible window. A structural notification during a
     * swipe makes Flexbox drop the attached row and leave the bar blank.
     */
    private fun notifyWindowMembership(previous: IntRange?, next: IntRange?) {
        var runStart = -1
        for (position in 0..itemCount) {
            val changed = position < itemCount &&
                ((previous == null || position in previous) != (next == null || position in next))
            if (changed && runStart < 0) {
                runStart = position
            } else if (!changed && runStart >= 0) {
                notifyItemRangeChanged(runStart, position - runStart)
                runStart = -1
            }
        }
    }

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
     * Same-size content changes must use [submitList]. Per-index [set] only
     * issues [notifyItemChanged] with a null payload, which can leave the old
     * Flexbox row on screen after the composition has already changed.
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
        // Size plan: rebind only the changed prefix and only insert/remove the delta.
        // The differ-less BaseQuickAdapter.submitList() ends in notifyDataSetChanged(),
        // which rebound all 16 rows on every key even when nothing but the highlight moved.
        val plan = planCompactCandidateListNotify(items, newItems, highlightedIdx, highlightedIndex)
        trace("submit_begin") {
            "old=$itemCount new=${newItems.size} change=${plan.changeCount} insert=${plan.insertCount} remove=${plan.removeCount} " +
                "fp=${CompactCandidateTrace.fingerprint(newItems)} total=$total highlight=$highlightedIndex"
        }
        replaceItems(newItems)
        this.total = total
        this.highlightedIdx = highlightedIndex
        trace("submit_end") { "items=$itemCount fp=${CompactCandidateTrace.fingerprint(items)}" }
        dispatchListNotify(plan)
        return true
    }

    /**
     * Replaces the backing list without notifying. adapter4 has no silent setter, and
     * [submitList] always ends in [notifyDataSetChanged] when no differ is configured,
     * so the caller dispatches the planned ranges itself.
     */
    @Suppress("DEPRECATION_ERROR")
    private fun replaceItems(newItems: List<CandidateProto>) {
        items = newItems
    }

    private fun dispatchListNotify(plan: CompactCandidateListNotify) {
        if (plan.isNoOp) return
        if (plan.changeCount > 0) notifyItemRangeChanged(0, plan.changeCount)
        if (plan.insertCount > 0) notifyItemRangeInserted(plan.insertStart, plan.insertCount)
        if (plan.removeCount > 0) notifyItemRangeRemoved(plan.removeStart, plan.removeCount)
    }

    override fun getItemViewType(position: Int, list: List<CandidateProto>): Int = VIEW_TYPE_CANDIDATE

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
        val ui = holder.ui
        if (!showsFullCandidate(position)) {
            bindPlaceholder(holder, ui, position, item)
            return
        }
        if (LagDiagnostics.enabled) diagnosticBinds++
        val isHighlighted = position == highlightedIdx
        if (position < 12) {
            trace("bind") {
                "holder=${System.identityHashCode(holder)} position=$position cached_position=${holder.idx} " +
                    "generation=$bindGeneration cached_generation=${holder.bindGeneration} " +
                    "cache_text_match=${holder.text == item.text} cache_comment_match=${holder.comment == item.comment} " +
                    "ui_text_match=${ui.displayedText == item.text} ui_comment_match=${ui.displayedComment == item.comment}"
            }
        }
        // Flexbox lays this row out on every key. Skip the text, color and
        // background work when the recycled row already shows this candidate.
        if (holder.bindGeneration != bindGeneration || holder.idx != position || holder.text != item.text || holder.comment != item.comment || holder.highlighted != isHighlighted) {
            if (LagDiagnostics.enabled) diagnosticUpdates++
            ui.update(item, isHighlighted)
            holder.text = item.text
            holder.comment = item.comment
            holder.idx = position
            holder.highlighted = isHighlighted
            holder.bindGeneration = bindGeneration
        }
        if (position < 12) {
            trace("bind_end") {
                "holder=${System.identityHashCode(holder)} position=$position text_match=${ui.displayedText == item.text} " +
                    "comment_match=${ui.displayedComment == item.comment} highlighted=$isHighlighted"
            }
        }
        val root = ui.root
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

    /**
     * Off-screen candidates stay in the adapter so the row can scroll, but they
     * do not measure a full candidate layout. Hide the existing content and pin
     * the same holder to the cached width.
     */
    private fun bindPlaceholder(
        holder: CandidateViewHolder,
        ui: CandidateItemUi,
        position: Int,
        item: CandidateProto,
    ) {
        val root = ui.root
        val width = itemWidths.getOrNull(position)?.takeIf { it > 0 } ?: 1
        val params = root.layoutParams as? FlexboxLayoutManager.LayoutParams
            ?: FlexboxLayoutManager.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT)
        if (params.width != width || params.minWidth != 0 || params.maxWidth != width || params.flexShrink != 0f || params.flexGrow != 0f) {
            params.width = width
            params.minWidth = 0
            params.maxWidth = width
            params.flexShrink = 0f
            params.flexGrow = 0f
            root.layoutParams = params
        }
        if (root.minimumWidth != 0) root.minimumWidth = 0
        ui.setContentVisible(false)
        holder.idx = position
        holder.text = item.text
        holder.comment = item.comment
        holder.highlighted = false
        holder.bindGeneration = -1
    }

    private companion object {
        const val VIEW_TYPE_CANDIDATE = 0
    }
}
