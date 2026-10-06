package com.psiqos.spheres.game

import kotlin.math.abs
import kotlin.random.Random

data class Cell(val row: Int, val col: Int) {
    fun isAdjacentTo(other: Cell): Boolean =
        abs(row - other.row) + abs(col - other.col) == 1
}

/** Outcome of a completed connection. */
class MoveResult(
    val color: Int,
    val removed: Set<Cell>,
    val isSquare: Boolean,
    /** For every cell after the refill: how many rows the dot now in it has fallen. */
    val drops: Array<IntArray>,
    /** True if the board had no possible move afterwards and was reshuffled. */
    val shuffled: Boolean,
)

/**
 * Pure game state of a Dots board: a grid of colored dots, the path the player
 * is currently drawing, and the rules for removing dots and refilling the grid.
 * Row 0 is the top row.
 */
class Board(
    val rows: Int = 6,
    val cols: Int = 6,
    val colorCount: Int = 5,
    private val random: Random = Random.Default,
) {
    private val grid = Array(rows) { IntArray(cols) }
    private val _path = ArrayList<Cell>()

    /** Cells of the current selection in drawing order. A cell appears twice once a loop is closed. */
    val path: List<Cell> get() = _path

    init {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = random.nextInt(colorCount)
        ensurePlayable()
    }

    operator fun get(row: Int, col: Int): Int = grid[row][col]
    operator fun get(cell: Cell): Int = grid[cell.row][cell.col]

    fun contains(cell: Cell): Boolean = cell.row in 0 until rows && cell.col in 0 until cols

    /** Color of the current selection, or -1 if nothing is selected. */
    val pathColor: Int get() = if (_path.isEmpty()) -1 else this[_path[0]]

    /** True when the current path closes at least one loop ("square"). */
    val isSquare: Boolean get() = _path.size != _path.toSet().size

    /** Replaces the board contents (used by tests). */
    fun setColors(colors: Array<IntArray>) {
        require(colors.size == rows && colors.all { it.size == cols })
        for (r in 0 until rows) colors[r].copyInto(grid[r])
        _path.clear()
    }

    fun begin(cell: Cell) {
        _path.clear()
        if (contains(cell)) _path.add(cell)
    }

    /**
     * Tries to extend the path to [cell]. Moving back onto an earlier cell of the path
     * undoes the steps after it. Returns true if the path changed.
     */
    fun extend(cell: Cell): Boolean {
        if (_path.isEmpty() || !contains(cell)) return false
        val last = _path.last()
        if (cell == last) return false
        if (_path.size >= 2 && cell == _path[_path.size - 2]) {
            _path.removeAt(_path.size - 1)
            return true
        }
        if (!cell.isAdjacentTo(last) || this[cell] != pathColor) return backtrackTo(cell)
        if (hasSegment(last, cell)) return false
        // A dot can be revisited only to close a loop; it may not be passed through twice.
        if (_path.count { it == cell } >= 2) return false
        _path.add(cell)
        return true
    }

    /**
     * The finger reached a dot further back on the path, e.g. when swiping back fast
     * and missing a dot in between: undo everything after it.
     */
    private fun backtrackTo(cell: Cell): Boolean {
        val index = _path.lastIndexOf(cell)
        if (index < 0 || index > _path.size - 3) return false
        while (_path.size > index + 1) _path.removeAt(_path.size - 1)
        return true
    }

    private fun hasSegment(a: Cell, b: Cell): Boolean {
        for (i in 1 until _path.size) {
            val p = _path[i - 1]
            val q = _path[i]
            if ((p == a && q == b) || (p == b && q == a)) return true
        }
        return false
    }

    fun cancel() = _path.clear()

    /**
     * Ends the current selection. If at least two dots are connected they are removed
     * (all dots of that color for a square), the columns collapse and are refilled.
     */
    fun commit(): MoveResult? {
        if (_path.size < 2) {
            _path.clear()
            return null
        }
        val color = pathColor
        val square = isSquare
        val removed: Set<Cell> = if (square) {
            buildSet {
                for (r in 0 until rows) for (c in 0 until cols) if (grid[r][c] == color) add(Cell(r, c))
            }
        } else {
            _path.toSet()
        }
        _path.clear()
        val drops = collapse(removed, avoidColor = if (square) color else -1)
        val shuffled = ensurePlayable()
        return MoveResult(color, removed, square, drops, shuffled)
    }

    private fun collapse(removed: Set<Cell>, avoidColor: Int): Array<IntArray> {
        val drops = Array(rows) { IntArray(cols) }
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                if (Cell(r, c) in removed) continue
                grid[write][c] = grid[r][c]
                drops[write][c] = write - r
                write--
            }
            val newCount = write + 1
            for (r in write downTo 0) {
                grid[r][c] = randomColor(avoidColor)
                drops[r][c] = newCount
            }
        }
        return drops
    }

    private fun randomColor(avoid: Int): Int {
        if (avoid < 0 || colorCount < 2) return random.nextInt(colorCount)
        val c = random.nextInt(colorCount - 1)
        return if (c >= avoid) c + 1 else c
    }

    fun hasMove(): Boolean {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (c + 1 < cols && grid[r][c] == grid[r][c + 1]) return true
            if (r + 1 < rows && grid[r][c] == grid[r + 1][c]) return true
        }
        return false
    }

    /** Reshuffles the board until a move exists. Returns true if a shuffle was needed. */
    private fun ensurePlayable(): Boolean {
        if (hasMove()) return false
        val colors = grid.flatMap { it.asList() }.toMutableList()
        var attempts = 0
        do {
            if (attempts++ < 50) colors.shuffle(random) else colors.indices.forEach { colors[it] = random.nextInt(colorCount) }
            for (i in colors.indices) grid[i / cols][i % cols] = colors[i]
        } while (!hasMove())
        return true
    }
}
