package com.osfans.trime.data.theme

object CustomFontPolicy {
    const val MAX_BYTES = 50L * 1024 * 1024

    fun validSize(size: Long): Boolean = size in 1..MAX_BYTES

    fun extension(name: String): String = if (name.substringAfterLast('.', "").lowercase() == "otf") "otf" else "ttf"
}
