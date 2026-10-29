// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import kotlin.math.abs

internal object DeleteSwipeDecision {
    fun vertical(dx: Float, dy: Float, threshold: Float): Boolean = abs(dy) >= threshold && abs(dy) >= abs(dx)

    fun horizontal(dx: Float, dy: Float, threshold: Float): Boolean = abs(dx) >= threshold && abs(dx) > abs(dy)
}
