/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.annotation.RequiresApi
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import timber.log.Timber
import java.io.File

class ThemeFonts(private val theme: Theme) {
    private val style = theme.style

    private data class FileState(
        val path: String,
        val lastModified: Long,
        val size: Long,
    )

    private data class TypefaceKey(val files: List<FileState>)

    private val fontDir get() = File(DataManager.userDataDir, "fonts")

    private val fontFamilyCache =
        object : androidx.collection.LruCache<FileState, FontFamily>(16) {}

    private val typefaceCache =
        object : androidx.collection.LruCache<TypefaceKey, Typeface>(16 * 1024) {
            override fun sizeOf(key: TypefaceKey, value: Typeface): Int = key.files.sumOf {
                (it.size / 1024).toInt().coerceAtLeast(1)
            }
        }

    private val roleFonts by lazy {
        mapOf(
            "hanb" to style.hanbFont.toList(),
            "latin" to style.latinFont.toList(),
            "candidate" to style.candidateFont.toList(),
            "comment" to style.commentFont.toList(),
            "key" to style.keyFont.toList(),
            "label" to style.labelFont.toList(),
            "popup" to style.popupFont.toList(),
            "symbol" to style.symbolFont.toList(),
            "text" to style.textFont.toList(),
            "toolbar" to theme.toolBar.buttonFont.toList(),
        )
    }

    private val resolved = ResolvedFontCache<Typeface>()
    private val resolvedKeys = mutableMapOf<String, TypefaceKey>()

    fun refresh(): Boolean {
        val previousKeys = resolvedKeys.toMap()
        resolved.invalidate()
        resolvedKeys.clear()
        typefaceCache.evictAll()
        fontFamilyCache.evictAll()
        previousKeys.keys.forEach { getResolvedTypeface(it) }
        prepareForDrawing()
        return previousKeys.any { (role, key) -> resolvedKeys[role] != key }
    }

    // Resolve the draw-time roles before views receive the theme.
    fun prepareForDrawing() {
        key
        symbol
    }

    val hanb: Typeface
        get() = getResolvedTypeface("hanb")

    val latin: Typeface
        get() = getResolvedTypeface("latin")

    val candidate: Typeface
        get() = getResolvedTypeface("candidate")

    val comment: Typeface
        get() = getResolvedTypeface("comment")

    val key: Typeface
        get() = getResolvedTypeface("key")

    val label: Typeface
        get() = getResolvedTypeface("label")

    val popup: Typeface
        get() = getResolvedTypeface("popup")

    val symbol: Typeface
        get() = getResolvedTypeface("symbol")

    val text: Typeface
        get() = getResolvedTypeface("text")

    val toolbar: Typeface
        get() = getResolvedTypeface("toolbar")

    private fun getResolvedTypeface(role: String): Typeface = resolved.get(role) {
        getTypeface(role, roleFonts.getValue(role))
    }

    private fun makeFileStateList(names: List<String>): List<FileState> = names.asSequence()
        .map { File(fontDir, it) }
        .filter { it.isFile }
        .map { FileState(it.path, it.lastModified(), it.length()) }
        .toList()

    private fun resolveKey(fonts: List<String>): TypefaceKey {
        val existing = makeFileStateList(fonts)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TypefaceKey(if (existing.isEmpty()) emptyList() else makeFileStateList(style.latinFont.toList() + fonts + style.hanbFont.toList()))
        } else {
            TypefaceKey(existing.take(1))
        }
    }

    private fun getTypeface(role: String, fonts: List<String>): Typeface {
        val custom = AppPrefs.defaultInstance().customFont.getValue()
        val override = if (custom.isEmpty()) emptyList() else makeFileStateList(listOf(custom))
        val key = if (override.isNotEmpty()) TypefaceKey(override) else resolveKey(fonts)
        resolvedKeys[role] = key
        val typeface = typefaceCache[key]
        return if (typeface == null) {
            Timber.d("getTypeface: key=$key")
            val newTypeface = if (key.files.isEmpty()) {
                Typeface.DEFAULT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fontFamilies = key.files.map { getFontFamily(it) }
                Typeface.CustomFallbackBuilder(fontFamilies.first())
                    .apply {
                        for (i in 1 until fontFamilies.size) {
                            addCustomFallback(fontFamilies[i])
                        }
                        setSystemFallback("sans-serif")
                    }
                    .build()
            } else {
                Typeface.createFromFile(key.files.first().path)
            }
            typefaceCache.put(key, newTypeface)
            newTypeface
        } else {
            typeface
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamily(state: FileState): FontFamily {
        val family = fontFamilyCache[state]
        return if (family == null) {
            val newFamily = FontFamily.Builder(
                Font.Builder(File(state.path)).build(),
            ).build()
            fontFamilyCache.put(state, newFamily)
            newFamily
        } else {
            family
        }
    }
}
