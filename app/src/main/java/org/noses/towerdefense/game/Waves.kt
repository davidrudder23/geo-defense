package org.noses.towerdefense.game

import kotlin.math.roundToInt
import kotlin.random.Random

/** The only game lengths offered. */
val WAVE_COUNTS = listOf(10, 20, 25, 30)

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
 * Everything scales off how far through the game the wave sits rather than off its number, so a
 * 10-wave game and a 30-wave game both run from gentle to frantic.
 */
fun buildWave(number: Int, totalWaves: Int, random: Random = Random.Default): Wave {
    val progress = if (totalWaves <= 1) 1f else (number - 1).toFloat() / (totalWaves - 1)
    val last = number == totalWaves

    val kinds = ArrayList<EnemyKind>()
    repeat(3 + (progress * 13).roundToInt() + if (last) 4 else 0) { kinds.add(EnemyKind.SQUARE) }
    if (number >= 2) repeat(2 + (progress * 16).roundToInt()) { kinds.add(EnemyKind.TRIANGLE) }
    if (number >= 3) repeat(1 + (progress * 9).roundToInt()) { kinds.add(EnemyKind.CIRCLE) }
    kinds.shuffle(random)

    // Enemies arrive in packs with a breather between them.
    val gap = 0.62f - progress * 0.22f
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
        hpScale = 1f + progress * 2.5f + if (last) 0.6f else 0f,
        speedScale = 1f + progress * 0.25f,
        clearBonus = 18 + number * 3,
    )
}
