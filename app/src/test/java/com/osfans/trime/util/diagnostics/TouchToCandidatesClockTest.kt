// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.diagnostics

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TouchToCandidatesClockTest :
    BehaviorSpec({
        given("a touch-to-candidates clock") {
            then("it measures from a key touch to the layout caused by that touch") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onCandidateUpdate(empty = false)
                clock.onCandidatesLaidOut(1080L) shouldBe 80L
                clock.hasPendingTouch() shouldBe false
            }

            then("a later touch replaces an unused stamp so idle gaps are not counted") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                // no candidate update; user waits, then taps again
                clock.onTouchDown(2800L)
                clock.onCandidateUpdate(empty = false)
                clock.onCandidatesLaidOut(2860L) shouldBe 60L
            }

            then("a layout without a candidate update after the touch is ignored") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onCandidatesLaidOut(1500L) shouldBe null
                clock.hasPendingTouch() shouldBe true
            }

            then("an empty candidate update does not wait for a later layout") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onCandidateUpdate(empty = true)
                clock.onCandidatesLaidOut(4000L) shouldBe null
                clock.hasPendingTouch() shouldBe false
            }

            then("hide or show drops any in-flight stamp") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onCandidateUpdate(empty = false)
                clock.onWindowTransition()
                clock.onCandidatesLaidOut(28701L) shouldBe null
                clock.hasPendingTouch() shouldBe false
            }

            then("a touch after hide starts a fresh measurement") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onWindowTransition()
                clock.onTouchDown(30000L)
                clock.onCandidateUpdate(empty = false)
                clock.onCandidatesLaidOut(30040L) shouldBe 40L
            }
        }
    })
