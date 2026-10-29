// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CommitHistoryTest :
    StringSpec({
        "undo and redo a Unicode commit once each" {
            val history = CommitHistory()
            history.record("你好😀", "prefix你好😀", "tail")
            val edits = mutableListOf<Pair<String, Int>>()
            val edit = { text: String, delete: Int -> edits.add(text to delete) }
            history.apply(false, "prefix你好😀", "tail", false, edit) shouldBe true
            history.apply(false, "prefix", "tail", false, edit) shouldBe false
            history.apply(true, "prefix", "tail", false, edit) shouldBe true
            edits shouldBe listOf("" to 4, "你好😀" to 0)
        }
        "changed context or selection invalidates the entry" {
            for ((before, after, selected) in listOf(Triple("otherx", "tail", false), Triple("prefixx", "changed", false), Triple("prefixx", "tail", true))) {
                val history = CommitHistory()
                history.record("x", "prefixx", "tail")
                history.apply(false, before, after, selected) { _, _ -> error("unsafe edit") } shouldBe false
                history.apply(false, "prefixx", "tail", false) { _, _ -> error("stale entry") } shouldBe false
            }
        }
        "failed edits, new commits and session reset discard old history" {
            val history = CommitHistory()
            history.record("old", "old", "")
            history.record("new", "new", "")
            history.apply(false, "new", "", false) { _, delete ->
                delete shouldBe 3
                false
            } shouldBe false
            history.apply(true, "", "", false) { _, _ -> error("failed undo") } shouldBe false
            history.record("new", "new", "")
            history.clear()
            history.apply(false, "new", "", false) { _, _ -> error("reset") } shouldBe false
        }
        "unsupported reads, oversized commits and missing committed suffix have no history" {
            for ((text, before, after) in listOf(Triple("x", null, ""), Triple("x", "x", null), Triple("x", "y", ""), Triple("x".repeat(4097), "x".repeat(4097), ""))) {
                val history = CommitHistory()
                history.record(text, before, after)
                history.apply(false, before, after, false) { _, _ -> error("invalid commit") } shouldBe false
            }
        }
    })
