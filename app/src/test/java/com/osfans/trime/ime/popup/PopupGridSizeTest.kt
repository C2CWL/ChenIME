/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.popup

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PopupGridSizeTest :
    BehaviorSpec({
        Given("an eleven-key punctuation popup") {
            When("sizing the grid") {
                val (rows, columns) = popupGridSize(11)
                Then("it uses a centered 4x3 grid") {
                    rows shouldBe 4
                    columns shouldBe 3
                }
            }
        }

        Given("an eight-key popup") {
            When("sizing the grid") {
                val (rows, columns) = popupGridSize(8)
                Then("it uses a centered 3x3 grid") {
                    rows shouldBe 3
                    columns shouldBe 3
                }
            }
        }

        Given("a four-key math popup") {
            When("sizing the grid") {
                val (rows, columns) = popupGridSize(4)
                Then("it stays a single row") {
                    rows shouldBe 1
                    columns shouldBe 4
                }
            }
        }

        Given("an empty popup") {
            When("sizing the grid") {
                val (rows, columns) = popupGridSize(0)
                Then("it returns a safe fallback") {
                    rows shouldBe 1
                    columns shouldBe 1
                }
            }
        }
    })
