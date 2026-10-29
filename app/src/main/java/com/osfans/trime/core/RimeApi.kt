/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import kotlinx.coroutines.flow.SharedFlow

interface RimeApi {
    val messageFlow: SharedFlow<RimeMessage<*>>

    val isReady: Boolean

    val schemaCached: RimeSchema

    val statusCached: StatusProto

    val compositionCached: CompositionProto

    val hasMenu: Boolean

    val paging: Boolean

    suspend fun isEmpty(): Boolean

    suspend fun deploy()

    suspend fun updateConfig()

    suspend fun syncUserData(): Boolean

    /** Flush and close native databases while a backup operation accesses user files. */
    suspend fun <T> withUserDataClosed(block: () -> T): T

    suspend fun processKey(
        value: Int,
        modifiers: UInt = 0u,
        isVirtual: Boolean = true,
    ): Boolean

    suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
        isVirtual: Boolean = true,
    ): Boolean

    suspend fun simulateKeySequence(
        sequence: String,
    ): Boolean

    /** Insert literal text into raw input, bypassing key processors and candidate selection. */
    suspend fun insertCompositionText(text: String): Boolean

    suspend fun selectCandidate(idx: Int, global: Boolean): Boolean

    suspend fun deleteCandidate(idx: Int, global: Boolean): Boolean

    suspend fun changeCandidatePage(backward: Boolean): Boolean

    suspend fun moveCursorPos(position: Int)

    suspend fun availableSchemata(): Array<SchemaItem>

    suspend fun enabledSchemata(): Array<SchemaItem>

    suspend fun setEnabledSchemata(schemaIds: Array<String>): Boolean

    suspend fun selectedSchemata(): Array<SchemaItem>

    suspend fun selectedSchemaId(): String

    suspend fun selectSchema(schemaId: String): Boolean

    suspend fun currentSchema(): RimeSchema

    suspend fun commitComposition(): Boolean

    suspend fun clearComposition()

    suspend fun getRawInput(): String

    suspend fun setRuntimeOption(
        option: String,
        value: Boolean,
    )

    /** Temporary keyboard mode changes do not announce a language switch. */
    suspend fun setKeyboardAsciiMode(value: Boolean)

    suspend fun getRuntimeOption(option: String): Boolean

    /** Read a label seed in one Rime dispatcher operation, away from drawing. */
    suspend fun getRuntimeOptions(options: Set<String>): Map<String, Boolean> = options.associateWith { getRuntimeOption(it) }

    suspend fun setNullInputType(value: Boolean)

    suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
    ): Array<CandidateProto>

    /** Last menu segment: [start, end, candidateEnd0, ...], in raw input bytes. */
    suspend fun getCandidateEnds(startIndex: Int, limit: Int): IntArray

    suspend fun setCandidatePagingMode(enabled: Boolean)
}
