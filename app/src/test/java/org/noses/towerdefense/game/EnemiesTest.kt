package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemiesTest {

    private fun straightMap() = GameMap((0 until ROWS).map { Cell(0, it) })

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
    fun `an enemy walks the road and reports arriving at the end`() {
        val map = straightMap()
        val enemy = Enemy(SQUARE_ENEMY).apply { placeOn(map) }
        assertEquals(map.entry.center(), enemy.position)

        var arrived = false
        var guard = 0
        while (!arrived && guard < 10_000) {
            arrived = enemy.advance(1f / 60f, map)
            guard++
        }
        assertTrue("never reached the end", arrived)
        assertTrue(enemy.distance >= map.pathLength)
    }

    @Test
    fun `a faster enemy gets further in the same time`() {
        val map = straightMap()
        val square = Enemy(SQUARE_ENEMY).apply { placeOn(map) }
        val triangle = Enemy(TRIANGLE_ENEMY).apply { placeOn(map) }
        repeat(60) {
            square.advance(1f / 60f, map)
            triangle.advance(1f / 60f, map)
        }
        assertTrue(triangle.distance > square.distance)
    }

    @Test
    fun `an enemy faces the way it is walking`() {
        val map = straightMap()
        val enemy = Enemy(SQUARE_ENEMY).apply { placeOn(map) }
        enemy.advance(0.5f, map)
        // Walking straight down the board is +y, which is a quarter turn in radians.
        assertEquals(Math.PI.toFloat() / 2f, enemy.heading, 0.05f)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected $expected +/- $tolerance but was $actual",
            kotlin.math.abs(expected - actual) <= tolerance)
    }
}
