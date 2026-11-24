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
import org.noses.towerdefense.game.GameEngine
import org.noses.towerdefense.game.Phase
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
    musicOn: Boolean,
    onToggleMusic: () -> Unit,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val engine = remember { GameEngine(totalWaves) }

    var frame by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(engine.hudState()) }
    var armed by remember { mutableStateOf<TowerKind?>(TowerKind.SQUARE) }
    var selected by remember { mutableStateOf<Tower?>(null) }
    var boardSize by remember { mutableStateOf(Size.Zero) }

    // The game loop. withFrameNanos suspends until the display is ready for the next frame and
    // hands back its clock, so the simulation advances by however much real time passed. That
    // keeps the game running at the same speed whatever the refresh rate, and it stops
    // automatically when this screen leaves the composition.
    LaunchedEffect(engine) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val elapsed = (now - previous) / 1_000_000_000f
                    engine.update(elapsed)
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
        TopBar(hud, musicOn, onToggleMusic)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boardSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(engine) {
                        detectTapGestures { tap ->
                            if (boardSize == Size.Zero) return@detectTapGestures
                            val cell = projectionFor(boardSize).toCell(tap) ?: return@detectTapGestures
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
                drawGame(engine, projectionFor(size), armed, selected, redrawKey = frame)
            }

            if (hud.phase == Phase.READY) {
                GameButton(
                    label = "START WAVE " + (hud.wave + 1),
                    color = Palette.entry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(0.7f),
                    onClick = { engine.startWave() },
                )
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
