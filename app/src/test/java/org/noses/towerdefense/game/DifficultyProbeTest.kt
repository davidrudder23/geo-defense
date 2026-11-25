package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Runs the measurement rig and writes the difficulty report.
 *
 * The assertions here check the *instrument*, not the balance - a probe that silently measured
 * nothing would be worse than no probe at all. The balance reading is the report, which is
 * printed and written to `app/build/difficulty/report.md`.
 */
class DifficultyProbeTest {

    private fun scenarios(): List<GameRun> = listOf(
        // The same strategy over the three game lengths. Difficulty currently scales on progress
        // through the game, so these should look alike; that is the thing to watch if that
        // changes.
        Probe.play("serpentine", 10, Fixtures.serpentine(), Spam(TowerKind.SQUARE)),
        Probe.play("serpentine", 20, Fixtures.serpentine(), Spam(TowerKind.SQUARE)),
        Probe.play("serpentine", 30, Fixtures.serpentine(), Spam(TowerKind.SQUARE)),

        // Same board and length, different ways of spending. If one of these is far ahead of the
        // others it is the dominant strategy, and that is a balance problem on its own.
        Probe.play("serpentine", 20, Fixtures.serpentine(), Upgrader(TowerKind.SQUARE, 4)),
        Probe.play("serpentine", 20, Fixtures.serpentine(), Mixed()),

        // Does one tower dominate? These four differ only in what gets built - same board, same
        // cap, all levelling with their spare money - so the comparison is not tangled up with
        // whether upgrades happened, which was what made the earlier Mixed-vs-Spam reading
        // worthless.
        Probe.play("kinds", 20, Fixtures.serpentine(), Upgrader(TowerKind.HEXAGON, 4)),
        Probe.play("kinds", 20, Fixtures.serpentine(), Upgrader(TowerKind.PLUS, 4)),
        Probe.play("kinds", 20, Fixtures.serpentine(), Mixed(levelUp = true)),

        // The strategy the maze invites, on an empty board so the maze is the player's doing and
        // not the map's. Three runs that differ by one thing each, so a result can be pinned on
        // the maze or on the upgrades rather than on both at once:
        //   spam        - neither
        //   maze only   - the maze, at level one throughout
        //   maze + levels - both
        Probe.play("open field", 20, Fixtures.openField(), Spam(TowerKind.SQUARE)),
        Probe.play("open field", 20, Fixtures.openField(), Mazer(TowerKind.SQUARE, levelUp = false)),
        Probe.play("open field", 20, Fixtures.openField(), Mazer(TowerKind.SQUARE)),
        Probe.play("serpentine", 20, Fixtures.serpentine(), Mazer(TowerKind.SQUARE)),

        // Identical to the second row except the player never presses the button early, which
        // isolates what the rush bonus is worth over a whole game.
        Probe.play("serpentine, patient", 20, Fixtures.serpentine(), Spam(TowerKind.SQUARE), rush = false),

        // Real generated boards, which is what a player actually gets. Three seeds, because the
        // board shape changes how much defence fits.
        Probe.play("generated #1", 20, generateMap(Random(1)), Spam(TowerKind.SQUARE), seed = 1),
        Probe.play("generated #2", 20, generateMap(Random(2)), Spam(TowerKind.SQUARE), seed = 2),
        Probe.play("generated #3", 20, generateMap(Random(3)), Spam(TowerKind.SQUARE), seed = 3),
    )

    @Test
    fun `difficulty report`() {
        val runs = scenarios()
        val report = Probe.report(runs)

        val directory = File("build/difficulty")
        directory.mkdirs()
        val file = File(directory, "report.md")
        file.writeText(report)

        println()
        runs.forEach { println(Probe.format(it)) }
        println("written to ${file.absolutePath}")

        assertTrue("the report came out empty", report.isNotEmpty())
    }

    @Test
    fun `the probe records every wave it played`() {
        val run = Probe.play("serpentine", 10, Fixtures.serpentine(), Spam(TowerKind.SQUARE))
        // A game that was not lost must have a row for every wave, numbered in order from one.
        assertEquals(Phase.WON, run.outcome)
        assertEquals(10, run.rows.size)
        assertEquals((1..10).toList(), run.rows.map { it.wave })
    }

    @Test
    fun `the probe measures something on every wave`() {
        val run = Probe.play("serpentine", 10, Fixtures.serpentine(), Spam(TowerKind.SQUARE))
        for (row in run.rows) {
            assertTrue("wave ${row.wave} had no enemies", row.enemies > 0)
            assertTrue("wave ${row.wave} resolved instantly", row.seconds > 0f)
            assertTrue("wave ${row.wave} reach out of range: ${row.reach}", row.reach in 0f..1f)
            // Every enemy either died or leaked, so the two together account for the wave.
            assertEquals(
                "wave ${row.wave} lost enemies somewhere",
                row.enemies,
                row.killed + row.leaked,
            )
        }
    }

    @Test
    fun `leaks and lives agree with each other`() {
        val run = Probe.play("serpentine", 20, Fixtures.serpentine(), Spam(TowerKind.SQUARE))
        assertEquals(run.livesLost, run.rows.sumOf { it.livesLost })
        // Nothing but a leak costs a life, and the cheapest leak costs one.
        for (row in run.rows) {
            assertTrue(
                "wave ${row.wave} lost ${row.livesLost} lives to ${row.leaked} leaks",
                row.livesLost >= row.leaked,
            )
            if (row.leaked == 0) assertEquals(0, row.livesLost)
        }
    }

    @Test
    fun `reach reads 100 percent when something leaks`() {
        // An undefended game is the one case where a leak is guaranteed, so it pins the top of
        // the scale.
        val run = Probe.play("undefended", 10, Fixtures.serpentine(), NoDefence())
        assertTrue("an undefended game should leak", run.leaks > 0)
        assertEquals(1f, run.rows.first().reach, 0.0001f)
        assertEquals(Phase.LOST, run.outcome)
    }

    @Test
    fun `the rush bonus is only paid when the wave is started early`() {
        val rushed = Probe.play("serpentine", 10, Fixtures.serpentine(), Spam(TowerKind.SQUARE))
        val patient = Probe.play(
            "serpentine", 10, Fixtures.serpentine(), Spam(TowerKind.SQUARE), rush = false,
        )
        assertTrue(rushed.rows.all { it.rushBonus > 0 })
        assertTrue(patient.rows.all { it.rushBonus == 0 })
    }

    @Test
    fun `the mazing strategy actually lengthens the route`() {
        // On an open field the shortest walk is a straight line, so any detour is the strategy's
        // doing rather than the board's.
        val map = Fixtures.openField()
        val bare = GameEngine(totalWaves = 20, map = map, random = Random(42))
        val before = bare.flow[map.entry]

        val mazed = GameEngine(totalWaves = 20, map = map, random = Random(42))
        Mazer(TowerKind.SQUARE).spend(mazed)
        val after = mazed.flow[map.entry]

        assertTrue("route went from $before to $after steps", after > before)
        assertTrue("the mazer built nothing", mazed.towers.isNotEmpty())
    }

    /** Builds nothing at all, to pin the bottom of the scale. */
    private class NoDefence : Strategy("no defence") {
        override fun spend(engine: GameEngine) = Unit
    }
}
