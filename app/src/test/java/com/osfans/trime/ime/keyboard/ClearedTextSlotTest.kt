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
    })
