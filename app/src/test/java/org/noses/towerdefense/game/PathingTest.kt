package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathingTest {

    private val everywhere: (Cell) -> Boolean = { it.col in 0 until COLUMNS && it.row in 0 until ROWS }

    @Test
    fun `distance to the exit counts steps, not diagonals`() {
        val exit = Cell(0, 0)
        val flow = flowFieldTo(exit, everywhere)
        assertEquals(0, flow[exit])
        assertEquals(1, flow[Cell(1, 0)])
        assertEquals(1, flow[Cell(0, 1)])
        // Three across and four down is seven steps, not five: there are no diagonal moves.
        assertEquals(7, flow[Cell(3, 4)])
    }

    @Test
    fun `cells off the board are unreachable`() {
        val flow = flowFieldTo(Cell(0, 0), everywhere)
        assertEquals(UNREACHABLE, flow[Cell(-1, 0)])
        assertEquals(UNREACHABLE, flow[Cell(COLUMNS, 0)])
        assertEquals(UNREACHABLE, flow[Cell(0, ROWS)])
    }

    @Test
    fun `a walled off cell cannot reach the exit`() {
        // Box the corner cell in completely.
        val walls = setOf(Cell(1, 0), Cell(0, 1))
        val flow = flowFieldTo(Cell(5, 5)) { everywhere(it) && it !in walls }
        assertTrue(!flow.canReachExit(Cell(0, 0)))
        assertNull(flow.next(Cell(0, 0)))
    }

    @Test
    fun `a blocked target makes everything unreachable`() {
        val flow = flowFieldTo(Cell(3, 3)) { it != Cell(3, 3) }
        assertTrue(!flow.canReachExit(Cell(0, 0)))
    }

    @Test
    fun `next always steps closer to the exit`() {
        val exit = Cell(4, ROWS - 1)
        val walls = setOf(Cell(4, 8), Cell(3, 8), Cell(5, 8))
        val flow = flowFieldTo(exit) { everywhere(it) && it !in walls }

        var cell = Cell(4, 0)
        var guard = 0
        while (cell != exit && guard < 200) {
            val next = flow.next(cell)!!
            assertTrue("$next was not closer than $cell", flow[next] < flow[cell])
            cell = next
            guard++
        }
        assertEquals(exit, cell)
    }

    @Test
    fun `the exit itself has nowhere further to go`() {
        val exit = Cell(2, 2)
        val flow = flowFieldTo(exit, everywhere)
        assertNull(flow.next(exit))
    }

    @Test
    fun `a route runs from the start to the exit without gaps`() {
        val exit = Cell(0, ROWS - 1)
        val walls = (0..5).map { Cell(it, 7) }.toSet()
        val flow = flowFieldTo(exit) { everywhere(it) && it !in walls }

        val route = flow.routeFrom(Cell(0, 0))
        assertEquals(Cell(0, 0), route.first())
        assertEquals(exit, route.last())
        route.zipWithNext { from, to ->
            val steps = kotlin.math.abs(to.col - from.col) + kotlin.math.abs(to.row - from.row)
            assertEquals("$from to $to was not a single step", 1, steps)
        }
        // It had to go round the wall, so it is longer than the straight run down.
        assertTrue(route.size > ROWS)
    }

    @Test
    fun `a route from a sealed off cell is empty`() {
        val walls = setOf(Cell(1, 0), Cell(0, 1))
        val flow = flowFieldTo(Cell(5, 5)) { everywhere(it) && it !in walls }
        assertTrue(flow.routeFrom(Cell(0, 0)).isEmpty())
    }

    @Test
    fun `walking keeps going straight when two routes are equally short`() {
        // On an open board there are many shortest routes. Preferring the current direction
        // stops an enemy dithering between two of them.
        val flow = flowFieldTo(Cell(0, 0), everywhere)
        val straightOn = flow.next(Cell(4, 4), preferred = 0 to -1)
        assertEquals(Cell(4, 3), straightOn)
        val turning = flow.next(Cell(4, 4), preferred = -1 to 0)
        assertEquals(Cell(3, 4), turning)
    }

    @Test
    fun `a preferred direction is ignored when it does not get closer`() {
        val flow = flowFieldTo(Cell(0, 0), everywhere)
        // Heading away from the exit cannot be honoured.
        val next = flow.next(Cell(4, 4), preferred = 0 to 1)!!
        assertTrue(flow[next] < flow[Cell(4, 4)])
    }
}
