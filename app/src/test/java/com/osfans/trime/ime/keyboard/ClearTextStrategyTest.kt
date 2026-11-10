// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ClearTextStrategyTest :
    StringSpec({
        "deletion before the cursor preserves text after it" {
            val editor = ClearEditor("before|after", 6)
            ClearTextStrategy.clear(editor) shouldBe "before"
            editor.text shouldBe "|after"
            editor.start shouldBe 0
        }
        "only the non-empty selection is deleted and saved" {
            val editor = ClearEditor("before selected after", 7, 15)
            ClearTextStrategy.clear(editor) shouldBe "selected"
            editor.text shouldBe "before  after"
            editor.start shouldBe 7
            editor.calls shouldBe 0
        }
        "text longer than a chunk is saved in its original order" {
            val before = "abcdef".repeat(5000)
            val editor = ClearEditor(before + "after", before.length)
            ClearTextStrategy.clear(editor) shouldBe before
            editor.text shouldBe "after"
        }
        "identical full chunks are not mistaken for a non-deleting editor" {
            val before = " ".repeat(10000)
            val editor = ClearEditor(before + "after", before.length)
            ClearTextStrategy.clear(editor) shouldBe before
            editor.text shouldBe "after"
        }
        "surrogate pairs at chunk boundaries are deleted together" {
            val before = "prefix😀" + "x".repeat(4095)
            val editor = ClearEditor(before + "😃after", before.length)
            ClearTextStrategy.clear(editor) shouldBe before
            editor.text shouldBe "😃after"
            editor.splitSurrogate shouldBe false
        }
        "emoji selection is saved exactly" {
            val editor = ClearEditor("a😀b", 1, 3)
            ClearTextStrategy.clear(editor) shouldBe "😀"
            editor.text shouldBe "ab"
        }
        "when nothing is before the cursor, the first swipe clears the remaining text" {
            val editor = ClearEditor("after", 0)
            ClearTextStrategy.clear(editor) shouldBe "after"
            editor.text shouldBe ""
            editor.start shouldBe 0
        }
        "an empty field is left untouched" {
            val editor = ClearEditor("", 0)
            ClearTextStrategy.clear(editor) shouldBe ""
            editor.text shouldBe ""
            editor.calls shouldBe 0
        }
        "first swipe clears before the cursor and the next swipe clears the rest" {
            val editor = ClearEditor("before after", 7)
            ClearTextStrategy.clearDetailed(editor) shouldBe ClearTextStrategy.Outcome("before ", ClearTextStrategy.Mode.BEFORE)
            editor.text shouldBe "after"
            editor.start shouldBe 0
            ClearTextStrategy.clearDetailed(editor) shouldBe ClearTextStrategy.Outcome("after", ClearTextStrategy.Mode.AFTER)
            editor.text shouldBe ""
            editor.start shouldBe 0
        }
        "a third swipe on an empty field does nothing" {
            val editor = ClearEditor("before after", 7)
            ClearTextStrategy.clear(editor)
            ClearTextStrategy.clear(editor)
            editor.calls = 0
            ClearTextStrategy.clearDetailed(editor) shouldBe ClearTextStrategy.Outcome("", ClearTextStrategy.Mode.NONE)
            editor.text shouldBe ""
            editor.calls shouldBe 0
        }
        "after-cursor text longer than a chunk is saved in its original order" {
            val after = "abcdef".repeat(5000)
            val editor = ClearEditor("keep" + after, 0)
            // cursor at 0, "keep" is after the cursor in this constructor? start=0 means all text is after.
            ClearTextStrategy.clear(editor) shouldBe "keep" + after
            editor.text shouldBe ""
        }
        "identical full chunks after the cursor are not mistaken for a non-deleting editor" {
            val after = " ".repeat(10000)
            val editor = ClearEditor(after + "tail", 0)
            ClearTextStrategy.clear(editor) shouldBe after + "tail"
            editor.text shouldBe ""
        }
        "surrogate pairs at after-chunk boundaries are deleted together" {
            val after = "x".repeat(4095) + "😀tail"
            val editor = ClearEditor(after, 0)
            ClearTextStrategy.clear(editor) shouldBe after
            editor.text shouldBe ""
            editor.splitSurrogate shouldBe false
        }
        "failed deletion after the cursor does not save text" {
            val editor = ClearEditor("after", 0)
            editor.rejectDelete = true
            ClearTextStrategy.clear(editor) shouldBe ""
            editor.text shouldBe "after"
        }
        "a non-deleting editor does not save an unverified chunk" {
            val editor = ClearEditor("text")
            editor.stuck = true
            ClearTextStrategy.clear(editor) shouldBe ""
            editor.text shouldBe "text"
            editor.calls shouldBe 1
        }
        "failure after the first chunk preserves only the confirmed deletion" {
            val chunk = "x".repeat(4096)
            for (failure in listOf("before", "verification", "delete", "unchanged")) {
                val editor = ClearEditor("prefix" + chunk)
                val failingEditor = object : ClearTextStrategy.Editor by editor {
                    var reads = 0

                    override fun before(length: Int): String? {
                        reads++
                        if (failure == "before" && reads == 3) return null
                        if (failure == "verification" && reads == 4) return null
                        return editor.before(length)
                    }

                    override fun delete(before: Int): Boolean {
                        if (editor.calls == 1) {
                            if (failure == "delete") return false
                            if (failure == "unchanged") editor.stuck = true
                        }
                        return editor.delete(before)
                    }
                }
                ClearTextStrategy.clear(failingEditor) shouldBe chunk
            }
        }
        "reaching the deletion bound preserves everything deleted" {
            val deleted = "x".repeat(4096 * 256)
            val editor = ClearEditor("prefix" + deleted)
            ClearTextStrategy.clear(editor) shouldBe deleted
            editor.text shouldBe "prefix"
            editor.calls shouldBe 256
        }
        "failed deletion does not save text" {
            val editor = ClearEditor("before after", 6)
            editor.rejectDelete = true
            ClearTextStrategy.clear(editor) shouldBe ""
            editor.text shouldBe "before after"
        }
        "restoring cleared text at the cursor round-trips before text and selections" {
            val original = "prefix😀 selected😃 after"
            for ((start, end) in listOf(10 to 10, 9 to 19)) {
                val editor = ClearEditor(original, start, end)
                val saved = ClearTextStrategy.clear(editor)
                editor.commit(saved)
                editor.text shouldBe original
                editor.start shouldBe end
            }
        }
    })

