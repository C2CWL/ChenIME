// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.keyboard

import com.osfans.trime.ime.candidates.unrolled.StrokeOrderIndex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

class StrokeOrderIndexTest :
    StringSpec({
        "prefix matching excludes unknown characters and words" {
            val index = StrokeOrderIndex.read("# test\n旧\t22511\n救\t12413443134\n".reader())
            index.matches("旧", "2") shouldBe true
            index.matches("旧", "225") shouldBe true
            index.matches("旧", "22511") shouldBe true
            index.matches("旧", "225111") shouldBe false
            index.matches("旧", "1") shouldBe false
            index.matches("旧时", "2") shouldBe false
            index.matches("舊", "2") shouldBe false
            index.matches("救", "124") shouldBe true
        }
        "invalid data is rejected at the asset boundary" {
            listOf("旧\t226", "旧\t", "旧时\t12", "旧\t12\n旧\t21", "旧").forEach { data ->
                shouldThrow<IllegalArgumentException> { StrokeOrderIndex.read(data.reader()) }
            }
        }
        "real generated dictionary includes simplified forms and maps hooks correctly" {
            val asset = listOf(File("src/main/assets/stroke/orders.tsv"), File("app/src/main/assets/stroke/orders.tsv")).first { it.isFile }
            val index = asset.reader().use(StrokeOrderIndex::read)
            index.matches("你", "32") shouldBe true
            index.matches("丁", "12") shouldBe true
            index.matches("乙", "5") shouldBe true
            index.matches("九", "35") shouldBe true
            index.matches("汉", "44") shouldBe true
            index.matches("汉", "44154") shouldBe true
            index.matches("汉", "445") shouldBe false
            index.matches("漢", "4") shouldBe false
            index.matches("後", "3") shouldBe false
            index.matches("后", "33") shouldBe true
            index.matches("國", "2") shouldBe false
        }
        "filter keeps original candidate order rather than dictionary order" {
            val index = StrokeOrderIndex.read("九\t35\n救\t12413443134\n旧\t22511\n".reader())
            val candidates = listOf("旧时", "救", "旧", "九", "舊")
            candidates.withIndex().filter { index.matches(it.value, "2") }.map { it.index to it.value } shouldBe listOf(2 to "旧")
        }
    })
