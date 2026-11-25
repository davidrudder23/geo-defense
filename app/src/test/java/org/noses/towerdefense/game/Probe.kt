package org.noses.towerdefense.game

import java.util.Locale
import kotlin.random.Random

/**
 * A measurement rig for difficulty.
 *
 * This is an *instrument*, not a test. It plays whole games headlessly, with a strategy standing
 * in for the player, and records what happened wave by wave. Nothing here changes the game; it
 * only reads the engine's public surface, so a report is evidence about the build it ran against.
 *
 * The question it exists to answer is "where does the difficulty curve bend", which a win or lose
 * verdict cannot tell you. A game won with twenty lives left and a game won on the last life both
 * say WON.
 */

/** What happened during one wave. */
data class WaveRow(
    val wave: Int,
    val enemies: Int,
    val hpScale: Float,
    val speedScale: Float,
    /** Towers standing when the wave was started. */
    val towers: Int,
    /**
     * Steps from entry to exit when the wave started.
     *
     * The player sets this by where they build, so it is a lever they hold rather than one the
     * game sets, and it is the number to watch for "build the longest snake" play.
     */
    val route: Int,
    /** Everything spent on towers so far, which is the player's accumulated power. */
    val invested: Int,
    /** Paid for starting the wave early. */
    val rushBonus: Int,
    /** Money gained across the wave: rush bonus, kill rewards and the clear bonus together. */
    val income: Int,
    val moneyAtEnd: Int,
    val killed: Int,
    val leaked: Int,
    val livesLost: Int,
    val livesAfter: Int,
    /**
     * How far the deepest enemy got, as a fraction of the route: 0 means they died at the entry,
     * 1 means at least one reached the exit.
     *
     * This is the metric a leak count cannot give you. Zero leaks can mean "comfortable" or
     * "every enemy died on the last tower", and only this separates them.
     */
    val reach: Float,
    /** Game seconds the wave took to resolve. */
    val seconds: Float,
)

/** One whole game, played out. */
data class GameRun(
    val label: String,
    val strategy: String,
    val totalWaves: Int,
    val outcome: Phase,
    val livesLeft: Int,
    val startingLives: Int,
    val rows: List<WaveRow>,
) {
    /** How much of the life budget survived, 0 to 1. Comparable across game lengths. */
    val livesFraction: Float get() = livesLeft.toFloat() / startingLives
    val leaks: Int get() = rows.sumOf { it.leaked }

    /** The worst single wave, in lives. A difficulty curve should not hide a cliff. */
    val worstWave: Int get() = rows.maxOfOrNull { it.livesLost } ?: 0
    val livesLost: Int get() = startingLives - livesLeft
    val peakReach: Float get() = rows.maxOfOrNull { it.reach } ?: 0f

    /** Route length on the last wave, which is how long a maze the player ended up with. */
    val finalRoute: Int get() = rows.lastOrNull()?.route ?: 0

    /** Total wealth at the end: cash in hand plus everything sunk into towers. */
    val wealth: Int get() = (rows.lastOrNull()?.let { it.moneyAtEnd + it.invested }) ?: 0

    /**
     * A reading, not a judgement the game enforces. The thresholds are a starting point picked to
     * name what the numbers currently show, and are meant to be argued with.
     */
    val verdict: String get() = when {
        outcome == Phase.LOST -> "LOST on wave ${rows.size}"
        leaks == 0 && peakReach < 0.85f -> "TOO EASY - never threatened"
        leaks == 0 -> "CLEAN - won without a leak, but enemies got close"
        livesFraction > 0.66f -> "WORKABLE - leaked, never in danger"
        livesFraction > 0.33f -> "GOOD - cost real lives and still won"
        else -> "TENSE - won on the last few lives"
    }
}

// --- the stand-in player ------------------------------------------------------------------------

/**
 * Cells worth putting a tower on, in the order the enemies meet them.
 *
 * Recomputed from the *live* route every time, because building bends the route - a spot that was
 * beside the path a moment ago may be nowhere near it now. Cells on the route itself are included:
 * building there is legal maze play, and the engine refuses the ones that would seal the exit.
 */
