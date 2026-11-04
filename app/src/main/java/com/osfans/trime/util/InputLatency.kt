/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.util.Log
import com.osfans.trime.data.prefs.AppPrefs

/** Content-free timing diagnostics, explicitly enabled in both release and debug builds. */
object InputLatency {
    val enabled: Boolean
        get() = AppPrefs.defaultInstance().keyboard.inputLatencyLogging.getValue()

    fun log(message: String, vararg args: Any) {
        if (enabled) Log.d("InputLatency", message.format(*args))
    }
}
