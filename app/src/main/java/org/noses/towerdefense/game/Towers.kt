package org.noses.towerdefense.game

import kotlin.math.pow
import kotlin.math.roundToInt

enum class TowerKind { SQUARE, HEXAGON, PLUS }

const val MAX_TOWER_LEVEL = 3

/**
 * @param range how far it can shoot, in cells
 * @param cooldown seconds between shots
 * @param splashRadius damage radius of the blast, in cells; 0 for single-target weapons
 * @param pierce true for the laser, which hits everything in a line to the edge of the board
 */
data class TowerStats(
    val kind: TowerKind,
    val cost: Int,
    val damage: Int,
    val range: Float,
    val cooldown: Float,
    val splashRadius: Float = 0f,
    val pierce: Boolean = false,
    val label: String,
    val description: String,
)

/** Plain bullets, one target at a time, at a steady middling pace. */
val SQUARE_TOWER = TowerStats(
    kind = TowerKind.SQUARE,
    cost = 20,
    damage = 10,
    range = 2.6f,
    cooldown = 0.65f,
    label = "BULLET",
    description = "Single shots, medium pace",
)

/** Lobs a shell that blows up, damaging everything close to where it lands. */
val HEXAGON_TOWER = TowerStats(
    kind = TowerKind.HEXAGON,
    cost = 45,
    damage = 16,
    range = 3.0f,
    cooldown = 1.6f,
    splashRadius = 1.25f,
    label = "BLAST",
    description = "Area damage, slow pace",
)

/** Fires a beam that runs off the edge of the board, hitting everything it crosses. */
val PLUS_TOWER = TowerStats(
    kind = TowerKind.PLUS,
    cost = 60,
    damage = 9,
    range = 99f,
    cooldown = 1.1f,
    pierce = true,
    label = "LASER",
    description = "Pierces every enemy in line",
)

val ALL_TOWERS = listOf(SQUARE_TOWER, HEXAGON_TOWER, PLUS_TOWER)

fun statsFor(kind: TowerKind): TowerStats = when (kind) {
    TowerKind.SQUARE -> SQUARE_TOWER
    TowerKind.HEXAGON -> HEXAGON_TOWER
    TowerKind.PLUS -> PLUS_TOWER
}

/** A built tower. Levels make it hit harder, reach further and reload faster. */
class Tower(val stats: TowerStats, val cell: Cell) {
    var level: Int = 1
        private set

    /** Seconds left before it can fire again. */
    var cooldown: Float = 0f

    /** Angle it is pointing, in radians; purely visual. */
    var facing: Float = 0f

    /** Everything spent on this tower so far, which sets the refund. */
    var invested: Int = stats.cost
        private set

    val center: Point get() = cell.center()

    val kind: TowerKind get() = stats.kind

    val damage: Int get() = (stats.damage * 1.55f.pow(level - 1)).roundToInt()

    val range: Float get() = stats.range * (1f + 0.1f * (level - 1))

    val splashRadius: Float get() = stats.splashRadius * (1f + 0.12f * (level - 1))

    val fireInterval: Float get() = stats.cooldown * 0.85f.pow(level - 1)

    val canUpgrade: Boolean get() = level < MAX_TOWER_LEVEL

    /** Cost of the next level, or null at max level. */
    val upgradeCost: Int? get() = if (canUpgrade) stats.cost * level else null

    val sellValue: Int get() = (invested * 0.6f).toInt()

    fun applyUpgrade() {
        val price = upgradeCost ?: return
        level++
        invested += price
    }
}
