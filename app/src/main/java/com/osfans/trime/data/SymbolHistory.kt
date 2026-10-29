// SPDX-FileCopyrightText: 2015 - 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data

import com.osfans.trime.TrimeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Main-thread snapshots; file work is serialized on IO, including the initial load. */
class SymbolHistory(private val capacity: Int) {
    companion object {
        const val FILE_NAME = "symbol_history"
        val shared by lazy { SymbolHistory(180) }
    }

    private val scope = TrimeApplication.getInstance().coroutineScope
    private val file = TrimeApplication.getInstance().directBootAwareContext.filesDir.resolve(FILE_NAME)
    private val entries = linkedSetOf<String>()
    private val fileMutex = Mutex()
    private var loaded = false
    private var saveJob: Job? = null
    private val initial = scope.async(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching { if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList() }
                .onFailure { Timber.w(it, "Cannot read symbol history") }.getOrDefault(emptyList())
        }
    }

    suspend fun load() {
        if (loaded) return
        val saved = initial.await()
        if (loaded) return
        val recent = entries.toList()
        entries.clear()
        (saved + recent).forEach { insert(it) }
        loaded = true
    }

    fun insert(text: String) {
        entries.remove(text)
        entries.add(text)
        while (entries.size > capacity) entries.remove(entries.first())
    }

    fun save() {
        saveJob?.cancel()
        saveJob = scope.launch {
            load()
            delay(300) // Debounce disk writes, never frames or input events.
            val snapshot = entries.joinToString("\n")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    runCatching { file.writeText(snapshot) }
                        .onFailure { Timber.w(it, "Cannot save symbol history") }
                }
            }
        }
    }

    fun toOrderedList() = entries.toList().asReversed()
}
