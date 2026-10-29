// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.util.get
import com.osfans.trime.util.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class BuiltinThemeTest :
    StringSpec({
        val root = File("src/main/assets/shared")
        for ((id, name) in mapOf("iflytek.trime" to "讯飞", "tongwenfeng.trime" to "标准")) {
            "$id loads from shipped sources with its picker name" {
                val sources = ThemeLoader.SourceLoader { resource -> ThemeLoader.findSourceFile(resource, listOf(root)) }
                val file = root.resolve("$id.yaml")
                // Parse explicitly so malformed YAML cannot silently fall back to native deployment.
                val node = ThemeYaml.parser.parseToYamlNode(file.readText())
                val theme = ThemeLoader.decodeSource(id, node) { resource -> sources.load(resource, null) }
                theme.name shouldBe name
                val clear = theme.presetKeys.getValue("Ifly_clear_all")
                clear.send shouldBe "FUNCTION"
                clear.command shouldBe "clear_all"
                clear.text shouldBe ""
                val deleteKeys = theme.presetKeyboards.values.flatMap { it.keys }
                    .filter { (it.click as? KeyActionToken.Plain)?.token in setOf("BackSpace", "BackSpace2") }
                deleteKeys.isNotEmpty() shouldBe true
                deleteKeys.forEach { it.swipeUp shouldBe KeyActionToken.Plain("Ifly_clear_all") }

                val letterLayouts = if (id == "iflytek.trime") listOf("default") else listOf("default", "letter")
                for (layout in letterLayouts) {
                    val letters = theme.presetKeyboards.getValue(layout).keys.filter {
                        val token = (it.click as? KeyActionToken.Plain)?.token.orEmpty()
                        token.length == 1 && token[0] in 'a'..'z'
                    }
                    letters.size shouldBe 26
                    letters.forEach { key ->
                        val letter = (key.click as KeyActionToken.Plain).token
                        key.popup shouldBe listOf(
                            (key.longClick as KeyActionToken.Plain).token,
                            "Letter_commit_$letter",
                            "Letter_commit_${letter.uppercase()}",
                        )
                        for (choice in listOf(letter, letter.uppercase())) {
                            val action = theme.presetKeys.getValue("Letter_commit_$choice")
                            action.label shouldBe choice
                            action.commit shouldBe choice
                            action.send shouldBe ""
                            action.text shouldBe ""
                        }
                    }
                }

                theme.presetKeyboards.containsKey("default") shouldBe true
                theme.presetKeys.containsKey("Keyboard_number") shouldBe true
                theme.presetColorSchemes.containsKey("default") shouldBe true
                theme.fallbackColors.isNotEmpty() shouldBe true
                theme.presetKeyboards.getValue("number").lock shouldBe true
                val toolbarActions = theme.toolBar.buttons.map { it.action } + theme.toolBar.primaryButton?.action
                toolbarActions.containsAll(listOf("undo", "redo")) shouldBe true
                if (id == "iflytek.trime") {
                    val standard = ThemeLoader.decodeSource("tongwenfeng.trime", sources.load("tongwenfeng.trime", null)!!) { null }
                    theme.style.candidateViewHeight shouldBe standard.style.candidateViewHeight
                    theme.style.commentHeight shouldBe standard.style.commentHeight
                    theme.style.verticalGap shouldBe standard.style.verticalGap
                    theme.toolBar.buttonSpacing shouldBe standard.toolBar.buttonSpacing
                    theme.toolBar.buttons.forEach { it.foreground.padding shouldBe standard.toolBar.buttons.first().foreground.padding }
                    val symbols = theme.presetKeyboards.getValue("default").keys
                        .first { it.click == KeyActionToken.Plain("Ifly_symbols") }
                    symbols.longClick shouldBe KeyActionToken.Plain("{hide_key_symbol}{hide_key_hint}")
                    ColorSchemeResolver.resolve(theme.presetColorSchemes, "default", true, false)["key_back_color"] shouldBe "0xffffffff"
                    ColorSchemeResolver.resolve(theme.presetColorSchemes, "light", true, true)["key_back_color"] shouldBe "0xff444444"
                    ColorSchemeResolver.resolve(theme.presetColorSchemes, "default", false, false)["key_back_color"] shouldBe "0xff444444"
                    theme.style.keyHeight shouldBe 56
                    theme.style.keyFont shouldBe ThemeLoader.decodeSource("tongwenfeng.trime", sources.load("tongwenfeng.trime", null)!!) { null }.style.keyFont
                    theme.presetColorSchemes.getValue("default")["key_back_color"] shouldBe "0xff444444"
                    theme.presetKeyboards.keys shouldBe setOf("default", "number", "editor")
                    theme.liquidKeyboard.keyboards.map { it.name } shouldBe listOf("常用", "中文", "英文", "网址", "数学", "邮箱", "特殊")
                }
                ThemeLoader.loadFromSource(id, file, sources).shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>()
                ThemeLoader.loadSourceNode(id, file, sources)?.get("name")?.string shouldBe name
            }
        }

        "Frost enables native correction through a bundled patch" {
            val patch = ThemeYaml.parser.parseToYamlNode(root.resolve("rime_frost.custom.yaml").readText())
            patch["patch"]?.get("translator/enable_correction")?.string shouldBe "true"
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
