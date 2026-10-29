// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** One in-memory undo entry; never persisted to disk. */
class ClearedTextSlot {
    data class Entry(val text: String, val composing: Boolean)

    private var entry: Entry? = null

    fun save(text: String, composing: Boolean) {
        entry = text.takeIf { it.isNotEmpty() }?.let { Entry(it, composing) }
    }

    fun take(): Entry? = entry.also { entry = null }
}
