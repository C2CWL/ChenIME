// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Editor operations are queried afresh; no monitored/cached ExtractedText is used. */
internal object ClearTextStrategy {
    private const val CHUNK_SIZE = 4096
    private const val MAX_DELETIONS = 256

    enum class Mode {
        NONE,
        SELECTION,
        BEFORE,
        AFTER,
    }

    data class Outcome(
        val text: String,
        val mode: Mode,
    )

    interface Editor {
        fun selected(): String?
        fun replace(): Boolean
        fun before(length: Int): String?
        fun after(length: Int): String?
        fun delete(before: Int): Boolean
        fun deleteAfter(length: Int): Boolean
    }

    fun clear(editor: Editor): String = clearDetailed(editor).text

    /**
     * Two-step editor clear:
     * 1. A non-empty selection is deleted first.
     * 2. Otherwise text before the cursor is deleted (first swipe-up).
     * 3. If nothing was before the cursor, remaining text after it is deleted
     *    (a follow-up swipe, or a first swipe when the cursor is already at the start).
     * An empty field is left untouched.
     */
    fun clearDetailed(editor: Editor): Outcome {
        val selected = editor.selected()
        if (!selected.isNullOrEmpty()) {
            if (!editor.replace() || editor.selected() == selected) return Outcome("", Mode.NONE)
            return Outcome(selected, Mode.SELECTION)
        }

        val before = deleteBefore(editor)
        if (before.isNotEmpty()) return Outcome(before, Mode.BEFORE)

        val after = deleteAfter(editor)
        if (after.isNotEmpty()) return Outcome(after, Mode.AFTER)
        return Outcome("", Mode.NONE)
    }

    private fun deleteBefore(editor: Editor): String {
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

    private fun deleteAfter(editor: Editor): String {
        val deleted = StringBuilder()
        repeat(MAX_DELETIONS) {
            val after = editor.after(CHUNK_SIZE) ?: return deleted.toString()
            if (after.isEmpty()) return deleted.toString()
            // Leave a trailing high surrogate for the next chunk, together with its low surrogate.
            val chunk = if (after.last().isHighSurrogate()) after.dropLast(1) else after
            if (chunk.isEmpty() || !editor.deleteAfter(chunk.length)) return deleted.toString()
            val remaining = editor.after(CHUNK_SIZE) ?: return deleted.toString()
            if (after.length < CHUNK_SIZE && remaining == after) return deleted.toString()
            deleted.append(chunk)
            if (remaining.isEmpty()) return deleted.toString()
        }
        return deleted.toString()
    }
}
