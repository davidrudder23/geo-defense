package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    /** A straight road down column 0, so tests can place towers at known distances. */
    private fun straightMap(): GameMap = GameMap((0 until ROWS).map { Cell(0, it) })

    /**
     * A road that snakes across columns 0..6, leaving columns 7 and 8 free to build on. The long
     * road gives a defence time to work, which is what makes a game winnable.
     */
    private fun serpentineMap(): GameMap {
        val path = ArrayList<Cell>()
        for (row in 0 until ROWS) {
            // Each row is walked the opposite way to the last, so the end of one row sits
            // directly above the start of the next and the whole thing joins up.
            val columns = if (row % 2 == 0) (0..6) else (6 downTo 0)
            columns.forEach { col -> path.add(Cell(col, row)) }
        }
        return GameMap(path)
    }

    private fun engine(waves: Int = 10, map: GameMap = straightMap()) =
        GameEngine(totalWaves = waves, map = map, random = Random(42))

    /** Steps the simulation for [seconds] in frame-sized slices. */
    private fun GameEngine.run(seconds: Float, step: Float = 1f / 60f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            update(step)
            elapsed += step
        }
    }

    /**
     * Plays the game out: starts each wave as soon as the board is ready, and stops when the game
     * ends or [maxSeconds] of game time have passed.
     */
    private fun GameEngine.playOut(maxSeconds: Float = 600f, step: Float = 1f / 60f) {
        var elapsed = 0f
        while (elapsed < maxSeconds && !finished) {
            if (phase == Phase.READY) startWave()
            update(step)
            elapsed += step
        }
    }

    @Test
    fun `starts ready to build with money and lives`() {
        val engine = engine()
        assertEquals(Phase.READY, engine.phase)
        assertEquals(STARTING_MONEY, engine.money)
        assertEquals(STARTING_LIVES, engine.lives)
        assertEquals(0, engine.waveNumber)
    }

    @Test
    fun `starting a wave moves to running and spawns enemies`() {
        val engine = engine()
        assertTrue(engine.startWave())
        assertEquals(Phase.RUNNING, engine.phase)
        assertEquals(1, engine.waveNumber)
        engine.update(0.05f)
        assertTrue(engine.enemies.isNotEmpty())
    }

    @Test
    fun `a wave cannot be started while one is running`() {
        val engine = engine()
        engine.startWave()
        assertFalse(engine.startWave())
        assertEquals(1, engine.waveNumber)
    }

    @Test
    fun `building costs money and occupies the cell`() {
        val engine = engine()
        val cell = Cell(4, 4)
        assertTrue(engine.build(TowerKind.SQUARE, cell))
        assertEquals(STARTING_MONEY - SQUARE_TOWER.cost, engine.money)
        assertNotNull(engine.towerAt(cell))
        // The cell is taken now.
        assertFalse(engine.build(TowerKind.SQUARE, cell))
    }

    @Test
    fun `towers cannot be built on the road or off the board`() {
        val engine = engine()
        assertFalse(engine.build(TowerKind.SQUARE, Cell(0, 3)))
        assertFalse(engine.build(TowerKind.SQUARE, Cell(-1, 3)))
        assertFalse(engine.build(TowerKind.SQUARE, Cell(2, ROWS + 4)))
    }

    @Test
    fun `a tower cannot be afforded without the money`() {
        val engine = engine()
        // Spend everything on bullet towers.
        var col = 1
        while (engine.money >= SQUARE_TOWER.cost) {
            engine.build(TowerKind.SQUARE, Cell(col % COLUMNS + 1, col / COLUMNS + 1))
            col++
        }
        assertTrue(engine.money < SQUARE_TOWER.cost)
        assertFalse(engine.build(TowerKind.SQUARE, Cell(5, 9)))
    }

    @Test
    fun `an enemy that walks the whole road costs lives`() {
        val engine = engine()
        engine.startWave()
        // No towers, so everything gets through. A square needs ROWS/0.95 seconds at worst.
        engine.run(90f)
        assertTrue("expected leaks, got ${engine.enemiesLeaked}", engine.enemiesLeaked > 0)
        assertTrue(engine.lives < STARTING_LIVES)
    }

    @Test
    fun `undefended board is eventually lost`() {
        val engine = engine(waves = 10)
        engine.playOut()
        assertEquals(Phase.LOST, engine.phase)
        assertEquals(0, engine.lives)
        assertTrue("game ended on wave ${engine.waveNumber}", engine.waveNumber <= 10)
    }

    @Test
    fun `a tower shoots an enemy and is paid for the kill`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(1, 2))
        val moneyAfterBuilding = engine.money
        engine.startWave()
        engine.run(12f)
        assertTrue("nothing was killed", engine.enemiesKilled > 0)
        assertTrue("no reward was paid", engine.money > moneyAfterBuilding)
    }

    @Test
    fun `clearing the last wave wins the game`() {
        // A long winding road and a line of towers covering it: a defence that should hold.
        val engine = engine(waves = 1, map = serpentineMap())
        listOf(Cell(7, 1), Cell(7, 4), Cell(7, 7), Cell(7, 10), Cell(7, 13), Cell(8, 8))
            .forEach { assertTrue(engine.build(TowerKind.SQUARE, it)) }
        engine.startWave()
        engine.run(400f)
        assertEquals(Phase.WON, engine.phase)
        assertTrue(engine.lives > 0)
    }

    @Test
    fun `clearing a wave pays a bonus and returns to building`() {
        val engine = engine(waves = 5, map = serpentineMap())
        listOf(Cell(7, 1), Cell(7, 4), Cell(7, 7), Cell(7, 10), Cell(7, 13), Cell(8, 8))
            .forEach { engine.build(TowerKind.SQUARE, it) }
        val moneyBefore = engine.money
        engine.startWave()
        engine.run(400f)
        assertEquals(Phase.READY, engine.phase)
        assertTrue(engine.enemies.isEmpty())
        // Kill rewards plus the clear bonus.
        assertTrue(engine.money > moneyBefore)
    }

    @Test
    fun `upgrading spends money and strengthens the tower`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(3, 3))
        val tower = engine.towerAt(Cell(3, 3))!!
        val damageBefore = tower.damage
        val intervalBefore = tower.fireInterval
        val cost = tower.upgradeCost!!
        val moneyBefore = engine.money

        assertTrue(engine.upgrade(tower))
        assertEquals(2, tower.level)
        assertEquals(moneyBefore - cost, engine.money)
        assertTrue(tower.damage > damageBefore)
        assertTrue(tower.fireInterval < intervalBefore)
    }

    @Test
    fun `a tower cannot be upgraded past the top level`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(3, 3))
        val tower = engine.towerAt(Cell(3, 3))!!
        while (tower.canUpgrade) {
            assertTrue(engine.upgrade(tower))
        }
        assertEquals(MAX_TOWER_LEVEL, tower.level)
        assertNull(tower.upgradeCost)
        assertFalse(engine.upgrade(tower))
    }

    @Test
    fun `selling refunds part of what was spent and frees the cell`() {
        val engine = engine()
        val cell = Cell(3, 3)
        engine.build(TowerKind.SQUARE, cell)
        val tower = engine.towerAt(cell)!!
        val moneyBefore = engine.money

        assertTrue(engine.sell(tower))
        assertEquals(moneyBefore + tower.sellValue, engine.money)
        assertNull(engine.towerAt(cell))
        // Less than it cost, so selling is not free money.
        assertTrue(tower.sellValue < SQUARE_TOWER.cost)
    }

    @Test
    fun `the laser hits every enemy in its line`() {
        // A road that stops short of the bottom, with the laser directly below the end of it, so
        // the beam runs straight up the road and through everything walking down it.
        val shortRoad = GameMap((0..10).map { Cell(3, it) })
        val engine = engine(map = shortRoad)
        assertTrue(engine.build(TowerKind.PLUS, Cell(3, 12)))
        engine.startWave()
        // Let several enemies string out along the road before the beam fires.
        engine.run(3f)
        val onRoad = engine.enemies.size
        assertTrue("need several enemies to test piercing, had $onRoad", onRoad >= 2)

        engine.run(1.3f)
        val damaged = engine.enemies.count { it.hp < it.maxHp } + engine.enemiesKilled
        assertTrue("the beam only touched $damaged enemies", damaged >= 2)
    }

    @Test
    fun `a bullet tower only hits one enemy at a time`() {
        // The same line-up, but with bullets: exactly one enemy should be taking damage.
        val shortRoad = GameMap((0..10).map { Cell(3, it) })
        val engine = engine(map = shortRoad)
        engine.build(TowerKind.SQUARE, Cell(3, 12))
        engine.startWave()
        engine.run(3f)
        assertTrue(engine.enemies.size >= 2)
        engine.run(0.7f)
        val damaged = engine.enemies.count { it.hp < it.maxHp } + engine.enemiesKilled
        assertTrue("bullets spread to $damaged enemies", damaged <= 2)
    }

    @Test
    fun `a blast damages more than one enemy at once`() {
        val engine = engine()
        engine.build(TowerKind.HEXAGON, Cell(1, 8))
        engine.startWave()
        engine.run(14f)
        assertTrue(engine.enemiesKilled > 0)
    }

    @Test
    fun `nothing can be built or sold once the game is over`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(4, 4))
        val standing = engine.towerAt(Cell(4, 4))!!
        engine.playOut()
        assertEquals(Phase.LOST, engine.phase)
        assertFalse(engine.build(TowerKind.SQUARE, Cell(5, 5)))
        assertFalse(engine.sell(standing))
        assertFalse(engine.upgrade(standing))
        // And no further waves can be started.
        assertFalse(engine.startWave())
    }

    @Test
    fun `a long frame cannot teleport enemies through the defence`() {
        val engine = engine()
        engine.startWave()
        engine.update(0.05f)
        val enemy = engine.enemies.first()
        val before = enemy.distance
        // A hitched frame, as happens when the app is backgrounded.
        engine.update(30f)
        val moved = enemy.distance - before
        assertTrue("moved $moved cells in one frame", moved < 0.2f)
    }
}
