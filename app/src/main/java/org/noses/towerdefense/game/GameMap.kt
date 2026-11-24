package org.noses.towerdefense.game

import kotlin.math.min
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
 * A generated board: a single road from the top edge to the bottom edge, plus the empty ground
 * on either side of it where towers can be built.
 */
class GameMap(val path: List<Cell>) {
    val columns = COLUMNS
    val rows = ROWS

    private val road: Set<Cell> = path.toSet()

    /** How far an enemy must travel, measured in path steps. */
    val pathLength: Int = path.size - 1

    val entry: Cell get() = path.first()
    val exit: Cell get() = path.last()

    fun isRoad(col: Int, row: Int): Boolean = Cell(col, row) in road

    fun inBounds(col: Int, row: Int): Boolean = col in 0 until columns && row in 0 until rows

    fun isBuildable(col: Int, row: Int): Boolean = inBounds(col, row) && !isRoad(col, row)

    /** Where an enemy that has walked [distance] path steps stands, in cell units. */
    fun positionAt(distance: Float): Point {
        if (distance <= 0f) return path.first().center()
        if (distance >= pathLength) return path.last().center()
        val index = distance.toInt()
        val t = distance - index
        val from = path[index].center()
        val to = path[index + 1].center()
        return Point(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    }
}

/**
 * Builds a random road down the board.
 *
 * Each pass walks sideways for a few cells and then steps down one row, alternating the sideways
 * direction so the road snakes. Because the row only ever increases and each row is crossed in a
 * single direction, the road can never cross or touch itself, so every generated map is valid.
 */
fun generateMap(random: Random = Random.Default): GameMap {
    val path = ArrayList<Cell>()
    var col = random.nextInt(1, COLUMNS - 1)
    var row = 0
    path.add(Cell(col, row))

    var lastDirection = if (random.nextBoolean()) 1 else -1
    // A row that runs sideways is always followed by one that only steps down. Without this,
    // two sideways runs can land on neighbouring rows and the road thickens into a 2x2 block
    // instead of reading as a single winding lane.
    var ranSideways = false

    while (row < ROWS - 1) {
        if (!ranSideways && random.nextInt(100) < 72) {
            var direction = -lastDirection
            if (roomSideways(col, direction) < 2) direction = -direction
            val room = min(roomSideways(col, direction), 4)
            if (room >= 2) {
                val run = 2 + random.nextInt(room - 1)
                repeat(run) {
                    col += direction
                    path.add(Cell(col, row))
                }
                lastDirection = direction
                ranSideways = true
            } else {
                ranSideways = false
            }
        } else {
            ranSideways = false
        }
        row++
        path.add(Cell(col, row))
    }
    return GameMap(path)
}

/** How many cells of board remain to the side of [col] in [direction]. */
private fun roomSideways(col: Int, direction: Int): Int =
    if (direction > 0) COLUMNS - 1 - col else col
