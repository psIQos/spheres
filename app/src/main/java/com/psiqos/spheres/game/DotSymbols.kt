package com.psiqos.spheres.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/** Draws the [Symbol] of a dot color inside the dot, within about half the dot radius. */
class DotSymbols {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /** Draws the symbol of color [index] on a dot of [radius] at ([x], [y]). */
    fun draw(canvas: Canvas, index: Int, x: Float, y: Float, radius: Float) {
        val r = radius * 0.5f
        paint.color = Palette.ink(Palette.dot(index))
        paint.style = Paint.Style.FILL
        when (Palette.symbol(index)) {
            Symbol.TRIANGLE -> {
                // Centered on its centroid, which sits lower than the bounding box center.
                polygon(x, y + r * 0.12f, r, 3, -90f)
                canvas.drawPath(path, paint)
            }
            Symbol.DOT -> canvas.drawCircle(x, y, r * 0.55f, paint)
            Symbol.SQUARE -> {
                val h = r * 0.66f
                canvas.drawRect(x - h, y - h, x + h, y + h, paint)
            }
            Symbol.PLUS -> {
                val t = r * 0.24f
                canvas.drawRect(x - r, y - t, x + r, y + t, paint)
                canvas.drawRect(x - t, y - r, x + t, y + r, paint)
            }
            Symbol.RING -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.28f
                canvas.drawCircle(x, y, r * 0.72f, paint)
            }
            Symbol.STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val d = if (i % 2 == 0) r * 1.05f else r * 0.45f
                    val px = x + d * cos(a).toFloat()
                    val py = y + r * 0.08f + d * sin(a).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun polygon(x: Float, y: Float, r: Float, corners: Int, startDegrees: Float) {
        path.reset()
        for (i in 0 until corners) {
            val a = Math.toRadians(startDegrees + i * 360.0 / corners)
            val px = x + r * cos(a).toFloat()
            val py = y + r * sin(a).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }
}
