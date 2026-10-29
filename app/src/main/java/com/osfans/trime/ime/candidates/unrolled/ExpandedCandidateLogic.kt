// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import kotlin.math.ceil

internal fun candidateSpan(width: Float, cellWidth: Float, columns: Int = 6): Int = if (cellWidth <= 0f) 1 else ceil(width / cellWidth).toInt().coerceIn(1, columns)

internal fun isSingleCharacter(text: String): Boolean = text.codePointCount(0, text.length) == 1

/** Only expose boundaries supplied by the engine; retain repeated syllables. */
internal fun preeditSyllables(preedit: String?): List<String> = preedit.orEmpty()
    .split(Regex("[^a-zA-ZüÜāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+"))
    .filter { it.isNotEmpty() }

/** Positions are raw input byte offsets; pinyin raw input is ASCII. */
internal fun syllableInputEnd(raw: String, start: Int, syllables: List<String>, index: Int, candidateEnds: List<Int>): Int? {
    if (start !in 0..raw.length || index !in syllables.indices) return null
    fun normalized(c: Char) = if (c.lowercaseChar() == 'ü') 'v' else c.lowercaseChar()
    var position = start
    var aligned = true
    for (syllable in syllables.take(index + 1)) {
        for (letter in syllable) {
            while (position < raw.length && (raw[position] == '\'' || raw[position] == ' ')) position++
            if (position >= raw.length || normalized(raw[position]) != normalized(letter)) {
                aligned = false
                break
            }
            position++
        }
        if (!aligned) break
    }
    if (aligned) return position
    val ends = candidateEnds.filter { it > start }.distinct().sorted()
    return ends.getOrNull(index.coerceAtMost(ends.lastIndex).coerceAtLeast(0))
}
