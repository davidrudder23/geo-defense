package org.noses.towerdefense.game

/** What happened when a projectile was stepped forward. */
enum class Flight { TRAVELLING, ARRIVED, LOST }

/**
 * A shot in the air. Bullets chase their [target]; blast shells ignore it and fly to the fixed
 * [aimPoint] where they were aimed, so they still explode if the enemy dies on the way.
 */
class Projectile(
    val kind: TowerKind,
    var position: Point,
    val target: Enemy?,
    val aimPoint: Point,
    val damage: Int,
    val splashRadius: Float,
    val speed: Float,
) {
    var spin: Float = 0f
        private set

    private val destination: Point get() = target?.position ?: aimPoint

    fun advance(dt: Float): Flight {
        if (target != null && !target.alive) return Flight.LOST
        spin += dt * 9f
        val dest = destination
        val dx = dest.x - position.x
        val dy = dest.y - position.y
        val gap = kotlin.math.hypot(dx, dy)
        val step = speed * dt
        if (gap <= step || gap < 1e-4f) {
            position = dest
            return Flight.ARRIVED
        }
        position = Point(position.x + dx / gap * step, position.y + dy / gap * step)
        return Flight.TRAVELLING
    }
}

/** An expanding ring left by a blast shell. Purely visual; the damage lands when it is created. */
class Blast(val center: Point, val radius: Float) {
    companion object {
        const val LIFE = 0.36f
    }

    var age: Float = 0f
        private set

    val expired: Boolean get() = age >= LIFE

    /** 0 at the moment of the blast, 1 as it fades out. */
    val progress: Float get() = (age / LIFE).coerceIn(0f, 1f)

    fun age(dt: Float) {
        age += dt
    }
}

/** A laser line, drawn for a moment after it fires. */
class Beam(val from: Point, val to: Point) {
    companion object {
        const val LIFE = 0.14f
    }

    var age: Float = 0f
        private set

    val expired: Boolean get() = age >= LIFE

    val progress: Float get() = (age / LIFE).coerceIn(0f, 1f)

    fun age(dt: Float) {
        age += dt
    }
}
