//
// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later
//

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyboardAsciiState
import com.osfans.trime.ime.keyboard.inputStartKeyboard
import com.osfans.trime.ime.keyboard.retainedKeyboard
import com.osfans.trime.util.get
import com.osfans.trime.util.sequence
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
                val sources = ThemeLoader.SourceLoader { resource -> ThemeLoader.findSourceFile(resource, null) }
                val file = root.resolve("$id.yaml")
                // Parse explicitly so malformed YAML cannot silently fall back to native deployment.
                val node = ThemeYaml.parser.parseToYamlNode(file.readText())
                val theme = ThemeLoader.decodeSource(id, node) { resource -> sources.load(resource, null) }
                theme.name shouldBe name
                theme.presetKeys.getValue("Shift_L").shiftLock shouldBe "long"
                theme.presetKeys.getValue("Shift_L3").shiftLock shouldBe "click"
                // Check every resolved Shift, including inherited English layouts.
                val shifts = theme.presetKeyboards.values.flatMap { it.keys }
                    .filter { it.click == KeyActionToken.Plain("Shift_L") }
                shifts.isNotEmpty() shouldBe true
                shifts.forEach {
                    it.longClick shouldBe KeyActionToken.Plain("Shift_L")
                    it.doubleClick shouldBe KeyActionToken.Plain("Shift_L3")
                }
                theme.presetKeys.getValue("undo").send shouldBe "Control+z"
                theme.presetKeys.getValue("redo").send shouldBe "Control+Shift+z"
                val clear = theme.presetKeys.getValue("Ifly_clear_all")
                clear.send shouldBe "FUNCTION"
                clear.command shouldBe "clear_all"
                clear.text shouldBe ""
                val deleteKeys = theme.presetKeyboards.values.flatMap { it.keys }
                    .filter { (it.click as? KeyActionToken.Plain)?.token in setOf("BackSpace", "BackSpace2") }
                deleteKeys.isNotEmpty() shouldBe true
                for (name in listOf("BackSpace", "BackSpace2")) {
                    theme.presetKeys.getValue(name).label shouldBe "ic@backspace_outline"
                    theme.presetKeys.getValue(name).repeatable shouldBe true
                    theme.presetKeys.getValue(name).send shouldBe "BackSpace"
                }
                theme.presetKeys.getValue("BackSpace2").slideDelete shouldBe true
                theme.liquidKeyboard.fixedKeyBar.keys.contains("BackSpace") shouldBe true
                theme.presetKeys.getValue("restore_cleared").command shouldBe "restore_cleared"
                deleteKeys.forEach {
                    val name = (it.click as KeyActionToken.Plain).token
                    it.label.ifEmpty { theme.presetKeys.getValue(name).label } shouldBe "ic@backspace_outline"
                    it.swipeUp shouldBe KeyActionToken.Plain("Ifly_clear_all")
                    it.swipeDown shouldBe KeyActionToken.Plain("restore_cleared")
                }
                theme.toolBar.primaryButton?.action shouldBe "menu_keyboard"
                theme.toolBar.buttons.first().foreground.style shouldBe "ic@keyboard_close"
                theme.toolBar.buttons.takeLast(2).map { it.foreground.style } shouldBe listOf("ic@redo", "ic@undo")
                theme.toolBar.buttons.forEach { it.size shouldBe listOf(40, 40) }
                theme.toolBar.buttons.takeLast(2).map { it.action } shouldBe listOf("redo", "undo")
                theme.liquidKeyboard.fixedKeyBar.keys.forEach { theme.presetKeys.containsKey(it) shouldBe true }
                val builtinEmojiKeyboard = theme.liquidKeyboard.keyboards.single { it.id == "emoji" }
                builtinEmojiKeyboard.type shouldBe LiquidKeyboard.DataType.SINGLE
                builtinEmojiKeyboard.keys.first() shouldBe LiquidKeyboard.KeyItem("🙂")
                builtinEmojiKeyboard.keys.any { it.text.contains("[旺柴]") || it.text.contains("[狗头]") } shouldBe false
                val letterLayouts = if (id == "iflytek.trime") listOf("default") else listOf("default", "letter")
                for (layout in letterLayouts) {
                    val letters = theme.presetKeyboards.getValue(layout).keys.filter {
                        val token = (it.click as? KeyActionToken.Plain)?.token.orEmpty()
                        token.length == 1 && token[0] in 'a'..'z'
                    }
                    letters.size shouldBe 26
                    letters.forEach { key ->
                        val letter = (key.click as KeyActionToken.Plain).token
                        key.popup.first() shouldBe (key.longClick as KeyActionToken.Plain).token
                        key.popup shouldBe if (id == "iflytek.trime" && letter == "m") {
                            listOf("Ifly_symbol_m", "Ifly_symbol_apostrophe", "Letter_commit_M", "Letter_commit_m")
                        } else {
                            listOf(
                                (key.longClick as KeyActionToken.Plain).token,
                                "Letter_commit_$letter",
                                "Letter_commit_${letter.uppercase()}",
                                "Key_$letter",
                                "Key_${letter.uppercase()}",
                                "Key_${letter.uppercase()}_repeat",
                            )
                        }
                    }
                }
                ThemeLoader.loadFromSource(id, file, sources).shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>()
                ThemeLoader.loadSourceNode(id, file, sources)?.get("name")?.string shouldBe name
            }
        }

        "Frost question punctuation is Chinese in both shape modes" {
            val schema = ThemeYaml.parser.parseToYamlNode(root.resolve("rime_frost.schema.yaml").readText())
            val defaults = ThemeYaml.parser.parseToYamlNode(root.resolve("default.yaml").readText())
            val punctuation = ThemeYaml.parser.parseToYamlNode(root.resolve("punctuation.yaml").readText())
            for (shape in listOf("full_shape", "half_shape")) {
                schema["punctuator"]?.get(shape)?.get("__include")?.string shouldBe "default:/punctuator/$shape"
                defaults["punctuator"]?.get(shape)?.get("__include")?.string shouldBe "punctuation:/$shape"
                punctuation[shape]?.get("?")?.get("commit")?.string shouldBe "？"
            }
            punctuation["ascii_style"]?.get("?")?.get("commit")?.string shouldBe "?"
        }

        "Frost defaults native correction off through a bundled patch" {
            val patch = ThemeYaml.parser.parseToYamlNode(root.resolve("rime_frost.custom.yaml").readText())
            patch["patch"]?.get("translator/enable_correction")?.string shouldBe "false"
            ThemeYaml.parser.parseToYamlNode(root.resolve("rime_frost.schema.yaml").readText())
        }

        "only Frost and its dependency schemas are bundled" {
            root.listFiles()!!.filter { it.name.endsWith(".schema.yaml") }
                .map { it.name.removeSuffix(".schema.yaml") }.toSet() shouldBe
                setOf("rime_frost", "melt_eng", "radical_pinyin", "rime_frost_aux", "chen_eng")
        }

        "chen_eng shares the English dictionary with its own prism and user dictionary" {
            val schema = ThemeYaml.parser.parseToYamlNode(root.resolve("chen_eng.schema.yaml").readText())
            val translator = schema["translator"]
            translator?.get("dictionary")?.string shouldBe "melt_eng"
            translator?.get("prism")?.string shouldBe "chen_eng"
            translator?.get("user_dict")?.string shouldBe "chen_eng"
            translator?.get("enable_completion")?.string shouldBe "true"
        }

        "chen_eng is excluded from the default schema list" {
            val defaults = ThemeYaml.parser.parseToYamlNode(root.resolve("default.yaml").readText())
            val schemas = defaults["schema_list"]!!.sequence!!.items.map { it["schema"]?.string }
            schemas shouldBe listOf("rime_frost")
            schemas.contains("chen_eng") shouldBe false
        }

        "default ascii composer does not turn Caps Lock input into delimited uppercase words" {
            val defaults = ThemeYaml.parser.parseToYamlNode(root.resolve("default.yaml").readText())
            defaults["ascii_composer"]?.get("good_old_caps_lock")?.string shouldBe "false"
        }

        "the old include id prevents both loading and picker metadata expansion" {
            val sources = ThemeLoader.SourceLoader { resource -> ThemeLoader.findSourceFile(resource, null) }
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
