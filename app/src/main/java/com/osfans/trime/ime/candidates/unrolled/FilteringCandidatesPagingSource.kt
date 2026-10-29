// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.daemon.RimeSession

/** Scan raw menu indices so sparse matches never strand Paging on an empty page. */
internal class FilteringCandidatesPagingSource(
    private val rime: RimeSession,
    private val offset: Int,
    private val end: Int?,
    private val singleCharacter: Boolean,
) : PagingSource<Int, IndexedCandidate>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IndexedCandidate> {
        var rawIndex = params.key ?: if (end != null) 0 else offset
        val matches = mutableListOf<IndexedCandidate>()
        val scanLimit = 1500
        var exhausted = false
        while (matches.size < params.loadSize && rawIndex < scanLimit) {
            val count = minOf(48, scanLimit - rawIndex)
            val (candidates, ends) = rime.runOnReady {
                getCandidates(rawIndex, count) to if (end != null) getCandidateEnds(rawIndex, count) else intArrayOf()
            }
            var consumed = 0
            for ((index, candidate) in candidates.withIndex()) {
                consumed++
                if ((end == null || ends.getOrNull(index + 2) == end) &&
                    (!singleCharacter || isSingleCharacter(candidate.text))
                ) {
                    matches.add(IndexedCandidate(rawIndex + index, candidate))
                }
                if (matches.size == params.loadSize) break
            }
            rawIndex += consumed
            if (consumed == candidates.size && candidates.size < count) {
                exhausted = true
                break
            }
        }
        return LoadResult.Page(matches, null, if (exhausted || rawIndex >= scanLimit) null else rawIndex)
    }

    override fun getRefreshKey(state: PagingState<Int, IndexedCandidate>): Int? = null
}
