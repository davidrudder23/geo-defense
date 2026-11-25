package org.noses.towerdefense.game

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** The only game lengths offered. */
val WAVE_COUNTS = listOf(10, 20, 25, 30)

/**
 * How much tougher each wave is than the one before it.
 *
 * The headline difficulty number. Compounding rather than added, because the *ratio* between
 * consecutive waves is what a player feels: a flat increment makes wave 2 twice wave 1 and wave
 * 20 a few percent above wave 19, so the whole spike lands at the start and the game flattens out
 * exactly when it should be tightening.
 */
const val HP_PER_WAVE = 1.18f

/** A last shove on the final wave, so a game ends on its hardest moment rather than its flattest. */
const val LAST_WAVE_EXTRA = 1.2f

/** How much faster each wave moves than the one before, and the ceiling it stops climbing at. */
const val SPEED_PER_WAVE = 0.012f
const val SPEED_CAP = 1.5f

/** Seconds between arrivals on wave one, how fast that tightens, and how tight it may get. */
private const val SPAWN_GAP = 0.62f
private const val SPAWN_GAP_PER_WAVE = 0.008f
private const val SPAWN_GAP_FLOOR = 0.3f

/** One enemy, due [time] seconds after the wave starts. */
data class Spawn(val kind: EnemyKind, val time: Float)

data class Wave(
    val number: Int,
    val spawns: List<Spawn>,
    val hpScale: Float,
    val speedScale: Float,
    val clearBonus: Int,
) {
    val duration: Float get() = spawns.lastOrNull()?.time ?: 0f
    val size: Int get() = spawns.size
}

/**
 * Builds wave [number] of [totalWaves].
 *
 * Everything scales off the wave's own number, not off how far through the game it sits. Wave 12
 * is wave 12 whether the game is 20 waves long or 30, so choosing a longer game is choosing a
 * harder one - a 30-wave game is an endurance run that climbs well past where a 10-wave game
 * stops. The player is not left behind by that: thirty waves is also thirty waves of income, of
 * building, and of route to lengthen.
 *
 * [totalWaves] is only used to spot the finale.
 */
fun buildWave(number: Int, totalWaves: Int, random: Random = Random.Default): Wave {
    val waveIndex = (number - 1).toFloat()
    val last = number == totalWaves

    val kinds = ArrayList<EnemyKind>()
    repeat(3 + (waveIndex * 0.7f).roundToInt() + if (last) 4 else 0) { kinds.add(EnemyKind.SQUARE) }
    if (number >= 2) repeat(2 + ((number - 2) * 0.85f).roundToInt()) { kinds.add(EnemyKind.TRIANGLE) }
    if (number >= 3) repeat(1 + ((number - 3) * 0.5f).roundToInt()) { kinds.add(EnemyKind.CIRCLE) }
    kinds.shuffle(random)

    // Enemies arrive in packs with a breather between them.
    val gap = (SPAWN_GAP - waveIndex * SPAWN_GAP_PER_WAVE).coerceAtLeast(SPAWN_GAP_FLOOR)
    var time = 0f
    val spawns = kinds.mapIndexed { index, kind ->
        if (index > 0) {
            time += gap
            if (index % 7 == 0) time += 1.1f
        }
        Spawn(kind, time)
    }

    return Wave(
        number = number,
        spawns = spawns,
        hpScale = HP_PER_WAVE.pow(waveIndex) * if (last) LAST_WAVE_EXTRA else 1f,
        speedScale = (1f + waveIndex * SPEED_PER_WAVE).coerceAtMost(SPEED_CAP),
        clearBonus = 18 + number * 3,
    )
}
