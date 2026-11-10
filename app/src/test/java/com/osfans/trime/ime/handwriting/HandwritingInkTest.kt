/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class HandwritingInkTest :
    StringSpec({
        "a finished stroke can be undone and cleared" {
            val ink = HandwritingInk()
            ink.isEmpty shouldBe true
            ink.begin(1f, 1f)
            ink.append(2f, 3f)
            ink.end()
            ink.isEmpty shouldBe false
            ink.allStrokes.size shouldBe 1
            ink.undoLastStroke() shouldBe true
            ink.isEmpty shouldBe true
            ink.undoLastStroke() shouldBe false
        }

        "bounds cover every finished point" {
            val ink = HandwritingInk()
            ink.begin(10f, 20f)
            ink.append(40f, 5f)
            ink.end()
            val box = ink.bounds()!!
            box.left shouldBe 10f
            box.top shouldBe 5f
            box.right shouldBe 40f
            box.bottom shouldBe 20f
            ink.clear()
            ink.bounds() shouldBe null
        }
    })
