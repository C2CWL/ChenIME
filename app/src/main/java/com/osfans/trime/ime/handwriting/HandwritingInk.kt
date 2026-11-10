/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

/** In-memory ink for the handwriting canvas. Coordinates are in view pixels. */
class HandwritingInk {
    data class Point(val x: Float, val y: Float)
    data class Stroke(val points: List<Point>) {
        init {
            require(points.isNotEmpty())
        }
    }

    private val strokes = mutableListOf<Stroke>()
    private var current: MutableList<Point>? = null

    val allStrokes: List<Stroke> get() = strokes.toList()
    val inProgress: List<Point> get() = current?.toList().orEmpty()
    val isEmpty: Boolean get() = strokes.isEmpty() && current.isNullOrEmpty()
    val canUndo: Boolean get() = strokes.isNotEmpty() || !current.isNullOrEmpty()

    fun begin(x: Float, y: Float) {
        current = mutableListOf(Point(x, y))
    }

    fun append(x: Float, y: Float) {
        val stroke = current ?: return
        val last = stroke.last()
        if (x != last.x || y != last.y) stroke.add(Point(x, y))
    }

    fun end() {
        val stroke = current
        current = null
        if (stroke != null && stroke.isNotEmpty()) {
            strokes.add(Stroke(stroke))
        }
    }

    fun undoLastStroke(): Boolean {
        current = null
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.lastIndex)
        return true
    }

    fun clear() {
        strokes.clear()
        current = null
    }

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun bounds(): Bounds? {
        val points = buildList {
            strokes.forEach { addAll(it.points) }
            current?.let { addAll(it) }
        }
        if (points.isEmpty()) return null
        return Bounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )
    }
}
