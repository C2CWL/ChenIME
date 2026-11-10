/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

internal data class HandwritingCandidate(val text: String, val rimeIndex: Int? = null)

/** Keep recognition order, but select by the original global Rime menu index. */
internal suspend fun findHandwritingCandidates(
    recognized: List<String>,
    getCandidates: suspend (Int, Int) -> List<String>,
): List<HandwritingCandidate> {
    val remaining = recognized.toMutableSet()
    val matches = mutableMapOf<String, HandwritingCandidate>()
    var start = 0
    val pageSize = 48
    while (remaining.isNotEmpty()) {
        val candidates = getCandidates(start, pageSize)
        candidates.forEachIndexed { index, text ->
            if (remaining.remove(text)) {
                matches[text] = HandwritingCandidate(text, start + index)
            }
        }
        if (candidates.size < pageSize) break
        start += candidates.size
    }
    return recognized.distinct().mapNotNull { matches[it] }
}
