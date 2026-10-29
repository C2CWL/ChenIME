// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RuntimeOptionCacheTest :
    BehaviorSpec({
        given("runtime option labels before and after a seed") {
            val cache = RuntimeOptionCache()

            then("unknown options use false without consulting Rime") {
                cache["ascii_mode"] shouldBe false
            }

            `when`("startup reads the current option states") {
                val revision = cache.beginSeed()
                cache.finishSeed(revision, mapOf("ascii_mode" to true, "custom_toggle" to true))

                then("both standard and custom toggle labels use the seeded values") {
                    cache["ascii_mode"] shouldBe true
                    cache["custom_toggle"] shouldBe true
                }
            }
        }

        given("a schema seed overlapping option notifications") {
            val cache = RuntimeOptionCache()
            cache.update("custom_toggle", true)
            val revision = cache.beginSeed()
            cache.update("ascii_mode", true)
            // Even a notification retaining false must override an older snapshot.
            cache.update("ascii_punct", false)
            cache.finishSeed(
                revision,
                mapOf("ascii_mode" to false, "ascii_punct" to true, "custom_toggle" to false),
            )

            then("new notifications win while other options refresh for the schema") {
                cache["ascii_mode"] shouldBe true
                cache["ascii_punct"] shouldBe false
                cache["custom_toggle"] shouldBe false
            }

            then("last known values survive until the next snapshot arrives") {
                cache.beginSeed()
                cache["ascii_mode"] shouldBe true
                cache["missing"] shouldBe false
            }
        }

        given("two seeds completing in reverse order") {
            val cache = RuntimeOptionCache()
            val older = cache.beginSeed()
            val newer = cache.beginSeed()
            cache.finishSeed(newer, mapOf("custom_toggle" to true))
            cache.finishSeed(older, mapOf("custom_toggle" to false))

            then("the newer snapshot wins") {
                cache["custom_toggle"] shouldBe true
            }
        }

        given("repeated status updates") {
            val cache = RuntimeOptionCache()

            then("only a displayed value change needs to invalidate labels") {
                cache.update("ascii_mode", false) shouldBe false
                cache.update("ascii_mode", true) shouldBe true
                cache.update("ascii_mode", true) shouldBe false
                cache.update("ascii_mode", false) shouldBe true
            }
        }
    })
