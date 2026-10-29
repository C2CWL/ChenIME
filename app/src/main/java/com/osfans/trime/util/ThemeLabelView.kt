// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import com.mikepenz.iconics.IconicsDrawable

/** For ordinary TextViews, outside the keyboard's custom drawing path. */
fun TextView.setThemeLabel(label: String, fallback: String = "") {
    val parsed = parseThemeLabel(label, fallback)
    val drawable = parsed.iconName?.let { name ->
        runCatching {
            IconicsDrawable(context, name).apply {
                require(icon != null)
                val size = textSize.toInt().coerceAtLeast(1)
                setBounds(0, 0, size, size)
                colorFilter = PorterDuffColorFilter(currentTextColor, PorterDuff.Mode.SRC_IN)
            }
        }.getOrNull()
    }
    text = if (drawable == null) {
        parsed.text
    } else {
        SpannableString("\uFFFC").apply {
            setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    contentDescription = parsed.text
}
