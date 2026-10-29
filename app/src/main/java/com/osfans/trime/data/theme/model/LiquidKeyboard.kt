/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable(with = LiquidKeyboardSerializer::class)
data class LiquidKeyboard(
    val singleWidth: Int = 0,
    val keyHeight: Int = 0,
    val marginX: Float = 0f,
    val fixedKeyBar: KeyBar = KeyBar(),
    val keyboards: List<Keyboard> = emptyList(),
) {

    @Serializable
    data class KeyBar(
        val keys: List<String> = emptyList(),
        val position: Position = Position.BOTTOM,
    ) {
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
        }
    }

    data class Keyboard(
        val id: String,
        val type: DataType,
        val name: String,
        val keys: List<KeyItem>,
    )

    data class KeyItem(
        val text: String,
        val altText: String,
    ) {
        constructor(text: String) : this(text, text)
    }

    enum class DataType {
        SINGLE,
        SYMBOL,
        TABS,
        HISTORY,
    }

    data class Tag(val label: String = "", val type: DataType)

    // Prepared with the theme on IO; opening a resident panel only selects a list.
    @Transient
    private val tags = keyboards.map { Tag(it.name, it.type) }

    @Transient
    private val tabKeys = tags.filter { it.type != DataType.TABS }.map { KeyItem(it.label) }

    fun getTagList() = tags

    fun getDataByIndex(index: Int): List<KeyItem> = if (keyboards[index].type == DataType.TABS) tabKeys else keyboards[index].keys
}
