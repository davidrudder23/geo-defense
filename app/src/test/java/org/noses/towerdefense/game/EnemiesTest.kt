package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemiesTest {

    private fun lane() = Fixtures.lane()

    private fun flowFor(map: GameMap) = flowFieldOn(map) { map.isOpen(it) }

    @Test
    fun `the three kinds play differently`() {
        // The triangle is the fastest and the flimsiest.
        assertTrue(TRIANGLE_ENEMY.speed > SQUARE_ENEMY.speed)
        assertTrue(TRIANGLE_ENEMY.speed > CIRCLE_ENEMY.speed)
        assertTrue(TRIANGLE_ENEMY.baseHp < SQUARE_ENEMY.baseHp)
        // The square is the tank: most health, slowest, worst to let through.
        assertTrue(SQUARE_ENEMY.baseHp > CIRCLE_ENEMY.baseHp)
        assertTrue(SQUARE_ENEMY.leakDamage > TRIANGLE_ENEMY.leakDamage)
        // Only the circle is armoured.
        assertEquals(0, SQUARE_ENEMY.armor)
        assertEquals(0, TRIANGLE_ENEMY.armor)
        assertTrue(CIRCLE_ENEMY.armor > 0)
    }

    @Test
    fun `armour means a circle needs many hits`() {
        val circle = Enemy(CIRCLE_ENEMY)
        var hits = 0
        while (circle.alive && hits < 100) {
            circle.hit(SQUARE_TOWER.damage)
            hits++
        }
        assertFalse(circle.alive)
        assertTrue("a circle died in $hits bullets", hits >= 5)
    }

    @Test
    fun `a triangle dies to a couple of bullets`() {
        val triangle = Enemy(TRIANGLE_ENEMY)
        triangle.hit(SQUARE_TOWER.damage)
        triangle.hit(SQUARE_TOWER.damage)
        assertFalse(triangle.alive)
    }

    @Test
    fun `armour never blocks a hit completely`() {
        // Even a weak hit has to do something, or an armoured enemy would be invincible.
        val circle = Enemy(CIRCLE_ENEMY)
        val dealt = circle.hit(1)
        assertTrue(dealt > 0)
        assertTrue(circle.hp < circle.maxHp)
    }

    @Test
    fun `damage never overshoots the health left`() {
        val triangle = Enemy(TRIANGLE_ENEMY)
        val dealt = triangle.hit(10_000)
        assertEquals(triangle.maxHp, dealt)
        assertEquals(0, triangle.hp)
        // And a corpse takes no further damage.
        assertEquals(0, triangle.hit(50))
    }

    @Test
    fun `wave scaling raises health`() {
        val plain = Enemy(SQUARE_ENEMY, hpScale = 1f)
        val tough = Enemy(SQUARE_ENEMY, hpScale = 3f)
        assertTrue(tough.maxHp > plain.maxHp)
        assertEquals(plain.maxHp * 3, tough.maxHp, 1)
    }

    @Test
    fun `an enemy finds its way to the exit and says so`() {
        val map = lane()
        val flow = flowFor(map)
        val enemy = Enemy(SQUARE_ENEMY).apply { spawnAt(map.entry, flow) }
        assertEquals(map.entry.center(), enemy.position)

        var arrived = false
        var guard = 0
        while (!arrived && guard < 10_000) {
            arrived = enemy.advance(1f / 60f, map.exit, flow)
            guard++
        }
        assertTrue("never reached the exit", arrived)
        assertEquals(map.exit.center(), enemy.position)
    }

    @Test
    fun `a faster enemy gets further in the same time`() {
        val map = lane()
        val flow = flowFor(map)
        val square = Enemy(SQUARE_ENEMY).apply { spawnAt(map.entry, flow) }
        val triangle = Enemy(TRIANGLE_ENEMY).apply { spawnAt(map.entry, flow) }
        repeat(60) {
            square.advance(1f / 60f, map.exit, flow)
            triangle.advance(1f / 60f, map.exit, flow)
        }
        assertTrue(triangle.remainingSteps(flow) < square.remainingSteps(flow))
    }

    @Test
    fun `an enemy faces the way it is walking`() {
        val map = lane()
        val flow = flowFor(map)
        val enemy = Enemy(SQUARE_ENEMY).apply { spawnAt(map.entry, flow) }
        enemy.advance(0.5f, map.exit, flow)
        // Walking straight down the board is +y, which is a quarter turn in radians.
        assertEquals(Math.PI.toFloat() / 2f, enemy.heading, 0.05f)
    }

    @Test
    fun `an enemy re-routes when the maze changes under it`() {
        // Straight down column 3 to begin with.
        val map = Fixtures.openField()
        val direct = flowFieldOn(map) { map.isOpen(it) }
        val enemy = Enemy(SQUARE_ENEMY).apply { spawnAt(map.entry, direct) }
        repeat(120) { enemy.advance(1f / 60f, map.exit, direct) }
        val wentStraight = enemy.cell

        // Now wall off the column ahead of it and hand it the new routing.
        val wall = (0..4).map { Cell(it, wentStraight.row + 2) }.toSet()
        val detour = flowFieldOn(map) { map.isOpen(it) && it !in wall }
        repeat(240) { enemy.advance(1f / 60f, map.exit, detour) }

        assertTrue("walked into the wall", enemy.cell !in wall)
        assertTrue("did not go round", enemy.cell.col > 0)
        assertTrue(!enemy.stranded)
    }

    @Test
    fun `an enemy with nowhere to go stands still rather than wandering off`() {
        val map = Fixtures.openField()
        val flow = flowFor(map)
        val enemy = Enemy(SQUARE_ENEMY).apply { spawnAt(map.entry, flow) }

        // A field where nothing can reach the exit at all.
        val sealed = flowFieldOn(map) { false }
        repeat(120) { enemy.advance(1f / 60f, map.exit, sealed) }

        assertTrue(enemy.stranded)
        assertTrue(enemy.position.x in 0f..COLUMNS.toFloat())
        assertTrue(enemy.position.y in 0f..ROWS.toFloat())
    }

    @Test
    fun `a fast enemy still turns corners instead of cutting through walls`() {
        // One frame at 5x covers several cells; the movement budget has to be spent cell by
        // cell or a triangle would cut the corner and pass through a blocker.
        val map = Fixtures.serpentine()
        val flow = flowFor(map)
        val enemy = Enemy(TRIANGLE_ENEMY, speedScale = 5f).apply { spawnAt(map.entry, flow) }
        repeat(200) {
            enemy.advance(1f / 60f, map.exit, flow)
            assertTrue("walked through a blocker at ${enemy.cell}", enemy.cell !in map.blockers)
        }
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected $expected +/- $tolerance but was $actual",
            kotlin.math.abs(expected - actual) <= tolerance)
    }
}
