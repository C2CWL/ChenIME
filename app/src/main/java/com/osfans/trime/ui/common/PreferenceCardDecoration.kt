/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.common

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import splitties.dimensions.dp

/** Draw group surfaces behind rows without replacing preference widgets or their click targets. */
class PreferenceCardDecoration : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val path = Path()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val item = adapter.getItem(position) ?: return
        outRect.set(0, 0, 0, 0)
        if (item is PreferenceCategory) {
            outRect.top = parent.context.dp(12)
            return
        }
        if (position == 0 || adapter.getItem(position - 1) is PreferenceCategory) {
            outRect.top = parent.context.dp(4)
        }
        if (position == adapter.itemCount - 1 || adapter.getItem(position + 1) is PreferenceCategory) {
            outRect.bottom = parent.context.dp(12)
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val radius = parent.context.dp(20).toFloat()
        val surface = ContextCompat.getColor(parent.context, R.color.settings_surface)
        val divider = ContextCompat.getColor(parent.context, R.color.settings_divider)
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || adapter.getItem(position) is PreferenceCategory) continue
            val first = position == 0 || adapter.getItem(position - 1) is PreferenceCategory
            val last = position == adapter.itemCount - 1 || adapter.getItem(position + 1) is PreferenceCategory
            val top = if (first) radius else 0f
            val bottom = if (last) radius else 0f
            bounds.set(
                child.left.toFloat(),
                child.top + child.translationY,
                child.right.toFloat(),
                child.bottom + child.translationY,
            )
            path.reset()
            path.addRoundRect(bounds, floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom), Path.Direction.CW)
            paint.color = surface
            c.drawPath(path, paint)
            if (!last) {
                paint.color = divider
                paint.strokeWidth = parent.context.dp(1).toFloat()
                c.drawLine(bounds.left + parent.context.dp(20), bounds.bottom, bounds.right - parent.context.dp(20), bounds.bottom, paint)
            }
        }
    }
}
