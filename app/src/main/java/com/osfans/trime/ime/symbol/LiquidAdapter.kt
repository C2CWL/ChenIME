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
import com.osfans.trime.ime.core.AutoScaleTextView
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

    override fun onCurrentListChanged(previousList: MutableList<LiquidKeyboard.KeyItem>, currentList: MutableList<LiquidKeyboard.KeyItem>) {
        super.onCurrentListChanged(previousList, currentList)
        // The locked grid's row height depends on the total list size.
        if (previousList.size != currentList.size && "liquid_keyboard_lock" in scope.theme.liquidKeyboard.fixedKeyBar.keys) {
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
            scaleMode = AutoScaleTextView.Mode.Proportional
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
        if (parent != null && "liquid_keyboard_lock" in scope.theme.liquidKeyboard.fixedKeyBar.keys) {
            val spacing = parent.context.dp(scope.theme.liquidKeyboard.marginX.toInt())
            val available = (parent.width - parent.paddingLeft - parent.paddingRight).coerceAtLeast(1)
            val minimum = parent.context.dp(scope.theme.liquidKeyboard.singleWidth) + spacing * 2
            val columns = (available / minimum.coerceAtLeast(1)).coerceAtLeast(1)
            val rows = ((itemCount + columns - 1) / columns).coerceIn(1, 4)
            val width = (available / columns - spacing * 2).coerceAtLeast(1)
            val height = ((parent.height - parent.paddingTop - parent.paddingBottom) / rows - spacing * 2).coerceAtLeast(1)
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
        if (holder.ui.mainText.text.toString() != item.text) holder.ui.mainText.text = item.text
        holder.ui.root.setOnClickListener {
            onItemClick.invoke(item, position)
        }
    }
}
