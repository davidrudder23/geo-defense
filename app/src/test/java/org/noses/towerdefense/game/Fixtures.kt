package org.noses.towerdefense.game

/**
 * Hand-built boards for tests.
 *
 * Generated maps are random, so anything that needs to know where an enemy will be uses one of
 * these instead.
 */
object Fixtures {

    /** Nothing in the way at all: entry top-left, exit bottom-left. */
    fun openField(): GameMap = GameMap(
        entry = Cell(0, 0),
        exit = Cell(0, ROWS - 1),
        blockers = emptySet(),
    )

    /**
     * Columns 0 and 1 open and everything else walled off.
     *
     * Enemies run straight down column 0, because any detour into column 1 is longer, and column
     * 1 is free for towers. The equivalent of the old fixed road with building space beside it.
     */
    fun lane(): GameMap {
        val blockers = HashSet<Cell>()
        for (row in 0 until ROWS) {
            for (col in 2 until COLUMNS) blockers.add(Cell(col, row))
        }
        return GameMap(entry = Cell(0, 0), exit = Cell(0, ROWS - 1), blockers = blockers)
    }

    /**
     * A single open column with the exit part way down it, leaving the cells below the exit
     * free to build on.
     *
     * Lets a tower sit directly in line with the enemies, which is the only way to test that the
     * laser pierces a row of them.
     */
    fun stub(exitRow: Int = 10): GameMap {
        val blockers = HashSet<Cell>()
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) if (col != 3) blockers.add(Cell(col, row))
        }
        return GameMap(entry = Cell(3, 0), exit = Cell(3, exitRow), blockers = blockers)
    }

    /**
     * A long snaking corridor that runs the full width of the board.
     *
     * Every third row is a wall with a single gap in it, and the gap alternates sides, so the
     * only way down is to cross the whole board on each open band in turn. The bands are two
     * rows deep, which leaves the second row of each free to build in without sealing the
     * corridor — the length is what gives a defence time to work.
     */
    fun serpentine(): GameMap {
        val blockers = HashSet<Cell>()
        var wallIndex = 0
        for (row in 0 until ROWS) {
            if (row % 3 != 2) continue
            val gap = if (wallIndex % 2 == 0) COLUMNS - 1 else 0
            for (col in 0 until COLUMNS) if (col != gap) blockers.add(Cell(col, row))
            wallIndex++
        }
        return GameMap(entry = Cell(0, 0), exit = Cell(0, ROWS - 1), blockers = blockers)
    }
}
