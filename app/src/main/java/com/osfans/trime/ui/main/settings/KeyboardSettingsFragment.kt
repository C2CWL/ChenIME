/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.c2cwl.chenime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.util.diagnostics.LagDiagnostics
import com.osfans.trime.util.toast
import io.github.whiredplanck.storageaccess.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyboardSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().keyboard) {

    // Must be created before the fragment is started: it registers activity-result launchers.
    private val storageAccess = StorageAccess(this)

    private var exportPreference: Preference? = null

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.findPreference<DialogSeekBarPreference>(AppPrefs.Keyboard.SLIDE_STEP_SIZE)?.apply {
            setDialogMessage(R.string.key_slide_step_size_message)
        }
        screen.findPreference<Preference>("custom_sound_effect_name")?.apply {
            setOnPreferenceClickListener {
                lifecycleScope.launch {
                    SoundEffectPickerDialog.build(lifecycleScope, requireContext())
                        .show()
                }
                true
            }
        }
        addLagDiagnosticsButtons(screen)
    }

    override fun onResume() {
        super.onResume()
        refreshExportSummary()
    }

    /** Places 导出 / 清空 directly under the 卡顿诊断 switch. */
    private fun addLagDiagnosticsButtons(screen: PreferenceScreen) {
        val switch = screen.findPreference<Preference>(LAG_DIAGNOSTICS_KEY) ?: return
        for (i in 0 until screen.preferenceCount) {
            screen.getPreference(i).order = i * 10
        }
        val ctx = screen.context
        val export = Preference(ctx).apply {
            key = "lag_diagnostics_export"
            setTitle(R.string.lag_diagnostics_export)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = switch.order + 1
            setOnPreferenceClickListener {
                exportLagDiagnostics()
                true
            }
        }
        val clear = Preference(ctx).apply {
            key = "lag_diagnostics_clear"
            setTitle(R.string.lag_diagnostics_clear)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = switch.order + 2
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.lag_diagnostics_clear_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { LagDiagnostics.clear() }
                            requireContext().toast(R.string.lag_diagnostics_cleared)
                            refreshExportSummary()
                        }
                    }
                    .show()
                true
            }
        }
        screen.addPreference(export)
        screen.addPreference(clear)
        exportPreference = export
        refreshExportSummary()
    }

    private fun refreshExportSummary() {
        val pref = exportPreference ?: return
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { LagDiagnostics.storedBytes() }
            pref.summary = getString(R.string.lag_diagnostics_export_summary, Formatter.formatShortFileSize(pref.context, bytes))
        }
    }

    private fun exportLagDiagnostics() {
        val ctx = requireContext().applicationContext
        lifecycleScope.launch {
            if (withContext(Dispatchers.IO) { LagDiagnostics.storedBytes() } == 0L) {
                ctx.toast(R.string.lag_diagnostics_empty)
                return@launch
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            runCatching {
                val file = storageAccess.createFile(
                    filename = "ChenIME-卡顿诊断-$stamp.txt",
                    mimeType = "text/plain",
                ) ?: return@launch
                withContext(Dispatchers.IO) {
                    StorageAccess.writeFile(file.uri) { outs ->
                        outs.bufferedWriter(Charsets.UTF_8).use { LagDiagnostics.export(ctx, it) }
                    }
                }
            }.onSuccess {
                ctx.toast(R.string.lag_diagnostics_exported)
            }.onFailure {
                ctx.toast(it)
            }
        }
    }

    companion object {
        private const val LAG_DIAGNOSTICS_KEY = "input_latency_logging"
    }
}
