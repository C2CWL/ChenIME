/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

/**
 * LRU cache of measured compact-bar candidate widths. The same short words
 * reappear across keystrokes; re-measuring them with [android.graphics.Paint]
 * on every key added a few milliseconds to the main-thread layout.
 */
internal class CandidateWidthCache(private val maxSize: Int = DEFAULT_MAX_SIZE) {
    private val map = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean = size > maxSize
    }

    val size: Int get() = map.size

    fun getOrMeasure(
        text: String,
        comment: String,
        textSize: Float,
        commentSize: Float,
        padding: Float,
        commentOnRight: Boolean,
        measure: () -> Float,
    ): Float {
        val key = "$text\u0000$comment\u0000$textSize\u0000$commentSize\u0000$padding\u0000$commentOnRight"
        val cached = map[key]
        if (cached != null) return cached
        val width = measure()
        map[key] = width
        return width
    }

    fun clear() {
        map.clear()
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 512
    }
}
