// SPDX-FileCopyrightText: 2015 - 2025 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CandidatesPagingSourceTest : BehaviorSpec({
    given("expanded candidate paging end detection") {
        then("continues when a full page was loaded") {
            candidatesNextKey(
                startIndex = 48,
                loadSize = 48,
                loadedCount = 48,
            ) shouldBe 96
        }

        then("stops when a short page was loaded") {
            candidatesNextKey(
                startIndex = 0,
                loadSize = 48,
                loadedCount = 11,
            ) shouldBe null
        }

        then("stops when a page is empty") {
            candidatesNextKey(
                startIndex = 96,
                loadSize = 48,
                loadedCount = 0,
            ) shouldBe null
        }
    }
})
