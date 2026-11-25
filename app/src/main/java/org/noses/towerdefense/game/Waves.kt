package org.noses.towerdefense.game

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** The only game lengths offered. */
val WAVE_COUNTS = listOf(10, 20, 25, 30)

/**
 * The shape of the difficulty curve.
 *
 * It is a **power law** - `(1 + growth * waveIndex) ^ exponent` - rather than the compounding
 * `ratio ^ waveIndex` it started as, and that is the whole point of the type existing.
 *
 * A compounding curve grows at a constant *ratio*, but the player's power does not: it comes out
 * of accumulated income, which grows polynomially. An exponential and a polynomial cross at
 * exactly one wave, and they cross hard - trivial on one side, hopeless on the other. Measured,
 * that is exactly what happened: a 30-wave game went 29 waves without losing a single life and
 * then lost 16 of 20 on the last one. There was no stretch anywhere that was merely difficult.
 *
 * A power law grows the way the player's power grows, so the gap between them opens slowly and
 * there is a long band of real pressure instead of a wall.
 *
 * The parameters are a constructor rather than constants so the difficulty probe can sweep them;
 * the defaults are what the game ships with.
 */
data class DifficultyCurve(
    /** Overall multiplier: moves every wave by the same proportion. */
    val scale: Float = DEFAULT_SCALE,
    /** How fast the curve's base rises per wave. */
    val growth: Float = DEFAULT_GROWTH,
    /** How sharply that base is bent upward. 1 is linear. */
    val exponent: Float = DEFAULT_EXPONENT,
    /** A last shove on the final wave. Kept small: a finale should not hold all the difficulty. */
    val lastWaveExtra: Float = DEFAULT_LAST_WAVE_EXTRA,
    /**
     * How much more each wave's kills pay than the one before.
     *
     * Without this a long game cannot be balanced at all. The board only fits so many towers, so
     * past a point the only way to get stronger is to buy levels - and measurement showed a
     * 30-wave game ending with 10,262 invested and 672 in hand. The player was not saturated,
     * they were *broke*: every curve that made wave 20 cost lives then lost wave 30 outright,
     * because income could not keep pace with its own difficulty.
     *
     * Paying more for later waves lets the economy of a long game grow into it, and it lands
     * where it is needed - by wave 30 it is worth far more than by wave 10.
     */
    val rewardGrowth: Float = DEFAULT_REWARD_GROWTH,
) {
    fun hpScaleFor(number: Int, isLast: Boolean): Float {
        val base = 1f + growth * (number - 1)
        return scale * base.pow(exponent) * if (isLast) lastWaveExtra else 1f
    }

    fun rewardScaleFor(number: Int): Float = 1f + rewardGrowth * (number - 1)

    /** The same curve with everything [factor] times as healthy. */
    fun scaledBy(factor: Float): DifficultyCurve = copy(scale = scale * factor)
}

// Found by sweeping the whole grid against three targets at once - a 10-wave game that is easy,
// a 20-wave game a competent player beats, and a 30-wave game that is tough - rather than by
// guessing. See CurveSweepTest.
const val DEFAULT_SCALE = 0.85f
const val DEFAULT_GROWTH = 0.80f
const val DEFAULT_EXPONENT = 1.73f
const val DEFAULT_LAST_WAVE_EXTRA = 1.05f
const val DEFAULT_REWARD_GROWTH = 0.05f

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
    /** Multiplier on what each kill in this wave pays. */
    val rewardScale: Float,
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
fun buildWave(
    number: Int,
    totalWaves: Int,
    random: Random = Random.Default,
    curve: DifficultyCurve = DifficultyCurve(),
): Wave {
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
        hpScale = curve.hpScaleFor(number, last),
        rewardScale = curve.rewardScaleFor(number),
        speedScale = (1f + waveIndex * SPEED_PER_WAVE).coerceAtMost(SPEED_CAP),
        clearBonus = 18 + number * 3,
    )
}
