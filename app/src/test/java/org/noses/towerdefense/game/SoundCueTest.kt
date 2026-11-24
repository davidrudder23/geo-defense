package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SoundCueTest {

    private fun straightMap(): GameMap = GameMap((0 until ROWS).map { Cell(0, it) })

    private fun engine(waves: Int = 10, map: GameMap = straightMap()) =
        GameEngine(totalWaves = waves, map = map, random = Random(42))

    private fun GameEngine.run(seconds: Float, step: Float = 1f / 60f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            update(step)
            elapsed += step
        }
    }

    private fun GameEngine.collect(seconds: Float): List<SoundCue> {
        val heard = ArrayList<SoundCue>()
        var elapsed = 0f
        while (elapsed < seconds) {
            update(1f / 60f)
            drainSounds(heard)
            elapsed += 1f / 60f
        }
        return heard
    }

    @Test
    fun `every tower has its own shot sound`() {
        val cues = TowerKind.entries.map { shotCueFor(it) }
        assertEquals(cues.size, cues.toSet().size)
    }

    @Test
    fun `every enemy has its own death sound`() {
        val cues = EnemyKind.entries.map { deathCueFor(it) }
        assertEquals(cues.size, cues.toSet().size)
    }

    @Test
    fun `draining sounds clears them`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(1, 2))
        engine.startWave()
        engine.run(3f)

        val first = ArrayList<SoundCue>()
        engine.drainSounds(first)
        assertTrue(first.isNotEmpty())

        val second = ArrayList<SoundCue>()
        engine.drainSounds(second)
        assertTrue("sounds were handed out twice", second.isEmpty())
    }

    @Test
    fun `a bullet tower reports its shots`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(1, 2))
        engine.startWave()
        val heard = engine.collect(4f)
        assertTrue(SoundCue.BULLET_SHOT in heard)
        assertTrue("heard another tower's shot", SoundCue.LASER_SHOT !in heard)
    }

    @Test
    fun `a blast tower reports both the launch and the explosion`() {
        val engine = engine()
        engine.build(TowerKind.HEXAGON, Cell(1, 6))
        engine.startWave()
        val heard = engine.collect(8f)
        assertTrue(SoundCue.BLAST_LAUNCH in heard)
        assertTrue(SoundCue.BLAST_IMPACT in heard)
    }

    @Test
    fun `a laser tower reports its shots`() {
        val engine = engine()
        engine.build(TowerKind.PLUS, Cell(1, 8))
        engine.startWave()
        val heard = engine.collect(5f)
        assertTrue(SoundCue.LASER_SHOT in heard)
    }

    @Test
    fun `killing an enemy reports the sound for that kind of enemy`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(1, 2))
        engine.build(TowerKind.SQUARE, Cell(1, 4))
        engine.startWave()
        val heard = engine.collect(14f)
        // Wave 1 is squares only, so that is the only death sound that can have happened.
        assertTrue(SoundCue.SQUARE_DOWN in heard)
        assertTrue(SoundCue.TRIANGLE_DOWN !in heard)
        assertTrue(SoundCue.CIRCLE_DOWN !in heard)
    }

    @Test
    fun `upgrading reports a sound`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(3, 3))
        val tower = engine.towerAt(Cell(3, 3))!!

        val before = ArrayList<SoundCue>()
        engine.drainSounds(before)
        assertTrue(SoundCue.UPGRADE !in before)

        engine.upgrade(tower)
        val after = ArrayList<SoundCue>()
        engine.drainSounds(after)
        assertEquals(listOf(SoundCue.UPGRADE), after)
    }

    @Test
    fun `a refused upgrade is silent`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(3, 3))
        val tower = engine.towerAt(Cell(3, 3))!!
        // Max it out, then try once more.
        while (tower.canUpgrade) engine.upgrade(tower)

        val drained = ArrayList<SoundCue>()
        engine.drainSounds(drained)
        drained.clear()

        engine.upgrade(tower)
        engine.drainSounds(drained)
        assertTrue("a rejected upgrade still made a noise", drained.isEmpty())
    }

    @Test
    fun `a quiet frame costs nothing`() {
        val engine = engine()
        val into = ArrayList<SoundCue>()
        engine.update(1f / 60f)
        engine.drainSounds(into)
        assertTrue(into.isEmpty())
    }
}
