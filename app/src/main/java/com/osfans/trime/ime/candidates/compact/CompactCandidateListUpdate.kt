// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

/**
 * RecyclerView notifications to apply after the compact adapter's item list
 * has already been replaced. Same-size updates stay in place instead of
 * going through [androidx.recyclerview.widget.RecyclerView.Adapter.notifyDataSetChanged],
 * which detaches every row and waits for a full Flexbox relayout.
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
    return CompactCandidateListNotify(
        changeCount = common,
        insertStart = oldSize,
        insertCount = (newSize - oldSize).coerceAtLeast(0),
        removeStart = newSize,
        removeCount = (oldSize - newSize).coerceAtLeast(0),
    )
}
