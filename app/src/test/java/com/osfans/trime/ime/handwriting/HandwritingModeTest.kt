/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class HandwritingModeTest :
    StringSpec({
        "find-char pad uses back, clear, backspace and confirm" {
            HandwritingMode.FindChar.actions.shouldContainExactly(
                HandwritingAction.Back,
                HandwritingAction.Clear,
                HandwritingAction.Backspace,
                HandwritingAction.Confirm,
            )
        }

        "full handwriting keyboard uses back, clear, space, backspace and enter" {
            HandwritingMode.Full.actions.shouldContainExactly(
                HandwritingAction.Back,
                HandwritingAction.Clear,
                HandwritingAction.Space,
                HandwritingAction.Backspace,
                HandwritingAction.Enter,
            )
        }

        "the two modes share a pad but not a confirm/enter key" {
            (HandwritingAction.Confirm in HandwritingMode.Full.actions) shouldBe false
            (HandwritingAction.Enter in HandwritingMode.FindChar.actions) shouldBe false
            (HandwritingAction.Space in HandwritingMode.FindChar.actions) shouldBe false
        }
    })
