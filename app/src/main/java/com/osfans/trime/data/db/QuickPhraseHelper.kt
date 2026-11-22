/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.Context
import android.content.SharedPreferences
import com.osfans.trime.util.appContext
import org.json.JSONArray

/**
 * 常用语存储：用 SharedPreferences 保存一个 JSON 字符串数组。
 *
 * 常用语数量少、结构简单，无需引入 Room 表及数据库迁移。
 * 保存后通知监听者，常用于编辑页返回后刷新窗口列表。
 */
object QuickPhraseHelper {
    private const val PREF_NAME = "quick_phrases"
    private const val KEY_ITEMS = "items"

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val changeListeners = mutableListOf<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        if (listener !in changeListeners) changeListeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    fun list(): List<String> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        changeListeners.toList().forEach { it() }
    }

    fun add(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        save(list() + value)
    }

    fun update(
        index: Int,
        text: String,
    ) {
        val value = text.trim()
        if (value.isEmpty()) return
        val items = list().toMutableList()
        if (index !in items.indices) return
        items[index] = value
        save(items)
    }

    fun remove(index: Int) {
        val items = list().toMutableList()
        if (index !in items.indices) return
        items.removeAt(index)
        save(items)
    }
}
