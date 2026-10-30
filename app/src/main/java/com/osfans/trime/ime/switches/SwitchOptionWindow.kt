/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import android.app.Dialog
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.c2cwl.chenime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.gridLayoutManager

class SwitchOptionWindow(di: DI) :
    BoardWindow.BarBoardWindow(di),
    InputBroadcastReceiver,
    ResidentWindow {
    companion object : ResidentWindow.Key
    override val key: ResidentWindow.Key get() = SwitchOptionWindow
    private val service: TrimeInputMethodService by instance()
    private val rime: RimeSession by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme

    private val staticEntries by lazy {
        arrayOf(
            SwitchOptionEntry(
                context.getString(R.string.theme),
                R.drawable.ic_baseline_color_lens_24,
                SwitchOptionEntry.Type.ThemeList,
            ),
            SwitchOptionEntry(
                context.getString(R.string.schemata),
                R.drawable.ic_round_view_list_24,
                SwitchOptionEntry.Type.SchemaList,
            ),
            SwitchOptionEntry(
                context.getString(R.string.update_config),
                R.drawable.ic_baseline_sync_24,
                SwitchOptionEntry.Type.UpdateConfig,
            ),
            SwitchOptionEntry(
                context.getString(R.string.virtual_keyboard),
                R.drawable.ic_baseline_keyboard_24,
                SwitchOptionEntry.Type.Keyboard,
            ),
        )
    }

    private fun showDialog(builder: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                service.showDialog(builder(api))
            }
        }
    }

    private val adapter: SwitchOptionAdapter by lazy {
        object : SwitchOptionAdapter() {
            override val scope: ThemeScope = this@SwitchOptionWindow.scope

            override fun onItemClick(
                view: View,
                entry: SwitchOptionEntry,
            ) {
                when (entry.type) {
                    SwitchOptionEntry.Type.SchemaList -> showDialog { r ->
                        EnabledSchemaPickerDialog.build(r, service.lifecycleScope, context) {
                            setNegativeButton(R.string.enable_schemata) { _, _ ->
                                AppUtils.launchMainToSchemaList(context)
                            }
                        }
                    }

                    SwitchOptionEntry.Type.UpdateConfig -> rime.launchOnReady { r ->
                        r.updateConfig()
                        service.lifecycleScope.launch {
                            Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
                        }
                    }

                    SwitchOptionEntry.Type.Keyboard -> AppUtils.launchMainToKeyboard(context)

                    SwitchOptionEntry.Type.ThemeList -> showDialog { r ->
                        ThemePickerDialog.build(service.lifecycleScope, context) {
                            r.commitComposition()
                        }
                    }
                }
            }
        }
    }

    val view by lazy {
        context.recyclerView {
            layoutManager = gridLayoutManager(4).apply { initialPrefetchItemCount = staticEntries.size }
            adapter = this@SwitchOptionWindow.adapter
            itemAnimator = null
            setHasFixedSize(true)
        }
    }

    private var viewCreated = false

    override fun onCreateView(): View {
        viewCreated = true
        adapter.submitList(staticEntries.toList())
        return view
    }

    private var colorRevision = -1L

    override fun refreshColors() {
        if (!viewCreated || colorRevision == scope.colorRevision) return
        colorRevision = scope.colorRevision
        (view.adapter as? SwitchOptionAdapter)?.refreshColors()
        settingsButton.refreshColors()
    }

    private val settingsButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_settings_24, scope).apply {
            setOnClickListener { AppUtils.launchMainActivity(context) }
        }
    }

    private val barExternalView by lazy {
        context.constraintLayout {
            val size = dp(theme.style.run { candidateViewHeight + commentHeight })
            add(
                settingsButton,
                lParams(size, size) {
                    endOfParent()
                },
            )
        }
    }

    override fun onCreateBarView() = barExternalView

    override fun onAttached() {
        refreshColors()
    }

    override fun onDetached() = Unit
}
