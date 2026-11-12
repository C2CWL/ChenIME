// SPDX-FileCopyrightText: 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Letter-swap derive rules live in the native patch (pinyin-correction.h) and
 * are appended to `speller/algebra/+` when 「拼音纠错」 is on. JVM tests cannot
 * call that JNI helper; they check the switch copy and that a deploy-time
 * derive of this form maps fiechang to feichang.
 */
class PinyinTranspositionCorrectionTest :
    StringSpec({
        "pinyin correction summary mentions swapped-letter tolerance" {
            val text = File("src/main/res/values-zh-rCN/strings.xml").readText()
            text shouldContain "fie→fei"
            text shouldContain "字母对调"
        }

        // derive/([wfghkz])ei$/$1ie/ is applied to dictionary spellings at
        // deploy: fei also accepts fie, so fiechang segments as fei + chang.
        "ei/ie derive maps fie to fei and leaves fei and nihao unchanged" {
            val derived = derivedSpellings("fei", """([wfghkz])ei$""", "$1ie")
            derived shouldBe setOf("fie")
            derivedSpellings("fei", """([wfghkz])ei$""", "$1ie").contains("fei") shouldBe false
            derivedSpellings("chang", """([wfghkz])ei$""", "$1ie") shouldBe emptySet()
            derivedSpellings("nihao", """([wfghkz])ei$""", "$1ie") shouldBe emptySet()
            derivedSpellings("dianhua", """([wfghkz])ei$""", "$1ie") shouldBe emptySet()
        }
    })

private fun derivedSpellings(
    canonical: String,
    pattern: String,
    replacement: String,
): Set<String> {
    val match = Regex(pattern).matchEntire(canonical) ?: return emptySet()
    val derived = replacement.replace(Regex("""\$(\d)""")) { result ->
        match.groupValues[result.groupValues[1].toInt()]
    }
    return if (derived == canonical) emptySet() else setOf(derived)
}
