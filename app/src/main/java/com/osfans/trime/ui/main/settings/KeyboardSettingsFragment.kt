/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.c2cwl.chenime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.ime.bar.ToolbarButtonLayout
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.navigateWithAnim
import kotlinx.coroutines.launch

class KeyboardSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().keyboard) {

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
        screen.findPreference<Preference>(AppPrefs.Keyboard.TOOLBAR_BUTTONS)?.apply {
            setOnPreferenceClickListener {
                findNavController().navigateWithAnim(NavigationRoute.ToolbarButtons)
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshToolbarSummary()
    }

    private fun refreshToolbarSummary() {
        val pref = preferenceScreen.findPreference<Preference>(AppPrefs.Keyboard.TOOLBAR_BUTTONS) ?: return
        val raw = AppPrefs.defaultInstance().keyboard.toolbarButtons.getValue()
        // Theme buttons are resolved when the editor opens; here we only count
        // visible entries already saved, or show the default summary.
        val saved = ToolbarButtonLayout.deserialize(raw)
        if (saved.isEmpty()) {
            pref.setSummary(R.string.toolbar_buttons_summary)
        } else {
            val shown = ToolbarButtonLayout.visibleActions(saved).size
            pref.summary = getString(R.string.toolbar_buttons_shown, shown)
        }
    }
}
