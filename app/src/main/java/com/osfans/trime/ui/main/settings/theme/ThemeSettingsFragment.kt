/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.c2cwl.chenime.R
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.theme.CustomFontPolicy
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.startActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ThemeSettingsFragment : PreferenceDelegateFragment(ThemeManager.prefs) {
    private val fontPref get() = AppPrefs.defaultInstance().customFont
    private var fontChoice: Preference? = null
    private val chooseFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val context = requireContext()
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(DataManager.userDataDir, "fonts").apply { check(isDirectory || mkdirs()) }
                        val temp = File.createTempFile("chen_font_", ".tmp", dir)
                        try {
                            var name = "font.ttf"
                            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) name = it.getString(0) ?: name
                            }
                            context.contentResolver.openInputStream(uri).use { input ->
                                requireNotNull(input)
                                temp.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= CustomFontPolicy.MAX_BYTES)
                                        output.write(buffer, 0, count)
                                    }
                                    require(CustomFontPolicy.validSize(total))
                                }
                            }
                            Typeface.createFromFile(temp)
                            val target = File(dir, "chen_custom.${CustomFontPolicy.extension(name)}")
                            check(temp.renameTo(target))
                            val previous = fontPref.getValue()
                            fontPref.setValue(target.name)
                            if (previous != target.name && previous in listOf("chen_custom.ttf", "chen_custom.otf")) File(dir, previous).delete()
                        } finally {
                            temp.delete()
                        }
                    }
                }
                if (result.isSuccess) fontChanged() else Toast.makeText(context, R.string.custom_font_invalid, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fontChanged() {
        fontChoice?.summary = fontPref.getValue().ifEmpty { getString(R.string.default_font) }
        ThemeManager.refreshFonts()
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        findPreference<Preference>("selected_theme")?.setOnPreferenceClickListener {
            lifecycleScope.launch { ThemePickerDialog.build(lifecycleScope, requireContext()).show() }
            true
        }
        findPreference<Preference>("normal_mode_color")?.setOnPreferenceClickListener {
            lifecycleScope.launch { ColorPickerDialog.build(lifecycleScope, requireContext()).show() }
            true
        }
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        fontChoice = Preference(screen.context).apply {
            setTitle(R.string.choose_font)
            summary = fontPref.getValue().ifEmpty { getString(R.string.default_font) }
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                chooseFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
                true
            }
        }
        screen.addPreference(requireNotNull(fontChoice))
        screen.addPreference(R.string.restore_default_font) {
            val previous = fontPref.getValue()
            fontPref.setValue("")
            if (previous in listOf("chen_custom.ttf", "chen_custom.otf")) File(File(DataManager.userDataDir, "fonts"), previous).delete()
            fontChanged()
        }
        screen.addPreference(
            R.string.theme_diagnostics,
            R.string.theme_diagnostics_summary,
        ) {
            startActivity<ThemeDiagnosticsActivity>()
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }
}
