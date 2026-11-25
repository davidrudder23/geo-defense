package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The maze the player builds makes the enemies tougher, in proportion to the extra walking they
 * caused.
 *
 * Without this, mazing is not a strategy but the answer: measurement showed it was worth more
 * than every other advantage together. These pin the rule down, including the two ways it could
 * be got round.
 */
class MazeThreatTest {

    private fun engine(map: GameMap, waves: Int = 20) =
        GameEngine(totalWaves = waves, map = map, random = Random(42))

    /**
     * Builds towers that lengthen the route, and reports how long it ended up.
     *
     * Capped at what the starting money buys, since the engine will not let a test invent gold.
     */
    private fun GameEngine.mazeUp(limit: Int = STARTING_MONEY / 20): Int {
        var built = 0
        while (built < limit) {
            var best: Cell? = null
            var bestLength = flow[map.exit]
            for (row in 0 until ROWS) {
                for (col in 0 until COLUMNS) {
                    val cell = Cell(col, row)
                    if (towerAt(cell) != null) continue
                    if (!map.isBuildable(cell.col, cell.row)) continue
                    if (!leavesAWayThrough(cell)) continue
                    val length = flowFieldTo(map.exit) { at ->
                        map.isOpen(at) && at != cell && towerAt(at) == null
                    }[map.entry]
                    if (length != UNREACHABLE && length > bestLength) {
                        best = cell
                        bestLength = length
                    }
                }
            }
            val cell = best ?: break
            if (!build(TowerKind.SQUARE, cell)) break
            built++
        }
        return flow[map.entry]
    }

    @Test
    fun `an untouched board carries no extra threat`() {
        val engine = engine(Fixtures.openField())
        assertEquals(1f, engine.mazeFactor, 0.0001f)
        assertEquals(1f, engine.threatScale, 0.0001f)
    }

    @Test
    fun `a route the map forces is not the player's doing`() {
        // The serpentine's long way round is the map's character, not something the player built,
        // so it must not arrive pre-punished.
        val engine = engine(Fixtures.serpentine())
        assertTrue("the fixture should force a long walk", engine.flow[engine.map.entry] > 60)
        assertEquals(1f, engine.mazeFactor, 0.0001f)
        assertEquals(1f, engine.threatScale, 0.0001f)
    }

    @Test
    fun `building a longer way round raises the threat`() {
        val engine = engine(Fixtures.openField())
        val before = engine.flow[engine.map.entry]
        val after = engine.mazeUp()

        assertTrue("the route did not get longer: $before to $after", after > before)
        assertEquals(after.toFloat() / before, engine.mazeFactor, 0.0001f)
        assertTrue("threat did not rise: ${engine.threatScale}", engine.threatScale > 1f)
    }

    @Test
    fun `selling the maze back gives the threat up again`() {
        val engine = engine(Fixtures.openField())
        engine.mazeUp()
        assertTrue(engine.threatScale > 1f)

        while (engine.towers.isNotEmpty()) engine.sell(engine.towers.first())
        assertEquals(1f, engine.threatScale, 0.0001f)
    }

    @Test
    fun `enemies in a mazed game have more health than in a plain one`() {
        fun firstEnemyHp(maze: Boolean): Int {
            val engine = engine(Fixtures.openField())
            if (maze) engine.mazeUp()
            engine.startWave()
            engine.update(1f / 60f)
            return engine.enemies.first().hp
        }

        val plain = firstEnemyHp(maze = false)
        val mazed = firstEnemyHp(maze = true)
        assertTrue("plain $plain, mazed $mazed", mazed > plain)
    }

    @Test
    fun `mazing once a wave is running still counts`() {
        // The hole worth closing: keep the path short, start the wave cheap, then wall it up.
        // Because the threat is read as each enemy sets off, later arrivals answer for the maze.
        val engine = engine(Fixtures.openField())
        engine.startWave()
        engine.update(1f / 60f)
        val early = engine.enemies.first().hp

        engine.mazeUp()
        // Run on until something that spawned after the walls went up is on the board.
        var guard = 0
        while (engine.enemies.size < 2 && guard < 3000) {
            engine.update(1f / 60f)
            guard++
        }
        val late = engine.enemies.last().hp
        assertTrue("early $early, late $late", late > early)
    }

    @Test
    fun `the threat is proportional to the extra distance`() {
        val engine = engine(Fixtures.openField())
        engine.mazeUp()
        // MAZE_THREAT of 1 means exactly proportional: twice the walk, twice the health.
        assertEquals(engine.mazeFactor, engine.threatScale, 0.0001f)
    }
}
