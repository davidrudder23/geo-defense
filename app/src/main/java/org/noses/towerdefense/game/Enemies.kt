package org.noses.towerdefense.game

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class EnemyKind { SQUARE, TRIANGLE, CIRCLE }

/**
 * @param speed cells travelled per second
 * @param armor flat amount taken off every hit, so armoured enemies need many hits
 * @param leakDamage lives lost if this enemy walks off the end of the road
 * @param radius drawn size, in cells
 */
data class EnemyStats(
    val kind: EnemyKind,
    val baseHp: Int,
    val speed: Float,
    val armor: Int,
    val reward: Int,
    val leakDamage: Int,
    val radius: Float,
)

/** Slow, fat, and hits hard when it gets through. */
val SQUARE_ENEMY = EnemyStats(EnemyKind.SQUARE, baseHp = 34, speed = 0.95f, armor = 0, reward = 9, leakDamage = 2, radius = 0.34f)

/** Quick but falls over to almost anything. */
val TRIANGLE_ENEMY = EnemyStats(EnemyKind.TRIANGLE, baseHp = 12, speed = 2.4f, armor = 0, reward = 5, leakDamage = 1, radius = 0.30f)

/** Armoured: every hit is blunted, so it takes a lot of them. */
val CIRCLE_ENEMY = EnemyStats(EnemyKind.CIRCLE, baseHp = 26, speed = 1.35f, armor = 5, reward = 11, leakDamage = 1, radius = 0.33f)

fun statsFor(kind: EnemyKind): EnemyStats = when (kind) {
    EnemyKind.SQUARE -> SQUARE_ENEMY
    EnemyKind.TRIANGLE -> TRIANGLE_ENEMY
    EnemyKind.CIRCLE -> CIRCLE_ENEMY
}

/** One enemy walking the road. */
class Enemy(val stats: EnemyStats, hpScale: Float = 1f, val speedScale: Float = 1f) {
    val maxHp: Int = max(1, (stats.baseHp * hpScale).roundToInt())
    var hp: Int = maxHp
        private set

    /** Path steps walked so far. */
    var distance: Float = 0f
        private set

    var position: Point = Point(0f, 0f)
        private set

    val kind: EnemyKind get() = stats.kind
    val alive: Boolean get() = hp > 0
    val healthFraction: Float get() = hp.toFloat() / maxHp

    /** Spin for the squares, so movement reads at a glance. */
    var spin: Float = 0f
        private set

    /** Which way it is walking, in radians; triangles are drawn pointing this way. */
    var heading: Float = 0f
        private set

    fun placeOn(map: GameMap) {
        position = map.positionAt(distance)
        heading = headingOn(map)
    }

    /** Walks for [dt] seconds. Returns true once it has reached the end of the road. */
    fun advance(dt: Float, map: GameMap): Boolean {
        distance += stats.speed * speedScale * dt
        spin += dt * stats.speed * 1.6f
        position = map.positionAt(distance)
        heading = headingOn(map)
        return distance >= map.pathLength
    }

    /** Looks a little way further down the road to work out which way this enemy is pointing. */
    private fun headingOn(map: GameMap): Float {
        val ahead = map.positionAt(distance + 0.25f)
        val dx = ahead.x - position.x
        val dy = ahead.y - position.y
        if (dx * dx + dy * dy < 1e-8f) return heading
        return atan2(dy, dx)
    }

    /** Applies [amount] of damage, blunted by armour. Returns the damage actually dealt. */
    fun hit(amount: Int): Int {
        if (!alive) return 0
        val effective = max(1, amount - stats.armor)
        val dealt = min(effective, hp)
        hp -= dealt
        return dealt
    }
}
