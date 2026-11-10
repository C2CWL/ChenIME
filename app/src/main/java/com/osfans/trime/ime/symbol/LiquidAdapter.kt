/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.LiquidKeyboard.KeyBar
import splitties.dimensions.dp
import splitties.views.gravityCenter

class LiquidAdapter(
    private val scope: ThemeScope,
    private val onItemClick: LiquidKeyboard.KeyItem.(Int) -> Unit,
) : ListAdapter<LiquidKeyboard.KeyItem, LiquidAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<LiquidKeyboard.KeyItem>() {
        override fun areItemsTheSame(oldItem: LiquidKeyboard.KeyItem, newItem: LiquidKeyboard.KeyItem) = oldItem == newItem
        override fun areContentsTheSame(oldItem: LiquidKeyboard.KeyItem, newItem: LiquidKeyboard.KeyItem) = oldItem == newItem
    },
) {
    private var recycler: RecyclerView? = null

    private val fillsEvenGrid: Boolean
        get() {
            val bar = scope.theme.liquidKeyboard.fixedKeyBar
            return "liquid_keyboard_lock" in bar.keys ||
                bar.position == KeyBar.Position.LEFT ||
                bar.position == KeyBar.Position.RIGHT
        }

    override fun onCurrentListChanged(previousList: MutableList<LiquidKeyboard.KeyItem>, currentList: MutableList<LiquidKeyboard.KeyItem>) {
        super.onCurrentListChanged(previousList, currentList)
        // The even grid's cell size depends on the total list size and the panel size.
        if (previousList.size != currentList.size && fillsEvenGrid) {
            notifyItemRangeChanged(0, itemCount, Unit)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recycler = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recycler = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ViewHolder(
        val ui: LiquidItemUi,
    ) : RecyclerView.ViewHolder(ui.root) {
        var colorRevision = -1L
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val context = parent.context
        val ui = LiquidItemUi(context, scope)
        ui.mainText.apply {
            gravity = gravityCenter
            updateLayoutParams {
                width = context.dp(scope.theme.liquidKeyboard.singleWidth)
            }
        }
        return ViewHolder(ui).apply { colorRevision = scope.colorRevision }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        val parent = recycler
        if (parent != null && fillsEvenGrid) {
            val bar = scope.theme.liquidKeyboard.fixedKeyBar
            val spacing = parent.context.dp(scope.theme.liquidKeyboard.marginX.toInt())
            val available = (parent.width - parent.paddingLeft - parent.paddingRight).coerceAtLeast(1)
            val availableHeight = (parent.height - parent.paddingTop - parent.paddingBottom).coerceAtLeast(1)
            val sideBar = bar.position == KeyBar.Position.LEFT || bar.position == KeyBar.Position.RIGHT
            val minCell = if (sideBar) {
                parent.context.dp(36) + spacing * 2
            } else {
                parent.context.dp(scope.theme.liquidKeyboard.singleWidth) + spacing * 2
            }
            val columns = if (sideBar) {
                LiquidGridGeometry.columnCount(available, minCell)
            } else {
                (available / minCell.coerceAtLeast(1)).coerceAtLeast(1)
            }
            val rows = if (sideBar && bar.keys.isNotEmpty()) {
                bar.keys.size.coerceAtLeast(1)
            } else {
                ((itemCount + columns - 1) / columns).coerceIn(1, 4)
            }
            val width = LiquidGridGeometry.cellSize(available, columns, spacing)
            val height = LiquidGridGeometry.cellSize(availableHeight, rows, spacing)
            val params = holder.ui.root.layoutParams
            if (params?.width != width || params.height != height) {
                holder.ui.root.layoutParams = FlexboxLayoutManager.LayoutParams(width, height).apply { flexGrow = 1f }
            }
            if (holder.ui.mainText.layoutParams.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT) {
                holder.ui.mainText.updateLayoutParams { this.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT }
            }
        }
        if (holder.colorRevision != scope.colorRevision) {
            holder.ui.refreshColors()
            holder.colorRevision = scope.colorRevision
        }
        holder.ui.setLabel(item.text)
        holder.ui.root.setOnClickListener {
            onItemClick.invoke(item, position)
        }
    }
}
