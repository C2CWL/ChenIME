// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import kotlin.math.ceil

internal fun candidateSpan(width: Float, cellWidth: Float, columns: Int = 6): Int = if (cellWidth <= 0f) 1 else ceil(width / cellWidth).toInt().coerceIn(1, columns)

internal fun isSingleCharacter(text: String): Boolean = text.codePointCount(0, text.length) == 1

/** Only expose boundaries supplied by the engine; never guess segmentation of raw input. */
internal fun preeditSyllables(preedit: String?): List<String> = preedit.orEmpty().split(Regex("[\\s'’]+"))
    .filter { it.isNotEmpty() && it.all { c -> c in 'a'..'z' || c in 'A'..'Z' || c == 'ü' || c in 'ā'..'ǜ' } }
    .distinct()