fun buildSpots(engine: GameEngine): List<Cell> {
    val offsets = listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)
    val spots = LinkedHashSet<Cell>()
    for (step in engine.route) {
        for ((dx, dy) in offsets) {
            val cell = Cell(step.col + dx, step.row + dy)
            if (engine.map.isBuildable(cell.col, cell.row) && engine.towerAt(cell) == null) {
                spots.add(cell)
            }
        }
    }
    return spots.toList()
}

/** How the stand-in player spends between waves. */
abstract class Strategy(val name: String) {
    abstract fun spend(engine: GameEngine)

    /** Builds one tower in the first spot that will take it. */
    protected fun buildOne(engine: GameEngine, kind: TowerKind): Boolean =
        buildSpots(engine).any { engine.build(kind, it) }

    /** Levels up the cheapest upgrade it can afford. */
    protected fun upgradeOne(engine: GameEngine): Boolean {
        val next = engine.towers
            .filter { it.canUpgrade && (it.upgradeCost ?: Int.MAX_VALUE) <= engine.money }
            .minByOrNull { it.upgradeCost ?: Int.MAX_VALUE }
            ?: return false
        return engine.upgrade(next)
    }
}

/** Quantity: buys as many of one kind as the money allows. The simplest thing a player does. */
class Spam(private val kind: TowerKind) : Strategy("${statsFor(kind).label.lowercase()} spam") {
    override fun spend(engine: GameEngine) {
        while (engine.money >= statsFor(kind).cost) {
            if (!buildOne(engine, kind)) return
        }
    }
}

/**
 * Quality: a handful of towers levelled to the top before any more go up.
 *
 * Worth measuring separately because upgrades are superlinear - damage rises 1.55x per level
 * while the reload drops to 0.85x - so this is the strategy most likely to outrun the waves.
 */
class Upgrader(
    private val kind: TowerKind,
    private val cap: Int,
) : Strategy("${statsFor(kind).label.lowercase()} x$cap, levelled") {
    override fun spend(engine: GameEngine) {
        while (engine.towers.size < cap && engine.money >= statsFor(kind).cost) {
            if (!buildOne(engine, kind)) break
        }
        while (true) {
            if (upgradeOne(engine)) continue
            // Everything affordable is maxed, so widen the defence instead.
            if (engine.money >= statsFor(kind).cost && buildOne(engine, kind)) continue
            return
        }
    }
}

/**
 * The strategy the maze design invites: every tower goes wherever it makes the walk longest.
 *
 * This is the one that matters most. Since the player authors the path, "build the longest
 * possible snake" is available as a single dominant answer, and if it is far stronger than
 * placing towers sensibly then the maze is the whole game and the three towers are decoration.
 * Leftover money goes into levels, because a long maze with weak towers is not the real threat.
 */
class Mazer(
    private val kind: TowerKind,
    /**
     * Whether leftover money goes into levels.
     *
     * Off is the useful setting for attributing a result. With it on, a strong run cannot be
     * credited to the maze rather than to the upgrades, because both happened at once.
     */
    private val levelUp: Boolean = true,
) : Strategy("${statsFor(kind).label.lowercase()} maze${if (levelUp) ", levelled" else " only"}") {
    override fun spend(engine: GameEngine) {
        while (engine.money >= statsFor(kind).cost) {
            // Lengthen the route if anything can; otherwise fall back to standing near it.
            val cell = longestDetour(engine)
            val built = if (cell != null) engine.build(kind, cell) else buildOne(engine, kind)
            if (!built) break
        }
        if (!levelUp) return
        while (upgradeOne(engine)) {
            // Everything left goes into levels.
        }
    }

    /** The legal cell that would make the route longest, or null if none of them lengthen it. */
    private fun longestDetour(engine: GameEngine): Cell? {
        var best: Cell? = null
        var bestLength = engine.flow[engine.map.entry]
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val cell = Cell(col, row)
                if (!engine.canBuild(kind, cell)) continue
                val length = routeLengthWith(engine, cell)
                if (length != UNREACHABLE && length > bestLength) {
                    best = cell
                    bestLength = length
                }
            }
        }
        return best
    }

    /**
     * How long the route would be with a tower on [cell], without building one.
     *
     * The same question [GameEngine.leavesAWayThrough] asks, kept separate because the strategy
     * wants the distance rather than the yes or no.
     */
    private fun routeLengthWith(engine: GameEngine, cell: Cell): Int {
        val field = flowFieldTo(engine.map.exit) { at ->
            engine.map.isOpen(at) && at != cell && engine.towerAt(at) == null
        }
        return field[engine.map.entry]
    }
}

