/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

@Serializable
data class FrostUpdateState(
    val version: String = "",
    val zipSha256: String = "",
    val appliedAt: Long = 0L,
    val paths: List<String> = emptyList(),
)

object FrostUpdateStateStore {
    private const val STATE_FILE_NAME = "frost_update_state.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun stateFile(context: Context): File = File(context.filesDir, STATE_FILE_NAME)

    fun read(context: Context): FrostUpdateState? {
        val file = stateFile(context)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString<FrostUpdateState>(file.readText())
        }.onFailure {
            Timber.w(it, "Failed to read frost update state")
        }.getOrNull()
    }

    fun write(
        context: Context,
        state: FrostUpdateState,
    ) {
        val file = stateFile(context)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(FrostUpdateState.serializer(), state))
    }
}
