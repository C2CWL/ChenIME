/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.ThemeScope

abstract class SwitchOptionAdapter :
    ListAdapter<SwitchOptionEntry, SwitchOptionAdapter.ViewHolder>(
        object : DiffUtil.ItemCallback<SwitchOptionEntry>() {
            override fun areItemsTheSame(oldItem: SwitchOptionEntry, newItem: SwitchOptionEntry): Boolean = when {
                oldItem is SwitchOptionEntry.Static && newItem is SwitchOptionEntry.Static -> oldItem.type == newItem.type

                oldItem is SwitchOptionEntry.Custom && newItem is SwitchOptionEntry.Custom ->
                    oldItem.switch.name == newItem.switch.name && oldItem.switch.options == newItem.switch.options

                else -> false
            }

            override fun areContentsTheSame(oldItem: SwitchOptionEntry, newItem: SwitchOptionEntry) = oldItem.label == newItem.label && oldItem.icon == newItem.icon &&
                (oldItem !is SwitchOptionEntry.Custom || newItem !is SwitchOptionEntry.Custom || oldItem.switch == newItem.switch)
        },
    ) {
    inner class ViewHolder(
        val ui: SwitchOptionEntryUi,
    ) : RecyclerView.ViewHolder(ui.root) {
        var colorRevision = -1L
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(SwitchOptionEntryUi(parent.context, scope)).apply { colorRevision = scope.colorRevision }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        if (holder.colorRevision != scope.colorRevision) {
            holder.ui.refreshColors()
            holder.colorRevision = scope.colorRevision
        }
        holder.ui.setEntry(item)
        holder.ui.root.setOnClickListener {
            onItemClick(it, item)
        }
    }

    /** Re-colors the visible rows after a scheme switch; rows re-apply colors on bind. */
    fun refreshColors() {
        notifyDataSetChanged()
    }

    abstract val scope: ThemeScope

    abstract fun onItemClick(
        view: View,
        entry: SwitchOptionEntry,
    )
}
