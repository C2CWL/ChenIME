/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

/**
 * Order and visibility of the always-visible top toolbar.
 *
 * Default visual order matches the current layout: the primary (menu) button
 * on the left, the first theme button (Hide) pinned on the right, and the
 * remaining theme buttons between them in reverse of their yaml order.
 */
data class ToolbarButtonPref(
    val action: String,
    val visible: Boolean,
)

object ToolbarButtonLayout {

    const val HIDE_ACTION = "Hide"
    const val VOICE_ACTION = "VOICE_ASSIST"

    /**
     * Visual left-to-right order of the theme's toolbar, matching AlwaysUi
     * before user customization: primary, then buttons.drop(1) reversed,
     * then buttons.first (typically Hide).
     */
    fun defaultActions(primaryAction: String?, buttonActions: List<String>): List<String> {
        val first = buttonActions.firstOrNull()
        val middle = buttonActions.drop(1).asReversed()
        return (listOfNotNull(primaryAction) + middle + listOfNotNull(first)).distinct()
    }

    /**
     * Merge a saved preference with the theme's current buttons.
     * [saved] null or empty means "use the default, all visible".
     * Known entries keep the user's order and visibility; Hide stays visible;
     * buttons newly added to the theme are appended and shown; unknown ids
     * that the theme no longer has are dropped.
     */
    fun resolve(
        themeActions: List<String>,
        saved: List<ToolbarButtonPref>?,
    ): List<ToolbarButtonPref> {
        val themeSet = themeActions.toSet()
        if (saved.isNullOrEmpty()) {
            return themeActions.map { ToolbarButtonPref(it, visible = true) }
        }
        val kept = saved
            .filter { it.action in themeSet }
            .distinctBy { it.action }
            .map { pref ->
                if (pref.action == HIDE_ACTION) pref.copy(visible = true) else pref
            }
        val seen = kept.map { it.action }.toSet()
        val newcomers = themeActions
            .filter { it !in seen }
            .map { ToolbarButtonPref(it, visible = true) }
        return kept + newcomers
    }

    fun visibleActions(resolved: List<ToolbarButtonPref>): List<String> {
        val visible = resolved.filter { it.visible }.map { it.action }
        return if (visible.contains(HIDE_ACTION) || resolved.none { it.action == HIDE_ACTION }) {
            visible
        } else {
            // Hide must remain available even if a saved pref tried to hide it.
            visible + HIDE_ACTION
        }
    }

    fun canHide(action: String): Boolean = action != HIDE_ACTION

    fun isVoiceBesideHide(visibleActions: List<String>): Boolean {
        val hideIdx = visibleActions.indexOf(HIDE_ACTION)
        val voiceIdx = visibleActions.indexOf(VOICE_ACTION)
        return hideIdx >= 0 && voiceIdx >= 0 && kotlin.math.abs(hideIdx - voiceIdx) == 1
    }

    /** True when Hide is immediately to the right of Voice in left-to-right order. */
    fun hideIsOnRightOfVoice(visibleActions: List<String>): Boolean {
        val voiceIdx = visibleActions.indexOf(VOICE_ACTION)
        val hideIdx = visibleActions.indexOf(HIDE_ACTION)
        return voiceIdx >= 0 && hideIdx == voiceIdx + 1
    }

    fun serialize(prefs: List<ToolbarButtonPref>): String = prefs.joinToString(",") { "${it.action}:${if (it.visible) 1 else 0}" }

    fun deserialize(raw: String?): List<ToolbarButtonPref> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                val sep = trimmed.indexOf(':')
                if (sep <= 0) {
                    ToolbarButtonPref(trimmed, visible = true)
                } else {
                    val action = trimmed.substring(0, sep).trim()
                    if (action.isEmpty()) {
                        null
                    } else {
                        val flag = trimmed.substring(sep + 1).trim()
                        ToolbarButtonPref(action, visible = flag != "0" && !flag.equals("false", ignoreCase = true))
                    }
                }
            }
        }
    }

    fun move(prefs: List<ToolbarButtonPref>, from: Int, to: Int): List<ToolbarButtonPref> {
        if (from !in prefs.indices || to !in prefs.indices || from == to) return prefs
        val result = prefs.toMutableList()
        val item = result.removeAt(from)
        result.add(to, item)
        return result
    }

    fun setVisible(prefs: List<ToolbarButtonPref>, action: String, visible: Boolean): List<ToolbarButtonPref> {
        if (action == HIDE_ACTION && !visible) return prefs
        return prefs.map {
            if (it.action == action) it.copy(visible = visible) else it
        }
    }
}
