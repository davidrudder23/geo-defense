package org.noses.towerdefense.game

/**
 * Something worth hearing that just happened.
 *
 * The engine names the event; it has no idea what any of these sound like, or that an audio
 * system exists at all. The UI drains them each frame and plays the matching clip, which keeps
 * the simulation testable and lets the sounds be redesigned without touching the rules.
 */
enum class SoundCue {
    /** A bullet tower fired a single shot. */
    BULLET_SHOT,

    /** A blast tower lobbed a shell. */
    BLAST_LAUNCH,

    /** A shell landed and exploded. */
    BLAST_IMPACT,

    /** A laser tower fired its beam. */
    LASER_SHOT,

    /** A square went down: heavy and low, because it was a tank. */
    SQUARE_DOWN,

    /** A triangle went down: light and quick, because it barely had any health. */
    TRIANGLE_DOWN,

    /** A circle went down: metallic, because it was armoured. */
    CIRCLE_DOWN,

    /** A tower was upgraded. */
    UPGRADE,

    /** Paid for starting a wave before the build clock ran out. */
    RUSH_BONUS,
}

/** The sound a given enemy makes when it is destroyed. */
fun deathCueFor(kind: EnemyKind): SoundCue = when (kind) {
    EnemyKind.SQUARE -> SoundCue.SQUARE_DOWN
    EnemyKind.TRIANGLE -> SoundCue.TRIANGLE_DOWN
    EnemyKind.CIRCLE -> SoundCue.CIRCLE_DOWN
}

/** The sound a given tower makes when it fires. */
fun shotCueFor(kind: TowerKind): SoundCue = when (kind) {
    TowerKind.SQUARE -> SoundCue.BULLET_SHOT
    TowerKind.HEXAGON -> SoundCue.BLAST_LAUNCH
    TowerKind.PLUS -> SoundCue.LASER_SHOT
}
