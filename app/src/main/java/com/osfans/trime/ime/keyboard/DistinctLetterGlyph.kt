package com.osfans.trime.ime.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.Keep
import androidx.core.graphics.withSave
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate

/** Authored outlines following sample A: equal I bars, l flag and curved right tail. */
object DistinctLetterGlyph {
    @Volatile
    private var hasCustomFont = false

    @Keep
    private val fontChangeListener = PreferenceDelegate.OnChangeListener<String> { _, value ->
        hasCustomFont = value.isNotEmpty()
    }

    init {
        val preference = AppPrefs.defaultInstance().customFont
        preference.registerOnChangeListener(fontChangeListener)
        hasCustomFont = preference.getValue().isNotEmpty()
    }

    // Only the glyph affects these normalized outlines. Size and alignment are canvas transforms.
    private val capitalI = buildPath("I")
    private val lowercaseL = buildPath("l")

    fun enabled(label: String): Boolean = useDistinctLetter(label, hasCustomFont)

    private fun buildPath(label: String): Path = Path().apply {
        if (label == "I") {
            addRect(0.14f, 0f, 0.86f, 0.105f, Path.Direction.CW)
            addRect(0.42f, 0f, 0.58f, 1f, Path.Direction.CW)
            addRect(0.14f, 0.895f, 0.86f, 1f, Path.Direction.CW)
        } else {
            moveTo(0.20f, 0f)
            lineTo(0.62f, 0f)
            lineTo(0.62f, 0.74f)
            cubicTo(0.62f, 0.88f, 0.66f, 0.90f, 0.80f, 0.90f)
            lineTo(0.96f, 0.90f)
            lineTo(0.96f, 1f)
            lineTo(0.77f, 1f)
            cubicTo(0.54f, 1f, 0.46f, 0.94f, 0.46f, 0.74f)
            lineTo(0.46f, 0.10f)
            lineTo(0.20f, 0.10f)
            close()
        }
    }

    fun draw(canvas: Canvas, label: String, x: Float, baseline: Float, paint: Paint) {
        val width = paint.textSize * 0.60f
        val height = paint.textSize * if (label == "I") 0.73f else 0.76f
        val left = when (paint.textAlign) {
            Paint.Align.CENTER -> x - width / 2
            Paint.Align.RIGHT -> x - width
            else -> x
        }
        canvas.withSave {
            translate(left, baseline - height)
            scale(width, height)
            drawPath(if (label == "I") capitalI else lowercaseL, paint)
        }
    }
}
