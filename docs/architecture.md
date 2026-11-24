# How this game is put together

Notes on the design decisions, aimed at someone who wants to build the next one. The ordinary
parts are left to the code; this covers the choices that were not obvious and the two things
that actually bit during the build.

## The shape of it

```
    MainActivity
         |
    MenuScreen  ──pick wave count──▶  GameScreen
                                           |
                        ┌──────────────────┼──────────────────┐
                        │                  │                  │
                   GameEngine          Board.kt            Hud.kt
                  (the rules)         (drawing)          (the text)
```

`GameEngine` owns everything that can change: the map, the enemies, the towers, the money. It is
a plain Kotlin class with no Android imports and no Compose state. The UI reads it; it never
reads the UI.

That split is the single most useful decision in the project. It means the entire game — map
generation, targeting, wave scaling, the win and lose conditions — is testable in ordinary JVM
unit tests that run in seconds without an emulator. Most of the bugs found during the build were
found that way.

## The game loop

A game loop in Compose looks like this:

```kotlin
LaunchedEffect(engine) {
    var previous = 0L
    while (true) {
        withFrameNanos { now ->
            if (previous != 0L) engine.update((now - previous) / 1_000_000_000f)
            previous = now
            frame++
        }
    }
}
```

`withFrameNanos` suspends until the display is ready for the next frame and hands back its clock.
Three things fall out of that for free: the simulation advances by real elapsed time, so the game
runs at the same speed at 60Hz or 120Hz; it is throttled to the display rather than spinning; and
it stops by itself when the screen leaves the composition, so there is no lifecycle code to get
wrong.

### Why `update` clamps its own timestep

```kotlin
val step = dt.coerceIn(0f, 0.05f)
```

If the app is backgrounded for thirty seconds, the next frame arrives with a thirty-second `dt`.
Without the clamp every enemy would teleport the length of the road in one step, straight past
every tower, and the player would lose the game while not looking at it. Clamping means a long
stall makes the game briefly run slow rather than break. There is a test for this
(`a long frame cannot teleport enemies through the defence`).

The trade-off is real: on a device that cannot hold 20fps, the game runs in slow motion rather
than skipping ahead. For this game that is the right way round.

### The redraw trick, and the bug it caused

The board redraws every frame, but nothing on it is Compose state — `GameEngine` is a mutable
object Compose knows nothing about. So what tells Compose to draw again?

Reading the `frame` counter **inside the draw lambda**:

```kotlin
Canvas(modifier) {
    drawGame(engine, projectionFor(size), armed, selected, redrawKey = frame)
}
```

`redrawKey` is never used. Passing it is the point: the argument is evaluated inside the draw
phase, Compose records the state read, and bumping the counter invalidates the draw. One draw per
frame, no recomposition at all.

**This is where it went wrong the first time.** The HUD was written as `TopBar(engine)`, reading
`engine.money` directly. It rendered once and then never updated again — cash sat at its starting
value while towers appeared on the board. The engine is not observable, so nothing ever told
Compose the number had changed. The board looked fine, which made it look like a HUD bug rather
than a state-model bug.

The fix is the `HudState` value class: once per frame the loop copies the handful of numbers the
HUD shows into a `data class` held in Compose state. Equal values recompose nothing, so the text
only re-renders when a number actually changes, and it is impossible for it to go stale.

The general rule: **anything Compose has to react to must be Compose state.** Reading a plain
object during composition reads it exactly once.

## Generating maps

The road is built one row at a time, always moving down:

```
pick a starting column
for each row:
    maybe run sideways a few cells
    step down one row
```

Because the row only ever increases, and each row is crossed in a single direction, the road
**cannot** cross or touch itself. That is worth more than it sounds: it means generation always
succeeds on the first try. No retry loop, no "give up after 100 attempts", no possibility of
shipping a map with no route from top to bottom. The invariant does the work that validation
would otherwise have to.

One extra rule earns its keep: a row that runs sideways is always followed by a row that only
steps down. Without it two sideways runs can land on neighbouring rows, and the road thickens
into a 2×2 block that reads as a rectangle rather than a lane you can follow. The first build had
exactly this problem and it looked like a rendering bug. `the road is never two cells thick`
guards it now.

Enemies walk the road by distance rather than by cell — `positionAt(distance)` interpolates
between the two cells either side — so movement is smooth and independent of frame rate, and
"which enemy is furthest along" is just a number comparison. Every tower targets using it.

