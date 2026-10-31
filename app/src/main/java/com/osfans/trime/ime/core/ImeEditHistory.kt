// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import java.text.BreakIterator

/**
 * Undo/redo history of the edits this IME made in the current input field.
 *
 * The toolbar used to send Ctrl+Z / Ctrl+Shift+Z to the editor, as Fcitx5 for
 * Android does. Android's TextView merges contiguous insertions into a single
 * undo operation and keeps its history for the lifetime of the view, so one
 * Ctrl+Z wiped a whole sentence and Ctrl+Shift+Z brought back text from an
 * earlier message. This class records each IME commit or backspace as its own
 * step instead.
 *
 * Every [Edit] is relative to the cursor: after the edit, the text just before
 * the cursor is [Edit.inserted]; before it, that text was [Edit.removed].
 * [undo] and [redo] check the editor's real text against that before they
 * apply a step, so a step never deletes text the IME did not commit.
 *
 * The history is dropped when the selection moves in a way the IME did not
 * cause. Each recorded edit queues the cursor movement it should produce, and
 * [onSelectionUpdate] matches incoming selection updates against that queue.
 *
 * Methods are synchronized because commits come from both the main thread and
 * the Rime job thread.
 */
internal class ImeEditHistory(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    data class Edit(
        val removed: String,
        val inserted: String,
    )

    private sealed interface Move {
        /** The cursor start should move by [delta] UTF-16 units. */
        data class By(
            val delta: Int,
        ) : Move

        /** The next update may move the cursor anywhere (composing text changed). */
        data object Any : Move
    }

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private val pendingMoves = ArrayDeque<Move>()
    private var selStart = -1
    private var selEnd = -1

    val canUndo: Boolean
        @Synchronized get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        @Synchronized get() = redoStack.isNotEmpty()

    /**
     * True when the editor last reported a non-empty selection and no IME edit
     * has been sent since. The next commit or backspace replaces that
     * selection, so the caller should read it and pass it as `removed`.
     */
    val mayHaveSelection: Boolean
        @Synchronized get() = pendingMoves.isEmpty() && selStart >= 0 && selStart != selEnd

    /** Forgets everything; call when a new input field starts. */
    @Synchronized
    fun reset(
        initialSelStart: Int = -1,
        initialSelEnd: Int = -1,
    ) {
        clear()
        selStart = initialSelStart
        selEnd = initialSelEnd
    }

    @Synchronized
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        pendingMoves.clear()
    }

    /**
     * Records a commit of [inserted] that replaced [replaced] (the selection,
     * if any). Pass `cursorKnown = false` when the commit replaced composing
     * text, because the cursor then lands relative to the composing region.
     */
    @Synchronized
    fun recordCommit(
        inserted: String,
        replaced: String = "",
        cursorKnown: Boolean = true,
    ) {
        if (inserted.isEmpty() && replaced.isEmpty()) return
        push(Edit(replaced, inserted))
        if (cursorKnown) expectMove(inserted.length) else pendingMoves.addLast(Move.Any)
    }

    /**
     * Records a backspace that removes [removed]. When it removes a
     * selection the cursor start does not move.
     */
    @Synchronized
    fun recordDelete(
        removed: String,
        wasSelection: Boolean = false,
    ) {
        if (removed.isEmpty()) return
        push(Edit(removed, ""))
        if (!wasSelection) expectMove(-removed.length)
    }

    /** Composing text changed in the editor; the cursor moves but no step is recorded. */
    @Synchronized
    fun expectComposingChange() {
        pendingMoves.addLast(Move.Any)
    }

    /** The editor operations [undo] and [redo] need, backed by an `InputConnection`. */
    interface Editor {
        fun selectedText(): CharSequence?

        fun textBeforeCursor(length: Int): CharSequence?

        /** Deletes [deleteLength] UTF-16 units before the cursor, then inserts [insert] there. */
        fun replaceBeforeCursor(
            deleteLength: Int,
            insert: String,
        )
    }

    /**
     * Reverts the most recent step. Returns false when there is nothing to
     * undo, or when the editor no longer shows the step's text right before
     * the cursor, in which case the whole history is dropped.
     */
    @Synchronized
    fun undo(editor: Editor): Boolean {
        val edit = undoStack.lastOrNull() ?: return false
        if (!editor.cursorFollows(edit.inserted)) {
            clear()
            return false
        }
        // Update state before touching the editor so its selection update finds the move queued.
        undoStack.removeLast()
        redoStack.addLast(edit)
        expectMove(-edit.inserted.length)
        expectMove(edit.removed.length)
        editor.replaceBeforeCursor(edit.inserted.length, edit.removed)
        return true
    }

    /** Re-applies the step [undo] reverted last. Same checks as [undo]. */
    @Synchronized
    fun redo(editor: Editor): Boolean {
        val edit = redoStack.lastOrNull() ?: return false
        if (!editor.cursorFollows(edit.removed)) {
            clear()
            return false
        }
        redoStack.removeLast()
        undoStack.addLast(edit)
        expectMove(-edit.removed.length)
        expectMove(edit.inserted.length)
        editor.replaceBeforeCursor(edit.removed.length, edit.inserted)
        return true
    }

    private fun Editor.cursorFollows(expected: String): Boolean {
        if (!selectedText().isNullOrEmpty()) return false
        if (expected.isEmpty()) return true
        return textBeforeCursor(expected.length)?.toString() == expected
    }

    /**
     * Feeds a selection update from `InputMethodService.onUpdateSelection`.
     * Returns false when the update was not caused by the IME and the history
     * was dropped.
     */
    @Synchronized
    fun onSelectionUpdate(
        oldSelStart: Int,
        newSelStart: Int,
        newSelEnd: Int,
    ): Boolean {
        selStart = newSelStart
        selEnd = newSelEnd
        if (newSelStart != newSelEnd) {
            // The user selected text: new commits replace it, old steps no longer line up.
            clear()
            return false
        }
        val delta = newSelStart - oldSelStart
        var sum = 0
        for ((index, move) in pendingMoves.withIndex()) {
            val matched =
                when (move) {
                    Move.Any -> true

                    is Move.By -> {
                        sum += move.delta
                        sum == delta
                    }
                }
            if (matched) {
                repeat(index + 1) { pendingMoves.removeFirst() }
                return true
            }
        }
        // A collapsed cursor that did not move cannot invalidate cursor-relative steps.
        if (delta == 0) return true
        clear()
        return false
    }

    private fun push(edit: Edit) {
        redoStack.clear()
        undoStack.addLast(edit)
        while (undoStack.size > capacity) undoStack.removeFirst()
    }

    private fun expectMove(delta: Int) {
        if (delta != 0) pendingMoves.addLast(Move.By(delta))
    }

    companion object {
        const val DEFAULT_CAPACITY = 100

        /** Characters to read before the cursor to find what one backspace removes. */
        const val BACKSPACE_CONTEXT = 32

        /** The last user-perceived character of [text], which one backspace normally removes. */
        fun lastGrapheme(text: CharSequence): String {
            if (text.isEmpty()) return ""
            val s = text.toString()
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            val end = it.last()
            val start = it.previous()
            return if (start == BreakIterator.DONE) s else s.substring(start, end)
        }
    }
}
