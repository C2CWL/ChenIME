// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ToolbarButtonLayoutTest :
    StringSpec({
        val primary = "menu_keyboard"
        val yaml = listOf(
            "Hide",
            "VOICE_ASSIST",
            "Ifly_symbols",
            "clipboard_window",
            "Keyboard_editor",
            "redo",
            "undo",
        )
        val defaultOrder = listOf(
            "menu_keyboard",
            "undo",
            "redo",
            "Keyboard_editor",
            "clipboard_window",
            "Ifly_symbols",
            "VOICE_ASSIST",
            "Hide",
        )

        "default visual order matches the current toolbar" {
            ToolbarButtonLayout.defaultActions(primary, yaml) shouldBe defaultOrder
        }
        "an empty or missing preference uses the default, all visible" {
            val resolved = ToolbarButtonLayout.resolve(defaultOrder, null)
            resolved shouldBe defaultOrder.map { ToolbarButtonPref(it, visible = true) }
            ToolbarButtonLayout.resolve(defaultOrder, emptyList()) shouldBe resolved
            ToolbarButtonLayout.visibleActions(resolved) shouldBe defaultOrder
        }
        "user order and hidden buttons are kept, Hide stays visible" {
            val saved = listOf(
                ToolbarButtonPref("Hide", visible = false),
                ToolbarButtonPref("undo", visible = true),
                ToolbarButtonPref("VOICE_ASSIST", visible = false),
                ToolbarButtonPref("menu_keyboard", visible = true),
            )
            val resolved = ToolbarButtonLayout.resolve(defaultOrder, saved)
            resolved.map { it.action } shouldBe listOf(
                "Hide",
                "undo",
                "VOICE_ASSIST",
                "menu_keyboard",
                "redo",
                "Keyboard_editor",
                "clipboard_window",
                "Ifly_symbols",
            )
            resolved.first { it.action == "Hide" }.visible shouldBe true
            resolved.first { it.action == "VOICE_ASSIST" }.visible shouldBe false
            ToolbarButtonLayout.visibleActions(resolved) shouldBe listOf(
                "Hide",
                "undo",
                "menu_keyboard",
                "redo",
                "Keyboard_editor",
                "clipboard_window",
                "Ifly_symbols",
            )
        }
        "unknown saved ids are dropped and new theme buttons are appended visible" {
            val saved = listOf(
                ToolbarButtonPref("menu_keyboard", visible = true),
                ToolbarButtonPref("gone_button", visible = true),
                ToolbarButtonPref("Hide", visible = true),
            )
            val theme = listOf("menu_keyboard", "brand_new", "Hide")
            val resolved = ToolbarButtonLayout.resolve(theme, saved)
            resolved shouldBe listOf(
                ToolbarButtonPref("menu_keyboard", visible = true),
                ToolbarButtonPref("Hide", visible = true),
                ToolbarButtonPref("brand_new", visible = true),
            )
        }
        "serialize and deserialize round-trip, treating a blank value as default" {
            val prefs = listOf(
                ToolbarButtonPref("undo", visible = true),
                ToolbarButtonPref("Hide", visible = true),
                ToolbarButtonPref("VOICE_ASSIST", visible = false),
            )
            val raw = ToolbarButtonLayout.serialize(prefs)
            raw shouldBe "undo:1,Hide:1,VOICE_ASSIST:0"
            ToolbarButtonLayout.deserialize(raw) shouldBe prefs
            ToolbarButtonLayout.deserialize("") shouldBe emptyList()
            ToolbarButtonLayout.deserialize(null) shouldBe emptyList()
            ToolbarButtonLayout.deserialize("undo") shouldBe listOf(ToolbarButtonPref("undo", visible = true))
        }
        "Hide cannot be turned off, and a missing Hide is forced into the visible list" {
            ToolbarButtonLayout.canHide("Hide") shouldBe false
            ToolbarButtonLayout.canHide("VOICE_ASSIST") shouldBe true
            val hidden = listOf(
                ToolbarButtonPref("undo", visible = true),
                ToolbarButtonPref("Hide", visible = true),
            )
            ToolbarButtonLayout.setVisible(hidden, "Hide", visible = false) shouldBe hidden
            ToolbarButtonLayout.visibleActions(
                listOf(ToolbarButtonPref("undo", visible = true), ToolbarButtonPref("Hide", visible = false)),
            ) shouldBe listOf("undo", "Hide")
        }
        "voice is beside Hide only when the two are neighbors" {
            ToolbarButtonLayout.isVoiceBesideHide(defaultOrder) shouldBe true
            ToolbarButtonLayout.hideIsOnRightOfVoice(defaultOrder) shouldBe true
            val swapped = listOf("VOICE_ASSIST", "undo", "Hide")
            ToolbarButtonLayout.isVoiceBesideHide(swapped) shouldBe false
            ToolbarButtonLayout.hideIsOnRightOfVoice(swapped) shouldBe false
            val hideLeft = listOf("Hide", "VOICE_ASSIST", "undo")
            ToolbarButtonLayout.isVoiceBesideHide(hideLeft) shouldBe true
            ToolbarButtonLayout.hideIsOnRightOfVoice(hideLeft) shouldBe false
        }
        "move rearranges items and ignores out-of-range indexes" {
            val prefs = defaultOrder.map { ToolbarButtonPref(it, visible = true) }
            ToolbarButtonLayout.move(prefs, 0, 1).map { it.action }.take(3) shouldBe listOf(
                "undo",
                "menu_keyboard",
                "redo",
            )
            ToolbarButtonLayout.move(prefs, 0, 0) shouldBe prefs
            ToolbarButtonLayout.move(prefs, -1, 2) shouldBe prefs
            ToolbarButtonLayout.move(prefs, 0, 99) shouldBe prefs
        }
    })
