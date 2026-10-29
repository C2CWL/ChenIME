/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<ThemeItem> {
        val sharedThemes = ThemeFilesManager.listThemes(DataManager.sharedDataDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userDataDir)
        return sharedThemes + userThemes
    }

    private lateinit var _activeTheme: Theme

    private var _activeFindings: List<ThemeDiagnostics.Finding>? = null

    /**
     * What static checks found in the active theme, or null when it was read
     * from its deployed artifact and never checked. Serves the diagnostics
     * screen: the findings belong to the load that produced the active theme.
     */
    val activeFindings: List<ThemeDiagnostics.Finding>?
        get() {
            ensureActiveTheme()
            return _activeFindings
        }

    private fun ensureActiveTheme() {
        if (!::_activeTheme.isInitialized) {
            _activeTheme = evaluateActiveTheme()
        }
    }

    var activeTheme: Theme
        get() {
            ensureActiveTheme()
            return _activeTheme
        }
        private set(value) {
            if (::_activeTheme.isInitialized && _activeTheme == value) return
            value.fonts.prepareForDrawing()
            _activeTheme = value
            fireChange()
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(_activeTheme) }
    }

    private val fontPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if ((key == null || key == AppPrefs.defaultInstance().customFont.key) && ::_activeTheme.isInitialized) {
            refreshFonts(force = true)
        }
    }

    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    init {
        AppPrefs.defaultInstance().customFont.sharedPreferences.registerOnSharedPreferenceChangeListener(fontPreferenceListener)
    }

    private data class ResolvedTheme(
        val configId: String,
        val theme: Theme,
        val findings: List<ThemeDiagnostics.Finding>?,
    )

    private fun getThemeById(id: String): ResolvedTheme {
        // Retire the legacy selectable theme while keeping its resource available to librime.
        val selectedId = id.takeUnless { it == "trime" || it.isBlank() } ?: "iflytek.trime"
        var lastFailure: ThemeLoader.ThemeLoadError? = null
        for (candidateId in listOf(selectedId, "iflytek.trime", "tongwenfeng.trime").distinct()) {
            when (val result = ThemeLoader.loadTheme(candidateId)) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    if (candidateId != id) {
                        Timber.w("Theme '$id' is unavailable, fallback to theme '$candidateId'")
                    }
                    return ResolvedTheme(candidateId, result.theme, result.findings)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> {
                    Timber.w(result.error)
                    lastFailure = result.error
                }
            }
        }

        Timber.w(lastFailure, "No valid theme available")
        error("No valid theme available")
    }

    private fun evaluateActiveTheme(): Theme {
        val selectedThemeId = prefs.selectedTheme.getValue()
        val resolvedTheme = getThemeById(selectedThemeId)
        val newTheme = resolvedTheme.theme
        if (resolvedTheme.configId != selectedThemeId) {
            prefs.selectedTheme.setValue(resolvedTheme.configId)
        }
        applyTheme(resolvedTheme)
        return newTheme
    }

    private fun applyTheme(resolvedTheme: ResolvedTheme) {
        val theme = resolvedTheme.theme
        // The findings describe the file this load read; they are not tied to the
        // views, so they are refreshed even when the theme itself is unchanged.
        _activeFindings = resolvedTheme.findings
        // Keep the existing scope for equal themes so views and ColorManager stay
        // attached to the same instance. Font files may still have changed on disk.
        if (::_activeTheme.isInitialized && _activeTheme == theme) {
            refreshFonts()
            return
        }
        ColorManager.attachTheme(theme)
        activeTheme = theme
    }

    fun refreshFonts(force: Boolean = false) {
        if (!::_activeTheme.isInitialized) return
        val changed = _activeTheme.fonts.refresh()
        if (changed || force) fireChange()
    }

    fun init(configuration: Configuration) {
        ensureActiveTheme()
        ColorManager.init(configuration)
    }

    /**
     * Switches to theme [configId], falling back when it is unavailable.
     * Loading runs on [Dispatchers.IO]; state changes and listener callbacks run on the main thread.
     * @return the config id actually in effect; differs from [configId] when a fallback was used.
     */
    suspend fun selectTheme(configId: String): String {
        val resolvedTheme = withContext(Dispatchers.IO) { getThemeById(configId) }
        return withContext(Dispatchers.Main.immediate) {
            applyTheme(resolvedTheme)
            prefs.selectedTheme.setValue(resolvedTheme.configId)
            resolvedTheme.configId
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun syncToDeviceEncryptedStorage() {
        val ctx = appContext.createDeviceProtectedStorageContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit {
            prefs.preferenceDelegates.forEach {
                it.value.putValueTo(this@edit)
            }
        }
    }
}
