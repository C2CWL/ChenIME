/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class HandwritingLookupTest :
    StringSpec({
        "only exact characters in the current pinyin menu can be selected" {
            val menu = listOf("哈哈哈", "哈哈", "哈", "蛤", "虾")
            findHandwritingCandidates(listOf("合", "哈", "蛤")) { start, count ->
                menu.drop(start).take(count)
            } shouldBe listOf(HandwritingCandidate("哈", 2), HandwritingCandidate("蛤", 3))
        }

        "recognition order does not change the original global menu indices" {
            val menu = MutableList(105) { "词$it" }.apply {
                this[0] = "哈"
                this[53] = "蛤"
                this[104] = "铪"
            }
            findHandwritingCandidates(listOf("铪", "哈", "蛤")) { start, count ->
                menu.drop(start).take(count)
            } shouldBe listOf(
                HandwritingCandidate("铪", 104),
                HandwritingCandidate("哈", 0),
                HandwritingCandidate("蛤", 53),
            )
        }

        "sparse matches after index 1500 are not lost" {
            val menu = List(1537) { if (it == 1536) "哈" else "词$it" }
            findHandwritingCandidates(listOf("哈")) { start, count ->
                menu.drop(start).take(count)
            } shouldBe listOf(HandwritingCandidate("哈", 1536))
        }

        "duplicate recognition and menu entries keep the first matching segment" {
            val menu = listOf("哈哈哈", "哈", "哈")
            findHandwritingCandidates(listOf("哈", "哈")) { start, count ->
                menu.drop(start).take(count)
            } shouldBe listOf(HandwritingCandidate("哈", 1))
        }

        "a recognized character absent from the pinyin menu is not inserted" {
            val menu = listOf("哈哈哈", "哈哈")
            findHandwritingCandidates(listOf("哈")) { start, count ->
                menu.drop(start).take(count)
            } shouldBe emptyList()
        }

        "an exact page boundary terminates when no match exists" {
            val menu = List(48) { "词$it" }
            val pages = mutableListOf<Int>()
            findHandwritingCandidates(listOf("哈")) { start, count ->
                pages.add(start)
                menu.drop(start).take(count)
            } shouldBe emptyList()
            pages shouldBe listOf(0, 48)
        }

        "empty recognition never scans the menu" {
            val pages = mutableListOf<Int>()
            findHandwritingCandidates(emptyList()) { start, _ ->
                pages.add(start)
                emptyList()
            } shouldBe emptyList()
            pages shouldBe emptyList()
        }

        "an empty pinyin menu has no handwriting candidates" {
            findHandwritingCandidates(listOf("哈")) { _, _ -> emptyList() } shouldBe emptyList()
        }
    })
