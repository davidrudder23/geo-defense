package org.noses.towerdefense.game

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Searches for a difficulty curve that hits three targets at once.
 *
 * Hand-tuning three game lengths against each other is guesswork, because every parameter moves
 * all three. This plays the whole grid and scores it, which is the sort of thing the measurement
 * rig exists for.
 *
 * Opt in, because it plays hundreds of games:
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*CurveSweepTest" -Dsweep=1
 * ```
 */
class CurveSweepTest {

    /** What each game length is supposed to feel like, written as something measurable. */
    /**
     * Written as *fractions* of the life budget, because that budget now grows with the game
     * length: 20 lives for a 10-wave game, 30 for a 30-wave one. Absolute counts would not be
     * comparable across the three.
     */
    private data class Target(
        val waves: Int,
        val minLives: Float,
        val maxLives: Float,
        val maxReach: Float,
    )

    private val targets = listOf(
        // Easy: never seriously in danger, though the later waves should be visible.
        Target(waves = 10, minLives = 0.85f, maxLives = 1f, maxReach = 0.90f),
        // Difficult, but a competent player gets there: it has to cost real lives.
        Target(waves = 20, minLives = 0.25f, maxLives = 0.75f, maxReach = 1f),
        // Tough: won, but only just.
        Target(waves = 30, minLives = 0.05f, maxLives = 0.45f, maxReach = 1f),
    )

    /** No single wave may take more than this many lives, or the curve is hiding a cliff. */
    private val worstWaveAllowed = 7

    private data class Scored(
        val curve: DifficultyCurve,
        val runs: List<GameRun>,
        val hits: Int,
        val miss: Float,
    )

    /**
     * Boards to score every curve against.
     *
     * One board is not enough, and that is not a small point: ten sweeps were quietly fitting a
     * single map. Even once generation held the walk to 47-50 steps on all three, the same curve
     * won every game on one board and lost the 20- and 30-wave games on the other two - so shape
     * matters beyond length, and a curve picked on one board is a curve picked by accident.
     */
    private val boardSeeds = listOf(7, 2, 5)

    private fun score(curve: DifficultyCurve): Scored {
        // Every length on every board, so a curve has to work generally to score well.
        val runs = targets.flatMap { target ->
            boardSeeds.map { boardSeed ->
                Probe.play(
                    label = "${target.waves}w/s$boardSeed",
                    totalWaves = target.waves,
                    map = generateMap(kotlin.random.Random(boardSeed)),
                    strategy = Mazer(TowerKind.SQUARE),
                    seed = boardSeed,
                    curve = curve,
                )
            }
        }
        var hits = 0
        var miss = 0f
        for ((target, run) in targets.flatMap { t -> boardSeeds.map { t } }.zip(runs)) {
            val lost = run.outcome == Phase.LOST
            val livesOk = !lost && run.livesFraction in target.minLives..target.maxLives
            val reachOk = run.peakReach <= target.maxReach
            val cliffOk = run.worstWave <= worstWaveAllowed
            if (livesOk && reachOk && cliffOk) hits++
            // How far outside the band it landed, so near misses sort above wild ones.
            val lives = if (lost) 0f else run.livesFraction
            miss += when {
                lives < target.minLives -> (target.minLives - lives) * 20f
                lives > target.maxLives -> (lives - target.maxLives) * 20f
                else -> 0f
            }
            if (!reachOk) miss += (run.peakReach - target.maxReach) * 20f
            if (!cliffOk) miss += (run.worstWave - worstWaveAllowed).toFloat()
        }
        return Scored(curve, runs, hits, miss)
    }

    @Test
    fun `sweep the difficulty curve`() {
        assumeTrue("opt in with -Dsweep=1", System.getProperty("sweep").orEmpty().isNotEmpty())

        // Fourth pass. The first three all hit the same wall - any curve that made wave 20 cost
        // lives then lost wave 30 - and the reason turned out not to be the curve at all: at
        // wave 30 the player had 10,262 invested and 672 in hand, so they were broke rather than
        // saturated. So this sweeps the reward growth alongside the shape, over the handful of
        // curves that already got 10 and 20 right.
        // Fifth pass. Reward growth did keep the long game solvent, but on its own it just made
        // every length equally easy - difficulty went flat at about 55% reach everywhere. It
        // needs a heavier curve behind it, and the previous grid's scale did not go high enough.
        // Ninth pass, and the first against boards that are comparable with each other. Every
        // earlier sweep was quietly fitting one map: route length drove difficulty more than the
        // curve did, and a generated board could be anywhere from 31 to 74 steps, so a balance
        // that worked on one lost every game on another. Map generation now holds the walk to a
        // narrow band, which is what finally makes a single curve meaningful - but the band sits
        // shorter than the board the eighth pass was fitted to, so the weight has to come down.
        // Tenth pass. The ninth came out flat - reach read the same at 10, 20 and 30 waves - which
        // means reward growth of 0.09 was cancelling the whole curve: the economy grew exactly as
        // fast as the difficulty. With the life budget now doing the work it was added for, it
        // can come back down, and the curve itself has to carry more.
        // Eleventh pass, through the boundary the tenth bracketed: at exponent 1.70 the 30-wave
        // game is won but the 20-wave one costs nothing, and at 1.75 the 20-wave game is right
        // but the 30-wave one is lost. The band between them is narrow because the step from
        // "nothing leaks" to "bleeding every wave" is itself abrupt.
        // Twelfth pass, and the first that is not fitting a single board. Every curve now plays
        // all three lengths on all three boards, so the grid comes back down: a curve has to be
        // survivable on the hardest board, not just on the luckiest one.
        // Thirteenth pass, narrow, around the first curve that won all nine games with the cost
        // rising by length on every board. Scale is the sensitive knob: 0.70 left every game
        // untouched and 0.85 graded them, so this walks the gap in small steps.
        val growths = listOf(0.78f, 0.80f, 0.82f)
        val exponents = listOf(1.73f)
        val scales = listOf(0.85f, 0.90f, 0.95f, 1.00f)
        val rewardGrowths = listOf(0.04f, 0.05f)

        // A curve has to clear every board at a length to count that length as hit.
        val results = ArrayList<Scored>()
        for (growth in growths) {
            for (exponent in exponents) {
                for (scale in scales) {
                    for (reward in rewardGrowths) {
                        results.add(
                            score(
                                DifficultyCurve(
                                    scale = scale,
                                    growth = growth,
                                    exponent = exponent,
                                    lastWaveExtra = 1.05f,
                                    rewardGrowth = reward,
                                ),
                            ),
                        )
                    }
                }
            }
        }

        val ranked = results.sortedWith(compareByDescending<Scored> { it.hits }.thenBy { it.miss })
        val out = StringBuilder()
        // Nine games per curve: three lengths on three boards. "hits" counts all nine.
        out.appendLine(
            "scale growth  exp  rwd | hits  miss |  10w s7/s2/s5  |  20w s7/s2/s5  |  30w s7/s2/s5",
        )
        out.appendLine("-".repeat(100))
        for (r in ranked) {
            out.append(
                String.format(
                    Locale.ROOT,
                    "%5.2f %6.2f %4.2f %4.2f | %4d %5.1f |",
                    r.curve.scale, r.curve.growth, r.curve.exponent, r.curve.rewardGrowth,
                    r.hits, r.miss,
                ),
            )
            for (run in r.runs) {
                out.append(
                    String.format(
                        Locale.ROOT,
                        " %s%2d/%2d",
                        if (run.outcome == Phase.LOST) "L" else "W",
                        run.livesLeft, run.startingLives,
                    ),
                )
            }
            out.appendLine()
        }
        val text = out.toString()
        println()
        println(text)

        val directory = File("build/difficulty")
        directory.mkdirs()
        File(directory, "sweep.md").writeText("```\n$text```\n")
        println("written to " + File(directory, "sweep.md").absolutePath)
    }
}
