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
./gradlew :app:test --tests "*DifficultyProbeTest"   # play games headlessly and report difficulty
./gradlew :app:assembleDebug    # just build the APK
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` points at the Android SDK. Needs JDK 17+ and an Android SDK with API 37.

## How to play

Pick a board size on the menu — **S, M, L or XL** (7x12 up to 15x26). Bigger means more cells
rather than a bigger picture: the cells shrink and more of them fit on the screen, so a large
board is a wider canvas to build a maze on. Each size carries a health correction, because a
longer walk and more room to build would otherwise make the big boards much easier — so the size
changes the board, not the difficulty.

Then pick a game length: **10, 20, 25 or 30 waves**. Longer games *are* harder games: each
wave scales off its own number, so wave 8 is wave 8 whatever length you picked, and a 30-wave game
climbs well past where a 10-wave one stops. A 10-wave game is a sampler, 20 is a real game, and 30
is meant to be tough. Longer games also start with more lives — 20, 30 and 40 — and pay more per
kill in the later waves, because the defence has to keep growing to stay level with the waves.

- **Build**: tap a tower in the bottom bar to arm it, then tap any free cell. Free cells show a
  green dot while a tower is armed.
- **Upgrade or sell**: tap a tower you have already built. Eight levels, each costing about 1.85x
  the one before; selling refunds 60%.
- **Start the wave** when you are ready — or wait, and it starts itself. The button counts
  down from 15 seconds, and pressing it early pays 2 gold per second you hand back.
- **Build anywhere.** There is no road. Towers are walls, so where you put them decides the
  route the enemies take. You cannot seal the exit off completely — that placement is refused.
  The dashed line shows the way through as it currently stands.
- **Build the long way round.** A longer route means more time under fire, so bending the path
  back on itself is worth doing — and worth doing early, because the waves get harder every time.
- **Pick your length deliberately.** Wave 8 is wave 8 in every game, so a 30-wave run is not a
  10-wave run stretched out; it climbs far past where the short game stops.
- **Pause, speed, music and effects** sit in the top right. Music and effects mute separately,
  so you can keep the shots and drop the backing track. Speed cycles 1x, 2x, 5x and back to 1x.
  Building, upgrading and selling all still work while paused.
- You lose a life for every enemy that reaches the exit, two for a square. Twenty lives, and the
  board is lost.

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

Every tower shoots at whichever enemy in range has the least ground left to cover — the one
closest to getting out, which in a maze is not always the one that has walked furthest. The
laser's beam carries on to the edge of the board, so it is at its best down a long straight
stretch with enemies strung out along it, and since you build the maze you can lay that stretch
out for it on purpose.

## Where things live

```
app/src/main/java/org/noses/towerdefense/
  game/          the rules, in plain Kotlin with no Android imports
    GameMap.kt     board size, blockers, random map generation
    Pathing.kt     the flow field: one sweep that routes every enemy
    Enemies.kt     the three enemy kinds and their stats
    Towers.kt      the three tower kinds, levels, upgrade costs
    Waves.kt       what each wave is made of, and how it scales
    GameEngine.kt  the simulation: spawning, movement, firing, money, win/lose
    Effects.kt     shots in flight, blasts, beams
    Geometry.kt    vector maths for ranges and the laser line
    Sounds.kt      the sound cues the rules ask for, named but not played
  audio/
    Music.kt       the soundtrack, synthesized sample by sample
    MusicPlayer.kt hands the rendered loop to an AudioTrack
    Sfx.kt         every sound effect, synthesized the same way
    SfxPlayer.kt   mixes and plays the clips
  ui/
    GameScreen.kt  the game loop and screen layout
    Board.kt       everything drawn on the board
    Hud.kt         bars, palette, tower panel
    MenuScreen.kt  title screen
    Shapes.kt      the shape vocabulary: polygons and the plus sign
    Palette.kt     every colour in the game
```

Everything under `game/` and `audio/Music.kt` is pure Kotlin, so it runs in ordinary JVM unit
tests with no emulator. That is where the 124 tests point.

## Tuning it

The numbers worth playing with, all in one place each:

- **Enemy stats** — `Enemies.kt`, the three `EnemyStats` values at the top.
- **Tower stats** — `Towers.kt`, the three `TowerStats` values. Upgrade scaling is in the `Tower`
  class below them.
- **Wave composition and difficulty curve** — `buildWave` in `Waves.kt`.
- **Starting money and lives** — the constants at the top of `GameEngine.kt`. Lives scale with the
  game length in `startingLivesFor`.
- **Difficulty curve** — `DifficultyCurve` in `Waves.kt`: enemy health, and how fast kills start
  paying more. `CurveSweepTest` is the grid search that picked the current values.
- **Board generation** — `GameMap.kt`. Boards are drawn repeatedly and filtered on route length,
  because a board's route length drives difficulty more than anything else does.
- **Board sizes** — the `BoardSize` enum in `GameMap.kt`: the four grids, and the health
  multiplier each one carries so that size stays a choice about the board rather than about
  difficulty.
- **Colours** — `Palette.kt`.

### Measuring a change

Guessing at balance is slow, so there is an instrument for it. `DifficultyProbeTest` plays whole
games headlessly with a stand-in player and reports what happened wave by wave:

```bash
./gradlew :app:test --tests "*DifficultyProbeTest"
```

The table prints, and lands in `app/build/difficulty/report.md`. It changes nothing about the
game; it only watches. Beyond the obvious columns, the two that earn their place are `reach`, how
far along the route the deepest enemy got before dying, and `path`, how long a maze the player
built. Zero leaks can mean "comfortable" or "died on the last tower", and `reach` is what tells
those apart. Several stand-in players run, because difficulty is a property of the player and the
waves together, not of the waves alone.

As of the current numbers a near-optimal stand-in wins all three lengths on all three boards, and
the cost climbs with length: a 10-wave game takes 0–3 lives of 20, a 20-wave game 0–9 of 30, and a
30-wave game 5–28 of 40. A player who never upgrades still loses by wave 10, which is the floor
working. Two honest caveats: on one board of three the 20-wave game is still free, so "difficult"
there depends partly on the draw; and the stand-in plays better than a person will.
`docs/architecture.md` has the rest, including why the curve is a power law rather than a
compounding rate, and why boards have to be made comparable before a curve means anything.

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
