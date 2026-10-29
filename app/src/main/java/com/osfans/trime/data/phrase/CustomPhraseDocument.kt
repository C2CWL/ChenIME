// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.phrase

/** Lossless TSV editing: untouched comments, metadata, malformed lines and endings survive. */
class CustomPhraseDocument(private var source: String) {
    data class Entry(val text: String, val code: String, val weight: Int = 1) {
        fun serialize(): String {
            require(text.isNotBlank() && !text.startsWith('#') && text.none { it in "\t\r\n" })
            require(code.isNotBlank() && code.none { it in "\t\r\n" } && weight >= 0)
            return "$text\t$code\t$weight"
        }
    }

    data class Row(val line: Int, val entry: Entry)

    fun entries(): List<Row> = source.split('\n').mapIndexedNotNull { index, line ->
        val fields = line.removeSuffix("\r").split('\t')
        if (line.startsWith('#') || fields.size !in 2..3) return@mapIndexedNotNull null
        val entry = Entry(fields[0], fields[1], if (fields.size == 3) fields[2].toIntOrNull() ?: return@mapIndexedNotNull null else 1)
        if (runCatching { entry.serialize() }.isFailure) null else Row(index, entry)
    }

    fun replace(line: Int?, entry: Entry?) {
        val serialized = entry?.serialize()
        if (line == null) {
            requireNotNull(serialized)
            if (source.isNotEmpty() && !source.endsWith('\n')) source += "\n"
            source += "$serialized\n"
        } else {
            val lines = source.split('\n').toMutableList()
            require(line in lines.indices)
            if (serialized == null) lines.removeAt(line) else lines[line] = serialized + if (lines[line].endsWith('\r')) "\r" else ""
            source = lines.joinToString("\n")
        }
    }

    override fun toString(): String = source
}
