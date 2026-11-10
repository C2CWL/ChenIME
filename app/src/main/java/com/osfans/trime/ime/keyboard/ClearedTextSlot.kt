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

    fun peek(): Entry? = entry

    /**
     * Records a swipe-up clear. An empty deletion is ignored so a third swipe
     * on an already empty field cannot wipe the restore buffer. When
     * [combineWithPrevious] is set (a follow-up clear of leftover after-cursor
     * text), the new text is appended so swipe-down can restore the whole field.
     */
    fun saveCleared(
        text: String,
        composing: Boolean,
        combineWithPrevious: Boolean,
    ) {
        if (text.isEmpty()) return
        val prefix = if (combineWithPrevious) entry?.takeIf { !it.composing }?.text.orEmpty() else ""
        entry = Entry(prefix + text, composing)
    }

    fun take(): Entry? = entry.also { entry = null }
}
