// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DeleteSwipeDecisionTest :
    StringSpec({
        "slow upward movement uses distance and tolerates horizontal drift" {
            DeleteSwipeDecision.vertical(20f, -24f, 24f) shouldBe true
            DeleteSwipeDecision.horizontal(60f, -80f, 60f) shouldBe false
        }
        "horizontal delete and tap remain distinct" {
            DeleteSwipeDecision.vertical(48f, -20f, 24f) shouldBe false
            DeleteSwipeDecision.horizontal(-60f, 20f, 60f) shouldBe true
            DeleteSwipeDecision.vertical(2f, -5f, 24f) shouldBe false
        }
    })
