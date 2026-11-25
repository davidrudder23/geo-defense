package org.noses.towerdefense.game

/** Distance value for a cell the exit cannot be reached from. */
const val UNREACHABLE = Int.MAX_VALUE

/**
 * The four ways something can move. No diagonals: it keeps the maze honest, because a diagonal
 * step would let enemies slip through the corner where two blockers touch.
 */
private val STEPS = arrayOf(
    0 to 1,   // down first, so a tie leans towards the exit
    -1 to 0,
    1 to 0,
    0 to -1,
)

/**
 * How many steps each cell is from the exit, for every cell at once.
 *
 * This is the whole routing system. Rather than path each enemy separately, one sweep works out
 * the distance from every cell to the exit, and an enemy just walks to whichever neighbour has a
 * smaller number than the cell it is standing on. One sweep routes any number of enemies, and
 * when a tower changes the maze a single rebuild re-routes all of them at once.
 */
class FlowField(
    private val distances: IntArray,
    private val columns: Int,
    private val rows: Int,
) {

    operator fun get(cell: Cell): Int =
        if (cell.col in 0 until columns && cell.row in 0 until rows) {
            distances[cell.col + cell.row * columns]
        } else {
            UNREACHABLE
        }

    fun canReachExit(cell: Cell): Boolean = get(cell) != UNREACHABLE

    /**
     * The next cell to walk to from [from], or null if it is a dead end or already the exit.
     *
     * [preferred] is the direction the walker is already going. Taking it when it is just as good
     * as any other keeps enemies running in straight lines instead of jittering between two
     * equally short routes.
     */
    fun next(from: Cell, preferred: Pair<Int, Int>? = null): Cell? {
        val here = get(from)
        if (here == UNREACHABLE || here == 0) return null

        var best: Cell? = null
        var bestDistance = here

        if (preferred != null) {
            val ahead = Cell(from.col + preferred.first, from.row + preferred.second)
            val aheadDistance = get(ahead)
            if (aheadDistance < here) {
                best = ahead
                bestDistance = aheadDistance
            }
        }

        for ((dx, dy) in STEPS) {
            val candidate = Cell(from.col + dx, from.row + dy)
            val distance = get(candidate)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    /** The whole route from [start] to the exit, or an empty list if there is not one. */
    fun routeFrom(start: Cell): List<Cell> {
        if (!canReachExit(start)) return emptyList()
        val route = ArrayList<Cell>()
        var cell = start
        route.add(cell)
        var heading: Pair<Int, Int>? = null
        // The distance strictly decreases every step, so this cannot loop.
        while (get(cell) > 0) {
            val next = next(cell, heading) ?: break
            heading = (next.col - cell.col) to (next.row - cell.row)
            cell = next
            route.add(cell)
        }
        return route
    }
}

/**
 * Works out the distance from every cell to [target], walking outwards from it.
 *
 * [passable] says whether something can stand on a cell; blockers, towers and anything else in
 * the way simply answer false.
 */
fun flowFieldTo(
    target: Cell,
    columns: Int,
    rows: Int,
    passable: (Cell) -> Boolean,
): FlowField {
    val distances = IntArray(columns * rows) { UNREACHABLE }
    if (!passable(target)) return FlowField(distances, columns, rows)

    val queue = ArrayDeque<Cell>()
    distances[target.col + target.row * columns] = 0
    queue.add(target)

    while (queue.isNotEmpty()) {
        val cell = queue.removeFirst()
        val nextDistance = distances[cell.col + cell.row * columns] + 1
        for ((dx, dy) in STEPS) {
            val col = cell.col + dx
            val row = cell.row + dy
            if (col !in 0 until columns || row !in 0 until rows) continue
            val index = col + row * columns
            if (distances[index] != UNREACHABLE) continue
            if (!passable(Cell(col, row))) continue
            distances[index] = nextDistance
            queue.add(Cell(col, row))
        }
    }
    return FlowField(distances, columns, rows)
}

/** The flow field to [target] on [map]'s grid, which is nearly always the one wanted. */
fun flowFieldOn(map: GameMap, target: Cell = map.exit, passable: (Cell) -> Boolean): FlowField =
    flowFieldTo(target, map.columns, map.rows, passable)
