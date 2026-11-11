/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.util.Log
import com.osfans.trime.util.diagnostics.LagDiagnostics

/**
 * Content-free per-key timing to logcat. It shares the 卡顿诊断 switch with [LagDiagnostics],
 * which keeps the persisted, exportable record.
 */
object InputLatency {
    val enabled: Boolean
        get() = LagDiagnostics.enabled

    fun log(message: String, vararg args: Any) {
        if (enabled) Log.d("InputLatency", message.format(*args))
    }
}
