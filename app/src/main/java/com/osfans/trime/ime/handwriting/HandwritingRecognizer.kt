/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import android.content.res.AssetManager
import com.shiyu.handwritten.runtime.HCCRRecognizer
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device wrapper around the official Ismantic/Handwritten HCCR runtime.
 * Models live in the APK assets; nothing is downloaded or sent to a server.
 */
class HandwritingRecognizer {
    data class Candidate(val text: String, val probability: Float)

    @Volatile
    var loadError: String? = null
        private set

    private var native: HCCRRecognizer? = null
    private val started = AtomicBoolean(false)

    val isReady: Boolean get() = native != null

    fun ensureLoaded(assets: AssetManager): Boolean {
        if (native != null) return true
        if (loadError != null && started.get()) return false
        if (!started.compareAndSet(false, true)) {
            return native != null
        }
        return runCatching {
            HCCRRecognizer.loadNativeLibrary(LIBRARY)
            native = HCCRRecognizer(assets, PARAM, BIN, CHARSET)
        }.onFailure {
            loadError = it.message
            Timber.e(it, "Failed to load on-device handwriting model")
            started.set(false)
        }.isSuccess
    }

    fun predict(gray: ByteArray, width: Int, height: Int, k: Int = DEFAULT_K): List<Candidate> {
        val recognizer = native ?: return emptyList()
        if (width <= 0 || height <= 0 || gray.size != width * height) return emptyList()
        val input = FloatArray(INPUT_SIZE)
        if (!recognizer.preprocess(gray, width, height, input)) return emptyList()
        return recognizer.predict(input, k).map { Candidate(it.text, it.probability) }
    }

    fun close() {
        native?.close()
        native = null
        started.set(false)
        loadError = null
    }

    companion object {
        const val LIBRARY = "hccr_jni"
        const val ASSET_DIR = "handwritten"
        const val PARAM = "$ASSET_DIR/model.ncnn.param"
        const val BIN = "$ASSET_DIR/model.ncnn.bin"
        const val CHARSET = "$ASSET_DIR/charset.json"
        const val INPUT_SIZE = 64 * 64
        const val DEFAULT_K = 8
        const val COVERAGE = "GB2312 Level-1 (3755 characters)"
    }
}
