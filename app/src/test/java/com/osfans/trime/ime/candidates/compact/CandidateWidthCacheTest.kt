// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CandidateWidthCacheTest :
    BehaviorSpec({
        given("a candidate width cache") {
            then("it returns the measured width and does not remeasure the same key") {
                val cache = CandidateWidthCache()
                var measured = 0
                val first = cache.getOrMeasure("你好", "", 16f, 12f, 8f, false) {
                    measured += 1
                    40f
                }
                val second = cache.getOrMeasure("你好", "", 16f, 12f, 8f, false) {
                    measured += 1
                    99f
                }
                first shouldBe 40f
                second shouldBe 40f
                measured shouldBe 1
                cache.size shouldBe 1
            }

            then("a different comment, size, padding or typeface is a different key") {
                val cache = CandidateWidthCache()
                var measured = 0
                val measure = {
                    measured += 1
                    measured.toFloat()
                }
                cache.getOrMeasure("词", "", 16f, 12f, 8f, false, measure = measure)
                cache.getOrMeasure("词", "*", 16f, 12f, 8f, false, measure = measure)
                cache.getOrMeasure("词", "", 18f, 12f, 8f, false, measure = measure)
                cache.getOrMeasure("词", "", 16f, 12f, 10f, false, measure = measure)
                cache.getOrMeasure("词", "", 16f, 12f, 8f, true, measure = measure)
                cache.getOrMeasure("词", "", 16f, 12f, 8f, false, 42, measure = measure)
                measured shouldBe 6
                cache.size shouldBe 6
            }

            then("the oldest entry is evicted once the cache is full") {
                val cache = CandidateWidthCache(maxSize = 2)
                cache.getOrMeasure("a", "", 1f, 1f, 1f, false) { 1f }
                cache.getOrMeasure("b", "", 1f, 1f, 1f, false) { 2f }
                cache.size shouldBe 2
                cache.getOrMeasure("c", "", 1f, 1f, 1f, false) { 3f }
                cache.size shouldBe 2
                var remade = 0
                // "a" was evicted, so it is measured again
                cache.getOrMeasure("a", "", 1f, 1f, 1f, false) {
                    remade += 1
                    11f
                } shouldBe 11f
                remade shouldBe 1
                // "c" is still cached (and "a" just became most recent; "b" evicted)
                cache.getOrMeasure("c", "", 1f, 1f, 1f, false) {
                    remade += 1
                    99f
                } shouldBe 3f
                remade shouldBe 1
            }
        }
    })
