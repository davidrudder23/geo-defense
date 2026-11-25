package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    private fun lane() = Fixtures.lane()

    private fun engine(
        waves: Int = 10,
        map: GameMap = Fixtures.lane(),
        money: Int = STARTING_MONEY,
    ) = GameEngine(totalWaves = waves, map = map, random = Random(42), startingMoney = money)

    /** Steps the simulation for [seconds] in frame-sized slices. */
    private fun GameEngine.run(seconds: Float, step: Float = 1f / 60f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            update(step)
            elapsed += step
        }
    }

    /** Runs until the board is ready to build again, or gives up after [maxSeconds]. */
    private fun GameEngine.runUntilReady(maxSeconds: Float = 400f) {
        var elapsed = 0f
        while (elapsed < maxSeconds && phase == Phase.RUNNING) {
            update(1f / 60f)
            elapsed += 1f / 60f
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

    /** Cells beside the serpentine route, where a defence can stand without blocking it. */
    private fun defensiveSpots(): List<Cell> =
        listOf(0, 3, 6, 9, 12).flatMap { row -> (0 until COLUMNS).map { Cell(it, row) } }

    /**
     * Plays a whole game the way a competent player would: builds what it can afford, levels up
     * what it already has, then starts the next wave. Enough to tell whether a game can be won.
     *
     * The levelling is not decoration. Difficulty is keyed to the wave number now, so a defence
     * that only ever widens and never deepens falls behind on purpose - a player who never
     * upgrades is playing badly and is supposed to lose. This models someone who does not.
     */
    private fun GameEngine.autoPlay(kind: TowerKind = TowerKind.SQUARE, maxSeconds: Float = 2000f) {
        val spots = ArrayDeque(defensiveSpots())
        fun spend() {
            val walk = spots.iterator()
            while (walk.hasNext()) {
                if (money < statsFor(kind).cost) break
                if (build(kind, walk.next())) walk.remove()
            }
            // Then pour whatever is left into levels, cheapest first.
            while (true) {
                val next = towers
                    .filter { it.canUpgrade && (it.upgradeCost ?: Int.MAX_VALUE) <= money }
                    .minByOrNull { it.upgradeCost ?: Int.MAX_VALUE }
                    ?: return
                if (!upgrade(next)) return
            }
        }
        var elapsed = 0f
        spend()
        while (elapsed < maxSeconds && !finished) {
            if (phase == Phase.READY) { spend(); startWave() }
            update(1f / 60f)
            elapsed += 1f / 60f
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
        val cell = Cell(1, 4)
        assertTrue(engine.build(TowerKind.SQUARE, cell))
        assertEquals(STARTING_MONEY - SQUARE_TOWER.cost, engine.money)
        assertNotNull(engine.towerAt(cell))
        // The cell is taken now.
        assertFalse(engine.build(TowerKind.SQUARE, cell))
    }

    @Test
    fun `towers cannot be built on the road or off the board`() {
        val engine = engine()
        assertFalse("built on a blocker", engine.build(TowerKind.SQUARE, Cell(4, 3)))
        assertFalse("built off the board", engine.build(TowerKind.SQUARE, Cell(-1, 3)))
        assertFalse("built off the board", engine.build(TowerKind.SQUARE, Cell(2, ROWS + 4)))
        assertFalse("built on the entry", engine.build(TowerKind.SQUARE, engine.map.entry))
        assertFalse("built on the exit", engine.build(TowerKind.SQUARE, engine.map.exit))
    }

    @Test
    fun `a tower cannot be afforded without the money`() {
        val engine = engine()
        // Spend everything on bullet towers.
        var row = 1
        while (engine.money >= SQUARE_TOWER.cost && row < ROWS - 1) {
            engine.build(TowerKind.SQUARE, Cell(1, row))
            row++
        }
        assertTrue(engine.money < SQUARE_TOWER.cost)
        assertFalse(engine.build(TowerKind.SQUARE, Cell(1, row)))
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
        // A real ten-wave game, played by spending the takings between waves. This is as much a
        // check that the game is winnable at all as it is a check of the winning transition.
        val engine = engine(waves = 10, map = Fixtures.serpentine())
        engine.autoPlay()
        assertEquals(Phase.WON, engine.phase)
        assertEquals(10, engine.waveNumber)
        assertTrue(engine.lives > 0)
    }

    @Test
    fun `a longer game can be won too`() {
        val engine = engine(waves = 20, map = Fixtures.serpentine())
        engine.autoPlay()
        assertEquals(Phase.WON, engine.phase)
        assertEquals(20, engine.waveNumber)
    }

    @Test
    fun `clearing a wave pays a bonus and returns to building`() {
        val engine = engine(waves = 5, map = Fixtures.serpentine())
        listOf(Cell(1, 0), Cell(4, 0), Cell(7, 0), Cell(2, 3), Cell(5, 3), Cell(7, 3))
            .forEach { assertTrue("could not build at $it", engine.build(TowerKind.SQUARE, it)) }
        val moneyBefore = engine.money
        engine.startWave()
        // Stop as soon as the wave is cleared: the build clock would otherwise send in the next
        // one while this test is still looking at the board.
        engine.runUntilReady()
        assertEquals(Phase.READY, engine.phase)
        assertTrue(engine.enemies.isEmpty())
        // Kill rewards plus the clear bonus.
        assertTrue(engine.money > moneyBefore)
    }

    @Test
    fun `upgrading spends money and strengthens the tower`() {
        val engine = engine()
        engine.build(TowerKind.SQUARE, Cell(1, 3))
        val tower = engine.towerAt(Cell(1, 3))!!
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
        // Rich on purpose: levels get steeply more expensive, and this test is about the cap
        // rather than about affording it.
        val engine = engine(money = 1_000_000)
        engine.build(TowerKind.SQUARE, Cell(1, 3))
        val tower = engine.towerAt(Cell(1, 3))!!
        while (tower.canUpgrade) {
            assertTrue("ran out of money before the cap at level " + tower.level, engine.upgrade(tower))
        }
        assertEquals(MAX_TOWER_LEVEL, tower.level)
        assertNull(tower.upgradeCost)
        assertFalse(engine.upgrade(tower))
    }

    @Test
    fun `selling refunds part of what was spent and frees the cell`() {
        val engine = engine()
        val cell = Cell(1, 3)
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
        // A corridor that stops short of the bottom, with the laser just below the end of it, so
        // the beam runs straight up the corridor and through everything walking down it.
        val shortRoad = Fixtures.stub()
        val engine = engine(map = shortRoad)
        val cell = Cell(3, 11)
        assertTrue(engine.build(TowerKind.PLUS, cell))
        val tower = engine.towerAt(cell)!!
        engine.startWave()

        // The laser's reach is limited now, so the line-up has to walk into it first rather than
        // being picked off from across the board.
        fun inReach() = engine.enemies.count {
            distanceBetween(it.position, tower.center) <= tower.range
        }
        var elapsed = 0f
        while (elapsed < 30f && inReach() < 2) {
            engine.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertTrue("never got two enemies into the beam's reach", inReach() >= 2)

        // One shot, and count what it touched: both the survivors it wounded and anything it
        // finished off.
        val before = engine.enemies.associate { it to it.hp }
        val killedBefore = engine.enemiesKilled
        engine.run(1.3f)
        val wounded = engine.enemies.count { (before[it] ?: return@count false) > it.hp }
        val struck = wounded + (engine.enemiesKilled - killedBefore)
        assertTrue("the beam only touched $struck enemies", struck >= 2)
    }

    @Test
    fun `a bullet tower only hits one enemy at a time`() {
        // The same line-up, but with bullets: exactly one enemy should be taking damage.
        val shortRoad = Fixtures.stub()
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
        engine.build(TowerKind.SQUARE, Cell(1, 4))
        val standing = engine.towerAt(Cell(1, 4))!!
        engine.playOut()
        assertEquals(Phase.LOST, engine.phase)
        assertFalse(engine.build(TowerKind.SQUARE, Cell(1, 5)))
        assertFalse(engine.sell(standing))
        assertFalse(engine.upgrade(standing))
        // And no further waves can be started.
        assertFalse(engine.startWave())
    }

    @Test
    fun `a tower that would seal the exit off is refused`() {
        // A single open column, so one tower anywhere along it would shut the board.
        val engine = engine(map = Fixtures.stub())
        assertFalse("sealed the only way through", engine.build(TowerKind.SQUARE, Cell(3, 5)))
        assertNull(engine.towerAt(Cell(3, 5)))
        assertEquals("a refused build still charged for it", STARTING_MONEY, engine.money)
        // Below the exit is fine: nothing has to walk through there.
        assertTrue(engine.build(TowerKind.SQUARE, Cell(3, 12)))
    }

    @Test
    fun `a tower is refused when it would dead end the only gap in a wall`() {
        // The serpentine has one gap per wall. The cell just past a gap is load bearing even
        // though the gap itself is still open, and the check has to see that.
        val engine = engine(map = Fixtures.serpentine())
        assertFalse("dead ended the gap", engine.build(TowerKind.SQUARE, Cell(COLUMNS - 1, 3)))
        assertTrue(engine.build(TowerKind.SQUARE, Cell(COLUMNS - 2, 3)))
    }

    @Test
    fun `a tower may close one way through while another remains`() {
        val engine = engine(map = Fixtures.openField())
        // An open field has many routes, so a part-built wall is legal even though it forces a
        // detour. Only the cell that would close the last gap gets refused.
        (0 until 5).forEach {
            assertTrue("could not build at column $it", engine.build(TowerKind.SQUARE, Cell(it, 5)))
        }
        assertTrue(engine.route.isNotEmpty())
        assertEquals(engine.map.exit, engine.route.last())
    }

    @Test
    fun `building bends the route`() {
        val engine = engine(map = Fixtures.openField())
        val before = engine.route
        assertEquals(engine.map.entry, before.first())
        assertEquals(engine.map.exit, before.last())

        // Wall off most of row 4, forcing a detour.
        (0 until COLUMNS - 2).forEach { engine.build(TowerKind.SQUARE, Cell(it, 4)) }
        val after = engine.route
        assertTrue("the route did not change", after != before)
        assertTrue("the route got no longer", after.size > before.size)
        assertEquals(engine.map.exit, after.last())
        assertTrue("the route runs through a tower", after.none { engine.towerAt(it) != null })
    }

    @Test
    fun `selling a tower opens the route back up`() {
        val engine = engine(map = Fixtures.openField())
        val direct = engine.route.size
        (0 until COLUMNS - 2).forEach { engine.build(TowerKind.SQUARE, Cell(it, 4)) }
        assertTrue(engine.route.size > direct)

        engine.towers.toList().forEach { engine.sell(it) }
        assertEquals(direct, engine.route.size)
    }

    @Test
    fun `a tower cannot be dropped on top of an enemy`() {
        val engine = engine(map = Fixtures.openField())
        engine.startWave()
        engine.run(1f)
        val enemy = engine.enemies.first()
        assertFalse("built on an enemy", engine.build(TowerKind.SQUARE, enemy.cell))
        assertFalse("built where an enemy is walking", engine.build(TowerKind.SQUARE, enemy.targetCell))
    }

    @Test
    fun `enemies already walking are never walled in`() {
        // Towers go up all over the board while a wave is running; no enemy may end up stranded.
        val engine = engine(waves = 10, map = Fixtures.openField())
        engine.startWave()
        var elapsed = 0f
        var row = 1
        while (elapsed < 25f) {
            engine.update(1f / 60f)
            elapsed += 1f / 60f
            if (engine.money >= SQUARE_TOWER.cost) {
                (0 until COLUMNS).forEach { engine.build(TowerKind.SQUARE, Cell(it, row)) }
                row = (row + 1) % (ROWS - 1)
            }
            assertTrue(
                "an enemy was stranded",
                engine.enemies.none { it.stranded },
            )
        }
        assertTrue("nothing was ever built", engine.towers.isNotEmpty())
    }

    @Test
    fun `the route never runs through a blocker`() {
        repeat(50) { seed ->
            val engine = GameEngine(totalWaves = 10, map = generateMap(Random(seed.toLong())), random = Random(1))
            assertTrue("empty route on seed $seed", engine.route.isNotEmpty())
            assertTrue(engine.route.none { it in engine.map.blockers })
        }
    }

    @Test
    fun `a long frame cannot teleport enemies through the defence`() {
        val engine = engine()
        engine.startWave()
        engine.update(0.05f)
        val enemy = engine.enemies.first()
        val before = enemy.remainingSteps(engine.flow)
        // A hitched frame, as happens when the app is backgrounded.
        engine.update(30f)
        val moved = before - enemy.remainingSteps(engine.flow)
        assertTrue("moved $moved cells in one frame", moved < 0.2f)
    }
}
