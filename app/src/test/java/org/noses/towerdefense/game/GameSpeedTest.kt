package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** The speed button multiplies game time, so these pin down that it stays the same game. */
class GameSpeedTest {

    private fun engine() =
        GameEngine(totalWaves = 10, map = Fixtures.lane(), random = Random(42))

    @Test
    fun `the speed button cycles one two five and back to one`() {
        assertEquals(2, org.noses.towerdefense.ui.nextSpeed(1))
        assertEquals(5, org.noses.towerdefense.ui.nextSpeed(2))
        assertEquals(1, org.noses.towerdefense.ui.nextSpeed(5))
    }

    @Test
    fun `running at speed covers the same ground as running for longer`() {
        val slow = engine()
        val fast = engine()
        slow.startWave()
        fast.startWave()

        // Two seconds of game time: sixty frames at 1x, twelve frames at 5x.
        repeat(120) { slow.advance(1f / 60f) }
        repeat(24) { fast.advance(5f / 60f) }

        assertEquals(slow.enemies.size, fast.enemies.size)
        val slowLead = slow.enemies.minOf { it.remainingSteps(slow.flow) }
        val fastLead = fast.enemies.minOf { it.remainingSteps(fast.flow) }
        assertTrue(
            "1x had $slowLead left but 5x had $fastLead",
            abs(slowLead - fastLead) < 0.1f,
        )
    }

    @Test
    fun `speeding up does not let enemies slip past the defence`() {
        // The same board and the same towers should kill about the same number either way.
        fun play(multiplier: Int, frames: Int): GameEngine {
            val engine = engine()
            listOf(Cell(1, 3), Cell(1, 7), Cell(1, 11)).forEach {
                engine.build(TowerKind.SQUARE, it)
            }
            engine.startWave()
            repeat(frames) { engine.advance(multiplier / 60f) }
            return engine
        }

        val slow = play(1, 60 * 20)
        val fast = play(5, 60 * 4)

        assertEquals(slow.enemiesKilled, fast.enemiesKilled)
        assertEquals(slow.enemiesLeaked, fast.enemiesLeaked)
        assertEquals(slow.lives, fast.lives)
    }

    @Test
    fun `advance splits long slices into steps`() {
        // One big slice must not teleport an enemy; MAX_STEP_SECONDS caps each step.
        val engine = engine()
        engine.startWave()
        engine.advance(1f / 60f)
        val enemy = engine.enemies.first()
        val before = enemy.remainingSteps(engine.flow)

        engine.advance(0.2f)
        val moved = before - enemy.remainingSteps(engine.flow)
        // 0.2s at wave-one speed is a fraction of a cell, not a leap down the board.
        assertTrue("moved $moved cells", moved < 0.5f)
    }

    @Test
    fun `a huge slice is capped rather than freezing the game`() {
        val engine = engine()
        engine.startWave()
        engine.advance(1f / 60f)
        val enemy = engine.enemies.first()
        val before = enemy.remainingSteps(engine.flow)

        // Thirty seconds of stall at 5x would be 3000 steps if it were not capped.
        engine.advance(150f)
        val moved = before - enemy.remainingSteps(engine.flow)
        val ceiling = MAX_STEP_SECONDS * MAX_STEPS_PER_FRAME * enemy.stats.speed * 1.5f
        assertTrue("moved $moved cells, ceiling $ceiling", moved <= ceiling)
    }
}
