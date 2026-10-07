package com.psiqos.spheres.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/** A bar that runs out while the time stop power-up holds the clock. */
class TimeStopBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.withAlpha(Palette.dot(3), 0x33) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.dot(3) }

    private var totalMs = 1L
    private var endAt = 0L
    /** Remaining time shown while the clock is stopped (pause, app left); -1 while running. */
    private var heldMs = -1L

    /** The time stop runs: [remainingMs] of [totalMs] left. */
    fun run(remainingMs: Long, totalMs: Long) {
        this.totalMs = totalMs.coerceAtLeast(1)
        endAt = SystemClock.elapsedRealtime() + remainingMs
        heldMs = -1
        visibility = VISIBLE
        postInvalidateOnAnimation()
    }

    /** The game is paused with [remainingMs] of the time stop left. */
    fun hold(remainingMs: Long) {
        heldMs = remainingMs
        visibility = if (remainingMs > 0) VISIBLE else GONE
        invalidate()
    }

    fun hide() {
        heldMs = -1
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val left = if (heldMs >= 0) heldMs else endAt - SystemClock.elapsedRealtime()
        val fraction = (left.toFloat() / totalMs).coerceIn(0f, 1f)
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
        if (fraction > 0f) {
            // Shrinks towards the middle, like the clock's remaining stop.
            val w = width * fraction
            val x = (width - w) / 2
            canvas.drawRoundRect(x, 0f, x + w, height.toFloat(), r, r, fill)
        }
        if (heldMs < 0 && left > 0) postInvalidateOnAnimation()
    }
}
