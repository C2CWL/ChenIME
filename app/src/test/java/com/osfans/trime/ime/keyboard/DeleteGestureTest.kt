// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DeleteGestureTest :
    StringSpec({
        "clearing preedit protects committed text for all subsequent slide steps" {
            val gesture = DeleteGesture()
            gesture.consume(true) shouldBe true
            repeat(5) { gesture.consume(false) shouldBe true }
        }
        "a fresh non-composing gesture retains editor deletion and restoration" {
            val previous = DeleteGesture()
            previous.consume(true)
            val gesture = DeleteGesture()
            repeat(5) { gesture.consume(false) shouldBe false }
        }
    })
