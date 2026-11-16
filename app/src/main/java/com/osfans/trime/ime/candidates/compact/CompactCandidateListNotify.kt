// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import com.osfans.trime.core.CandidateProto

/**
 * Size-only plan for compact candidate list changes. Same-size *content*
 * replacement is not applied through this plan; the adapter uses submitList
 * because content-hash stable IDs make per-index set() notifications unsafe.
 */
data class CompactCandidateListNotify(
    val changeCount: Int,
    val insertStart: Int = 0,
    val insertCount: Int = 0,
    val removeStart: Int = 0,
    val removeCount: Int = 0,
) {
    val isNoOp: Boolean
        get() = changeCount == 0 && insertCount == 0 && removeCount == 0
}

fun planCompactCandidateListNotify(oldSize: Int, newSize: Int): CompactCandidateListNotify {
    require(oldSize >= 0 && newSize >= 0)
    val common = minOf(oldSize, newSize)
    val insertCount = (newSize - oldSize).coerceAtLeast(0)
    val removeCount = (oldSize - newSize).coerceAtLeast(0)
    return CompactCandidateListNotify(
        changeCount = common,
        // Unused starts stay at 0 so the plan matches equality checks.
        insertStart = if (insertCount > 0) oldSize else 0,
        insertCount = insertCount,
        removeStart = if (removeCount > 0) newSize else 0,
        removeCount = removeCount,
    )
}

/**
 * True when compact-bar data is already what the adapter shows. A same-size
 * list with different [CandidateProto] text must not be skipped.
 */
fun shouldSkipCompactCandidateUpdate(
    oldItems: List<CandidateProto>,
    newItems: List<CandidateProto>,
    oldTotal: Int,
    newTotal: Int,
    oldHighlight: Int,
    newHighlight: Int,
): Boolean = newItems == oldItems && oldTotal == newTotal && oldHighlight == newHighlight

/**
 * First..last position whose measured width differs between [old] and [new], or
 * null when nothing moved. Widths are positional, so a size change is not a
 * "change" range and returns null; the size plan covers that case.
 */
fun compactCandidateWidthChangeRange(
    old: List<Int>,
    new: List<Int>,
): IntRange? {
    if (old.size != new.size) return null
    var first = -1
    var last = -1
    for (i in old.indices) {
        if (old[i] != new[i]) {
            if (first < 0) first = i
            last = i
        }
    }
    return if (first < 0) null else first..last
}
