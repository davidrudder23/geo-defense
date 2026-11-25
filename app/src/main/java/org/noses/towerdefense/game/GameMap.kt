package org.noses.towerdefense.game

import kotlin.random.Random

/**
 * The board sizes offered at the menu.
 *
 * A bigger board is not the same board magnified - the cells get smaller and more of them fit on
 * the screen, so it is a wider canvas to build a maze on. All four keep roughly the same portrait
 * shape, since the board is drawn to fit and a different aspect would just letterbox.
 */
enum class BoardSize(
    val label: String,
    val columns: Int,
    val rows: Int,
    val difficulty: Float,
) {
    SMALL("S", 7, 12, SMALL_DIFFICULTY),
    MEDIUM("M", 9, 16, 1f),
    LARGE("L", 12, 21, LARGE_DIFFICULTY),
    XLARGE("XL", 15, 26, XLARGE_DIFFICULTY),
    ;

    val cells: Int get() = columns * rows
}

/**
 * What each size multiplies enemy health by, so that a size is a choice about the board rather
 * than a choice about how hard the game is.
 *
 * This is a correction, not a preference. A bigger board is a longer walk and more places to
 * build, and measurement put the effect at far more than a nudge: uncorrected, the small board
 * lost 5 of 18 games while the x-large board finished all 18 with 95% of its lives. Medium is 1
 * by definition, being the size the curve itself was tuned against.
 *
 * The values come from `CurveSweepTest`, picked to match medium's own profile over six boards at
 * three lengths rather than to hit a target directly. They are a compromise, because the sizes
 * differ in shape and not just in degree: the small board is harsher than medium in the middle of
 * a game and gentler at the end, which no single multiplier can straighten out.
 */
private const val SMALL_DIFFICULTY = 0.88f
private const val LARGE_DIFFICULTY = 1.12f
private const val XLARGE_DIFFICULTY = 1.15f

/** The size the game starts on, and the one the difficulty curve was tuned against. */
val DEFAULT_BOARD_SIZE = BoardSize.MEDIUM

/** The default board's dimensions, kept as names because so much is written in terms of them. */
val COLUMNS = DEFAULT_BOARD_SIZE.columns
val ROWS = DEFAULT_BOARD_SIZE.rows

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
    val columns: Int = COLUMNS,
    val rows: Int = ROWS,
    /** The size this was generated at, or null for a hand-built board such as a test fixture. */
    val size: BoardSize? = null,
) {

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
/**
 * How many boards to draw before keeping one.
 *
 * Difficulty turned out to depend on the length of the walk more than on anything else, and a
 * single random board gave anywhere from 19 to 60 steps. That is not a difficulty curve, it is a
 * lottery: a balance that worked on one generated map lost every game on another. Drawing a
 * handful and keeping the longest pulls the spread in tight enough that one curve can fit.
 */
private const val BOARD_CANDIDATES = 200

/**
 * The shortest way through a board may have, as a fraction of its cells.
 *
 * Best-of-a-handful was not enough: one seed still produced a 31-step board where another gave
 * 63, and that board lost every game. A floor is what actually bounds the spread. It has to be a
 * fraction rather than a count, because a small board cannot hold a big board's walk - the
 * medium board's 55 steps is more cells than the small board has open at all.
 *
 * It is a target rather than a guarantee: boards that cannot reach it in [BOARD_CANDIDATES]
 * draws fall back to the longest one drawn, which is the same thing by a slower route.
 */
private const val MIN_ROUTE_FRACTION = 0.382f

private fun minRouteSteps(size: BoardSize): Int = (size.cells * MIN_ROUTE_FRACTION).toInt()

/** Steps from entry to exit with nothing built, which is the number that matters. */
private fun routeLength(map: GameMap): Int =
    flowFieldTo(map.exit, map.columns, map.rows) { map.isOpen(it) }[map.entry]

/**
 * A board to play on.
 *
 * Draws several and keeps the one with the longest way through, so boards are comparable with
 * each other. See [BOARD_CANDIDATES].
 */
fun generateMap(size: BoardSize, random: Random = Random.Default): GameMap {
    val floor = minRouteSteps(size)
    var best: GameMap? = null
    var bestLength = -1
    repeat(BOARD_CANDIDATES) {
        val candidate = drawMap(size, random)
        val length = routeLength(candidate)
        if (length != UNREACHABLE && length > bestLength) {
            best = candidate
            bestLength = length
        }
        // Good enough is good enough; no need to draw the rest.
        if (bestLength >= floor) return best!!
    }
    return best ?: drawMap(size, random)
}

/** A board of the default size. */
fun generateMap(random: Random = Random.Default): GameMap = generateMap(DEFAULT_BOARD_SIZE, random)

private fun drawMap(size: BoardSize, random: Random): GameMap {
    val columns = size.columns
    val rows = size.rows
    val entry = Cell(random.nextInt(columns), 0)
    val exit = Cell(random.nextInt(columns), rows - 1)

    val blockers = HashSet<Cell>()
    // Denser than it looks like it needs to be. Blockers are what make the walk long, and the
    // length of the walk turned out to drive difficulty more than any other single thing, so a
    // sparse board is not just an easy board - it is an unbalanceable one.
    val wanted = (size.cells * 0.24f).toInt()

    var attempts = 0
    while (blockers.size < wanted && attempts < 400) {
        attempts++

        val horizontal = random.nextBoolean()
        val length = 1 + random.nextInt(4)
        val startCol = random.nextInt(columns)
        val startRow = random.nextInt(rows)

        val wall = (0 until length).map { step ->
            if (horizontal) Cell(startCol + step, startRow) else Cell(startCol, startRow + step)
        }

        // A wall has to fit on the board and leave the entry and exit cells alone.
        if (wall.any { it.col !in 0 until columns || it.row !in 0 until rows }) continue
        if (wall.any { it == entry || it == exit }) continue
        if (wall.all { it in blockers }) continue

        blockers.addAll(wall)
        // Putting it back is cheaper than being clever about which walls are safe.
        if (!hasRoute(entry, exit, blockers, columns, rows)) blockers.removeAll(wall.toSet())
    }

    return GameMap(
        entry = entry,
        exit = exit,
        blockers = blockers,
        columns = columns,
        rows = rows,
        size = size,
    )
}

/** Is there any way from [from] to [to] with [blockers] in the way? */
private fun hasRoute(
    from: Cell,
    to: Cell,
    blockers: Set<Cell>,
    columns: Int,
    rows: Int,
): Boolean = flowFieldTo(to, columns, rows) { it !in blockers }.canReachExit(from)
