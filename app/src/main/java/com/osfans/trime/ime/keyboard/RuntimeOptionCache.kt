// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Main-thread label state. Reads never query Rime, including for unknown options. */
class RuntimeOptionCache {
    private val values = mutableMapOf<String, Boolean>()
    private val revisions = mutableMapOf<String, Long>()
    private var revision = 0L

    operator fun get(option: String): Boolean = values[option] ?: false

    fun update(option: String, value: Boolean): Boolean {
        val changed = this[option] != value
        values[option] = value
        revisions[option] = ++revision
        return changed
    }

    fun beginSeed(): Long = ++revision

    /** Notifications received while the asynchronous snapshot was read take precedence. */
    fun finishSeed(startRevision: Long, snapshot: Map<String, Boolean>) {
        snapshot.forEach { (option, value) ->
            if ((revisions[option] ?: 0L) <= startRevision) {
                values[option] = value
                revisions[option] = startRevision
            }
        }
    }
}
