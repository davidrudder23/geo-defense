package org.noses.towerdefense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.noses.towerdefense.audio.SfxPlayer
import org.noses.towerdefense.game.BoardSize
import org.noses.towerdefense.game.GameEngine
import org.noses.towerdefense.game.generateMap
import org.noses.towerdefense.game.READY_SECONDS
import org.noses.towerdefense.game.Phase
import org.noses.towerdefense.game.SoundCue
import org.noses.towerdefense.game.Tower
import org.noses.towerdefense.game.TowerKind

/**
 * The numbers along the top of the screen, and the handful of facts the panels need.
 *
 * Holding these as a value rather than reading the engine straight from the HUD means the text
 * only recomposes when a number actually changes, instead of sixty times a second.
 */
data class HudState(
    val lives: Int,
    val money: Int,
    val wave: Int,
    val totalWaves: Int,
    val phase: Phase,
    val killed: Int,
    val leaked: Int,
    /** Whole seconds left to build before the next wave arrives on its own. */
    val readySeconds: Int,
    /** The same clock as a 0..1 fraction, for the bar inside the button. */
    val readyFraction: Float,
    /** Gold that starting the wave right now would pay. */
    val rushBonus: Int,
) {
    val finished: Boolean get() = phase == Phase.WON || phase == Phase.LOST
}

private fun GameEngine.hudState() = HudState(
    lives = lives,
    money = money,
    wave = waveNumber,
    totalWaves = totalWaves,
    phase = phase,
    killed = enemiesKilled,
    leaked = enemiesLeaked,
    readySeconds = readySecondsLeft,
    readyFraction = if (phase == Phase.READY) readySeconds / READY_SECONDS else 0f,
    rushBonus = rushBonus,
)

/**
 * The game itself: the board, the HUD above it and the build palette below.
 *
 * There are two separate update paths here, which is what keeps a game loop cheap in Compose:
 *
 *  - The **board** redraws every frame. [GameEngine] is a plain mutable object, not Compose
 *    state, and the draw code reads it directly. Reading the [frame] counter inside the draw
 *    lambda is what schedules the next pass, so a frame costs one draw and no recomposition.
 *  - The **HUD** recomposes only when a number changes, because it is handed a [HudState] value
 *    and equal values do not trigger work.
 *
 * Reading the engine straight from the HUD would look simpler but silently never update: the
 * engine is not observable, so nothing would tell Compose the money had changed.
 */
@Composable
fun GameScreen(
    totalWaves: Int,
    board: BoardSize,
    sfx: SfxPlayer,
    musicOn: Boolean,
    effectsOn: Boolean,
    onToggleMusic: () -> Unit,
    onToggleEffects: () -> Unit,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val engine = remember { GameEngine(totalWaves, generateMap(board)) }

    var frame by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(engine.hudState()) }
    var armed by remember { mutableStateOf<TowerKind?>(TowerKind.SQUARE) }
    var selected by remember { mutableStateOf<Tower?>(null) }
    var boardSize by remember { mutableStateOf(Size.Zero) }
    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(1) }

    // Reused every frame so draining sound cues allocates nothing.
    val cues = remember { ArrayList<SoundCue>() }

    // The game loop. withFrameNanos suspends until the display is ready for the next frame and
    // hands back its clock, so the simulation advances by however much real time passed. That
    // keeps the game running at the same speed whatever the refresh rate, and it stops
    // automatically when this screen leaves the composition.
    LaunchedEffect(engine) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L && !paused) {
                    val elapsed = (now - previous) / 1_000_000_000f
                    // advance() splits this into fixed steps, so 5x plays the same game as 1x.
                    engine.advance(elapsed * speed)
                    engine.drainSounds(cues)
                    sfx.playAll(cues)
                    cues.clear()
                }
                previous = now
                frame++
                // Cheap when nothing changed: an equal value recomposes nothing.
                hud = engine.hudState()
            }
        }
    }

    // A tower that has been sold is no longer on the board, so stop pointing at it.
    if (selected != null && selected !in engine.towers) selected = null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background),
    ) {
        TopBar(
            hud = hud,
            paused = paused,
            speed = speed,
            musicOn = musicOn,
            effectsOn = effectsOn,
            onTogglePause = { paused = !paused },
            onCycleSpeed = { speed = nextSpeed(speed) },
            onToggleMusic = onToggleMusic,
            onToggleEffects = onToggleEffects,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boardSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(engine) {
                        detectTapGestures { tap ->
                            if (boardSize == Size.Zero) return@detectTapGestures
                            val cell = projectionFor(boardSize, board.columns, board.rows)
                                .toCell(tap) ?: return@detectTapGestures
                            val existing = engine.towerAt(cell)
                            when {
                                // Tapping a tower selects it, so it can be upgraded or sold.
                                existing != null -> selected = existing
                                // Otherwise try to build whatever is armed.
                                armed != null -> {
                                    if (engine.build(armed!!, cell)) selected = null
                                }
                                else -> selected = null
                            }
                        }
                    },
            ) {
                drawGame(
                    engine,
                    projectionFor(size, board.columns, board.rows),
                    armed,
                    selected,
                    redrawKey = frame,
                )
            }

            if (hud.phase == Phase.READY) {
                StartWaveButton(
                    wave = hud.wave + 1,
                    secondsLeft = hud.readySeconds,
                    fraction = hud.readyFraction,
                    bonus = hud.rushBonus,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(0.7f),
                    onClick = { engine.startWave() },
                )
            }

            if (paused && !hud.finished) {
                PausedBanner()
            }

            if (hud.finished) {
                GameOverPanel(hud, onPlayAgain, onMenu)
            }
        }

        val chosen = selected
        if (chosen != null) {
            TowerInfoPanel(
                money = hud.money,
                tower = chosen,
                onUpgrade = { engine.upgrade(chosen) },
                onSell = {
                    engine.sell(chosen)
                    selected = null
                },
                onClose = { selected = null },
            )
        } else {
            TowerPalette(hud.money, armed) { armed = it }
        }
    }
}

/** 1x, 2x, 5x, and back round to 1x. */
fun nextSpeed(current: Int): Int = when (current) {
    1 -> 2
    2 -> 5
    else -> 1
}

/** A quiet marker that the game is held, without hiding the board behind it. */
@Composable
private fun PausedBanner() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PAUSED",
            color = Palette.text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp,
        )
    }
}

@Composable
private fun GameOverPanel(hud: HudState, onPlayAgain: () -> Unit, onMenu: () -> Unit) {
    val won = hud.phase == Phase.WON
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text(
                text = if (won) "BOARD HELD" else "OVERRUN",
                color = if (won) Palette.entry else Palette.exit,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (won) {
                    "All " + hud.totalWaves + " waves cleared"
                } else {
                    "Fell on wave " + hud.wave + " of " + hud.totalWaves
                },
                color = Palette.dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = hud.killed.toString() + " destroyed    " +
                    hud.leaked + " got through",
                color = Palette.text,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(
                    label = "PLAY AGAIN",
                    color = Palette.entry,
                    modifier = Modifier.width(150.dp),
                    onClick = onPlayAgain,
                )
                GameButton(
                    label = "MENU",
                    color = Palette.dim,
                    modifier = Modifier.width(110.dp),
                    onClick = onMenu,
                )
            }
        }
    }
}
