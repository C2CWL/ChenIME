// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Editor operations are queried afresh; no monitored/cached ExtractedText is used. */
internal object ClearTextStrategy {
    private const val CHUNK_SIZE = 4096
    private const val MAX_DELETIONS = 256

    interface Editor {
        fun selected(): String?
        fun replace(): Boolean
        fun before(length: Int): String?
        fun delete(before: Int): Boolean
    }

    fun clear(editor: Editor): String {
        val selected = editor.selected()
        if (!selected.isNullOrEmpty()) {
            if (!editor.replace() || editor.selected() == selected) return ""
            return selected
        }

        val deleted = StringBuilder()
        repeat(MAX_DELETIONS) {
            val before = editor.before(CHUNK_SIZE) ?: return deleted.toString()
            if (before.isEmpty()) return deleted.toString()
            // Leave a leading low surrogate for the next chunk, together with its high surrogate.
            val chunk = if (before.first().isLowSurrogate()) before.drop(1) else before
            if (chunk.isEmpty() || !editor.delete(chunk.length)) return deleted.toString()
            val remaining = editor.before(CHUNK_SIZE) ?: return deleted.toString()
            // Full windows can legitimately be identical (for example, repeated spaces).
            if (before.length < CHUNK_SIZE && remaining == before) return deleted.toString()
            deleted.insert(0, chunk)
            if (remaining.isEmpty()) return deleted.toString()
        }
        // Preserve all chunks accepted before reaching the work bound.
        return deleted.toString()
    }
}
