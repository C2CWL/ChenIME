/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class ResolvedFontCacheTest :
    BehaviorSpec({
        Given("cached key and symbol fonts") {
            Then("repeated draws reuse the resolved instances") {
                val cache = ResolvedFontCache<Any>()
                val key = Any()
                val symbol = Any()
                cache.get("key") { key } shouldBeSameInstanceAs key
                cache.get("symbol") { symbol } shouldBeSameInstanceAs symbol
                repeat(100) {
                    cache.get("key") { error("Unexpected font resolution") } shouldBeSameInstanceAs key
                    cache.get("symbol") { error("Unexpected font resolution") } shouldBeSameInstanceAs symbol
                }
            }
            Then("invalidation resolves every previously cached role again") {
                val cache = ResolvedFontCache<Int>()
                var version = 1
                val resolve = { version }
                cache.get("key", resolve) shouldBe 1
                cache.get("symbol", resolve) shouldBe 1
                version = 2
                cache.get("key", resolve) shouldBe 1
                cache.invalidate()
                cache.get("key", resolve) shouldBe 2
                cache.get("symbol", resolve) shouldBe 2
            }
        }
    })
