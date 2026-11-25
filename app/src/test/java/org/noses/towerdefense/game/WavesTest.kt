package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WavesTest {

    @Test
    fun `only the four advertised game lengths exist`() {
        assertEquals(listOf(10, 20, 25, 30), WAVE_COUNTS)
    }

    @Test
    fun `every wave of every game length has enemies`() {
        WAVE_COUNTS.forEach { total ->
            (1..total).forEach { number ->
                val wave = buildWave(number, total, Random(number.toLong()))
                assertTrue("wave $number of $total was empty", wave.size > 0)
            }
        }
    }

    @Test
    fun `spawn times never go backwards`() {
        val wave = buildWave(8, 20, Random(3))
        wave.spawns.zipWithNext { earlier, later ->
            assertTrue(later.time >= earlier.time)
        }
        assertEquals(0f, wave.spawns.first().time, 1e-6f)
    }

    @Test
    fun `waves get bigger and tougher as a game goes on`() {
        WAVE_COUNTS.forEach { total ->
            val first = buildWave(1, total, Random(1))
            val last = buildWave(total, total, Random(1))
            assertTrue("$total-wave game did not grow", last.size > first.size)
            assertTrue("$total-wave game did not toughen", last.hpScale > first.hpScale)
            assertTrue(last.speedScale > first.speedScale)
        }
    }

    @Test
    fun `a wave is the same wave whatever the game length`() {
        // Difficulty is keyed to the wave's own number, so wave 8 is wave 8 in every game. This
        // is what makes a longer game a harder one rather than the same arc stretched out.
        val inShort = buildWave(8, 10, Random(1))
        val inLong = buildWave(8, 30, Random(1))
        assertEquals(inShort.hpScale, inLong.hpScale, 0.001f)
        assertEquals(inShort.size, inLong.size)
        assertEquals(inShort.speedScale, inLong.speedScale, 0.001f)
    }

    @Test
    fun `choosing a longer game is choosing a harder one`() {
        // The end of a 30-wave game has to climb well past where a 10-wave game stops, or the
        // length picker is only changing how long you sit there.
        val shortEnd = buildWave(10, 10, Random(1))
        val longEnd = buildWave(30, 30, Random(1))
        assertTrue(
            "10-wave ended at ${shortEnd.hpScale}, 30-wave at ${longEnd.hpScale}",
            longEnd.hpScale > shortEnd.hpScale * 4f,
        )
        assertTrue(longEnd.size > shortEnd.size)
    }

    @Test
    fun `the first wave is only squares, and the other kinds arrive later`() {
        val first = buildWave(1, 20, Random(1))
        assertTrue(first.spawns.all { it.kind == EnemyKind.SQUARE })

        val second = buildWave(2, 20, Random(1))
        assertTrue(second.spawns.any { it.kind == EnemyKind.TRIANGLE })

        val third = buildWave(3, 20, Random(1))
        assertTrue(third.spawns.any { it.kind == EnemyKind.CIRCLE })
    }

    @Test
    fun `clearing a later wave pays more`() {
        val early = buildWave(2, 20, Random(1))
        val late = buildWave(18, 20, Random(1))
        assertTrue(late.clearBonus > early.clearBonus)
    }

    @Test
    fun `a single wave game is handled without dividing by zero`() {
        val wave = buildWave(1, 1, Random(1))
        assertTrue(wave.size > 0)
        assertTrue(wave.hpScale.isFinite())
    }
}