/** A spread of all three kinds, which is what the tower design is asking the player to do. */
class Mixed(
    private val levelUp: Boolean = false,
) : Strategy("one of each, repeating" + if (levelUp) ", levelled" else "") {
    private val order = listOf(TowerKind.SQUARE, TowerKind.SQUARE, TowerKind.HEXAGON, TowerKind.PLUS)
    private var next = 0

    override fun spend(engine: GameEngine) {
        var stuck = 0
        while (stuck < order.size) {
            val kind = order[next % order.size]
            if (engine.money >= statsFor(kind).cost && buildOne(engine, kind)) {
                next++
                stuck = 0
            } else {
                next++
                stuck++
            }
        }
        if (!levelUp) return
        while (upgradeOne(engine)) {
            // Levels, so this is comparable with the single-kind runs.
        }
    }
}

// --- the rig ------------------------------------------------------------------------------------

object Probe {
    private const val STEP = 1f / 60f

    /** A wave that has not resolved in this long is stuck, and the run is abandoned. */
    private const val WAVE_TIMEOUT = 400f

    /**
     * Plays one game to the end and reports it.
     *
     * @param rush whether the player presses the button the moment the board is ready, taking the
     *   gold bonus, or waits out the clock. Worth being able to turn off: pressing it every wave
     *   is worth [READY_SECONDS] * [RUSH_BONUS_PER_SECOND] gold a wave, which is a large slice of
     *   the early economy.
     */
    fun play(
        label: String,
        totalWaves: Int,
        map: GameMap,
        strategy: Strategy,
        seed: Int = 42,
        rush: Boolean = true,
        curve: DifficultyCurve = DifficultyCurve(),
    ): GameRun {
        val engine = GameEngine(
            totalWaves = totalWaves,
            map = map,
            random = Random(seed),
            curve = curve,
        )
        val rows = ArrayList<WaveRow>()

        while (!engine.finished && rows.size < totalWaves) {
            if (engine.phase != Phase.READY) break
            strategy.spend(engine)

            val moneyBefore = engine.money
            val towersBefore = engine.towers.size
            val investedBefore = engine.towers.sumOf { it.invested }
            val killedBefore = engine.enemiesKilled
            val leakedBefore = engine.enemiesLeaked
            val livesBefore = engine.lives
            val bonus = if (rush) engine.rushBonus else 0

            if (rush) {
                engine.startWave(rushed = true)
            } else {
                // Let the build clock run out and start the wave by itself.
                while (engine.phase == Phase.READY) engine.update(STEP)
            }
            val wave = engine.currentWave ?: break

            // The route as it stands now, to measure how far down it the enemies get.
            val routeLength = engine.flow[engine.map.entry]
            var closest = Float.MAX_VALUE
            var seconds = 0f
            while (engine.phase == Phase.RUNNING && seconds < WAVE_TIMEOUT) {
                engine.update(STEP)
                seconds += STEP
                for (enemy in engine.enemies) {
                    if (!enemy.alive) continue
                    val left = enemy.remainingSteps(engine.flow)
                    if (left < closest) closest = left
                }
            }

            val leaked = engine.enemiesLeaked - leakedBefore
            val reach = when {
                leaked > 0 -> 1f
                closest == Float.MAX_VALUE -> 0f
                routeLength == UNREACHABLE || routeLength <= 0 -> 0f
                else -> (1f - closest / routeLength).coerceIn(0f, 1f)
            }

            rows.add(
                WaveRow(
                    wave = wave.number,
                    enemies = wave.size,
                    hpScale = wave.hpScale,
                    speedScale = wave.speedScale,
                    towers = towersBefore,
                    route = routeLength,
                    invested = investedBefore,
                    rushBonus = bonus,
                    income = engine.money - moneyBefore,
                    moneyAtEnd = engine.money,
                    killed = engine.enemiesKilled - killedBefore,
                    leaked = leaked,
                    livesLost = livesBefore - engine.lives,
                    livesAfter = engine.lives,
                    reach = reach,
                    seconds = seconds,
                ),
            )
        }

        return GameRun(
            label, strategy.name, totalWaves, engine.phase, engine.lives,
            engine.startingLives, rows,
        )
    }

