package com.osfans.trime.data.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomFontPolicyTest {
    @Test
    fun sizeAndExtension() {
        assertFalse(CustomFontPolicy.validSize(0))
        assertFalse(CustomFontPolicy.validSize(CustomFontPolicy.MAX_BYTES + 1))
        assertTrue(CustomFontPolicy.validSize(CustomFontPolicy.MAX_BYTES))
        assertEquals("otf", CustomFontPolicy.extension("Font.OTF"))
        assertEquals("ttf", CustomFontPolicy.extension("../../unknown"))
    }
}
