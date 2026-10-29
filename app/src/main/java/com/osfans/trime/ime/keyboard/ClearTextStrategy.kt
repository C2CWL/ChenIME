// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Editor operations are queried afresh; no monitored/cached ExtractedText is used. */
internal object ClearTextStrategy {
    data class Snapshot(val text: String, val start: Int, val partial: Boolean)

    interface Editor {
        fun snapshot(): Snapshot?
        fun select(start: Int, end: Int): Boolean
        fun selectAll(): Boolean
        fun selected(): String?
        fun replace(): Boolean
        fun before(): String?
        fun after(): String?
        fun delete(before: Int, after: Int): Boolean
    }

    fun clear(editor: Editor): String {
        val deleted = StringBuilder()
        var reliable = true
        fun result() = if (reliable) deleted.toString() else ""
        fun replaceSelection(): Boolean {
            val selected = editor.selected()
            if (!editor.replace()) return false
            if (!selected.isNullOrEmpty() && editor.selected() == selected) return false
            if (selected != null) deleted.append(selected)
            return true
        }
        fun empty(): Boolean = editor.before() == "" && editor.after() == "" && editor.selected().isNullOrEmpty() &&
            editor.snapshot()?.text.isNullOrEmpty()

        val snapshot = editor.snapshot()
        if (snapshot != null && snapshot.start == 0 && !snapshot.partial && snapshot.text.isNotEmpty()) {
            // Check the actual selection before trusting offsets from an editor snapshot.
            if (editor.select(0, snapshot.text.length) && editor.selected() == snapshot.text) {
                replaceSelection()
                if (empty()) return result()
            }
        }
        if (editor.selectAll()) {
            replaceSelection()
            if (empty()) return result()
        }
        // Limited surrounding-text windows require repeated queries, in both directions.
        // Bound work even when an editor lies about successful deletion or never updates.
        repeat(256) {
            val selected = editor.selected()
            if (!selected.isNullOrEmpty()) {
                if (!replaceSelection()) return result()
            }
            val before = editor.before().orEmpty()
            val after = editor.after().orEmpty()
            if (before.isEmpty() && after.isEmpty()) return result()
            if (!editor.delete(before.length, after.length)) return result()
            if (editor.before() == before && editor.after() == after) reliable = false
            deleted.insert(0, before)
            deleted.append(after)
        }
        return result()
    }
}
