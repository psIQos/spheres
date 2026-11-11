package com.psiqos.spheres

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.psiqos.spheres.game.DotPattern
import com.psiqos.spheres.game.Palette
import com.psiqos.spheres.game.PatternPainter

/** Decorative row of the five dot colors. */
class DotRowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val patternPainter = PatternPainter()

    /** Shows the patterns of the dots (accessibility setting). */
    var patterns = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val count = 5
        val step = width / count.toFloat()
        val radius = minOf(step * 0.28f, height / 2f)
        for (i in 0 until count) {
            paint.color = Palette.dot(i)
            canvas.drawCircle(step * (i + 0.5f), height / 2f, radius, paint)
            if (patterns) patternPainter.draw(canvas, DotPattern.forColor(i), step * (i + 0.5f), height / 2f, radius)
        }
    }
}
