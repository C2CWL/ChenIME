package com.osfans.trime.ime.keyboard

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DistinctLetterPolicyTest {
    @Test
    fun exactLabelsOnlyWithoutCustomFont() {
        for (label in listOf("I", "l")) {
            assertTrue(useDistinctLetter(label, false))
            assertFalse(useDistinctLetter(label, true))
        }
        for (label in listOf("i", "L", "Il", "", " l")) assertFalse(useDistinctLetter(label, false))
    }
}
