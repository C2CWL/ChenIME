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
                clock.onCandidatesLaidOut(1080L)?.totalMs shouldBe 80L
                clock.hasPendingTouch() shouldBe false
            }

            then("a later touch replaces an unused stamp so idle gaps are not counted") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                // no candidate update; user waits, then taps again
                clock.onTouchDown(2800L)
                clock.onCandidateUpdate(empty = false)
                clock.onCandidatesLaidOut(2860L)?.totalMs shouldBe 60L
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
                clock.onCandidatesLaidOut(30040L)?.totalMs shouldBe 40L
            }

            then("it reports each stage and omits ones that were not marked") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onDispatched(1070L)
                clock.onRimeStarted(1074L)
                clock.onRimeFinished(1086L)
                clock.onCandidateUpdate(empty = false, uptimeMs = 1094L)
                val laidOut = clock.onCandidatesLaidOut(1116L)!!
                laidOut.totalMs shouldBe 116L
                laidOut.touchToDispatchMs shouldBe 70L
                laidOut.dispatchToRimeMs shouldBe 4L
                laidOut.rimeMs shouldBe 12L
                laidOut.rimeToMainMs shouldBe 8L
                laidOut.mainToLayoutMs shouldBe 22L
                laidOut.layoutToDrawMs shouldBe -1L
                laidOut.details() shouldBe
                    "touch_to_dispatch_ms=70 dispatch_to_rime_ms=4 rime_ms=12 rime_to_main_ms=8 main_to_layout_ms=22"
                val drawn = clock.onFrameDrawn(1124L)!!
                drawn.totalMs shouldBe 116L
                drawn.layoutToDrawMs shouldBe 8L
                drawn.details() shouldBe
                    "touch_to_dispatch_ms=70 dispatch_to_rime_ms=4 rime_ms=12 rime_to_main_ms=8 main_to_layout_ms=22 layout_to_draw_ms=8"
                clock.onFrameDrawn(1130L) shouldBe null
            }

            then("a missing dispatch is left out of the details, not reported as zero") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onRimeStarted(1010L)
                clock.onRimeFinished(1020L)
                clock.onCandidateUpdate(empty = false, uptimeMs = 1025L)
                val timing = clock.onCandidatesLaidOut(1040L)!!
                timing.touchToDispatchMs shouldBe -1L
                timing.dispatchToRimeMs shouldBe -1L
                timing.rimeMs shouldBe 10L
                timing.details() shouldBe "rime_ms=10 rime_to_main_ms=5 main_to_layout_ms=15"
            }

            then("a new touch cancels an unfinished draw wait") {
                val clock = TouchToCandidatesClock()
                clock.onTouchDown(1000L)
                clock.onCandidateUpdate(empty = false, uptimeMs = 1050L)
                clock.onCandidatesLaidOut(1080L)
                clock.onTouchDown(2000L)
                clock.onFrameDrawn(2001L) shouldBe null
                clock.onCandidateUpdate(empty = false, uptimeMs = 2050L)
                clock.onCandidatesLaidOut(2060L)?.totalMs shouldBe 60L
            }
        }

        given("a path timing") {
            then("an empty details string has no trailing space") {
                PathTiming(totalMs = 10L).details() shouldBe ""
            }
        }
    })
