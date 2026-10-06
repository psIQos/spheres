package com.psiqos.spheres.game

import kotlin.math.hypot
import kotlin.math.max

/**
 * Turns finger positions (in view pixels) into path changes on a [Board].
 * Kept free of Android classes so fast and sloppy gestures can be unit tested.
 */
class PathTracker(private val board: Board) {

    var originX = 0f
    var originY = 0f
    var cellSize = 1f

    var fingerX = 0f
        private set
    var fingerY = 0f
        private set

    fun centerX(col: Int) = originX + (col + 0.5f) * cellSize
    fun centerY(row: Int) = originY + (row + 0.5f) * cellSize

    /** The cell whose dot is within [tolerance] (in cell sizes) of the point, if any. */
    fun cellAt(x: Float, y: Float, tolerance: Float): Cell? {
        if (x < originX || y < originY) return null
        val cell = Cell(((y - originY) / cellSize).toInt(), ((x - originX) / cellSize).toInt())
        if (!board.contains(cell)) return null
        val d = hypot(x - centerX(cell.col), y - centerY(cell.row))
        return if (d <= cellSize * tolerance) cell else null
    }

    /** Starts a path if the finger is on a dot. Returns the cell, or null. */
    fun begin(x: Float, y: Float, tolerance: Float = START_TOLERANCE): Cell? {
        val cell = cellAt(x, y, tolerance) ?: return null
        board.begin(cell)
        fingerX = x
        fingerY = y
        return cell
    }

    /**
     * Follows the finger to (x, y). [onStep] is called after every change of the path
     * with the path size and square state from before the change.
     * Returns the cell a path was started on, if the finger only now reached a dot.
     */
    fun moveTo(x: Float, y: Float, onStep: (sizeBefore: Int, wasSquare: Boolean) -> Unit): Cell? {
        if (board.path.isEmpty()) {
            // The finger went down between dots: start once it reaches one.
            return begin(x, y, HIT_TOLERANCE)
        }
        // Sample the movement so fast swipes don't skip over dots.
        val dx = x - fingerX
        val dy = y - fingerY
        val steps = max(1, (hypot(dx, dy) / (cellSize / 4f)).toInt())
        for (i in 1..steps) {
            val sx = fingerX + dx * i / steps
            val sy = fingerY + dy * i / steps
            // Dots already on the path get the whole cell as target, so swiping back
            // works without following the line exactly. New dots need a closer hit,
            // so passing next to a dot does not pick it up.
            val cell = cellAt(sx, sy, BACKTRACK_TOLERANCE)
                ?.takeIf { it in board.path || cellAt(sx, sy, HIT_TOLERANCE) != null }
                ?: continue
            val wasSquare = board.isSquare
            val before = board.path.size
            if (board.extend(cell)) onStep(before, wasSquare)
        }
        fingerX = x
        fingerY = y
        return null
    }

    companion object {
        const val START_TOLERANCE = 0.6f
        const val HIT_TOLERANCE = 0.42f
        const val BACKTRACK_TOLERANCE = 0.5f
    }
}
