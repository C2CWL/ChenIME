/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.toolbar

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ToolbarButtonLayout
import com.osfans.trime.ime.bar.ToolbarButtonPref
import com.osfans.trime.util.toast

class ToolbarButtonsAdapter(
    private val theme: Theme?,
    private val onChanged: (List<ToolbarButtonPref>) -> Unit,
) : RecyclerView.Adapter<ToolbarButtonsAdapter.Holder>() {

    var items: List<ToolbarButtonPref> = emptyList()
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<ToolbarButtonPref>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        val moved = ToolbarButtonLayout.move(items, from, to)
        if (moved === items) return
        items = moved
        notifyDataSetChanged()
        onChanged(items)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ToolbarButtonsEntryUi(parent.context))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val pref = items[position]
        val ctx = holder.ui.ctx
        holder.ui.nameText.text = toolbarButtonLabel(ctx, pref.action, theme)
        holder.ui.toggle.setOnCheckedChangeListener(null)
        holder.ui.toggle.isChecked = pref.visible
        val canHide = ToolbarButtonLayout.canHide(pref.action)
        holder.ui.toggle.isEnabled = canHide
        holder.ui.toggle.setOnCheckedChangeListener { _, checked ->
            val idx = holder.bindingAdapterPosition
            if (idx == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            val action = items[idx].action
            if (!ToolbarButtonLayout.canHide(action) && !checked) {
                holder.ui.toggle.isChecked = true
                ctx.toast(R.string.toolbar_buttons_hide_required)
                return@setOnCheckedChangeListener
            }
            items = ToolbarButtonLayout.setVisible(items, action, checked)
            onChanged(items)
        }
        holder.ui.moveUp.isEnabled = position > 0
        holder.ui.moveDown.isEnabled = position < items.lastIndex
        holder.ui.moveUp.alpha = if (position > 0) 1f else 0.3f
        holder.ui.moveDown.alpha = if (position < items.lastIndex) 1f else 0.3f
        holder.ui.moveUp.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx > 0) moveItem(idx, idx - 1)
        }
        holder.ui.moveDown.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx >= 0 && idx < items.lastIndex) moveItem(idx, idx + 1)
        }
    }

    class Holder(val ui: ToolbarButtonsEntryUi) : RecyclerView.ViewHolder(ui.root)
}
