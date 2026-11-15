// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Scan raw menu indices so sparse matches never strand Paging on an empty page. */
internal class FilteringCandidatesPagingSource(
    private val rime: RimeSession,
    private val end: Int?,
) : PagingSource<Int, IndexedCandidate>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IndexedCandidate> {
        var rawIndex = params.key ?: 0
        val matches = mutableListOf<IndexedCandidate>()
        var exhausted = false
        while (matches.size < params.loadSize) {
            currentCoroutineContext().ensureActive()
            val count = 48
            val (candidates, ends) = rime.runOnReady {
                getCandidates(rawIndex, count) to if (end != null) getCandidateEnds(rawIndex, count) else intArrayOf()
            }
            var consumed = 0
            for ((index, candidate) in candidates.withIndex()) {
                consumed++
                if (ends.getOrNull(index + 2) == end) {
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
        return LoadResult.Page(matches, null, if (exhausted) null else rawIndex)
    }

    override fun getRefreshKey(state: PagingState<Int, IndexedCandidate>): Int? = null
}
