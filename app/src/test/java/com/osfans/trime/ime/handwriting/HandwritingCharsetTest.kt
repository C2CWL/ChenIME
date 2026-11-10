/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

class HandwritingCharsetTest :
    StringSpec({
        "parses a published charset map into an index table" {
            val json = """{"num_classes":3,"char_to_idx":{"一":0,"丁":1,"七":2}}"""
            val file = HandwritingCharset.parse(json)
            file.numClasses shouldBe 3
            val table = HandwritingCharset.indexToChar(file)
            table.toList() shouldBe listOf("一", "丁", "七")
        }

        "bundled published model charset covers GB2312 level-1" {
            val asset = File("src/main/assets/handwritten/charset.json")
            asset.exists() shouldBe true
            val file = HandwritingCharset.parse(asset.readText())
            file.numClasses shouldBe HandwritingCharset.EXPECTED_CLASSES
            file.charToIdx.size shouldBe HandwritingCharset.EXPECTED_CLASSES
            val table = HandwritingCharset.indexToChar(file)
            table.count { it != "?" } shouldBeGreaterThan (3000)
            table.contains("字") shouldBe true
        }
    })
