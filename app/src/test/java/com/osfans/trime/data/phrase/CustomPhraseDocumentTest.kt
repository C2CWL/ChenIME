// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.phrase

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CustomPhraseDocumentTest :
    StringSpec({
        "preserves comments headers unknown lines and CRLF when editing" {
            val original = "# Rime table\r\n#@/db_name\tcustom_phrase.txt\r\n\r\n你好\tnh\t3\r\nunknown\r\n"
            val document = CustomPhraseDocument(original)
            document.toString() shouldBe original
            document.entries().single().entry shouldBe CustomPhraseDocument.Entry("你好", "nh", 3)
            document.replace(3, CustomPhraseDocument.Entry("您好", "nh", 9))
            document.toString() shouldBe original.replace("你好\tnh\t3", "您好\tnh\t9")
            document.replace(3, null)
            document.toString() shouldBe original.replace("你好\tnh\t3\r\n", "")
        }
        "appends with a missing final newline and supports optional weight" {
            val document = CustomPhraseDocument("# keep\n你好\tnh")
            document.entries().single().entry.weight shouldBe 1
            document.replace(null, CustomPhraseDocument.Entry("邮箱", "yx", 8))
            document.toString() shouldBe "# keep\n你好\tnh\n邮箱\tyx\t8\n"
        }
        "new positions must be positive while legacy zero survives unrelated edits" {
            val original = "旧词\tjc\t0\n省略\tsl\n"
            val document = CustomPhraseDocument(original)
            document.entries().first().entry.weight shouldBe 0
            document.entries().first().entry.serialize() shouldBe "旧词\tjc\t0"
            document.toString() shouldBe original
            for (position in listOf(0, -1)) {
                shouldThrowAny { document.replace(null, CustomPhraseDocument.Entry("新增", "xz", position)) }
                shouldThrowAny { document.replace(0, CustomPhraseDocument.Entry("旧词", "jc", position)) }
            }
            document.replace(null, CustomPhraseDocument.Entry("新增", "xz"))
            document.toString() shouldBe original + "新增\txz\t1\n"
        }
        "tabs are separators and newline escapes remain literal" {
            for (text in listOf("a\tb", "a\nb", "a\rb", "#comment")) {
                shouldThrowAny { CustomPhraseDocument.Entry(text, "ab").serialize() }
            }
            shouldThrowAny { CustomPhraseDocument.Entry("你好", "n\th").serialize() }
            CustomPhraseDocument.Entry("你好", "ni hao").serialize() shouldBe "你好\tni hao\t1"
            CustomPhraseDocument.Entry("a\\nb", "ab").serialize() shouldBe "a\\nb\tab\t1"
            CustomPhraseDocument("bad\tab\t1\textra\n# note\tab\t2\n").entries() shouldBe emptyList()
        }
    })
