// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** A minimal editor that reports selection updates like a TextView does. */
private class FakeEditor(
    val history: ImeEditHistory,
    var text: String = "",
) : ImeEditHistory.Editor {
    var selStart = text.length
    var selEnd = text.length

    init {
        history.reset(selStart, selEnd)
    }

    override fun selectedText() = text.substring(selStart, selEnd)

    override fun textBeforeCursor(length: Int) = text.substring(maxOf(0, selStart - length), selStart)

    override fun replaceBeforeCursor(
        deleteLength: Int,
        insert: String,
    ) {
        // One batch edit: the editor reports once at the end.
        val old = selStart
        text = text.substring(0, selStart - deleteLength) + insert + text.substring(selEnd)
        selStart = old - deleteLength + insert.length
        selEnd = selStart
        history.onSelectionUpdate(old, selStart, selEnd)
    }

    /** What TrimeInputMethodService.commitText does. */
    fun imeCommit(s: String) {
        history.recordCommit(s, if (history.mayHaveSelection) selectedText() else "")
        replace(s)
    }

    /** What TrimeInputMethodService.recordBackspace plus the DEL key do. */
    fun imeBackspace() {
        if (history.mayHaveSelection && selStart != selEnd) {
            history.recordDelete(selectedText(), wasSelection = true)
            replace("")
            return
        }
        val removed = ImeEditHistory.lastGrapheme(text.substring(0, selStart))
        history.recordDelete(removed)
        if (removed.isNotEmpty()) {
            val old = selStart
            text = text.substring(0, selStart - removed.length) + text.substring(selStart)
            selStart -= removed.length
            selEnd = selStart
            history.onSelectionUpdate(old, selStart, selEnd)
        }
    }

    /** The app or the user changes the text or moves the cursor. */
    fun external(
        newText: String,
        start: Int = newText.length,
        end: Int = start,
    ) {
        val old = selStart
        text = newText
        selStart = start
        selEnd = end
        history.onSelectionUpdate(old, start, end)
    }

    private fun replace(s: String) {
        val old = selStart
        text = text.substring(0, selStart) + s + text.substring(selEnd)
        selStart += s.length
        selEnd = selStart
        history.onSelectionUpdate(old, selStart, selEnd)
    }
}

class ImeEditHistoryTest :
    StringSpec({
        "undo removes one commit at a time and redo restores them in order" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("你好")
            e.imeCommit("，")
            e.imeCommit("世界")
            h.undo(e) shouldBe true
            e.text shouldBe "你好，"
            h.undo(e) shouldBe true
            e.text shouldBe "你好"
            h.redo(e) shouldBe true
            e.text shouldBe "你好，"
            h.redo(e) shouldBe true
            e.text shouldBe "你好，世界"
            h.redo(e) shouldBe false
            e.text shouldBe "你好，世界"
        }

        "undo never touches text the IME did not commit" {
            val h = ImeEditHistory()
            val e = FakeEditor(h, "draft ")
            e.imeCommit("abc")
            h.undo(e) shouldBe true
            e.text shouldBe "draft "
            h.undo(e) shouldBe false
            e.text shouldBe "draft "
        }

        "typing after undo clears redo" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("a")
            e.imeCommit("b")
            h.undo(e)
            e.imeCommit("c")
            h.canRedo shouldBe false
            h.redo(e) shouldBe false
            e.text shouldBe "ac"
            h.undo(e)
            e.text shouldBe "a"
        }

        "backspace is one step and undo puts the deleted character back" {
            val h = ImeEditHistory()
            val e = FakeEditor(h, "x")
            e.imeCommit("ab")
            e.imeBackspace()
            e.imeBackspace()
            e.imeBackspace()
            e.text shouldBe ""
            h.undo(e)
            e.text shouldBe "x"
            h.undo(e)
            e.text shouldBe "xa"
            h.undo(e)
            e.text shouldBe "xab"
            h.undo(e)
            e.text shouldBe "x"
            h.redo(e)
            e.text shouldBe "xab"
        }

        "undo of a commit over a selection restores the selected text" {
            val h = ImeEditHistory()
            val e = FakeEditor(h, "hello world")
            e.external("hello world", 6, 11)
            e.imeCommit("there")
            e.text shouldBe "hello there"
            h.undo(e)
            e.text shouldBe "hello world"
        }

        "the app clearing the field after sending drops the history" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("first message")
            h.undo(e)
            e.imeCommit("sent")
            e.external("")
            h.canUndo shouldBe false
            h.canRedo shouldBe false
            h.redo(e) shouldBe false
            e.text shouldBe ""
        }

        "redo stack from before a send is not replayed" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("old")
            h.undo(e)
            e.external("")
            e.imeCommit("new")
            h.redo(e) shouldBe false
            e.text shouldBe "new"
        }

        "moving the cursor or selecting drops the history" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("abc")
            e.external("abc", 1)
            h.undo(e) shouldBe false
            e.text shouldBe "abc"
            e.external("abc", 3)
            e.imeCommit("d")
            e.external("abcd", 0, 2)
            h.canUndo shouldBe false
        }

        "a new input field starts empty" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("abc")
            h.reset(0, 0)
            h.canUndo shouldBe false
        }

        "selection updates coalesced across several commits still match" {
            val h = ImeEditHistory()
            h.reset(0, 0)
            h.recordCommit("ab")
            h.recordCommit("c")
            h.onSelectionUpdate(0, 3, 3) shouldBe true
            h.canUndo shouldBe true
        }

        "a commit the editor rejected makes the next mismatch drop history" {
            val h = ImeEditHistory()
            h.reset(0, 0)
            h.recordCommit("ab")
            h.onSelectionUpdate(0, 5, 5) shouldBe false
            h.canUndo shouldBe false
        }

        "a step whose text is gone from the editor is not applied" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("abc")
            // An editor that does not report selection updates changes its text.
            e.text = "xyz"
            h.undo(e) shouldBe false
            e.text shouldBe "xyz"
            h.canUndo shouldBe false
        }

        "composing changes do not drop history" {
            val h = ImeEditHistory()
            val e = FakeEditor(h)
            e.imeCommit("ab")
            h.expectComposingChange()
            h.onSelectionUpdate(2, 4, 4) shouldBe true
            h.canUndo shouldBe true
        }

        "history is capped" {
            val h = ImeEditHistory(capacity = 2)
            val e = FakeEditor(h)
            e.imeCommit("a")
            e.imeCommit("b")
            e.imeCommit("c")
            h.undo(e)
            h.undo(e)
            h.undo(e) shouldBe false
            e.text shouldBe "a"
        }

        "lastGrapheme keeps surrogate pairs together" {
            ImeEditHistory.lastGrapheme("a😀") shouldBe "😀"
            ImeEditHistory.lastGrapheme("中文") shouldBe "文"
            ImeEditHistory.lastGrapheme("") shouldBe ""
        }
    })