private class ClearEditor(var text: String, var start: Int = text.length, var end: Int = start) : ClearTextStrategy.Editor {
    var stuck = false
    var rejectDelete = false
    var calls = 0
    var splitSurrogate = false

    override fun selected() = text.substring(start, end)

    override fun replace(): Boolean {
        commit("")
        return true
    }

    fun commit(value: String) {
        text = text.replaceRange(start, end, value)
        start += value.length
        end = start
    }

    override fun before(length: Int) = text.substring(0, start).takeLast(length)

    override fun after(length: Int) = text.substring(end).take(length)

    override fun delete(before: Int): Boolean {
        calls++
        if (rejectDelete) return false
        if (!stuck) {
            val boundary = start - before
            if (boundary > 0 && text[boundary].isLowSurrogate() && text[boundary - 1].isHighSurrogate()) {
                splitSurrogate = true
            }
            text = text.removeRange(boundary, start)
            start = boundary
            end = start
        }
        return true
    }

    override fun deleteAfter(length: Int): Boolean {
        calls++
        if (rejectDelete) return false
        if (!stuck) {
            val boundary = (end + length).coerceAtMost(text.length)
            if (boundary < text.length && text[boundary - 1].isHighSurrogate() && text[boundary].isLowSurrogate()) {
                splitSurrogate = true
            }
            text = text.removeRange(end, boundary)
        }
        return true
    }
}
