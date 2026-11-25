package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MapGeneratorTest {

    /** Every map has to be playable, so check a lot of them rather than one. */
    private fun maps(count: Int = 300): List<GameMap> =
        (0 until count).map { seed -> generateMap(Random(seed.toLong())) }

    @Test
    fun `the entry is on the top row and the exit on the bottom`() {
        maps().forEach { map ->
            assertEquals(0, map.entry.row)
            assertEquals(ROWS - 1, map.exit.row)
            assertTrue(map.entry.col in 0 until COLUMNS)
            assertTrue(map.exit.col in 0 until COLUMNS)
        }
    }

    @Test
    fun `blockers stay on the board`() {
        maps().forEach { map ->
            map.blockers.forEach { cell ->
                assertTrue(
                    "blocker $cell is off the board",
                    cell.col in 0 until COLUMNS && cell.row in 0 until ROWS,
                )
            }
        }
    }

    @Test
    fun `the entry and exit are never blocked`() {
        maps().forEach { map ->
            assertTrue(map.entry !in map.blockers)
            assertTrue(map.exit !in map.blockers)
        }
    }

    @Test
    fun `there is always a way from the entry to the exit`() {
        // This is the one invariant the generator must never break: an unsolvable map is an
        // unplayable game.
        maps().forEach { map ->
            val flow = flowFieldOn(map) { map.isOpen(it) }
            assertTrue("no route on this map", flow.canReachExit(map.entry))
        }
    }

    @Test
    fun `maps have enough blockers to be worth navigating`() {
        maps().forEach { map ->
            assertTrue("only ${map.blockers.size} blockers", map.blockers.size >= 8)
        }
    }

    @Test
    fun `maps leave plenty of room to build`() {
        maps().forEach { map ->
            val buildable = (0 until ROWS).sumOf { row ->
                (0 until COLUMNS).count { col -> map.isBuildable(col, row) }
            }
            assertTrue("only $buildable buildable cells", buildable > 80)
        }
    }

    @Test
    fun `maps differ from each other`() {
        val first = generateMap(Random(1))
        val second = generateMap(Random(2))
        assertNotEquals(first.blockers, second.blockers)
    }

    @Test
    fun `the route is not simply a straight line down the board`() {
        // If the blockers never got in the way the maps would be pointless.
        val wandering = maps(60).count { map ->
            val route = flowFieldOn(map) { map.isOpen(it) }.routeFrom(map.entry)
            route.size > ROWS
        }
        assertTrue("only $wandering of 60 maps made the route bend", wandering > 30)
    }

    @Test
    fun `a cell is buildable only when it is open and not a gate`() {
        val map = GameMap(
            entry = Cell(0, 0),
            exit = Cell(0, ROWS - 1),
            blockers = setOf(Cell(4, 4)),
        )
        assertTrue(map.isBuildable(3, 3))
        assertTrue("a blocker is not buildable", !map.isBuildable(4, 4))
        assertTrue("the entry is not buildable", !map.isBuildable(0, 0))
        assertTrue("the exit is not buildable", !map.isBuildable(0, ROWS - 1))
        assertTrue("off the board is not buildable", !map.isBuildable(-1, 3))
        assertTrue("off the board is not buildable", !map.isBuildable(3, ROWS + 2))
    }
}
