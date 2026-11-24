package org.noses.towerdefense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.noses.towerdefense.audio.MusicPlayer
import org.noses.towerdefense.ui.GameScreen
import org.noses.towerdefense.ui.MenuScreen
import org.noses.towerdefense.ui.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    // null while on the menu; the wave count once a game is running.
    var waves by remember { mutableStateOf<Int?>(null) }
    // Bumped to throw away the old game and build a fresh one on "play again".
    var gameId by remember { mutableIntStateOf(0) }
    var musicOn by remember { mutableStateOf(true) }

    val music = remember { MusicPlayer() }

    // Rendering the loop takes a moment, so it happens once, off the main thread.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { music.prepare() }
        if (musicOn) music.start()
    }

    LaunchedEffect(musicOn) {
        if (musicOn) music.start() else music.pause()
    }

    DisposableEffect(music) {
        onDispose { music.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background)
            .systemBarsPadding(),
    ) {
        val running = waves
        if (running == null) {
            MenuScreen(onStart = { waves = it })
        } else {
            // The key means a new game gets a brand new engine and a new random map.
            androidx.compose.runtime.key(gameId) {
                GameScreen(
                    totalWaves = running,
                    musicOn = musicOn,
                    onToggleMusic = { musicOn = !musicOn },
                    onPlayAgain = { gameId++ },
                    onMenu = { waves = null },
                )
            }
        }
    }
}
