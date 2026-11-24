package org.noses.towerdefense.game

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class EnemyKind { SQUARE, TRIANGLE, CIRCLE }

/**
 * @param speed cells travelled per second
 * @param armor flat amount taken off every hit, so armoured enemies need many hits
 * @param leakDamage lives lost if this enemy reaches the exit
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

/**
 * One enemy finding its own way across the board.
 *
 * It walks from cell centre to cell centre, asking the [FlowField] which neighbour to head for
 * next. Because the field is shared and rebuilt whenever the maze changes, an enemy re-routes
 * mid-stride the moment a tower goes up — it simply gets a different answer next time it asks.
 */
class Enemy(val stats: EnemyStats, hpScale: Float = 1f, val speedScale: Float = 1f) {
    val maxHp: Int = max(1, (stats.baseHp * hpScale).roundToInt())
    var hp: Int = maxHp
        private set

    var position: Point = Point(0f, 0f)
        private set

    /** The cell it is currently walking into. */
    var targetCell: Cell = Cell(0, 0)
        private set

    /** The cell it is standing in right now. */
    val cell: Cell get() = Cell(position.x.toInt(), position.y.toInt())

    val kind: EnemyKind get() = stats.kind
    val alive: Boolean get() = hp > 0
    val healthFraction: Float get() = hp.toFloat() / maxHp

    /** Spin for the squares, so movement reads at a glance. */
    var spin: Float = 0f
        private set

    /** Which way it is walking, in radians; triangles are drawn pointing this way. */
    var heading: Float = 0f
        private set

    /** The step it last took, used to keep it running straight when routes tie. */
    private var stepTaken: Pair<Int, Int>? = null

    /** True when the maze has shut this enemy out of the exit entirely. */
    var stranded: Boolean = false
        private set

    fun spawnAt(cell: Cell, flow: FlowField) {
        position = cell.center()
        targetCell = flow.next(cell) ?: cell
        stepTaken = null
        aimAtTarget()
    }

    /**
     * Walks for [dt] seconds, following [flow]. Returns true once it has reached the exit.
     *
     * The movement budget is spent in a loop rather than in one jump, so a fast enemy that
     * crosses more than one cell in a frame still turns each corner properly instead of cutting
     * through whatever is on the inside of the bend.
     */
    fun advance(dt: Float, exit: Cell, flow: FlowField): Boolean {
        spin += dt * stats.speed * 1.6f
        var budget = stats.speed * speedScale * dt
        var guard = 0

        while (budget > 0f && guard < 64) {
            guard++
            val centre = targetCell.center()
            val dx = centre.x - position.x
            val dy = centre.y - position.y
            val gap = kotlin.math.hypot(dx, dy)

            if (gap > budget) {
                position = Point(position.x + dx / gap * budget, position.y + dy / gap * budget)
                return false
            }

            // Arrived at the middle of the target cell.
            position = centre
            budget -= gap
            if (targetCell == exit) return true

            val next = flow.next(targetCell, stepTaken)
            if (next == null) {
                // Walled in. Stand still rather than wander; the engine refuses builds that
                // would cause this, so it only happens if something has gone wrong.
                stranded = true
                return false
            }
            stranded = false
            stepTaken = (next.col - targetCell.col) to (next.row - targetCell.row)
            targetCell = next
            aimAtTarget()
        }
        return false
    }

    private fun aimAtTarget() {
        val centre = targetCell.center()
        val dx = centre.x - position.x
        val dy = centre.y - position.y
        if (dx * dx + dy * dy > 1e-8f) heading = atan2(dy, dx)
    }

    /**
     * Roughly how far it still has to go, in cells. Towers shoot whichever enemy has the least
     * left, which is the one closest to getting through.
     */
    fun remainingSteps(flow: FlowField): Float {
        val stepsFromTarget = flow[targetCell]
        if (stepsFromTarget == UNREACHABLE) return Float.MAX_VALUE
        val centre = targetCell.center()
        return stepsFromTarget + kotlin.math.hypot(centre.x - position.x, centre.y - position.y)
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
