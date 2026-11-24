package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    @Test
    fun `direction is a unit vector`() {
        val dir = directionTo(Point(1f, 1f), Point(4f, 5f))
        assertEquals(1f, kotlin.math.hypot(dir.x, dir.y), 1e-4f)
        assertEquals(0.6f, dir.x, 1e-4f)
        assertEquals(0.8f, dir.y, 1e-4f)
    }

    @Test
    fun `direction to the same spot does not divide by zero`() {
        val dir = directionTo(Point(2f, 2f), Point(2f, 2f))
        assertEquals(1f, kotlin.math.hypot(dir.x, dir.y), 1e-4f)
    }

    @Test
    fun `a ray leaves the board at the edge`() {
        // Straight right from the middle of a 9 x 16 board.
        val exit = rayExit(Point(4.5f, 8f), Point(1f, 0f), 9f, 16f)
        assertEquals(9f, exit.x, 1e-3f)
        assertEquals(8f, exit.y, 1e-3f)

        // Straight up leaves through the top.
        val up = rayExit(Point(4.5f, 8f), Point(0f, -1f), 9f, 16f)
        assertEquals(0f, up.y, 1e-3f)

        // A diagonal leaves through whichever edge comes first.
        val diagonal = rayExit(Point(4.5f, 8f), Point(0.7071f, 0.7071f), 9f, 16f)
        assertEquals(9f, diagonal.x, 1e-2f)
        assertTrue(diagonal.y in 0f..16f)
    }

    @Test
    fun `a point on the ray has no offset and one beside it does`() {
        val origin = Point(0f, 0f)
        val dir = Point(1f, 0f)
        assertEquals(0f, offsetFromRay(Point(5f, 0f), origin, dir)!!, 1e-4f)
        assertEquals(3f, offsetFromRay(Point(5f, 3f), origin, dir)!!, 1e-4f)
    }

    @Test
    fun `a point behind the ray cannot be hit`() {
        val origin = Point(0f, 0f)
        val dir = Point(1f, 0f)
        assertNull(offsetFromRay(Point(-5f, 0f), origin, dir))
        assertNotNull(offsetFromRay(Point(5f, 0f), origin, dir))
    }

    @Test
    fun `distance is symmetric`() {
        val a = Point(1f, 2f)
        val b = Point(4f, 6f)
        assertEquals(5f, distanceBetween(a, b), 1e-4f)
        assertEquals(distanceBetween(a, b), distanceBetween(b, a), 1e-6f)
    }
}
