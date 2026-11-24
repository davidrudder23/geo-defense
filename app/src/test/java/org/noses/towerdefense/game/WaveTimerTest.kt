package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The build clock between waves, and the gold paid for giving it up. */
class WaveTimerTest {

    private fun engine(waves: Int = 10) =
        GameEngine(totalWaves = waves, map = Fixtures.serpentine(), random = Random(42))

    private fun GameEngine.run(seconds: Float) {
        var elapsed = 0f
        while (elapsed < seconds) {
            update(1f / 60f)
            elapsed += 1f / 60f
        }
    }

    @Test
    fun `the clock starts full and counts down`() {
        val engine = engine()
        assertEquals(READY_SECONDS.toInt(), engine.readySecondsLeft)
        // Half way through a second, so rounding up cannot land either side of the boundary.
        engine.run(5.5f)
        assertEquals(10, engine.readySecondsLeft)
        assertEquals(Phase.READY, engine.phase)
    }

    @Test
    fun `the wave starts on its own when the clock runs out`() {
        val engine = engine()
        engine.run(READY_SECONDS + 0.5f)
        assertEquals(Phase.RUNNING, engine.phase)
        assertEquals(1, engine.waveNumber)
    }

    @Test
    fun `a wave that comes on its own pays nothing`() {
        val engine = engine()
        val before = engine.money
        engine.run(READY_SECONDS + 0.5f)
        assertEquals("waiting it out still paid a bonus", before, engine.money)
    }

    @Test
    fun `starting early pays two gold a second`() {
        val engine = engine()
        val before = engine.money
        val expected = engine.readySecondsLeft * RUSH_BONUS_PER_SECOND
        assertTrue(engine.startWave())
        assertEquals(before + expected, engine.money)
        assertEquals(READY_SECONDS.toInt() * 2, expected)
    }

    @Test
    fun `the bonus shrinks as the clock runs down`() {
        val engine = engine()
        val full = engine.rushBonus
        engine.run(10f)
        val late = engine.rushBonus
        assertTrue("the bonus did not shrink", late < full)
        val before = engine.money
        engine.startWave()
        assertEquals(before + late, engine.money)
    }

    @Test
    fun `the bonus is exactly what the button was showing`() {
        // Whatever number is on the button when it is pressed is what gets paid.
        val engine = engine()
        engine.run(7.3f)
        val shown = engine.readySecondsLeft
        val before = engine.money
        engine.startWave()
        assertEquals(before + shown * RUSH_BONUS_PER_SECOND, engine.money)
    }

    @Test
    fun `clearing a wave reopens a full build clock`() {
        val engine = engine(waves = 10)
        listOf(Cell(1, 0), Cell(4, 0), Cell(7, 0), Cell(2, 3), Cell(5, 3), Cell(7, 3))
            .forEach { engine.build(TowerKind.SQUARE, it) }
        engine.startWave()
        var elapsed = 0f
        while (elapsed < 400f && engine.phase != Phase.READY) {
            engine.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertEquals(Phase.READY, engine.phase)
        assertEquals(READY_SECONDS.toInt(), engine.readySecondsLeft)
    }

    @Test
    fun `the clock does not run while a wave is on`() {
        val engine = engine()
        engine.startWave()
        assertEquals(Phase.RUNNING, engine.phase)
        assertEquals(0, engine.readySecondsLeft)
        assertEquals(0, engine.rushBonus)
        engine.run(3f)
        assertEquals(Phase.RUNNING, engine.phase)
    }

    @Test
    fun `starting early reports a sound`() {
        val engine = engine()
        val heard = ArrayList<SoundCue>()
        engine.startWave()
        engine.drainSounds(heard)
        assertTrue(SoundCue.RUSH_BONUS in heard)
    }

    @Test
    fun `a wave arriving on its own is silent`() {
        val engine = engine()
        val heard = ArrayList<SoundCue>()
        var elapsed = 0f
        while (elapsed < READY_SECONDS + 0.5f) {
            engine.update(1f / 60f)
            engine.drainSounds(heard)
            elapsed += 1f / 60f
        }
        assertFalse(SoundCue.RUSH_BONUS in heard)
    }

    @Test
    fun `the clock stops once the game is over`() {
        val engine = engine(waves = 10)
        var elapsed = 0f
        while (elapsed < 900f && !engine.finished) {
            engine.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertEquals(Phase.LOST, engine.phase)
        val wave = engine.waveNumber
        engine.run(60f)
        assertEquals("waves kept starting after the game ended", wave, engine.waveNumber)
    }

    @Test
    fun `an undefended game now plays itself out with no input at all`() {
        // The clock means a game no longer waits for a player who never presses anything.
        val engine = engine(waves = 10)
        var elapsed = 0f
        while (elapsed < 900f && !engine.finished) {
            engine.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertTrue(engine.finished)
    }
}
