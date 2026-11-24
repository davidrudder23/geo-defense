package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MapGeneratorTest {

    /** Every map has to be playable, so check a lot of them rather than one. */
    private fun maps(count: Int = 300): List<GameMap> =
        (0 until count).map { seed -> generateMap(Random(seed.toLong())) }

    @Test
    fun `road starts at the top and ends at the bottom`() {
        maps().forEach { map ->
            assertEquals(0, map.entry.row)
            assertEquals(ROWS - 1, map.exit.row)
        }
    }

    @Test
    fun `road stays on the board`() {
        maps().forEach { map ->
            map.path.forEach { cell ->
                assertTrue(
                    "cell $cell is off the board",
                    cell.col in 0 until COLUMNS && cell.row in 0 until ROWS,
                )
            }
        }
    }

    @Test
    fun `each step of the road touches the last`() {
        maps().forEach { map ->
            map.path.zipWithNext { from, to ->
                val steps = abs(to.col - from.col) + abs(to.row - from.row)
                assertEquals("$from to $to is not a single step", 1, steps)
            }
        }
    }

    @Test
    fun `road never crosses itself`() {
        maps().forEach { map ->
            assertEquals(map.path.size, map.path.toSet().size)
        }
    }

    @Test
    fun `the road is never two cells thick`() {
        // A 2x2 block of road means two sideways runs landed on neighbouring rows, which reads
        // as a rectangle rather than a lane you can follow.
        maps().forEach { map ->
            for (row in 0 until ROWS - 1) {
                for (col in 0 until COLUMNS - 1) {
                    val block = map.isRoad(col, row) && map.isRoad(col + 1, row) &&
                        map.isRoad(col, row + 1) && map.isRoad(col + 1, row + 1)
                    assertTrue("2x2 block of road at ($col, $row)", !block)
                }
            }
        }
    }

    @Test
    fun `the road has corners in it`() {
        // A road straight down the board would be a dull map.
        val turns = maps(50).map { map ->
            map.path.windowed(3).count { (a, _, c) -> a.col != c.col && a.row != c.row }
        }
        assertTrue("some maps had no corners", turns.all { it >= 2 })
    }

    @Test
    fun `there is room left to build`() {
        maps().forEach { map ->
            val buildable = (0 until ROWS).sumOf { row ->
                (0 until COLUMNS).count { col -> map.isBuildable(col, row) }
            }
            assertTrue("only $buildable buildable cells", buildable > 60)
        }
    }

    @Test
    fun `maps differ from each other`() {
        val first = generateMap(Random(1))
        val second = generateMap(Random(2))
        assertNotEquals(first.path, second.path)
    }

    @Test
    fun `walking the path moves from entry to exit`() {
        val map = generateMap(Random(7))
        assertEquals(map.entry.center(), map.positionAt(0f))
        assertEquals(map.exit.center(), map.positionAt(map.pathLength.toFloat()))
        // Clamped at both ends rather than running off.
        assertEquals(map.entry.center(), map.positionAt(-5f))
        assertEquals(map.exit.center(), map.positionAt(map.pathLength + 10f))
    }

    @Test
    fun `position halfway along a step is halfway between the cells`() {
        val map = GameMap(listOf(Cell(0, 0), Cell(0, 1)))
        val middle = map.positionAt(0.5f)
        assertEquals(0.5f, middle.x, 1e-4f)
        assertEquals(1.0f, middle.y, 1e-4f)
    }
}
