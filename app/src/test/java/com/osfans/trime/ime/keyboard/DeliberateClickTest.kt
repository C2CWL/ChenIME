// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DeliberateClickTest :
    StringSpec({
        "an ACTION_UP without ACTION_DOWN on this view does not click" {
            DeliberateClick.shouldFireClick(
                gestureStarted = false,
                requireReleaseInside = false,
                upX = 20f,
                upY = 20f,
                width = 40,
                height = 40,
            ) shouldBe false
        }
        "a complete press and release inside the view does click" {
            DeliberateClick.shouldFireClick(
                gestureStarted = true,
                requireReleaseInside = true,
                upX = 20f,
                upY = 20f,
                width = 40,
                height = 40,
            ) shouldBe true
        }
        "releasing outside the view is ignored when a complete gesture is required" {
            DeliberateClick.shouldFireClick(
                gestureStarted = true,
                requireReleaseInside = true,
                upX = 45f,
                upY = 20f,
                width = 40,
                height = 40,
            ) shouldBe false
            DeliberateClick.shouldFireClick(
                gestureStarted = true,
                requireReleaseInside = true,
                upX = -1f,
                upY = 10f,
                width = 40,
                height = 40,
            ) shouldBe false
            DeliberateClick.isReleaseInside(0f, 0f, 40, 40) shouldBe true
            DeliberateClick.isReleaseInside(39.9f, 39.9f, 40, 40) shouldBe true
            DeliberateClick.isReleaseInside(40f, 20f, 40, 40) shouldBe false
            DeliberateClick.isReleaseInside(20f, 20f, 0, 40) shouldBe false
        }
        "keys may still click after a small finger slide" {
            DeliberateClick.shouldFireClick(
                gestureStarted = true,
                requireReleaseInside = false,
                upX = 80f,
                upY = -10f,
                width = 40,
                height = 40,
            ) shouldBe true
        }
        "a press in the slop strip next to Hide is a missed Hide tap" {
            DeliberateClick.isNearHideEdge(35f, 40, 16f, hideOnRight = true) shouldBe true
            DeliberateClick.isNearHideEdge(20f, 40, 16f, hideOnRight = true) shouldBe false
            DeliberateClick.isNearHideEdge(5f, 40, 16f, hideOnRight = false) shouldBe true
            DeliberateClick.isNearHideEdge(20f, 40, 16f, hideOnRight = false) shouldBe false
        }
        "the hide-adjacent strip never takes more than a third of the button" {
            // 40px slop on a 40px button is capped at 40/3, so the left two thirds stay voice.
            DeliberateClick.isNearHideEdge(20f, 40, 40f, hideOnRight = true) shouldBe false
            DeliberateClick.isNearHideEdge(28f, 40, 40f, hideOnRight = true) shouldBe true
            DeliberateClick.isNearHideEdge(10f, 0, 16f, hideOnRight = true) shouldBe false
            DeliberateClick.isNearHideEdge(10f, 40, 0f, hideOnRight = true) shouldBe false
        }
        "voice is ignored briefly after Hide already ran" {
            DeliberateClick.isWithinHideDebounce(1000L, 1200L) shouldBe true
            DeliberateClick.isWithinHideDebounce(1000L, 1399L) shouldBe true
            DeliberateClick.isWithinHideDebounce(1000L, 1400L) shouldBe false
            DeliberateClick.isWithinHideDebounce(0L, 200L) shouldBe false
            DeliberateClick.isWithinHideDebounce(1000L, 900L) shouldBe false
        }
    })