## Difficulty, and what "no easy or hard" means

The brief said not to worry about difficulty, and that every game is 10, 20, 25 or 30 waves.
Those two together suggest something specific: the wave count should change how *long* you play,
not how *hard* it gets.

So `buildWave` scales off **progress through the game**, not the wave number:

```kotlin
val progress = (number - 1).toFloat() / (totalWaves - 1)
```

Wave 10 of 10 and wave 30 of 30 are near enough identical. A 30-wave game is not three times
harder than a 10-wave game, it is the same arc stretched out. There is a test pinning this
(`the difficulty arc is the same shape whatever the game length`).

## The three towers

They are deliberately different *shapes of answer*, not three damage numbers:

- **Bullet** homes on its target. The projectile keeps a reference to the enemy and is discarded
  if the enemy dies first, so damage is never wasted on a corpse.
- **Blast** does the opposite: the shell flies to a *fixed point* captured when it was fired. It
  explodes where it was aimed whether or not the target survived the trip, which is what makes it
  good against crowds and bad against stragglers.
- **Laser** picks a direction rather than a target. It aims at the enemy furthest along the road,
  then `rayExit` extends the beam to the edge of the board and everything within half a cell of
  that line takes the hit.

The armoured circle exists to stop one tower being the answer to everything. Armour subtracts a
flat amount per hit, so it punishes the laser (many small hits) and rewards the blast (few big
ones). Damage is floored at 1 per hit, or armour would make an enemy invulnerable rather than
tough — a distinction worth a test of its own.

## The music

`Music.kt` is a small offline synthesizer. Each voice is a function from time-since-the-note to
an amplitude, summed into a float buffer and normalized to 0.88 of full scale at the end:

- **kick** — a sine whose frequency sweeps 138Hz down to 48Hz in a few milliseconds
- **hats** — white noise through a one-line high pass, fast decay for closed, slow for open
- **clap** — three noise bursts 9ms apart, which is what stops it sounding like a single hiss
- **bass** — square wave, hard decay, jumping an octave every other note
- **lead** — two saws detuned 0.5% against each other

The noise is seeded, so the loop renders identically every time. That matters for a loop: it has
to join up with itself, and a test checks the first and last samples are both quiet so the seam
does not click.

Rendering is done once on a background thread at startup and written into a static `AudioTrack`
buffer with `setLoopPoints(0, frames, -1)`. The audio hardware loops it from there, so the game
never spends a cycle on music while it is running.

## What the tests cover

60 JVM tests, no emulator, a few seconds to run. Worth knowing what they are for:

- **Map generation** is checked across 300 random seeds, not one. Properties, not examples: the
  road starts at the top and ends at the bottom, every step is a single step, it never crosses
  itself, it is never two cells thick, and there is room left to build.
- **The engine** is tested on hand-built maps — a straight column, a serpentine — so towers can be
  placed at known distances and the result is deterministic. Winning, losing, building, selling,
  upgrading, piercing, and the long-frame clamp all have one.
- **The music** is checked structurally: right length for the tempo, not silent, normalized
  without clipping, quiet at both ends, a kick on every beat, and busier in the middle than at the
  start so the arrangement demonstrably happens.

The tests that failed first time all failed because the *test setup* was wrong, not the game —
asking a laser to pierce enemies it was not actually in line with, or expecting a game to be lost
when only one wave had been started. Worth remembering when writing tests against a simulation:
it is easy to assert something the scenario cannot produce.

## If you carry this forward

Things this deliberately does not do, roughly in the order they would be worth adding:

- **Saving.** Nothing persists. A process death loses the game in progress.
- **Sound effects.** There is no mixer — the music is one pre-rendered buffer. Effects need a
  second `AudioTrack` or a `SoundPool`.
- **Enemy variety beyond the three.** Statuses (slow, burn), flying units that skip the road, and
  healers all want hooks the engine does not currently have.
- **Pathfinding.** The road is fixed, so there is no routing. Maze-style tower defense, where
  towers block and enemies re-route, is a genuinely different engine: it needs A* and a rule that
  stops you walling the exit off completely.
- **Performance headroom.** Lists are iterated linearly every frame. Fine for the few dozen
  entities here; a few thousand would want spatial partitioning for target selection.
