// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import com.osfans.trime.core.CandidateProto
import com.osfans.trime.util.diagnostics.LagDiagnostics
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Process-local fingerprints: correlate stages without exporting candidate text or a reusable hash. */
internal object CompactCandidateTrace {
    private val key by lazy {
        SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "HmacSHA256")
    }

    fun fingerprint(items: List<CandidateProto>): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        for (item in items) {
            for (value in listOf(item.text, item.comment, item.label)) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                mac.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                mac.update(bytes)
            }
        }
        return mac.doFinal().take(8).joinToString("") { "%02x".format(it) }
    }

    inline fun log(owner: Any, sequence: Long, stage: String, details: () -> String) {
        if (!LagDiagnostics.enabled) return
        LagDiagnostics.log(
            "candidate_trace",
            "owner=${System.identityHashCode(owner)} seq=$sequence stage=$stage ${details()}",
        )
    }
}
