package com.psiqos.spheres.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Draws a [Board], handles drawing paths with the finger and animates falling dots. */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** The player started touching the board for the first time in this game. */
        fun onFirstTouch() {}
        fun onPathChanged(length: Int, isSquare: Boolean) {}
        fun onMove(result: MoveResult)
    }

    var listener: Listener? = null

    /** When false, touches are ignored (e.g. after game over). */
    var inputEnabled = true
        set(value) {
            field = value
            if (!value && board.path.isNotEmpty()) {
                board.cancel()
                invalidate()
            }
        }

    var board = Board()
        private set

    private var tracker = PathTracker(board)

    private var touched = false

    private val density = resources.displayMetrics.density
    private var cellSize = 0f
    private var originX = 0f
    private var originY = 0f
    private var dotRadius = 0f

    // Per-cell falling animation: how many rows above its slot a dot is currently drawn.
    private var offset = Array(board.rows) { FloatArray(board.cols) }
    private var velocity = Array(board.rows) { FloatArray(board.cols) }
    private var delay = Array(board.rows) { FloatArray(board.cols) }

    private class Effect(val x: Float, val y: Float, val color: Int, val start: Long, val kind: Int)

    private val effects = ArrayList<Effect>()
    private var squareFlashStart = 0L
    private var squareFlashColor = 0

    private var lastFrame = 0L

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePath = Path()

    init {
        isHapticFeedbackEnabled = true
        dropIn()
    }

    /** Vibration feedback; switched on and off in the settings. */
    val haptics = Haptics(context)

    /** Current dot colors, row by row, e.g. to save the game. */
    fun colors(): List<IntArray> = List(board.rows) { r -> IntArray(board.cols) { c -> board[r, c] } }

    /** Starts a new board, or continues one with the given [restore] colors. */
    fun newGame(size: Int = 6, colors: Int = 5, restore: List<IntArray>? = null) {
        board = Board(rows = size, cols = size, colorCount = colors)
        if (restore != null) board.setColors(restore.toTypedArray())
        tracker = PathTracker(board)
        updateGeometry()
        touched = false
        effects.clear()
        squareFlashStart = 0L
        dropIn()
        inputEnabled = true
        listener?.onPathChanged(0, false)
    }

    /** Lets the whole board fall in from above, row by row. */
    private fun dropIn() {
        offset = Array(board.rows) { FloatArray(board.cols) { board.rows + 1f } }
        velocity = Array(board.rows) { FloatArray(board.cols) }
        delay = Array(board.rows) { r -> FloatArray(board.cols) { c -> (board.rows - 1 - r) * 0.045f + c * 0.012f } }
        startAnimating()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateGeometry()

    private fun updateGeometry() {
        if (width == 0 || height == 0) return
        val pad = 16 * density
        val available = min(width - 2 * pad, height - 2 * pad)
        cellSize = available / max(board.rows, board.cols)
        originX = (width - cellSize * board.cols) / 2f
        originY = (height - cellSize * board.rows) / 2f
        tracker.originX = originX
        tracker.originY = originY
        tracker.cellSize = cellSize
        dotRadius = cellSize * 0.2f
        linePaint.strokeWidth = dotRadius * 0.55f
        framePaint.strokeWidth = 10 * density
    }

    private fun centerX(col: Int) = originX + (col + 0.5f) * cellSize
    private fun centerY(row: Int) = originY + (row + 0.5f) * cellSize

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker.begin(event.x, event.y)?.let(::onBegin)
            }
            MotionEvent.ACTION_MOVE -> {
                track(event)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // A fast flick can deliver its end point only with the UP event.
                if (board.path.isNotEmpty()) track(event)
                val result = board.commit()
                listener?.onPathChanged(0, false)
                if (result != null) onCommitted(result)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                board.cancel()
                listener?.onPathChanged(0, false)
                invalidate()
            }
        }
        return true
    }

    /** Follows the finger through all positions of [event], including batched historical ones. */
    private fun track(event: MotionEvent) {
        for (h in 0 until event.historySize) trackTo(event.getHistoricalX(h), event.getHistoricalY(h))
        trackTo(event.x, event.y)
    }

    private fun trackTo(x: Float, y: Float) {
        tracker.moveTo(x, y, ::onPathStep)?.let(::onBegin)
    }

    private fun onBegin(cell: Cell) {
        if (!touched) {
            touched = true
            listener?.onFirstTouch()
        }
        addPulse(cell)
        listener?.onPathChanged(board.path.size, false)
        Sound.playNote(0)
        haptics.tick()
        startAnimating()
    }

    private fun onPathStep(before: Int, wasSquare: Boolean) {
        val grew = board.path.size > before
        val square = board.isSquare
        listener?.onPathChanged(board.path.size, square)
        if (grew) {
            addPulse(board.path.last())
            if (square && !wasSquare) {
                Sound.playSquare()
                haptics.square()
                for (r in 0 until board.rows) for (c in 0 until board.cols) {
                    if (board[r, c] == board.pathColor) addPulse(Cell(r, c))
                }
            } else {
                Sound.playNote(board.path.size - 1)
                haptics.tick()
            }
        } else {
            Sound.playNote(board.path.size - 1)
            haptics.tick()
        }
        startAnimating()
    }

    private fun onCommitted(result: MoveResult) {
        val now = SystemClock.uptimeMillis()
        for (cell in result.removed) {
            effects += Effect(centerX(cell.col), centerY(cell.row) - offset[cell.row][cell.col] * cellSize,
                result.color, now, KIND_POP)
        }
        if (result.isSquare) {
            squareFlashStart = now
            squareFlashColor = result.color
        }
        if (result.shuffled) {
            dropIn()
        } else {
            val oldOffset = offset.map { it.copyOf() }
            val oldVelocity = velocity.map { it.copyOf() }
            for (r in 0 until board.rows) for (c in 0 until board.cols) {
                val d = result.drops[r][c]
                if (d > 0) {
                    // Keep dots that are still falling continuous with where they are drawn now.
                    val sourceRow = r - d
                    val falling = sourceRow >= 0
                    offset[r][c] = d + if (falling) oldOffset[sourceRow][c] else 0f
                    velocity[r][c] = if (falling) oldVelocity[sourceRow][c] else 0f
                    delay[r][c] = if (falling) 0f else 0.06f
                }
            }
        }
        listener?.onMove(result)
        startAnimating()
    }

    private fun addPulse(cell: Cell) {
        effects += Effect(centerX(cell.col), centerY(cell.row), board[cell], SystemClock.uptimeMillis(), KIND_PULSE)
    }

    private fun startAnimating() {
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    /** Advances falling dots. Returns true while something is still moving. */
    private fun step(dt: Float): Boolean {
        var moving = false
        val g = 70f // rows per second²
        for (r in 0 until board.rows) for (c in 0 until board.cols) {
            if (offset[r][c] <= 0f) continue
            moving = true
            if (delay[r][c] > 0f) {
                delay[r][c] -= dt
                continue
            }
            velocity[r][c] += g * dt
            offset[r][c] -= velocity[r][c] * dt
            if (offset[r][c] <= 0f) {
                offset[r][c] = 0f
                velocity[r][c] = 0f
            }
        }
        return moving
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else min(0.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        var animating = step(dt)

        // Square: tint the background and draw a frame in the square's color.
        val path = board.path
        val pathColor = if (path.isNotEmpty()) Palette.dot(board.pathColor) else 0
        if (path.isNotEmpty() && board.isSquare) {
            fillPaint.color = Palette.withAlpha(pathColor, 0x1A)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            framePaint.color = pathColor
            framePaint.alpha = 255
            val inset = framePaint.strokeWidth / 2
            canvas.drawRect(inset, inset, width - inset, height - inset, framePaint)
        }
        if (squareFlashStart != 0L) {
            val t = (now - squareFlashStart) / 450f
            if (t < 1f) {
                fillPaint.color = Palette.withAlpha(Palette.dot(squareFlashColor), (0x40 * (1 - t)).toInt())
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
                animating = true
            } else {
                squareFlashStart = 0L
            }
        }

        // Connection lines.
        if (path.isNotEmpty()) {
            linePaint.color = pathColor
            linePath.reset()
            linePath.moveTo(centerX(path[0].col), centerY(path[0].row))
            for (i in 1 until path.size) linePath.lineTo(centerX(path[i].col), centerY(path[i].row))
            linePath.lineTo(tracker.fingerX, tracker.fingerY)
            canvas.drawPath(linePath, linePaint)
        }

        // Expanding rings and popping dots.
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            val duration = if (e.kind == KIND_PULSE) 420f else 220f
            val t = (now - e.start) / duration
            if (t >= 1f) {
                iterator.remove()
                continue
            }
            animating = true
            fillPaint.color = Palette.dot(e.color)
            if (e.kind == KIND_PULSE) {
                fillPaint.alpha = (110 * (1 - t)).toInt()
                canvas.drawCircle(e.x, e.y, dotRadius * (1f + 1.1f * t), fillPaint)
            } else {
                fillPaint.alpha = 255
                canvas.drawCircle(e.x, e.y, dotRadius * (1f - t) * (1f + 0.3f * (1 - t)), fillPaint)
            }
        }

        // Dots.
        for (r in 0 until board.rows) for (c in 0 until board.cols) {
            val o = offset[r][c]
            val y = centerY(r) - o * cellSize
            if (y < -cellSize) continue
            dotPaint.color = Palette.dot(board[r, c])
            canvas.drawCircle(centerX(c), y, dotRadius, dotPaint)
        }

        if (animating || path.isNotEmpty()) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private companion object {
        const val KIND_PULSE = 0
        const val KIND_POP = 1
    }
}

object Palette {
    private val dots = intArrayOf(
        0xFFEC5B57.toInt(), // red
        0xFFF4C842.toInt(), // yellow
        0xFF83D66A.toInt(), // green
        0xFF5CA8EC.toInt(), // blue
        0xFF9E6CDB.toInt(), // purple
        0xFF27B9A6.toInt(), // teal (hard only), in the widest hue gap between green and blue
    )

    fun dot(index: Int): Int = dots[index.mod(dots.size)]

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
