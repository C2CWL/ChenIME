/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parser for the published Handwritten [charset.json] (index ↔ GB2312 Level-1 character). */
object HandwritingCharset {
    const val EXPECTED_CLASSES = 3755

    @Serializable
    data class File(
        @SerialName("num_classes")
        val numClasses: Int,
        @SerialName("char_to_idx")
        val charToIdx: Map<String, Int> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): File = json.decodeFromString(File.serializer(), text)

    fun indexToChar(file: File): Array<String> {
        val chars = Array(file.numClasses) { "?" }
        for ((ch, index) in file.charToIdx) {
            if (index in chars.indices) chars[index] = ch
        }
        return chars
    }
}
