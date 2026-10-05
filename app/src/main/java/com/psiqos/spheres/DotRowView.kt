package com.psiqos.spheres

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.psiqos.spheres.game.Palette

/** Decorative row of the five dot colors. */
class DotRowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val count = 5
        val step = width / count.toFloat()
        val radius = minOf(step * 0.28f, height / 2f)
        for (i in 0 until count) {
            paint.color = Palette.dot(i)
            canvas.drawCircle(step * (i + 0.5f), height / 2f, radius, paint)
        }
    }
}
