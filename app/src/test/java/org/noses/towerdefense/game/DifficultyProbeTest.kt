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

    /** One board for the length comparison, so only the number of waves differs. */
    private fun board() = generateMap(Random(7))

    private fun scenarios(): List<GameRun> = listOf(
        // The headline question now that difficulty is keyed to the wave number: does each
        // length land where it should? Same board, same competent player, three game lengths.
        // 10 should be a sampler, 20 a real game, 30 an endurance run.
        Probe.play("generated", 10, board(), Mazer(TowerKind.SQUARE), seed = 7),
        Probe.play("generated", 20, board(), Mazer(TowerKind.SQUARE), seed = 7),
        Probe.play("generated", 30, board(), Mazer(TowerKind.SQUARE), seed = 7),

        // The same three for a player who builds and levels well but never lengthens the route.
        // Mazing is meant to be worth doing, so these should sit behind the runs above.
        Probe.play("no maze", 10, board(), Upgrader(TowerKind.SQUARE, 6), seed = 7),
        Probe.play("no maze", 20, board(), Upgrader(TowerKind.SQUARE, 6), seed = 7),
        Probe.play("no maze", 30, board(), Upgrader(TowerKind.SQUARE, 6), seed = 7),

        // The floor: never upgrades at all. Expected to fall behind, since difficulty now
        // climbs with the wave number whether or not the defence deepens.
        Probe.play("no levels", 10, board(), Spam(TowerKind.SQUARE), seed = 7),
        Probe.play("no levels", 20, board(), Spam(TowerKind.SQUARE), seed = 7),

        // Tower kinds at one length, all levelling, so nothing is confounded.
        Probe.play("kinds", 20, Fixtures.serpentine(), Upgrader(TowerKind.SQUARE, 4)),
        Probe.play("kinds", 20, Fixtures.serpentine(), Upgrader(TowerKind.HEXAGON, 4)),
        Probe.play("kinds", 20, Fixtures.serpentine(), Upgrader(TowerKind.PLUS, 4)),
        Probe.play("kinds", 20, Fixtures.serpentine(), Mixed(levelUp = true)),

        // A couple of fixed boards, so a run of the report is comparable with the last one even
        // when map generation changes.
        Probe.play("serpentine", 20, Fixtures.serpentine(), Mazer(TowerKind.SQUARE)),
        Probe.play("open field", 20, Fixtures.openField(), Mazer(TowerKind.SQUARE)),
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
