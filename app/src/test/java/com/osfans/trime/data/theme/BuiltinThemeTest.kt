// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyboardAsciiState
import com.osfans.trime.ime.keyboard.inputStartKeyboard
import com.osfans.trime.ime.keyboard.retainedKeyboard
import com.osfans.trime.util.get
import com.osfans.trime.util.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class BuiltinThemeTest :
    StringSpec({
        "per-key vertical offsets distinguish absent from explicit zero" {
            val json = kotlinx.serialization.json.Json
            json.decodeFromString<TextKeyboard.TextKey>("{}").keyTextOffsetY shouldBe null
            json.decodeFromString<TextKeyboard.TextKey>("{\"keyTextOffsetY\":0}").keyTextOffsetY shouldBe 0f
            json.decodeFromString<TextKeyboard.TextKey>("{\"keyTextOffsetY\":5}").keyTextOffsetY shouldBe 5f
        }
        val root = File("src/main/assets/shared")
        for ((id, name) in mapOf("iflytek.trime" to "New布局", "tongwenfeng.trime" to "标准")) {
            "$id loads from shipped sources with its picker name" {
                val sources = ThemeLoader.SourceLoader { resource -> ThemeLoader.findSourceFile(resource, listOf(root)) }
                val file = root.resolve("$id.yaml")
                // Parse explicitly so malformed YAML cannot silently fall back to native deployment.
                val node = ThemeYaml.parser.parseToYamlNode(file.readText())
                val theme = ThemeLoader.decodeSource(id, node) { resource -> sources.load(resource, null) }
                theme.name shouldBe name
                theme.presetKeys.getValue("undo").send shouldBe "Control+z"
                theme.presetKeys.getValue("redo").send shouldBe "Control+Shift+z"
                val clear = theme.presetKeys.getValue("Ifly_clear_all")
                clear.send shouldBe "FUNCTION"
                clear.command shouldBe "clear_all"
                clear.text shouldBe ""
                val deleteKeys = theme.presetKeyboards.values.flatMap { it.keys }
                    .filter { (it.click as? KeyActionToken.Plain)?.token in setOf("BackSpace", "BackSpace2") }
                deleteKeys.isNotEmpty() shouldBe true
                deleteKeys.forEach {
                    it.swipeUp shouldBe KeyActionToken.Plain("Ifly_clear_all")
                    it.swipeDown shouldBe KeyActionToken.Plain("restore_cleared")
                }
                theme.toolBar.primaryButton?.action shouldBe "menu_keyboard"
                theme.toolBar.buttons.takeLast(2).map { it.action } shouldBe listOf("redo", "undo")
                theme.liquidKeyboard.fixedKeyBar.keys.forEach { theme.presetKeys.containsKey(it) shouldBe true }

                val letterLayouts = if (id == "iflytek.trime") listOf("default") else listOf("default", "letter")
                for (layout in letterLayouts) {
                    val letters = theme.presetKeyboards.getValue(layout).keys.filter {
                        val token = (it.click as? KeyActionToken.Plain)?.token.orEmpty()
                        token.length == 1 && token[0] in 'a'..'z'
                    }
                    letters.size shouldBe 26
                    letters.forEach { key ->
                        val letter = (key.click as KeyActionToken.Plain).token
                        key.popup shouldBe if (id == "iflytek.trime" && letter == "m") {
                            listOf("Letter_commit_M", "Letter_commit_m", "Ifly_symbol_m", "Ifly_symbol_apostrophe")
                        } else {
                            listOf(
                                (key.longClick as KeyActionToken.Plain).token,
                                "Letter_commit_$letter",
                                "Letter_commit_${letter.uppercase()}",
                            )
                        }
                        key.swipeUp shouldBe null
                        key.composing shouldBe null
                        for (choice in listOf(letter, letter.uppercase())) {
                            val action = theme.presetKeys.getValue("Letter_commit_$choice")
                            action.label shouldBe choice
                            action.commit shouldBe choice
                            action.send shouldBe ""
                            action.text shouldBe ""
                        }
                    }
                }

                theme.presetKeys.getValue("Ifly_separator").send shouldBe "apostrophe"
                theme.presetKeys.getValue("Return").label shouldBe "ic@keyboard_return"
                theme.toolBar.buttons.first { it.action == "Keyboard_editor" }
                    .foreground.style shouldBe "ic@arrow_all"
                theme.presetKeys.getValue("undo").label shouldBe "撤销"
                for (layout in letterLayouts) {
                    theme.presetKeyboards.getValue(layout).keys
                        .first { it.click == KeyActionToken.Plain("Shift_L") }
                        .composing shouldBe if (id == "iflytek.trime") KeyActionToken.Plain("Ifly_separator") else null
                }
                if (id == "iflytek.trime") {
                    val m = theme.presetKeyboards.getValue("default").keys
                        .first { it.click == KeyActionToken.Plain("m") }
                    m.swipeUp shouldBe null
                    m.swipeDown shouldBe null
                    m.swipeLeft shouldBe null
                    m.swipeRight shouldBe null
                    m.composing shouldBe null
                    m.labelSymbol shouldBe "、'"
                    m.longClick shouldBe KeyActionToken.Plain("Ifly_symbol_m")
                    m.popup shouldBe listOf("Letter_commit_M", "Letter_commit_m", "Ifly_symbol_m", "Ifly_symbol_apostrophe")
                    theme.presetKeys.getValue("Ifly_symbol_m").commit shouldBe "、"
                    theme.presetKeys.getValue("Ifly_symbol_m").command shouldBe ""
                    theme.presetKeys.getValue("Ifly_symbol_apostrophe").commit shouldBe "'"
                    theme.presetKeys.getValue("Ifly_symbol_apostrophe").command shouldBe ""
                }
                val layouts = theme.presetKeyboards
                val letters = layouts.getValue("default")
                letters.asciiMode shouldBe 0
                letters.lock shouldBe true
                letters.resetAsciiMode shouldBe false
                letters.asciiKeyboard shouldBe ""
                layouts.getValue("number").asciiMode shouldBe 1
                for (layout in letterLayouts) {
                    val shift = layouts.getValue(layout).keys.first { it.click == KeyActionToken.Plain("Shift_L") }
                    shift.composing shouldBe if (id == "iflytek.trime") KeyActionToken.Plain("Ifly_separator") else null
                    theme.presetKeys.getValue("Shift_L").send shouldBe "Shift_L"
                }
                for (trigger in listOf("open", "focus", "Send", "backspace", "swipe clear")) {
                    for (pad in listOf("default", "number")) {
                        val restarting = trigger !in listOf("open", "focus")
                        val resolved = inputStartKeyboard(1, restarting, pad)
                        resolved shouldBe if (restarting) pad else "default"
                        val config = layouts.getValue(resolved)
                        // Visit number, then letters: an implicit return must not
                        // revive the old locked number panel in either language.
                        for (request in listOf("", ".ascii")) {
                            retainedKeyboard(request, resolved, config.lock, "number", config.asciiKeyboard, layouts.keys) shouldBe resolved
                        }
                        for (mode in listOf(false, true)) {
                            val state = KeyboardAsciiState()
                            state.select(config.asciiMode == 1, mode) shouldBe if (resolved == "number") true else mode
                        }
                    }
                }
                theme.presetKeyboards.containsKey("default") shouldBe true
                theme.presetKeys.containsKey("Keyboard_number") shouldBe true
                theme.presetColorSchemes.containsKey("default") shouldBe true
                theme.fallbackColors.isNotEmpty() shouldBe true
                theme.presetKeyboards.getValue("number").lock shouldBe true
                // Named actions must resolve after includes; single characters and symbol
                // strings are intentional literal input, and these names are native keys.
                val nativeKeys = setOf("at", "comma", "minus", "period", "plus", "slash")
                val literalFaces = setOf("QwQ", "QAQ", "T_T", "o_O")
                fun checkAction(action: String) {
                    if (action.matches(Regex("[A-Za-z_][A-Za-z_0-9]+"))) {
                        withClue("Unresolved action: $action") {
                            (action in theme.presetKeys || action in nativeKeys || action in literalFaces) shouldBe true
                        }
                    }
                    Regex("\\{([A-Za-z_][A-Za-z_0-9]+)}").findAll(action).forEach {
                        withClue("Unresolved sequence: $action") {
                            (it.groupValues[1] in theme.presetKeys) shouldBe true
                        }
                    }
                }
                theme.presetKeyboards.values.flatMap { it.keys }.forEach { key ->
                    listOf(
                        key.click, key.doubleClick, key.lazyDoubleClick, key.longClick,
                        key.swipeUp, key.swipeDown, key.swipeLeft, key.swipeRight,
                        key.composing, key.hasMenu, key.paging, key.combo, key.ascii,
                    ).filterIsInstance<KeyActionToken.Plain>().forEach { checkAction(it.token) }
                    key.popup.forEach { checkAction(it) }
                }
                theme.presetKeys.values.forEach {
                    if (it.label.endsWith("_labels")) it.label shouldBe "enter_labels"
                }
                theme.liquidKeyboard.fixedKeyBar.keys.forEach { checkAction(it) }
                (theme.toolBar.buttons + listOfNotNull(theme.toolBar.primaryButton)).forEach {
                    checkAction(it.action)
                    checkAction(it.longPressAction)
                }
                theme.toolBar.buttons.first().action shouldBe "Hide"
                theme.toolBar.buttons.first().foreground.style shouldBe ""
                val emoji = theme.presetKeys.getValue("liquid_keyboard_emoji")
                emoji.command shouldBe "liquid_keyboard"
                emoji.option shouldBe "emoji"
                theme.toolBar.buttons.any { it.action == "liquid_keyboard_emoji" } shouldBe true
                theme.liquidKeyboard.keyboards.first { it.id == emoji.option }.keys.isNotEmpty() shouldBe true
                if (id == "iflytek.trime") {
                    theme.toolBar.buttons.map { it.action } shouldBe listOf(
                        "Hide",
                        "VOICE_ASSIST",
                        "liquid_keyboard_emoji",
                        "clipboard_window",
                        "Keyboard_editor",
                        "redo",
                        "undo",
                    )
                }
                if (id == "tongwenfeng.trime") {
                    theme.toolBar.buttons.map { it.action } shouldBe listOf(
                        "Hide", "VOICE_ASSIST", "liquid_keyboard_emoji", "clipboard_window",
                        "Keyboard_editor", "Mode_switch", "Zenkaku_Hankaku", "redo", "undo",
                    )
                }
                theme.toolBar.buttons[1].foreground.style shouldBe "ic@microphone"
                // 1080px at 3x density is 360dp; the fixed rightmost Hide is included.
                val toolbarButtons = theme.toolBar.buttons + listOfNotNull(theme.toolBar.primaryButton)
                val toolbarWidth = toolbarButtons.sumOf { it.size.first() } +
                    (theme.toolBar.buttons.size - 1) * theme.toolBar.buttonSpacing
                (toolbarWidth <= 360) shouldBe true
                val toolbarActions = theme.toolBar.buttons.map { it.action } + theme.toolBar.primaryButton?.action
                toolbarActions.containsAll(listOf("undo", "redo")) shouldBe true
                if (id == "iflytek.trime") {
                    val standard = ThemeLoader.decodeSource("tongwenfeng.trime", sources.load("tongwenfeng.trime", null)!!) { null }
                    theme.style.candidateViewHeight shouldBe standard.style.candidateViewHeight
                    theme.style.commentHeight shouldBe standard.style.commentHeight
                    theme.style.verticalGap shouldBe 4
                    theme.style.keyboardPaddingBottom shouldBe 0
                    theme.style.keyboardPaddingLandBottom shouldBe 0
                    theme.presetKeyboards.getValue("default").labelTransform shouldBe TextKeyboard.LabelTransform.NONE
                    toolbarActions.containsAll(listOf("Hide", "liquid_keyboard_emoji", "Keyboard_editor")) shouldBe true
                    for (scheme in listOf("default", "light")) {
                        val colors = theme.presetColorSchemes.getValue(scheme)
                        colors["key_symbol_color"] shouldBe colors["off_key_symbol_color"]
                        (colors["key_symbol_color"] != colors["key_back_color"]) shouldBe true
                        colors["on_key_symbol_color"] shouldBe "0xffffffff"
                    }
                    theme.toolBar.buttonSpacing shouldBe standard.toolBar.buttonSpacing
                    theme.toolBar.buttons.forEach { it.foreground.padding shouldBe standard.toolBar.buttons.first().foreground.padding }
                    val symbols = theme.presetKeyboards.getValue("default").keys
                        .first { it.click == KeyActionToken.Plain("Ifly_symbols") }
                    symbols.labelSymbol shouldBe "简洁"
                    val mainKeys = theme.presetKeyboards.getValue("default").keys
                    mainKeys.any { it.click == KeyActionToken.Plain("Keyboard_number") } shouldBe true
                    mainKeys.any { it.click == KeyActionToken.Plain("Return") } shouldBe true
                    mainKeys.first { it.click == KeyActionToken.Plain("Ifly_space") }
                        .longClick shouldBe KeyActionToken.Plain("Mode_switch")
                    theme.liquidKeyboard.fixedKeyBar.keys shouldBe listOf("liquid_keyboard_lock", "space1", "BackSpace", "liquid_keyboard_exit")
                    theme.liquidKeyboard.keyboards.first().keys.isNotEmpty() shouldBe true
                    symbols.longClick shouldBe KeyActionToken.Plain("{hide_key_symbol}{hide_key_hint}")
                    ColorSchemeResolver.resolve(theme.presetColorSchemes, "default", true, false)["key_back_color"] shouldBe "0xffffffff"
                    ColorSchemeResolver.resolve(theme.presetColorSchemes, "light", true, true)["key_back_color"] shouldBe "0xff444444"
                    ColorSchemeResolver.resolve(theme.presetColorSchemes, "default", false, false)["key_back_color"] shouldBe "0xff444444"
                    theme.style.keyHeight shouldBe 56
                    theme.style.keyFont shouldBe ThemeLoader.decodeSource("tongwenfeng.trime", sources.load("tongwenfeng.trime", null)!!) { null }.style.keyFont
                    theme.presetColorSchemes.getValue("default")["key_back_color"] shouldBe "0xff444444"
                    theme.presetKeyboards.keys shouldBe setOf("default", "number", "editor")
                    theme.liquidKeyboard.keyboards.map { it.name } shouldBe listOf("表情", "常用", "中文", "英文", "网址", "数学", "邮箱", "特殊")
                }
                ThemeLoader.loadFromSource(id, file, sources).shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>()
                ThemeLoader.loadSourceNode(id, file, sources)?.get("name")?.string shouldBe name
            }
        }

        "Frost defaults native correction off through a bundled patch" {
            val patch = ThemeYaml.parser.parseToYamlNode(root.resolve("rime_frost.custom.yaml").readText())
            patch["patch"]?.get("translator/enable_correction")?.string shouldBe "false"
            ThemeYaml.parser.parseToYamlNode(root.resolve("rime_frost.schema.yaml").readText())
        }

        "only Frost and its dependency schemas are bundled" {
            root.listFiles()!!.filter { it.name.endsWith(".schema.yaml") }
                .map { it.name.removeSuffix(".schema.yaml") }.toSet() shouldBe
                setOf("rime_frost", "melt_eng", "radical_pinyin", "rime_frost_aux")
            root.resolve("default.yaml").readLines().filter { it.trimStart().startsWith("- schema:") }
                .map { it.trim() } shouldBe listOf("- schema: rime_frost")
        }

        "the old include id prevents both loading and picker metadata expansion" {
            val sources = ThemeLoader.SourceLoader { resource -> ThemeLoader.findSourceFile(resource, listOf(root)) }
            val broken = File.createTempFile("iflytek", ".yaml")
            try {
                broken.writeText(root.resolve("iflytek.trime.yaml").readText().replace("tongwenfeng.trime:/", "tongwenfeng:/"))
                val node = ThemeYaml.parser.parseToYamlNode(broken.readText())
                shouldThrow<ThemeDslExpander.UnresolvedReference> {
                    ThemeLoader.decodeSource("iflytek.trime", node) { resource -> sources.load(resource, null) }
                }
                ThemeLoader.loadFromSource("iflytek.trime", broken, sources) shouldBe null
                ThemeLoader.loadSourceNode("iflytek.trime", broken, sources) shouldBe null
            } finally {
                broken.delete()
            }
        }

        "the legacy resource is shipped but excluded from the picker" {
            root.resolve("trime.yaml").isFile shouldBe true
            val dir = kotlin.io.path.createTempDirectory("legacy-theme").toFile()
            try {
                root.resolve("trime.yaml").copyTo(dir.resolve("trime.yaml"))
                ThemeFilesManager.listThemes(dir) shouldBe emptyList()
            } finally {
                dir.deleteRecursively()
            }
        }
    })
