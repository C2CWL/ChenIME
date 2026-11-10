// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ClearedTextSlotTest :
    StringSpec({
        "only the latest clear can be restored once" {
            val slot = ClearedTextSlot()
            slot.take() shouldBe null
            slot.save("editor text", false)
            slot.save("nihao", true)
            slot.take() shouldBe ClearedTextSlot.Entry("nihao", true)
            slot.take() shouldBe null
            slot.save("text", false)
            slot.take() shouldBe ClearedTextSlot.Entry("text", false)
            slot.save("old", false)
            slot.save("", false)
            slot.take() shouldBe null
        }

        "a follow-up after-cursor clear is combined for restore, and empty saves are ignored" {
            val slot = ClearedTextSlot()
            slot.saveCleared("before ", false, combineWithPrevious = false)
            slot.peek() shouldBe ClearedTextSlot.Entry("before ", false)
            slot.saveCleared("after", false, combineWithPrevious = true)
            slot.peek() shouldBe ClearedTextSlot.Entry("before after", false)
            slot.saveCleared("", false, combineWithPrevious = true)
            slot.take() shouldBe ClearedTextSlot.Entry("before after", false)
        }

        "a new before-cursor clear replaces the previous entry" {
            val slot = ClearedTextSlot()
            slot.saveCleared("old", false, combineWithPrevious = false)
            slot.saveCleared("new", false, combineWithPrevious = false)
            slot.take() shouldBe ClearedTextSlot.Entry("new", false)
        }
    })
