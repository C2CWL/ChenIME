// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import com.osfans.trime.core.CandidateProto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CompactCandidateTraceTest :
    BehaviorSpec({
        given("process-local candidate fingerprints") {
            val items = listOf(CandidateProto("first", "comment", "1"))

            then("the same content correlates across list instances") {
                CompactCandidateTrace.fingerprint(items) shouldBe CompactCandidateTrace.fingerprint(items.toList())
            }

            then("text, comment and label changes produce different fingerprints") {
                for (changed in listOf(
                    CandidateProto("second", "comment", "1"),
                    CandidateProto("first", "other", "1"),
                    CandidateProto("first", "comment", "2"),
                )) {
                    (CompactCandidateTrace.fingerprint(items) == CompactCandidateTrace.fingerprint(listOf(changed))) shouldBe false
                }
            }

            then("field boundaries are not ambiguous") {
                val first = listOf(CandidateProto("ab", "c", ""))
                val second = listOf(CandidateProto("a", "bc", ""))
                (CompactCandidateTrace.fingerprint(first) == CompactCandidateTrace.fingerprint(second)) shouldBe false
            }

            then("empty lists have a stable bounded fingerprint") {
                CompactCandidateTrace.fingerprint(emptyList()).length shouldBe 16
                CompactCandidateTrace.fingerprint(emptyList()) shouldBe CompactCandidateTrace.fingerprint(emptyList())
            }
        }
    })
