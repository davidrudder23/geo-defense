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

## Maps, and why there is no road

The board is an open field with fixed blockers scattered through it. There is no road, and no
fixed path: enemies work out their own way from the entry to the exit, and because **towers are
walls too**, where the player builds decides where the enemies walk. That is the whole game.

Generation places short walls rather than loose cells, so the field has something to steer
around instead of a scattering of dots. Each wall is kept only if the exit is still reachable
afterwards:

```
place a short wall
if the exit can no longer be reached from the entry:
    take it back again
```

Putting a wall back is cheaper than being clever about which walls are safe, and it makes the
invariant unconditional: a generated map always has a way through. `there is always a way from
the entry to the exit` checks three hundred of them.

## Routing

Routing is a **flow field**, in `Pathing.kt`. One breadth-first sweep outward from the exit
records how many steps every cell on the board is from it. An enemy then moves by looking at its
neighbours and walking to whichever has a smaller number.

This is worth understanding, because the obvious design — path each enemy individually — is
both slower and worse:

- **One sweep routes everybody.** Twenty enemies cost the same as one.
- **Re-routing is free.** When a tower goes up the field is rebuilt once, and every enemy on the
  board, including ones mid-stride, is re-routed by the next question it asks. No per-enemy
  bookkeeping, no invalidation logic.
- **Targeting falls out of it.** "Which enemy is closest to getting through" is just the smallest
  distance-to-exit, the same number the routing already computed.

It is breadth-first search rather than Dijkstra's algorithm because every move costs exactly one
step, and with uniform edge costs a FIFO queue pops cells in the same order a priority queue
would. Dijkstra would give identical answers for more work. If cells ever get *varying* movement
costs — mud that slows things down — that stops being true, and the `ArrayDeque` has to become a
priority queue. That is the seam to pull on.

Movement is deliberately spent cell by cell rather than in one jump per frame:

```kotlin
while (budget > 0f) {
    walk towards the middle of the target cell
    on arrival, ask the flow field for the next one
}
```

At 5x speed a triangle covers several cells in a frame. Without the loop it would cut corners,
and a corner here is a blocker.

## The rule that makes it a game and not a puzzle

Since towers are walls, nothing stops a player fencing the exit off and winning by default. So
every placement is checked first: build the flow field as it *would* be with something on that
cell, and refuse it if the entry can no longer reach the exit.

The same check also covers every enemy already walking, using the cell it is heading into.
Otherwise a tower could strand one somewhere it can neither be killed nor leak from, and the
wave would never end.

This is why the board only shows build dots on cells that are actually legal while a tower is
armed — the check runs per cell, and it is cheap enough to run for all of them, because the
board is 9x16 and a full sweep is 144 cells.

Two things that look like edge cases and are not:

- The cell just past the only gap in a wall is load-bearing even though the gap itself is still
  open. The reachability check sees this; a "is this cell a chokepoint" heuristic would not.
- A cell an enemy is standing on, or walking into, is refused outright. Dropping a wall on top
  of something is not a routing question.

## The wave timer

Between waves a 15 second clock runs, after which the next wave arrives on its own. Pressing the
button early pays 2 gold per second left on it.

It is counted in **game** time, not wall-clock time, which gets two behaviours for free: it stops
while the game is paused, and it runs down faster at 2x and 5x. It also means a game no longer
waits forever on a player who never presses anything.

The bonus is computed from the number *rounded up*, which is the same number the button is
showing. Whatever it says when it is pressed is what gets paid; a bonus that disagreed with the
display by one would read as a bug.

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
- **Enemy variety beyond the three.** Statuses (slow, burn) and healers all want hooks the
  engine does not currently have.
- **Varying movement costs.** Routing assumes every step costs the same, which is what lets it
  use breadth-first search. Terrain that slows enemies down would need a priority queue.
- **Enemies that ignore the maze.** Flying units that go straight over blockers would need to
  skip the flow field entirely, which is a clean addition but a second movement model.
- **Performance headroom.** Lists are iterated linearly every frame. Fine for the few dozen
  entities here; a few thousand would want spatial partitioning for target selection.
