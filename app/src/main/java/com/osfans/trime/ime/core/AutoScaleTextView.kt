/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.core.graphics.withSave
import com.osfans.trime.ime.keyboard.DistinctLetterGlyph
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("AppCompatCustomView")
class AutoScaleTextView
@JvmOverloads
constructor(
    context: Context?,
    attributeSet: AttributeSet? = null,
) : TextView(context, attributeSet) {
    enum class Mode {
        /**
         * do not scale or ellipse text, overflow when cannot fit width
         */
        None,

        /**
         * only scale in X axis, makes text looks "condensed" or "slim"
         */
        Horizontal,

        /**
         * scale both in X and Y axis, align center vertically
         */
        Proportional,
    }

    var distinctLetterLabels = false

    var scaleMode = Mode.None

    private var needsMeasureText = true
    private val fontMetrics = Paint.FontMetrics()
    private val textBounds = Rect()

    private var needsCalculateTransform = true
    private var translateY = 0.0f
    private var translateX = 0.0f
    private var textScaleX = 1.0f
    private var textScaleY = 1.0f

    override fun setText(
        charSequence: CharSequence?,
        bufferType: BufferType,
    ) {
        if (charSequence != null && text.contentEquals(charSequence)) return
        super.setText(charSequence, bufferType)
        // TextView also calls this from its constructor, before our paint caches exist.
        needsMeasureText = true
        needsCalculateTransform = true
        requestLayout()
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // Custom onDraw never uses the framework Layout. Building it on every
        // Flexbox remasure was a large part of the 20–35ms candidate_layout cost.
        // Only build it when a screen reader may query the TextView layout.
        if (needsFrameworkLayout()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val width = measureTextBounds().width() + paddingLeft + paddingRight
        val height = ceil(fontMetrics.bottom - fontMetrics.top + paddingTop + paddingBottom).toInt()
        val maxHeight = if (maxHeight >= 0) maxHeight else Int.MAX_VALUE
        val maxWidth = if (maxWidth >= 0) maxWidth else Int.MAX_VALUE
        setMeasuredDimension(
            measure(widthMode, widthSize, min(max(width, minimumWidth), maxWidth)),
            measure(heightMode, heightSize, min(max(height, minimumHeight), maxHeight)),
        )
    }

    private fun needsFrameworkLayout(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am?.isEnabled == true && am?.isTouchExplorationEnabled == true
    }

    private fun measure(
        specMode: Int,
        specSize: Int,
        calculatedSize: Int,
    ): Int = when (specMode) {
        MeasureSpec.EXACTLY -> specSize
        MeasureSpec.AT_MOST -> min(calculatedSize, specSize)
        else -> calculatedSize
    }

    private fun measureTextBounds(): Rect {
        if (needsMeasureText) {
            val paint = paint
            paint.getFontMetrics(fontMetrics)
            val codePointCount = Character.codePointCount(text, 0, text.length)
            if (codePointCount == 1) {
                // use actual text bounds when there is only one "character",
                // eg. full-width punctuation
                CharArray(text.length).also {
                    TextUtils.getChars(text, 0, text.length, it, 0)
                    paint.getTextBounds(it, 0, text.length, textBounds)
                }
            } else {
                textBounds.set(
                    // left =
                    0,
                    // top =
                    floor(fontMetrics.top).toInt(),
                    // right =
                    ceil(paint.measureText(text, 0, text.length)).toInt(),
                    // bottom =
                    ceil(fontMetrics.bottom).toInt(),
                )
            }
            if (distinctLetterLabels && DistinctLetterGlyph.enabled(text.toString())) {
                textBounds.set(0, -ceil(paint.textSize * 0.76f).toInt(), ceil(paint.textSize * 0.60f).toInt(), 0)
            }
            needsMeasureText = false
        }
        return textBounds
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        needsCalculateTransform = true
        // Text glyphs did not change; only the scale/translate fit needs a recalc.
        // Flexbox remasures often, and dropping this cache was a large part of
        // the compact-bar's 20–60ms candidate_layout cost.
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (needsCalculateTransform) {
            calculateTransform(right - left, bottom - top)
            needsCalculateTransform = false
        }
    }

    private fun calculateTransform(
        viewWidth: Int,
        viewHeight: Int,
    ) {
        val contentWidth = viewWidth - paddingLeft - paddingRight
        val contentHeight = viewHeight - paddingTop - paddingBottom
        measureTextBounds()
        val textWidth = displayTextWidth(contentWidth)
        val textHeight = textBounds.height()

        // Prevent division by zero: if text dimensions are zero
        if (textWidth == 0 || textHeight == 0) {
            textScaleX = 1.0f
            textScaleY = 1.0f
            translateX = 0f
            translateY = 0f
            return
        }

        @SuppressLint("RtlHardcoded")
        val shouldAlignLeft = gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT

        if (textWidth > contentWidth || textHeight > contentHeight) {
            when (scaleMode) {
                Mode.None -> {
                    textScaleX = 1.0f
                    textScaleY = 1.0f
                    translateX = calculateTranslateX(contentWidth, textWidth, 1.0f, shouldAlignLeft)
                }

                Mode.Horizontal -> {
                    textScaleX = contentWidth.toFloat() / textWidth.toFloat()
                    textScaleY = 1.0f
                    translateX = calculateTranslateX(contentWidth, textWidth, textScaleX, shouldAlignLeft)
                }

                Mode.Proportional -> {
                    val textXScale = contentWidth.toFloat() / textWidth.toFloat()
                    val textYScale = contentHeight.toFloat() / textHeight.toFloat()
                    val textScale = min(textXScale, textYScale)
                    textScaleX = textScale
                    textScaleY = textScale
                    translateX = calculateTranslateX(contentWidth, textWidth, textScaleX, shouldAlignLeft)
                }
            }
        } else {
            translateX = calculateTranslateX(contentWidth, textWidth, 1.0f, shouldAlignLeft)
            textScaleX = 1.0f
            textScaleY = 1.0f
        }
        val fontHeight = (fontMetrics.descent - fontMetrics.ascent) * textScaleY
        val fontOffsetY = fontMetrics.ascent * textScaleY
        translateY = (contentHeight.toFloat() - fontHeight) / 2.0f - fontOffsetY + paddingTop
    }

    /** Width used to place and clip text. When [Mode.None] and ellipsize is set, overflow is trimmed. */
    private fun displayTextWidth(contentWidth: Int): Int {
        val full = textBounds.width()
        if (scaleMode != Mode.None || ellipsize == null || contentWidth <= 0 || full <= contentWidth) return full
        return contentWidth
    }

    private fun textToDraw(contentWidth: Int): CharSequence {
        val raw = text ?: ""
        if (scaleMode != Mode.None || ellipsize == null || contentWidth <= 0) return raw
        if (textBounds.width() <= contentWidth) return raw
        return TextUtils.ellipsize(raw, paint, contentWidth.toFloat(), ellipsize)
    }

    private fun calculateTranslateX(
        contentWidth: Int,
        textWidth: Int,
        scaleX: Float,
        shouldAlignLeft: Boolean,
    ): Float {
        val scaledTextWidth = textWidth * scaleX
        val startX = if (shouldAlignLeft) {
            paddingLeft.toFloat()
        } else {
            paddingLeft.toFloat() + (contentWidth - scaledTextWidth) / 2.0f
        }
        return (startX - textBounds.left) / scaleX
    }

    override fun onDraw(canvas: Canvas) {
        if (needsCalculateTransform) {
            calculateTransform(width, height)
            needsCalculateTransform = false
        }
        val paint = paint
        paint.color = currentTextColor
        canvas.withSave {
            translate(scrollX.toFloat(), scrollY.toFloat())
            scale(textScaleX, textScaleY, 0f, translateY)
            translate(translateX, translateY)
            val label = textToDraw(width - paddingLeft - paddingRight)
            if (distinctLetterLabels && DistinctLetterGlyph.enabled(label.toString())) {
                DistinctLetterGlyph.draw(this, label.toString(), 0f, 0f, paint)
            } else {
                drawText(label, 0, label.length, 0f, 0f, paint)
            }
        }
    }

    override fun getTextScaleX(): Float = textScaleX

    override fun getBaseline(): Int {
        val baseline = -fontMetrics.top * textScaleY
        // Prevent NaN or Infinity from causing crash
        return if (baseline.isNaN() || baseline.isInfinite()) {
            super.getBaseline()
        } else {
            baseline.roundToInt()
        }
    }
}
