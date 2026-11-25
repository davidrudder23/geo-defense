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
/** How long the board stays open for building before the next wave comes anyway. */
const val READY_SECONDS = 15f

/** Gold paid per whole second given up by starting a wave early. */
const val RUSH_BONUS_PER_SECOND = 2

/**
 * How hard the maze the player builds pushes back, as a multiplier on the extra length they added.
 *
 * Towers are walls, so a player can stretch a short walk into a long one and give their defence
 * many times as long to work. Measured, that was worth more than every other advantage together:
 * on an open board, mazing alone took a losing game to a clean sweep. Left alone it is not a
 * strategy, it is the answer, and the rest of the game is decoration.
 *
 * So enemies are tougher in proportion to how much longer the player made their walk. Mazing is
 * still good - more time under fire is still more time under fire - but it is now a trade rather
 * than free, which is what makes it a decision.
 *
 * 1 is proportional: twice the walk, twice the health. 0 turns it off entirely.
 */
const val MAZE_THREAT = 1f

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

    /** Distances to the exit for the maze as it stands. Rebuilt whenever a tower changes. */
    var flow: FlowField = buildFlow()
        private set

    /** The way through as it stands, drawn on the board so the player can see their maze. */
    var route: List<Cell> = flow.routeFrom(map.entry)
        private set

    /**
     * The shortest way across this map with nothing built on it.
     *
     * The baseline the maze is measured against, so that only the length the *player* added
     * counts. A map that comes with a long way round is the map's own character and should not
     * read as the player's doing - otherwise a board that generated a winding route would arrive
     * pre-punished.
     */
    private val directSteps: Int = flowFieldTo(map.exit) { map.isOpen(it) }[map.entry]
        .let { if (it == UNREACHABLE) 1 else max(1, it) }

    /**
     * How many times longer the current way through is than the bare map's. Never below 1.
     */
    val mazeFactor: Float
        get() {
            val steps = flow[map.entry]
            if (steps == UNREACHABLE) return 1f
            return (steps.toFloat() / directSteps).coerceAtLeast(1f)
        }

    /**
     * What the player's maze costs them in enemy health, as a multiplier.
     *
     * Read when each enemy *spawns* rather than when the wave starts. Building is allowed during
     * a wave, so a wave-start reading could be dodged by keeping the path short until the wave is
     * running and then mazing it up. Reading it per spawn closes that: later arrivals in the same
     * wave answer for the maze as it is when they set off.
     */
    val threatScale: Float get() = 1f + MAZE_THREAT * (mazeFactor - 1f)
    val projectiles = ArrayList<Projectile>()
    val blasts = ArrayList<Blast>()
    val beams = ArrayList<Beam>()

    /**
     * Seconds left to build before the next wave starts on its own.
     *
     * Counted in game time, so it pauses when the game does and runs down faster at 2x or 5x.
     */
    var readySeconds: Float = READY_SECONDS
        private set

    private var wave: Wave? = null
    private var waveClock: Float = 0f
    private var spawned: Int = 0

    /** Events raised since the last drain; see [drainSounds]. */
    private val pendingSounds = ArrayList<SoundCue>()

    val currentWave: Wave? get() = wave

    val finished: Boolean get() = phase == Phase.WON || phase == Phase.LOST

    /**
     * Whole seconds still showing on the countdown.
     *
     * Rounded up so the number the button shows is the number the bonus is paid on: whatever it
     * says when it is pressed is what gets paid.
     */
    val readySecondsLeft: Int
        get() = if (phase == Phase.READY) kotlin.math.ceil(readySeconds).toInt().coerceAtLeast(0) else 0

    /** Gold that starting the wave right now would pay. */
    val rushBonus: Int get() = readySecondsLeft * RUSH_BONUS_PER_SECOND

    /** Enemies still to come this wave, plus the ones already walking. */
    val enemiesLeft: Int get() = ((wave?.size ?: 0) - spawned) + enemies.size

    // --- player actions -------------------------------------------------------------------

    /**
     * Starts the next wave, paying the rush bonus for the building time given up.
     *
     * [rushed] is false when the countdown ran out and the wave came on its own, which earns
     * nothing — the reward is for choosing to go early, not for waiting.
     */
    fun startWave(rushed: Boolean = true): Boolean {
        if (phase != Phase.READY) return false
        if (rushed) {
            val bonus = rushBonus
            if (bonus > 0) {
                money += bonus
                pendingSounds.add(SoundCue.RUSH_BONUS)
            }
        }
        readySeconds = 0f
        waveNumber++
        wave = buildWave(waveNumber, totalWaves, random)
        waveClock = 0f
        spawned = 0
        phase = Phase.RUNNING
        return true
    }

    fun towerAt(cell: Cell): Tower? = towers.firstOrNull { it.cell == cell }

    /** Is an enemy standing on, or walking into, this cell? */
    private fun occupied(cell: Cell): Boolean =
        enemies.any { it.cell == cell || it.targetCell == cell }

    fun canBuild(kind: TowerKind, cell: Cell): Boolean =
        !finished &&
            map.isBuildable(cell.col, cell.row) &&
            towerAt(cell) == null &&
            money >= statsFor(kind).cost &&
            !occupied(cell) &&
            leavesAWayThrough(cell)

    /**
     * Would the board still be solvable with something standing on [cell]?
     *
     * Towers are walls, so without this check the player could simply fence the exit off and
     * never be troubled again. Every enemy already on the board has to keep a route too, or
     * building could strand one somewhere it can never be killed or leak from.
     */
    fun leavesAWayThrough(cell: Cell): Boolean {
        val candidate = flowFieldTo(map.exit) { at ->
            map.isOpen(at) && at != cell && towerAt(at) == null
        }
        if (!candidate.canReachExit(map.entry)) return false
        return enemies.all { candidate.canReachExit(it.targetCell) }
    }

    fun build(kind: TowerKind, cell: Cell): Boolean {
        if (!canBuild(kind, cell)) return false
        val stats = statsFor(kind)
        money -= stats.cost
        towers.add(Tower(stats, cell))
        rebuildFlow()
        return true
    }

    private fun buildFlow(): FlowField =
        flowFieldTo(map.exit) { at -> map.isOpen(at) && towerAt(at) == null }

    /** Recomputes the routing for the maze as it now stands, re-routing every enemy at once. */
    private fun rebuildFlow() {
        flow = buildFlow()
        route = flow.routeFrom(map.entry)
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
        rebuildFlow()
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
        if (phase == Phase.READY) countDownToNextWave(step)
        if (phase == Phase.RUNNING) releaseSpawns(step)
        moveEnemies(step)
        fireTowers(step)
        moveProjectiles(step)
        ageEffects(step)
        collectDead()
        advancePhase()
    }

    /** Runs the build clock down and sends the wave in when it reaches zero. */
    private fun countDownToNextWave(dt: Float) {
        readySeconds -= dt
        if (readySeconds <= 0f) {
            readySeconds = 0f
            startWave(rushed = false)
        }
    }

    private fun releaseSpawns(dt: Float) {
        val wave = wave ?: return
        waveClock += dt
        while (spawned < wave.size && wave.spawns[spawned].time <= waveClock) {
            val spawn = wave.spawns[spawned]
            enemies.add(
                Enemy(statsFor(spawn.kind), wave.hpScale * threatScale, wave.speedScale)
                    .apply { spawnAt(map.entry, flow) }
            )
            spawned++
        }
    }

    private fun moveEnemies(dt: Float) {
        val walkers = enemies.iterator()
        while (walkers.hasNext()) {
            val enemy = walkers.next()
            if (enemy.advance(dt, map.exit, flow)) {
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

    /** The enemy in range with the least ground left to cover, so the most urgent one. */
    private fun leadTarget(tower: Tower): Enemy? {
        var best: Enemy? = null
        var bestRemaining = Float.MAX_VALUE
        for (enemy in enemies) {
            if (!enemy.alive) continue
            if (distanceBetween(enemy.position, tower.center) > tower.range) continue
            val remaining = enemy.remainingSteps(flow)
            if (best == null || remaining < bestRemaining) {
                best = enemy
                bestRemaining = remaining
            }
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
            if (phase == Phase.READY) readySeconds = READY_SECONDS
        }
    }
}
