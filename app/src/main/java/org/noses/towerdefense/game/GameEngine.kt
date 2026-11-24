package org.noses.towerdefense.game

import kotlin.math.atan2
import kotlin.math.max
import kotlin.random.Random

const val STARTING_MONEY = 120
const val STARTING_LIVES = 20

/** Half the width of the laser line, in cells. */
private const val BEAM_HALF_WIDTH = 0.22f

private const val BULLET_SPEED = 9f
private const val SHELL_SPEED = 5.5f

/** The longest slice of time the simulation will take in one step. */
const val MAX_STEP_SECONDS = 0.05f

/**
 * The most steps one frame may run. Caps how far the game can leap after a stall, and stops a
 * slow device at 5x speed from spiralling into ever longer frames.
 */
const val MAX_STEPS_PER_FRAME = 8

enum class Phase {
    /** Between waves: build, upgrade, then start the next one. */
    READY,
    RUNNING,
    WON,
    LOST,
}

/**
 * The whole simulation. Holds no Android types, so it can be stepped and asserted on in tests.
 * Call [update] once per frame with the elapsed seconds.
 */
class GameEngine(
    val totalWaves: Int,
    val map: GameMap = generateMap(),
    private val random: Random = Random.Default,
) {
    var money: Int = STARTING_MONEY
        private set
    var lives: Int = STARTING_LIVES
        private set
    var waveNumber: Int = 0
        private set
    var phase: Phase = Phase.READY
        private set
    var enemiesKilled: Int = 0
        private set
    var enemiesLeaked: Int = 0
        private set

    val towers = ArrayList<Tower>()
    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    val blasts = ArrayList<Blast>()
    val beams = ArrayList<Beam>()

    private var wave: Wave? = null
    private var waveClock: Float = 0f
    private var spawned: Int = 0

    /** Events raised since the last drain; see [drainSounds]. */
    private val pendingSounds = ArrayList<SoundCue>()

    val currentWave: Wave? get() = wave

    val finished: Boolean get() = phase == Phase.WON || phase == Phase.LOST

    /** Enemies still to come this wave, plus the ones already walking. */
    val enemiesLeft: Int get() = ((wave?.size ?: 0) - spawned) + enemies.size

    // --- player actions -------------------------------------------------------------------

    fun startWave(): Boolean {
        if (phase != Phase.READY) return false
        waveNumber++
        wave = buildWave(waveNumber, totalWaves, random)
        waveClock = 0f
        spawned = 0
        phase = Phase.RUNNING
        return true
    }

    fun towerAt(cell: Cell): Tower? = towers.firstOrNull { it.cell == cell }

    fun canBuild(kind: TowerKind, cell: Cell): Boolean =
        !finished &&
            map.isBuildable(cell.col, cell.row) &&
            towerAt(cell) == null &&
            money >= statsFor(kind).cost

    fun build(kind: TowerKind, cell: Cell): Boolean {
        if (!canBuild(kind, cell)) return false
        val stats = statsFor(kind)
        money -= stats.cost
        towers.add(Tower(stats, cell))
        return true
    }

    fun upgrade(tower: Tower): Boolean {
        if (finished) return false
        val price = tower.upgradeCost ?: return false
        if (money < price) return false
        money -= price
        tower.applyUpgrade()
        pendingSounds.add(SoundCue.UPGRADE)
        return true
    }

    fun sell(tower: Tower): Boolean {
        if (finished) return false
        if (!towers.remove(tower)) return false
        money += tower.sellValue
        return true
    }

    /**
     * Moves everything heard since the last call into [into] and forgets it. The caller owns a
     * reusable list, so a quiet frame costs nothing.
     */
    fun drainSounds(into: MutableList<SoundCue>) {
        if (pendingSounds.isEmpty()) return
        into.addAll(pendingSounds)
        pendingSounds.clear()
    }

    // --- simulation -----------------------------------------------------------------------

    /**
     * Runs [seconds] of game time, which may be several frames' worth when the game is sped up.
     *
     * The work is split into steps of at most [MAX_STEP_SECONDS] rather than handed to [update]
     * as one long slice. Stepping keeps fast-moving enemies from skipping past a tower's range
     * between one position and the next, so 5x speed plays the same game as 1x, only quicker.
     */
    fun advance(seconds: Float) {
        var remaining = seconds
        var steps = 0
        while (remaining > 0f && steps < MAX_STEPS_PER_FRAME) {
            val step = kotlin.math.min(remaining, MAX_STEP_SECONDS)
            update(step)
            remaining -= step
            steps++
        }
    }

    fun update(dt: Float) {
        val step = dt.coerceIn(0f, MAX_STEP_SECONDS)
        if (finished) {
            ageEffects(step)
            return
        }
        if (phase == Phase.RUNNING) releaseSpawns(step)
        moveEnemies(step)
        fireTowers(step)
        moveProjectiles(step)
        ageEffects(step)
        collectDead()
        advancePhase()
    }

    private fun releaseSpawns(dt: Float) {
        val wave = wave ?: return
        waveClock += dt
        while (spawned < wave.size && wave.spawns[spawned].time <= waveClock) {
            val spawn = wave.spawns[spawned]
            enemies.add(
                Enemy(statsFor(spawn.kind), wave.hpScale, wave.speedScale).apply { placeOn(map) }
            )
            spawned++
        }
    }

    private fun moveEnemies(dt: Float) {
        val walkers = enemies.iterator()
        while (walkers.hasNext()) {
            val enemy = walkers.next()
            if (enemy.advance(dt, map)) {
                lives = max(0, lives - enemy.stats.leakDamage)
                enemiesLeaked++
                walkers.remove()
            }
        }
    }

    private fun fireTowers(dt: Float) {
        for (tower in towers) {
            tower.cooldown -= dt
            if (tower.cooldown > 0f) continue
            val target = leadTarget(tower) ?: continue
            tower.facing = atan2(
                target.position.y - tower.center.y,
                target.position.x - tower.center.x,
            )
            when (tower.kind) {
                TowerKind.SQUARE -> fireBullet(tower, target)
                TowerKind.HEXAGON -> fireShell(tower, target)
                TowerKind.PLUS -> fireLaser(tower, target)
            }
            pendingSounds.add(shotCueFor(tower.kind))
            tower.cooldown = tower.fireInterval
        }
    }

    /** The enemy in range that has walked furthest, which is the one closest to getting through. */
    private fun leadTarget(tower: Tower): Enemy? {
        var best: Enemy? = null
        for (enemy in enemies) {
            if (!enemy.alive) continue
            if (distanceBetween(enemy.position, tower.center) > tower.range) continue
            if (best == null || enemy.distance > best.distance) best = enemy
        }
        return best
    }

    private fun fireBullet(tower: Tower, target: Enemy) {
        projectiles.add(
            Projectile(
                kind = TowerKind.SQUARE,
                position = tower.center,
                target = target,
                aimPoint = target.position,
                damage = tower.damage,
                splashRadius = 0f,
                speed = BULLET_SPEED,
            )
        )
    }

    private fun fireShell(tower: Tower, target: Enemy) {
        projectiles.add(
            Projectile(
                kind = TowerKind.HEXAGON,
                position = tower.center,
                target = null,
                aimPoint = target.position,
                damage = tower.damage,
                splashRadius = tower.splashRadius,
                speed = SHELL_SPEED,
            )
        )
    }

    /** Hits everything along a line from the tower to the edge of the board. */
    private fun fireLaser(tower: Tower, target: Enemy) {
        val dir = directionTo(tower.center, target.position)
        val end = rayExit(tower.center, dir, map.columns.toFloat(), map.rows.toFloat())
        beams.add(Beam(tower.center, end))
        for (enemy in enemies) {
            if (!enemy.alive) continue
            val offset = offsetFromRay(enemy.position, tower.center, dir) ?: continue
            if (offset <= BEAM_HALF_WIDTH + enemy.stats.radius) enemy.hit(tower.damage)
        }
    }

    private fun moveProjectiles(dt: Float) {
        val shots = projectiles.iterator()
        while (shots.hasNext()) {
            val shot = shots.next()
            when (shot.advance(dt)) {
                Flight.TRAVELLING -> Unit
                Flight.LOST -> shots.remove()
                Flight.ARRIVED -> {
                    land(shot)
                    shots.remove()
                }
            }
        }
    }

    private fun land(shot: Projectile) {
        if (shot.splashRadius > 0f) {
            blasts.add(Blast(shot.position, shot.splashRadius))
            pendingSounds.add(SoundCue.BLAST_IMPACT)
            for (enemy in enemies) {
                if (!enemy.alive) continue
                if (distanceBetween(enemy.position, shot.position) <= shot.splashRadius + enemy.stats.radius) {
                    enemy.hit(shot.damage)
                }
            }
        } else {
            shot.target?.hit(shot.damage)
        }
    }

    private fun ageEffects(dt: Float) {
        blasts.forEach { it.age(dt) }
        blasts.removeAll { it.expired }
        beams.forEach { it.age(dt) }
        beams.removeAll { it.expired }
    }

    private fun collectDead() {
        val walkers = enemies.iterator()
        while (walkers.hasNext()) {
            val enemy = walkers.next()
            if (!enemy.alive) {
                money += enemy.stats.reward
                enemiesKilled++
                pendingSounds.add(deathCueFor(enemy.kind))
                walkers.remove()
            }
        }
    }

    private fun advancePhase() {
        if (lives <= 0) {
            phase = Phase.LOST
            return
        }
        if (phase != Phase.RUNNING) return
        val wave = wave ?: return
        if (spawned >= wave.size && enemies.isEmpty()) {
            money += wave.clearBonus
            phase = if (waveNumber >= totalWaves) Phase.WON else Phase.READY
        }
    }
}
