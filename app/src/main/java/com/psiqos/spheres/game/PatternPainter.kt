package com.psiqos.spheres.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Draws a [DotPattern] darker inside a dot. */
class PatternPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5F000000 }
    private val clip = Path()

    fun draw(canvas: Canvas, pattern: DotPattern, cx: Float, cy: Float, r: Float, alpha: Int = 255) {
        if (pattern == DotPattern.SOLID || r <= 0f) return
        paint.alpha = 0x5F * alpha.coerceIn(0, 255) / 255
        canvas.save()
        clip.rewind()
        clip.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(clip)
        val stripe = r * 0.28f
        when (pattern) {
            DotPattern.SOLID -> Unit
            DotPattern.HORIZONTAL_STRIPES -> stripes(canvas, cx, cy, r, stripe)
            DotPattern.DIAGONAL_STRIPES -> {
                canvas.rotate(45f, cx, cy)
                stripes(canvas, cx, cy, r, stripe)
            }
            DotPattern.RINGS -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stripe
                canvas.drawCircle(cx, cy, r * 0.62f, paint)
                canvas.drawCircle(cx, cy, r * 0.22f, paint)
                paint.style = Paint.Style.FILL
            }
            DotPattern.SPOTS -> {
                val step = r * 0.5f
                var y = cy - 2 * step
                while (y <= cy + 2 * step) {
                    var x = cx - 2 * step
                    while (x <= cx + 2 * step) {
                        canvas.drawCircle(x, y, stripe * 0.55f, paint)
                        x += step
                    }
                    y += step
                }
            }
            DotPattern.CROSSHATCH -> {
                val line = r * 0.14f
                val step = r * 0.5f
                var d = -2 * step
                while (d <= 2 * step) {
                    canvas.drawRect(cx + d - line / 2, cy - r, cx + d + line / 2, cy + r, paint)
                    canvas.drawRect(cx - r, cy + d - line / 2, cx + r, cy + d + line / 2, paint)
                    d += step
                }
            }
        }
        canvas.restore()
    }

    /** Horizontal stripes of [width], one centered on the dot. */
    private fun stripes(canvas: Canvas, cx: Float, cy: Float, r: Float, width: Float) {
        var y = cy - width / 2 - 2 * (2 * width)
        while (y < cy + r) {
            canvas.drawRect(cx - r, y, cx + r, y + width, paint)
            y += 2 * width
        }
    }
}