    // --- printing -------------------------------------------------------------------------------

    private const val HEADER =
        "wave    n     hp   spd   twr   path    inv   rush    inc   cash   kill   leak   lost  lives  reach"

    private fun row(it: WaveRow): String = String.format(
        Locale.ROOT,
        "%4d %4d  %5.2f %5.2f  %4d %6d %6d %6d %6d %6d %6d %6d %6d %6d %5.0f%%",
        it.wave, it.enemies, it.hpScale, it.speedScale, it.towers, it.route, it.invested,
        it.rushBonus, it.income, it.moneyAtEnd, it.killed, it.leaked, it.livesLost,
        it.livesAfter, it.reach * 100f,
    )

    fun format(run: GameRun): String {
        val out = StringBuilder()
        out.appendLine("${run.label} | ${run.strategy} | ${run.totalWaves} waves")
        out.appendLine(HEADER)
        out.appendLine("-".repeat(HEADER.length))
        run.rows.forEach { out.appendLine(row(it)) }
        out.appendLine(
            String.format(
                Locale.ROOT,
                "%s | lives %d/%d | leaks %d | peak reach %.0f%% | wealth %d",
                run.outcome, run.livesLeft, run.startingLives, run.leaks,
                run.peakReach * 100f, run.wealth,
            ),
        )
        out.appendLine("verdict: ${run.verdict}")
        return out.toString()
    }

    /** The whole report, as markdown with the tables fenced so they stay aligned. */
    fun report(runs: List<GameRun>): String {
        val out = StringBuilder()
        out.appendLine("# Difficulty report")
        out.appendLine()
        out.appendLine("Generated by `DifficultyProbeTest`. Columns:")
        out.appendLine()
        out.appendLine("- `n` enemies in the wave, `hp`/`spd` the scaling applied to them")
        out.appendLine("- `twr`/`inv` towers standing and total gold sunk into them, before the wave")
        out.appendLine("- `path` route length in steps, which the player lengthens by where they build")
        out.appendLine("- `rush` bonus taken for starting early, `inc` all income across the wave")
        out.appendLine("- `leak` enemies that reached the exit, `lost` lives that cost")
        out.appendLine("- `reach` how far the deepest enemy got along the route, 100% being the exit")
        out.appendLine()
        for (run in runs) {
            out.appendLine("```")
            out.append(format(run))
            out.appendLine("```")
            out.appendLine()
        }
        out.appendLine("## Summary")
        out.appendLine()
        out.appendLine("| scenario | strategy | waves | outcome | lives | leaks | peak reach | path |")
        out.appendLine("|---|---|---:|---|---:|---:|---:|---:|")
        for (run in runs) {
            out.appendLine(
                String.format(
                    Locale.ROOT,
                    "| %s | %s | %d | %s | %d/%d | %d | %.0f%% | %d |",
                    run.label, run.strategy, run.totalWaves, run.outcome,
                    run.livesLeft, run.startingLives, run.leaks, run.peakReach * 100f,
                    run.finalRoute,
                ),
            )
        }
        return out.toString()
    }
}
