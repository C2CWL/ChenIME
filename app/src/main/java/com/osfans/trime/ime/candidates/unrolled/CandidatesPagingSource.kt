/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.daemon.RimeSession
import timber.log.Timber

class CandidatesPagingSource(
    val rime: RimeSession,
    val offset: Int,
) : PagingSource<Int, IndexedCandidate>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IndexedCandidate> {
        // use candidate index for key, null means load from beginning (including offset)
        val startIndex = params.key ?: offset
        val pageSize = params.loadSize
        Timber.d("getCandidates(offset=$startIndex, limit=$pageSize)")
        val candidates =
            rime.runOnReady {
                getCandidates(startIndex, pageSize)
            }
        val prevKey = if (startIndex - pageSize >= offset) startIndex - pageSize else null
        // The compact candidate bar's total is only a snapshot of its own limited batch.
        // In the expanded window it can be stale or smaller than what Rime actually returns,
        // so only a short/empty page marks the real end; otherwise paging would stop early
        // or keep appending blank pages forever.
        val nextKey = candidatesNextKey(startIndex, pageSize, candidates.size)
        return LoadResult.Page(candidates.mapIndexed { i, candidate -> IndexedCandidate(startIndex + i, candidate) }, prevKey, nextKey)
    }

    // always reload from beginning
    override fun getRefreshKey(state: PagingState<Int, IndexedCandidate>) = null
}

internal fun candidatesNextKey(
    startIndex: Int,
    loadSize: Int,
    loadedCount: Int,
): Int? = if (loadedCount < loadSize) null else startIndex + loadSize
