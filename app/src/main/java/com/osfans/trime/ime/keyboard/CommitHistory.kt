// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** One bounded commit, guarded by editor context; never persisted. Lengths use UTF-16. */
class CommitHistory {
    private data class Entry(val text: String, val prefix: String, val suffix: String, var undone: Boolean = false)

    private var entry: Entry? = null

    fun clear() {
        entry = null
    }

    fun record(text: String, before: String?, after: String?) {
        entry = if (text.isNotEmpty() && text.length <= MAX_TEXT && before != null && after != null && before.endsWith(text)) {
            Entry(text, before.dropLast(text.length).takeLast(CONTEXT), after)
        } else {
            null
        }
    }

    /** Returns true only when our edit succeeded. A mismatch invalidates stale history. */
    fun apply(redo: Boolean, before: String?, after: String?, selected: Boolean, edit: (String, Int) -> Boolean): Boolean {
        val saved = entry ?: return false
        val expected = saved.prefix + if (saved.undone) "" else saved.text
        if (selected || before == null || after != saved.suffix || before.takeLast(expected.length) != expected) {
            clear()
            return false
        }
        if (redo != saved.undone) return false
        val success = if (redo) edit(saved.text, 0) else edit("", saved.text.length)
        if (success) saved.undone = !saved.undone else clear()
        return success
    }

    companion object {
        const val CONTEXT = 128
        const val MAX_TEXT = 4096
    }
}
