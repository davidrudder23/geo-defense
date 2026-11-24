 # Geo Defense

A tower defense game for Android phones, built from bold geometric shapes. Portrait only. Every
map is generated fresh, and the soundtrack is synthesized at runtime rather than shipped as an
audio file.

Written as a learning project: the code is organised so the game rules can be read and changed
without touching any Android API, and the parts that are genuinely tricky are commented where
they sit. See [docs/architecture.md](docs/architecture.md) for how it fits together and why.

## Running it

```bash
./gradlew :app:installDebug     # build and install on a connected device or emulator
./gradlew :app:test             # run the unit tests
./gradlew :app:assembleDebug    # just build the APK
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` points at the Android SDK. Needs JDK 17+ and an Android SDK with API 37.

## How to play

Pick a game length on the menu: **10, 20, 25 or 30 waves**. Longer games are not harder games —
the difficulty curve is stretched to fit whatever length you choose, so the last wave of a
10-wave game is about as nasty as the last wave of a 30-wave game.

- **Build**: tap a tower in the bottom bar to arm it, then tap any free cell. Free cells show a
  green dot while a tower is armed.
- **Upgrade or sell**: tap a tower you have already built. Three levels; selling refunds 60%.
- **Start the wave** when you are ready. Nothing arrives until you say so.
- **Pause, speed and sound** sit in the top right. Speed cycles 1x, 2x, 5x and back to 1x.
  Building, upgrading and selling all still work while paused.
- You lose a life for every enemy that walks off the end of the road, two for a square. Twenty
  lives, and the board is lost.

### The pieces

| Enemy | Shape | Behaviour |
|---|---|---|
| Square | tumbling square | The tank. Most health, slowest, costs 2 lives if it gets through. |
| Triangle | arrowhead, points where it is running | Fast and flimsy. Dies to a couple of bullets. |
| Circle | ringed disc | Armoured. Every hit is blunted by a flat 5, so it takes many hits whatever you shoot it with. |

| Tower | Shape | Cost | Behaviour |
|---|---|---|---|
| Bullet | square | $20 | One target at a time, steady medium pace. |
| Blast | hexagon | $45 | Lobs a shell that explodes, damaging everything near where it lands. Slow. |
| Laser | plus sign | $60 | Fires a beam that runs off the edge of the board, hitting every enemy in the line. |

The laser aims at whichever enemy is furthest along the road and the beam continues to the board
edge, so it is at its best on a long straight stretch with enemies strung out along it.

## Where things live

```
app/src/main/java/org/noses/towerdefense/
  game/          the rules, in plain Kotlin with no Android imports
    GameMap.kt     board size, random road generation
    Enemies.kt     the three enemy kinds and their stats
    Towers.kt      the three tower kinds, levels, upgrade costs
    Waves.kt       what each wave is made of, and how it scales
    GameEngine.kt  the simulation: spawning, movement, firing, money, win/lose
    Effects.kt     shots in flight, blasts, beams
    Geometry.kt    vector maths for ranges and the laser line
  audio/
    Music.kt       the soundtrack, synthesized sample by sample
    MusicPlayer.kt hands the rendered loop to an AudioTrack
  ui/
    GameScreen.kt  the game loop and screen layout
    Board.kt       everything drawn on the board
    Hud.kt         bars, palette, tower panel
    MenuScreen.kt  title screen
    Shapes.kt      the shape vocabulary: polygons and the plus sign
    Palette.kt     every colour in the game
```

Everything under `game/` and `audio/Music.kt` is pure Kotlin, so it runs in ordinary JVM unit
tests with no emulator. That is where the 60 tests point.

## Tuning it

The numbers worth playing with, all in one place each:

- **Enemy stats** — `Enemies.kt`, the three `EnemyStats` values at the top.
- **Tower stats** — `Towers.kt`, the three `TowerStats` values. Upgrade scaling is in the `Tower`
  class below them.
- **Wave composition and difficulty curve** — `buildWave` in `Waves.kt`.
- **Starting money and lives** — the constants at the top of `GameEngine.kt`.
- **Board size** — `COLUMNS` and `ROWS` in `GameMap.kt`. The board scales to fit the screen.
- **Colours** — `Palette.kt`.

## The music

`Music.kt` writes the soundtrack one sample at a time: a 16-bar loop at 128 BPM in A minor,
over an Am–F–C–G progression. Four-on-the-floor kick, offbeat open hats, a backbeat clap, an
octave-jumping square-wave bassline, and a detuned saw lead that comes in at bar 4 and jumps an
octave for the last four bars. Roughly 30 seconds, rendered once on a background thread at
startup and then looped by the audio hardware, so it costs nothing while the game runs.

To hear it without installing the game, run `./gradlew :app:test` and open the WAV it writes to
`app/build/music.wav`.

## The sound effects

`Sfx.kt` synthesizes those too — there are no audio files anywhere in the project. Each weapon
has its own shot, and each enemy its own sound when it is destroyed:

| Sound | Character |
|---|---|
| Bullet shot | Dry, clipped pop. Kept quiet, because it fires constantly. |
| Blast launch / impact | Hollow thump, then the loudest thing on the board when the shell lands. |
| Laser shot | Downward zap. |
| Square destroyed | Low, dull crunch — it was the tank. |
| Triangle destroyed | Light, high tick — it barely had any health. |
| Circle destroyed | Metallic clank, built from deliberately inharmonic partials — it was the armoured one. |
| Upgrade | Three rising bell notes, an A major triad. |

The music is one pre-rendered buffer the hardware loops, which leaves no room to play anything
alongside it, so the effects get their own streaming track and a small mixer thread
([`SfxPlayer`](app/src/main/java/org/noses/towerdefense/audio/SfxPlayer.kt)). The engine never
touches audio: it raises named `SoundCue` events and the UI plays them.

`./gradlew :app:test` writes every effect to `app/build/sfx/*.wav` alongside the music, so they
can be auditioned without installing anything.
