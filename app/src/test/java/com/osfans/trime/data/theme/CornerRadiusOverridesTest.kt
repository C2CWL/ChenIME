/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CornerRadiusOverridesTest {
    @Test
    fun defaultsFollowTheTheme() {
        val overrides = CornerRadiusOverrides()
        assertEquals(6f, overrides.key(6f))
        assertEquals(5f, overrides.bar(5f))
        assertEquals(10f, overrides.popup(10f))
        assertEquals(0f, overrides.panel())
        assertFalse(CornerRadiusOverrides.isOverridden(overrides.key))
    }

    @Test
    fun overridesReplaceThemeRadiiPerRegion() {
        val overrides = CornerRadiusOverrides(key = 0, panel = 16, bar = 12, popup = 8)
        assertEquals(0f, overrides.key(6f))
        assertEquals(16f, overrides.panel())
        assertEquals(12f, overrides.bar(5f))
        assertEquals(8f, overrides.popup(10f))
        assertTrue(CornerRadiusOverrides.isOverridden(overrides.key))
    }

    @Test
    fun overridesAreClamped() {
        assertEquals(
            CornerRadiusOverrides.MAX_RADIUS.toFloat(),
            CornerRadiusOverrides.resolve(CornerRadiusOverrides.MAX_RADIUS + 50, 4f),
        )
        assertEquals(4f, CornerRadiusOverrides.resolve(-7, 4f))
    }
}
