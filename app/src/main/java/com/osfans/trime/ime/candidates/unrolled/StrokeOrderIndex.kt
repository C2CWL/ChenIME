// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.candidates.unrolled

import java.io.Reader

/** Simplified single characters only; prefixes follow the normal writing order. */
internal class StrokeOrderIndex private constructor(private val orders: Map<String, String>) {
    fun matches(text: String, prefix: String): Boolean = orders[text]?.startsWith(prefix) == true

    companion object {
        fun read(reader: Reader): StrokeOrderIndex {
            val orders = mutableMapOf<String, String>()
            reader.buffered().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
                    val fields = line.split('\t')
                    require(fields.size == 2 && isSingleCharacter(fields[0]) && fields[1].isNotEmpty() && fields[1].all { it in '1'..'5' }) {
                        "Invalid stroke dictionary entry"
                    }
                    require(orders.put(fields[0], fields[1]) == null) { "Duplicate stroke dictionary entry" }
                }
            }
            return StrokeOrderIndex(orders)
        }
    }
}
