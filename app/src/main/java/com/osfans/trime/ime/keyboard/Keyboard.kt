/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.KeyEvent
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.CornerRadiusOverrides
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.orAbsent
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import kotlin.math.pow

/** 從YAML中加載鍵盤配置，包含多個[按鍵][Key]。  */
@Suppress("ktlint:standard:property-naming")
class Keyboard(
    private val context: Context,
    private val theme: Theme,
    private val allowedWidth: Int,
    selfConfig: TextKeyboard? = null,
) {

    /** 按鍵默認水平間距  */
    internal val horizontalGap: Int =
        resolvePositive(selfConfig?.horizontalGap, theme.style.horizontalGap) { context.dp(it) }

    /** 默認鍵寬  */
    private val keyWidth: Int = (allowedWidth * theme.style.keyWidth / 100).toInt()

    /** 默認鍵高 (NOTE: 无需 dp 转换，会被 keyboardHeight 等比例缩放) */
    private val keyHeight: Int =
        resolvePositive(selfConfig?.height?.toInt(), theme.style.keyHeight)

    /** 默認行距  */
    internal val verticalGap: Int =
        resolvePositive(selfConfig?.verticalGap, theme.style.verticalGap) { context.dp(it) }

    /** 默認按鍵圓角半徑  */
    val roundCorner: Float =
        resolveNonNegative(selfConfig?.roundCorner, theme.style.roundCorner)

    /** 用户设置的按键圆角 (dp)，负数表示跟随主题 */
    internal val keyCornerOverride: Int = CornerRadiusOverrides.current().key

    /** 默認按鍵邊框寬度  */
    val keyBorder: Int =
        resolveNonNegative(selfConfig?.keyBorder, theme.style.keyBorder)

    /** 鍵盤的Shift鍵  */
    var mShiftKey: Key? = null
    var mCtrlKey: Key? = null
    var mAltKey: Key? = null
    var mMetaKey: Key? = null
    var mSymKey: Key? = null

    /**
     * Total height of the keyboard, including the padding and keys
     *
     * @return the total height of the keyboard
     */
    var height = 0
        private set

    /**
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the right
     * side.
     */
    var minWidth = 0
        private set

    /** List of keys in this keyboard  */
    private val mKeys = mutableListOf<Key>()
    val appearanceStateKeys = mutableListOf<Key>()
    var modifier = 0
        private set

    var firstPressedKeyIndex: Int = -1

    /** Keyboard default ascii mode  */
    val asciiMode = selfConfig?.asciiMode == 1
    val resetAsciiMode = selfConfig?.resetAsciiMode ?: true
    var lastAsciiMode: Boolean = asciiMode

    val landscapeKeyboard: String? = selfConfig?.landscapeKeyboard
    private val preferredSplitPercent by AppPrefs.defaultInstance().keyboard.splitSpacePercent
    private val landscapePercent =
        resolvePositive(selfConfig?.landscapeSplitPercent, preferredSplitPercent)

    // Variables for pre-computing nearest keys.
    private val labelTransform = selfConfig?.labelTransform ?: TextKeyboard.LabelTransform.NONE
    private var mCellWidth = 0
    private var mCellHeight = 0
    private var gridNeighbors: Array<IntArray?>? = null

    private val proximityThreshold: Int =
        (keyWidth * SEARCH_DISTANCE).pow(2).toInt() // Square it for comparison
    val isLock = selfConfig?.lock ?: false // 切換程序時記憶鍵盤
    val asciiKeyboard: String? = selfConfig?.asciiKeyboard // 英文鍵盤

    val keyboardHeight: Int =
        resolvePositive(
            selfConfig?.let {
                pickLandscape(it.keyboardHeight, it.keyboardHeightLand, context.isLandscapeMode())
            },
            pickLandscape(
                theme.style.keyboardHeight,
                theme.style.keyboardHeightLand,
                context.isLandscapeMode(),
            ),
        ) { context.dp(it) }.let { scaleKeyboardHeight(it, AppPrefs.defaultInstance().keyboard.heightPercent.getValue()) }

    private val expandKeypressArea: Boolean by AppPrefs.defaultInstance().keyboard.expandKeypressArea

    init {

        if (selfConfig != null) {

            val keys = selfConfig.keys
            val keyboardKeyWidth = selfConfig.width

            val maxColumns = if (selfConfig.columns == -1) Int.MAX_VALUE else selfConfig.columns

            val isSplit = context.isLandscapeMode() && landscapePercent > 0
            val splitRatio = if (isSplit) landscapePercent / 100f else 0f

            val reservedLeadingWeight =
                if (selfConfig.leftKeys.isNotEmpty() && selfConfig.leftKeyWidth > 0f) {
                    selfConfig.leftKeyWidth
                } else {
                    0f
                }

            fun widthWeight(textKey: TextKeyboard.TextKey): Float = if (textKey.width == 0f && textKey.click.orAbsent != null) keyboardKeyWidth else textKey.width

            val specs =
                keys.mapIndexed { index, textKey ->
                    KeyLayoutSpec(
                        index = index,
                        widthWeight = widthWeight(textKey),
                        rowHeightHint = textKey.height.toInt(),
                        rowSpan = textKey.rowSpan.coerceAtLeast(1),
                        clickable = textKey.click.orAbsent != null,
                    )
                }

            val placement =
                KeyboardGeometry.layout(
                    specs = specs,
                    allowedWidth = allowedWidth,
                    keyboardHeight = keyboardHeight,
                    defaultKeyHeight = keyHeight,
                    maxColumns = maxColumns,
                    reservedLeadingWeight = reservedLeadingWeight,
                    isSplit = isSplit,
                    splitRatio = splitRatio,
                    expandKeypressArea = expandKeypressArea,
                )

            fun applyOffsets(
                key: Key,
                textKey: TextKeyboard.TextKey,
            ) {
                key.keyTextOffsetX = resolveOffset(textKey.keyTextOffsetX, selfConfig.keyTextOffsetX, theme.style.keyTextOffsetX)
                key.keyTextOffsetY = textKey.keyTextOffsetY ?: resolveOffset(0f, selfConfig.keyTextOffsetY, theme.style.keyTextOffsetY)
                key.keySymbolOffsetX = resolveOffset(textKey.keySymbolOffsetX, selfConfig.keySymbolOffsetX, theme.style.keySymbolOffsetX)
                key.keySymbolOffsetY = resolveOffset(textKey.keySymbolOffsetY, selfConfig.keySymbolOffsetY, theme.style.keySymbolOffsetY)
                key.keyHintOffsetX = resolveOffset(textKey.keyHintOffsetX, selfConfig.keyHintOffsetX, theme.style.keyHintOffsetX)
                key.keyHintOffsetY = resolveOffset(textKey.keyHintOffsetY, selfConfig.keyHintOffsetY, theme.style.keyHintOffsetY)
                key.keyPressOffsetX = resolveOffset(textKey.keyPressOffsetX, selfConfig.keyPressOffsetX, theme.style.keyPressOffsetX)
                key.keyPressOffsetY = resolveOffset(textKey.keyPressOffsetY, selfConfig.keyPressOffsetY, theme.style.keyPressOffsetY)
            }

            minWidth = 0

            // Stacked left column occupies the reserved leading band and shares the keyboard height.
            if (reservedLeadingWeight > 0f && selfConfig.leftKeys.isNotEmpty()) {
                val leftWidth = (reservedLeadingWeight * allowedWidth / KeyboardGeometry.MAX_TOTAL_WEIGHT).toInt()
                val scaled = KeyboardGeometry.scaleHeights(
                    List(selfConfig.leftKeys.size) { keyHeight },
                    keyboardHeight,
                )
                var yPos = 0
                selfConfig.leftKeys.forEachIndexed { i, textKey ->
                    if (textKey.click.orAbsent == null) {
                        yPos += scaled[i]
                        return@forEachIndexed
                    }
                    val key = Key(this, theme, textKey)
                    applyOffsets(key, textKey)
                    key.x = 0
                    key.y = yPos
                    key.width = leftWidth
                    key.height = scaled[i]
                    key.row = i
                    key.column = 0
                    key.rowSpan = 1
                    mKeys.add(key)
                    yPos += key.height
                    if (key.x + key.width > minWidth) {
                        minWidth = key.x + key.width
                    }
                }
            }

            val placedByIndex = placement.keys.associateBy { it.index }
            for ((index, textKey) in keys.withIndex()) {
                val placed = placedByIndex[index] ?: continue
                val key = Key(this, theme, textKey)
                applyOffsets(key, textKey)
                key.x = placed.x
                key.y = placed.y
                key.width = placed.width
                key.height = placed.height
                key.row = placed.row
                key.column = placed.column
                key.rowSpan = placed.rowSpan
                mKeys.add(key)
                if (key.x + key.width > minWidth) {
                    minWidth = key.x + key.width
                }
            }

            if (expandKeypressArea && placement.spacers.isNotEmpty()) {
                for (spacer in placement.spacers) {
                    val rowKeys = mKeys.filter { it.row == spacer.row }
                    val (leftKeys, rightKeys) = rowKeys.partition { it.x + it.width <= spacer.x }
                    val leftKey = leftKeys.maxByOrNull { it.x }
                    val rightKey = rightKeys.minByOrNull { it.x }
                    when {
                        leftKey != null && rightKey != null -> {
                            leftKey.extraWidthRight += spacer.width / 2
                            rightKey.extraWidthLeft += spacer.width - spacer.width / 2
                        }

                        leftKey != null -> leftKey.extraWidthRight += spacer.width

                        rightKey != null -> rightKey.extraWidthLeft += spacer.width
                    }
                }
            }

            height = placement.height

            mKeys.forEachIndexed { index, key ->
                key.index = index
                if (key.x <= 0) key.edgeFlags = key.edgeFlags or EDGE_LEFT
                if (key.y <= 0) key.edgeFlags = key.edgeFlags or EDGE_TOP
                if (key.y + key.height >= height) key.edgeFlags = key.edgeFlags or EDGE_BOTTOM
                if (allowedWidth - (key.x + key.width) <= allowedWidth / 100) {
                    key.edgeFlags = key.edgeFlags or EDGE_RIGHT
                }
            }
        }
    }

    fun setModifierKey(
        c: Int,
        key: Key?,
    ) {
        when (c) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                mShiftKey = key
            }

            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                mCtrlKey = key
            }

            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                mMetaKey = key
            }

            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                mAltKey = key
            }

            KeyEvent.KEYCODE_SYM -> {
                mSymKey = key
            }
        }
    }

    val keys: List<Key>
        get() = mKeys

    private fun setModifier(
        mask: Int,
        value: Boolean,
    ): Boolean {
        if (modifier.hasFlag(mask) == value) return false
        modifier = if (value) modifier or mask else modifier and mask.inv()
        return true
    }

    val isShifted: Boolean
        get() = modifier.hasFlag(KeyEvent.META_SHIFT_ON) || mShiftKey?.isOn == true

    val isOnlyShiftOn: Boolean
        get() =
            isShifted &&
                !modifier.hasFlag(KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON)

    /**
     * 设置Shift键状态（用于自动大写）
     *
     * @param on 是否锁定Shift键
     * @param shifted 是否按下Shift键
     * @return Shift键状态是否改变
     */
    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        mShiftKey?.setOn(on)
        return setModifier(KeyEvent.META_SHIFT_ON, shifted)
    }

    /**
     * 设置修饰键的状态
     *
     * @param on 是否锁定修饰键
     * @param keycode 修饰键的 KeyEvent 掩码
     * @return 修饰键状态是否改变
     */
    fun clickModifierKey(
        on: Boolean,
        keycode: Int,
    ): Boolean {
        if (keycode == KeyEvent.META_SHIFT_ON) {
            val state = ShiftState(modifier.hasFlag(keycode), mShiftKey?.isOn == true).press(on)
            // Key.setOn toggles an existing lock; apply the desired state explicitly.
            mShiftKey?.let { if (it.isOn != state.locked) it.setOn(state.locked) }
            return setModifier(keycode, state.shifted)
        }
        val keyDown = !modifier.hasFlag(keycode)
        val modifierKey =
            when (keycode) {
                KeyEvent.META_SHIFT_ON -> mShiftKey
                KeyEvent.META_ALT_ON -> mAltKey
                KeyEvent.META_CTRL_ON -> mCtrlKey
                KeyEvent.META_META_ON -> mMetaKey
                KeyEvent.KEYCODE_SYM -> mSymKey
                else -> null
            }
        val keepOn = modifierKey?.setOn(on) ?: on
        return if (on) setModifier(keycode, keepOn) else setModifier(keycode, keyDown)
    }

    fun resetShift() {
        mShiftKey?.setOn(false)
        setModifier(KeyEvent.META_SHIFT_ON, false)
    }

    fun refreshModifier(): Boolean {
        // 这里改为了一次性重置全部修饰键状态并返回TRUE刷新UI，可能有bug
        var result = false
        if (mShiftKey != null) {
            val state = ShiftState(modifier.hasFlag(KeyEvent.META_SHIFT_ON), mShiftKey!!.isOn).afterKey()
            result = setModifier(KeyEvent.META_SHIFT_ON, state.shifted)
        }
        if (mAltKey != null && !mAltKey!!.isOn) result = result || setModifier(KeyEvent.META_ALT_ON, false)
        if (mCtrlKey != null && !mCtrlKey!!.isOn) result = result || setModifier(KeyEvent.META_CTRL_ON, false)
        if (mMetaKey != null && !mMetaKey!!.isOn) result = result || setModifier(KeyEvent.META_META_ON, false)
        if (mSymKey != null && !mSymKey!!.isOn) result = result || setModifier(KeyEvent.KEYCODE_SYM, false)
        return result
    }

    private fun computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (minWidth + GRID_WIDTH - 1) / GRID_WIDTH
        mCellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
        gridNeighbors = arrayOfNulls(GRID_SIZE)
        val indices = IntArray(mKeys.size)
        val gridWidth = GRID_WIDTH * mCellWidth
        val gridHeight = GRID_HEIGHT * mCellHeight
        var x = 0
        while (x < gridWidth) {
            var y = 0
            while (y < gridHeight) {
                var count = 0
                for (i in mKeys.indices) {
                    val key = mKeys[i]
                    if (key.squaredDistanceFrom(x, y) < proximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y) < proximityThreshold ||
                        (
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < proximityThreshold
                            ) ||
                        key.squaredDistanceFrom(x, y + mCellHeight - 1) < proximityThreshold ||
                        key.isInside(x, y) ||
                        key.isInside(x + mCellWidth - 1, y) ||
                        key.isInside(x + mCellWidth - 1, y + mCellHeight - 1) ||
                        key.isInside(x, y + mCellHeight - 1)
                    ) {
                        indices[count++] = i
                    }
                }
                val cell = IntArray(count)
                System.arraycopy(indices, 0, cell, 0, count)
                gridNeighbors?.set(y / mCellHeight * GRID_WIDTH + x / mCellWidth, cell)
                y += mCellHeight
            }
            x += mCellWidth
        }
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    fun getNearestKeys(
        x: Int,
        y: Int,
    ): IntArray? {
        if (gridNeighbors == null) computeNearestNeighbors()
        if (x in 0 until minWidth && y in 0 until height) {
            val index = y / mCellHeight * GRID_WIDTH + x / mCellWidth
            if (index < GRID_SIZE) {
                return gridNeighbors!![index]
            }
        }
        return IntArray(0)
    }

    val isLabelUppercase: Boolean
        get() = labelTransform == TextKeyboard.LabelTransform.UPPERCASE

    companion object {
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08
        private const val GRID_WIDTH = 10
        private const val GRID_HEIGHT = 5
        private const val GRID_SIZE = GRID_WIDTH * GRID_HEIGHT

        /** Number of key widths from current touch point to search for nearest keys.  */
        const val SEARCH_DISTANCE = 1.4f
    }
}
