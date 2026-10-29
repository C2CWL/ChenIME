// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ClearTextStrategyTest :
    StringSpec({
        "null partial stale and full snapshots all clear committed text" {
            for (mode in 0..3) {
                val editor = ClearEditor("committed text", mode)
                ClearTextStrategy.clear(editor) shouldBe "committed text"
                editor.text shouldBe ""
            }
        }
        "chunk fallback clears both sides of cursor and an existing selection" {
            val editor = ClearEditor("abcdefghijklmnop", 0, false)
            editor.start = 5
            editor.end = 8
            ClearTextStrategy.clear(editor) shouldBe "abcdefghijklmnop"
            editor.text shouldBe ""
        }
        "a truncated snapshot is verified and remaining text cleared" {
            val editor = ClearEditor("prefix and remaining text", 4)
            ClearTextStrategy.clear(editor) shouldBe "prefix and remaining text"
            editor.text shouldBe ""
        }
        "large text clears beyond one surrounding text window" {
            val editor = ClearEditor("abcdef".repeat(5000), 0, false)
            editor.chunk = 4096
            ClearTextStrategy.clear(editor) shouldBe "abcdef".repeat(5000)
            editor.text shouldBe ""
        }
        "selection unavailable still uses select all and replacement" {
            val editor = ClearEditor("committed", 0)
            editor.hideSelection = true
            ClearTextStrategy.clear(editor) shouldBe ""
            editor.text shouldBe ""
        }
        "unresponsive editor is bounded" {
            val editor = ClearEditor("text", 0, false)
            editor.stuck = true
            ClearTextStrategy.clear(editor) shouldBe ""
            editor.calls shouldBe 256
        }
    })

private class ClearEditor(var text: String, val mode: Int, val all: Boolean = true) : ClearTextStrategy.Editor {
    var start = text.length / 2
    var end = start
    var stuck = false
    var calls = 0
    var chunk = 3
    var hideSelection = false
    override fun snapshot() = when (mode) {
        1 -> ClearTextStrategy.Snapshot(text, 3, true)
        2 -> ClearTextStrategy.Snapshot("stale", 0, false)
        3 -> ClearTextStrategy.Snapshot(text, 0, false)
        4 -> ClearTextStrategy.Snapshot(text.take(6), 0, false)
        else -> null
    }
    override fun select(start: Int, end: Int): Boolean {
        this.start = start.coerceAtMost(text.length)
        this.end = end.coerceAtMost(text.length)
        return true
    }
    override fun selectAll() = all.also { if (it) select(0, text.length) }
    override fun selected() = if (hideSelection) null else text.substring(start, end)
    override fun replace(): Boolean {
        text = text.removeRange(start, end)
        end = start
        return true
    }
    override fun before() = text.substring(0, start).takeLast(chunk)
    override fun after() = text.substring(end).take(chunk)
    override fun delete(before: Int, after: Int): Boolean {
        calls++
        if (!stuck) {
            text = text.removeRange(start - before, end + after)
            start -= before
            end = start
        }
        return true
    }
}
