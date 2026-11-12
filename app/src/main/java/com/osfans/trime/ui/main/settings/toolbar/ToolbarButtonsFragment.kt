/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.toolbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.bar.ToolbarButtonLayout
import com.osfans.trime.ime.bar.ToolbarButtonPref
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.button
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager
import splitties.views.setPaddingDp

class ToolbarButtonsFragment : Fragment() {

    private val prefs = AppPrefs.defaultInstance().keyboard.toolbarButtons
    private lateinit var adapter: ToolbarButtonsAdapter
    private lateinit var resetButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val theme = runCatching { ThemeManager.activeTheme }.getOrNull()
        val themeActions = ToolbarButtonLayout.defaultActions(
            theme?.toolBar?.primaryButton?.action,
            theme?.toolBar?.buttons?.map { it.action }.orEmpty(),
        )
        adapter = ToolbarButtonsAdapter(theme) { items -> persist(items) }
        adapter.submit(loadItems(themeActions))

        val list = ctx.recyclerView {
            layoutManager = verticalLayoutManager()
            adapter = this@ToolbarButtonsFragment.adapter
            setPaddingDp(16, 16, 16, 8)
            clipToPadding = false
        }
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            },
        ).attachToRecyclerView(list)

        resetButton = ctx.button {
            setText(R.string.toolbar_buttons_reset)
            setOnClickListener {
                prefs.setValue("")
                adapter.submit(loadItems(themeActions))
            }
        }

        return ctx.verticalLayout {
            add(list, lParams(matchParent, 0, weight = 1f))
            add(
                resetButton,
                lParams(matchParent, wrapContent) {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                    bottomMargin = dp(16)
                    topMargin = dp(4)
                },
            )
        }
    }

    private fun loadItems(themeActions: List<String>): List<ToolbarButtonPref> = ToolbarButtonLayout.resolve(themeActions, ToolbarButtonLayout.deserialize(prefs.getValue()))

    private fun persist(items: List<ToolbarButtonPref>) {
        prefs.setValue(ToolbarButtonLayout.serialize(items))
    }
}
