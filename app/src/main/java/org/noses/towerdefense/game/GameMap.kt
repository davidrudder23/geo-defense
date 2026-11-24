package org.noses.towerdefense.game

import kotlin.random.Random

/** Board size in cells. Portrait, so taller than it is wide. */
const val COLUMNS = 9
const val ROWS = 16

/** A whole-number board cell. */
data class Cell(val col: Int, val row: Int)

/** A position on the board in cell units; (0.5, 0.5) is the middle of the top-left cell. */
data class Point(val x: Float, val y: Float)

/** The middle of this cell, in cell units. */
fun Cell.center(): Point = Point(col + 0.5f, row + 0.5f)

/**
 * The board: an open field with fixed blockers scattered through it, an entry at the top and an
 * exit at the bottom.
 *
 * There is no road. Enemies work out their own way from the entry to the exit around whatever is
 * in the way, so the route is decided by where the blockers are and where the player has built.
 */
class GameMap(
    val entry: Cell,
    val exit: Cell,
    val blockers: Set<Cell>,
) {
    val columns = COLUMNS
    val rows = ROWS

    fun inBounds(col: Int, row: Int): Boolean = col in 0 until columns && row in 0 until rows

    fun isBlocker(col: Int, row: Int): Boolean = Cell(col, row) in blockers

    /** Nothing fixed is standing here, so something could walk through it. */
    fun isOpen(cell: Cell): Boolean = inBounds(cell.col, cell.row) && cell !in blockers

    /**
     * Could a tower ever stand here, ignoring what else is on the board right now?
     *
     * The entry and exit are kept clear so neither can be plugged outright.
     */
    fun isBuildable(col: Int, row: Int): Boolean {
        val cell = Cell(col, row)
        return isOpen(cell) && cell != entry && cell != exit
    }
}

/**
 * Builds a random board.
 *
 * Blockers go down as short walls rather than loose cells, which gives the field something to
 * steer around instead of a scattering of dots. Each wall is only kept if the exit is still
 * reachable from the entry afterwards, so a generated map always has a way through.
 */
fun generateMap(random: Random = Random.Default): GameMap {
    val entry = Cell(random.nextInt(COLUMNS), 0)
    val exit = Cell(random.nextInt(COLUMNS), ROWS - 1)

    val blockers = HashSet<Cell>()
    val wanted = (COLUMNS * ROWS * 0.17f).toInt()

    var attempts = 0
    while (blockers.size < wanted && attempts < 400) {
        attempts++

        val horizontal = random.nextBoolean()
        val length = 1 + random.nextInt(4)
        val startCol = random.nextInt(COLUMNS)
        val startRow = random.nextInt(ROWS)

        val wall = (0 until length).map { step ->
            if (horizontal) Cell(startCol + step, startRow) else Cell(startCol, startRow + step)
        }

        // A wall has to fit on the board and leave the entry and exit cells alone.
        if (wall.any { it.col !in 0 until COLUMNS || it.row !in 0 until ROWS }) continue
        if (wall.any { it == entry || it == exit }) continue
        if (wall.all { it in blockers }) continue

        blockers.addAll(wall)
        // Putting it back is cheaper than being clever about which walls are safe.
        if (!hasRoute(entry, exit, blockers)) blockers.removeAll(wall.toSet())
    }

    return GameMap(entry = entry, exit = exit, blockers = blockers)
}

/** Is there any way from [from] to [to] with [blockers] in the way? */
private fun hasRoute(from: Cell, to: Cell, blockers: Set<Cell>): Boolean =
    flowFieldTo(to) { it !in blockers }.canReachExit(from)
