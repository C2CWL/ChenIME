// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UndoRedoShortcutTest :
    StringSpec({
        "undo accepts aggregate and sided control states" {
            for (ctrl in listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_CTRL_LEFT_ON, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)) {
                undoRedoShortcut(KeyEvent.KEYCODE_Z, ctrl) shouldBe UndoRedoShortcut.UNDO
                undoRedoShortcut(KeyEvent.KEYCODE_Z, ctrl or KeyEvent.META_SHIFT_ON) shouldBe UndoRedoShortcut.REDO
                undoRedoShortcut(KeyEvent.KEYCODE_Y, ctrl) shouldBe UndoRedoShortcut.REDO
            }
        }
        "unrelated shortcuts are left to the editor" {
            undoRedoShortcut(KeyEvent.KEYCODE_Z, 0) shouldBe null
            undoRedoShortcut(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON) shouldBe null
            undoRedoShortcut(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON) shouldBe null
        }
    })
