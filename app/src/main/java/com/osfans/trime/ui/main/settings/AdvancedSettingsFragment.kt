/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.Formatter
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.c2cwl.chenime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.util.diagnostics.LagDiagnostics
import com.osfans.trime.util.toast
import io.github.whiredplanck.storageaccess.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdvancedSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().advanced) {

    // Must be created before the fragment is started: it registers activity-result launchers.
    private val storageAccess = StorageAccess(this)

    private var exportPreference: Preference? = null

    private val uiMode = AppPrefs.defaultInstance().advanced.uiMode

    private val showAppIcon = AppPrefs.defaultInstance().advanced.showAppIcon

    @Keep
    private val onUiModeChange = PreferenceDelegate.OnChangeListener<AppPrefs.Advanced.UiMode> { _, v ->
        val mode = when (v) {
            AppPrefs.Advanced.UiMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppPrefs.Advanced.UiMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPrefs.Advanced.UiMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    @Keep
    private val onShowAppIconChange = PreferenceDelegate.OnChangeListener<Boolean> { _, v ->
        showAppIcon(requireContext(), v)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiMode.registerOnChangeListener(onUiModeChange)
        showAppIcon.registerOnChangeListener(onShowAppIconChange)
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        addLagDiagnosticsButtons(screen)
    }

    override fun onResume() {
        super.onResume()
        refreshExportSummary()
    }

    /** Places 导出 / 分享 / 清空 directly under the 卡顿诊断 switch. */
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
        val share = Preference(ctx).apply {
            key = "lag_diagnostics_share"
            setTitle(R.string.lag_diagnostics_share)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = switch.order + 2
            setOnPreferenceClickListener {
                shareLagDiagnostics()
                true
            }
        }
        val clear = Preference(ctx).apply {
            key = "lag_diagnostics_clear"
            setTitle(R.string.lag_diagnostics_clear)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = switch.order + 3
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
        screen.addPreference(share)
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

    private fun shareLagDiagnostics() {
        val ctx = requireContext()
        lifecycleScope.launch {
            if (withContext(Dispatchers.IO) { LagDiagnostics.storedBytes() } == 0L) {
                ctx.toast(R.string.lag_diagnostics_empty)
                return@launch
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            runCatching {
                val file = File(ctx.cacheDir, "ChenIME-卡顿诊断-$stamp.txt")
                withContext(Dispatchers.IO) {
                    file.bufferedWriter(Charsets.UTF_8).use { LagDiagnostics.export(ctx, it) }
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.lag_diagnostics_share)))
            }.onFailure {
                ctx.toast(it)
            }
        }
    }

    override fun onDestroy() {
        uiMode.unregisterOnChangeListener(onUiModeChange)
        showAppIcon.unregisterOnChangeListener(onShowAppIconChange)
        super.onDestroy()
    }

    companion object {
        private const val LAG_DIAGNOSTICS_KEY = "input_latency_logging"

        fun showAppIcon(context: Context, enable: Boolean) {
            val state = if (enable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.MainLauncherAlias"),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
