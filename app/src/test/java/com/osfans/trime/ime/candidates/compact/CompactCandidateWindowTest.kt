// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactCandidateWindowTest {
    @Test
    fun initialWindowKeepsOnlyTheVisiblePrefixPlusSlack() {
        val widths = List(16) { 100 }
        assertEquals(0..4, compactCandidateInitialWindow(widths, barWidth = 400, separator = 0))
    }

    @Test
    fun initialWindowIncludesTheSeparator() {
        val widths = List(8) { 100 }
        // 100+10+100+10+100 = 320, the fourth item does not fit.
        assertEquals(0..3, compactCandidateInitialWindow(widths, barWidth = 320, separator = 10, slack = 0))
        assertEquals(0..4, compactCandidateInitialWindow(widths, barWidth = 320, separator = 10))
    }

    @Test
    fun unknownBarWidthBindsEverything() {
        assertEquals(0..3, compactCandidateInitialWindow(listOf(10, 10, 10, 10), barWidth = 0, separator = 0))
    }

    @Test
    fun scrollWindowPadsOneItemOnEachSide() {
        assertEquals(2..7, compactCandidateFullWindow(firstVisible = 3, lastVisible = 6, itemCount = 16))
    }

    @Test
    fun scrollWindowClampsAtTheEnds() {
        assertEquals(0..2, compactCandidateFullWindow(firstVisible = 0, lastVisible = 1, itemCount = 16))
        assertEquals(13..15, compactCandidateFullWindow(firstVisible = 14, lastVisible = 15, itemCount = 16))
    }
}
