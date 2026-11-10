/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import splitties.dimensions.dp
import kotlin.math.max

/**
 * Visible ink is themed (light stroke on a dark well, or dark stroke on a light well).
 * The recognizer always receives a white-background, black-ink grayscale bitmap.
 */
class HandwritingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    val ink = HandwritingInk()
    var onStrokeFinished: (() -> Unit)? = null

    var wellColor: Int = Color.rgb(36, 36, 36)
        set(value) {
            field = value
            invalidate()
        }
    var inkColor: Int = Color.rgb(236, 236, 240)
        set(value) {
            field = value
            invalidate()
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(8f)
    }
    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wellRect = RectF()
    private val corner = dp(18f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = dp(2f)
        wellRect.set(inset, inset, width - inset, height - inset)
        wellPaint.color = wellColor
        canvas.drawRoundRect(wellRect, corner, corner, wellPaint)
        strokePaint.color = inkColor
        drawInk(canvas, strokePaint)
    }

    private fun drawInk(canvas: Canvas, paint: Paint) {
        val path = Path()
        fun add(stroke: HandwritingInk.Stroke) {
            val pts = stroke.points
            path.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            if (pts.size == 1) {
                canvas.drawPoint(pts[0].x, pts[0].y, paint)
            }
        }
        ink.allStrokes.forEach { add(it) }
        val live = ink.inProgress
        if (live.isNotEmpty()) add(HandwritingInk.Stroke(live))
        canvas.drawPath(path, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                ink.begin(event.x, event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                ink.append(event.x, event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ink.end()
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) onStrokeFinished?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun clearInk() {
        ink.clear()
        invalidate()
    }

    fun undoStroke(): Boolean {
        val changed = ink.undoLastStroke()
        if (changed) invalidate()
        return changed
    }

    /** Rasterize current ink to the format expected by [hccr_preprocess] (255=white, 0=ink). */
    fun rasterize(): Pair<ByteArray, Pair<Int, Int>>? {
        if (ink.isEmpty || width <= 0 || height <= 0) return null
        val w = width
        val h = height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        val paint = Paint(strokePaint).apply {
            color = Color.BLACK
            // Keep the same visual thickness so touch and recognition see the same ink.
            strokeWidth = max(strokePaint.strokeWidth, dp(6f))
        }
        drawInk(canvas, paint)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val gray = ByteArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            // Luma; black ink becomes 0, white stays 255.
            val y = ((c shr 16 and 0xff) * 30 + (c shr 8 and 0xff) * 59 + (c and 0xff) * 11) / 100
            gray[i] = y.toByte()
        }
        return gray to (w to h)
    }
}
