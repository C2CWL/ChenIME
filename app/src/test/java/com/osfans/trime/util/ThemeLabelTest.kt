// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeLabelTest :
    StringSpec({
        "ordinary labels remain literal" {
            parseThemeLabel("空格") shouldBe ThemeLabel(null, "空格")
            parseThemeLabel("text ic@undo") shouldBe ThemeLabel(null, "text ic@undo")
            parseThemeLabel("") shouldBe ThemeLabel(null, "")
        }

        "icon labels use the Iconics namespace and a readable fallback" {
            parseThemeLabel("ic@backspace_outline") shouldBe ThemeLabel("cmd_backspace_outline", "退格")
            parseThemeLabel("ic@keyboard_return") shouldBe ThemeLabel("cmd_keyboard_return", "回车")
            parseThemeLabel("ic@keyboard_return", "发送") shouldBe ThemeLabel("cmd_keyboard_return", "发送")
            parseThemeLabel("ic@missing", "返回") shouldBe ThemeLabel("cmd_missing", "返回")
        }

        "empty icons and icon syntax in the fallback never leak into text" {
            parseThemeLabel("ic@", "发送") shouldBe ThemeLabel(null, "发送")
            parseThemeLabel("ic@missing", "ic@undo").text shouldBe "按键"
            parseThemeLabel("ic@keyboard_return", " ").text shouldBe "回车"
        }
    })
