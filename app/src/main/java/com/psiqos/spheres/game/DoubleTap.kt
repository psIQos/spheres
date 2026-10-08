package com.psiqos.spheres.game

/**
 * Recognizes a double tap on one dot: a second tap on the same cell that starts within
 * [timeoutMs] after the first one ended. Android-free so it can be unit-tested.
 */
class DoubleTap(private val timeoutMs: Long) {
    private var last: Cell? = null
    private var lastUp = 0L

    /** A tap on [cell] from [downTime] to [upTime]; true if it completes a double tap. */
    fun tap(cell: Cell, downTime: Long, upTime: Long): Boolean {
        val double = cell == last && downTime - lastUp in 0..timeoutMs
        last = if (double) null else cell
        lastUp = upTime
        return double
    }

    /** Anything else happened in between (a path, a power-up): start over. */
    fun reset() {
        last = null
    }
}
