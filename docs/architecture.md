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
Without the clamp every enemy would cross the whole board in one step, straight through the maze
and past every tower, and the player would lose the game while not looking at it. Clamping means a long
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

## Difficulty, and what the wave count means

Every game is 10, 20, 25 or 30 waves, and `buildWave` scales off the wave's **own number** rather
than how far through the game it sits:

```kotlin
hpScale = scale * (1f + growth * (number - 1)).pow(exponent)
```

So wave 8 is wave 8 in every game, and choosing a longer game is choosing a harder one: a 10-wave
game is a sampler, 20 is a real game, and 30 climbs well past where either of them stops. Two
tests pin this down - `a wave is the same wave whatever the game length` and `choosing a longer
game is choosing a harder one`.

### Why it is a power law and not a compounding rate

The curve used to be `rate^(number - 1)`, a fixed percentage per wave. That shape cannot be tuned
to fit, and the reason is worth keeping: an exponential and the player's power are different kinds
of function. Player power comes out of *cumulative* income, which grows about quadratically, so an
exponential wave curve crosses it steeply - the game is trivial on one side of the crossing and
hopeless on the other, with nothing in between to aim at. Measurement showed it exactly: 29 waves
with zero leaks, then 16 of 20 lives gone on wave 30.

A power law rises on the same sort of terms as the thing it is racing. The ratio between
consecutive waves decelerates - early waves climb steeply, late ones level off - which is what
lets the difficulty sit *near* the player's power for a long stretch instead of crossing it once.

This replaced an earlier design where difficulty scaled on *progress*, so every game length ended
at the same peak and the wave count changed only how long you sat there. The reason for the swap
is that the player is not standing still either: thirty waves is also thirty waves of income, of
building, and of route to lengthen, so the longer game can afford to ask for more.

The curve compounds rather than adds, because the *ratio* between consecutive waves is what a
player feels. A flat increment makes wave 2 twice wave 1 and wave 20 a few percent above wave 19:
the whole spike lands at the start and the game flattens out exactly when it should be tightening.

### Why the tower level cap is deep

`MAX_TOWER_LEVEL` is 8, and each level costs about 1.85x the one before. That is not generosity,
it is a consequence of the above. With difficulty keyed to the wave number, a player whose power
stops growing is overtaken for certain, and measurement showed exactly that: with a cap of 3, a
30-wave game ended with the defence maxed out at 48 towers, **5,500 gold banked with nothing to
spend it on**, and the waves walking straight past it.

The cost ratio is close to the power a level buys (1.55x damage at 0.85x reload, so about 1.8x
output). Pricing them alike keeps gold worth about the same spent deep or spent wide, so the
choice between them is about board space and timing rather than one of them simply being correct.

### The economy and the life budget also scale

Two things scale with the game beside enemy health, and neither was a decoration - without them a
30-wave game cannot be made tough-but-winnable at all.

**Kills pay more in later waves** (`rewardGrowth`). The board only fits so many towers, so past a
point the only way to get stronger is to buy levels. A 30-wave game was ending with 10,262 gold
invested and 672 in hand: the player was not saturated, they were *broke*. Every curve that made
wave 20 cost lives then lost wave 30 outright, because income could not keep pace with its own
difficulty. The effect compounds more at wave 30 than at wave 10, which is where it is needed.

**Longer games start with more lives** (`startingLivesFor`): 20 for a 10-wave game, 30 for 20
waves, 40 for 30. The life pool is the budget for a whole run. Once the late waves cost a few
lives each - which is what "tough" means - ten waves of that drains twenty lives no matter how the
curve is shaped, and measurement bore it out: every curve that made wave 20 cost anything lost
wave 30, and not to a spike. The worst single wave was four to six lives. It simply bled out.

### Boards have to be comparable before a curve means anything

`generateMap` draws up to 200 boards and keeps the first whose walk is long enough - 38% of the
board's cells, so 55 steps on the medium board - at 24% blocker density. Boards that cannot reach
that in 200 draws fall back to the longest one drawn. That is not cosmetic. Route length drives difficulty more than any other single
thing, and a single random draw gave anywhere from 19 to 74 steps - which is not a difficulty
curve, it is a lottery. Ten rounds of tuning were quietly fitting one map: the curve that won
every game on it lost the 20- and 30-wave games on two other boards.

Holding the walk to a narrow band is what makes one curve meaningful. It is not sufficient, though
- at matched length, board *shape* still swings the outcome, so the sweep scores every curve on
three boards at three lengths and a curve has to survive the hardest of them, not the luckiest.

## Board size, and why it carries a correction

The menu offers four sizes, and bigger means *more cells*, not the same cells magnified: the
projection divides the screen by the grid, so the cells shrink and more of them fit.

| size | cells | walk, measured |
|---|---|---:|
| S | 7 x 12 | 28-32 |
| M | 9 x 16 | 47-50 |
| L | 12 x 21 | 95-96 |
| XL | 15 x 26 | 106-126 |

Each size also carries a health multiplier, and this is the part that is not obvious. A size is
meant to be a choice about the board, not about how hard the game is - but a bigger board is a
longer walk *and* more places to build, and both make a game easier on their own. Left
uncorrected the picker would have been a difficulty slider by accident, and not a subtle one. At
20 waves, uncorrected:

| size | lives left, two boards |
|---|---|
| S | **2/30**, 25/30 |
| M | 30/30, 21/30 |
| L | 30/30, 30/30 |
| XL | 30/30, 28/30 |

So the corrections are 0.85 for small, 1.10 for large and x-large, and 1 for medium by definition,
since medium is the board the curve itself was tuned against. `CurveSweepTest` has the sweep that
chose them, and with them every size plays all three lengths without losing, with 30 waves costing
real lives on all four.

The correction lives in `GameEngine`, applied to the curve it was handed, rather than inside the
curve. Board size is a choice the player makes up front, like the wave count - not something that
emerges from how they play - which is also why a hand-built board such as a test fixture carries
no size and plays the curve exactly as given.

## Measuring difficulty

`Probe.kt`, in the test sources, plays whole games headlessly and records what happened wave by
wave. It is an instrument rather than a test: it only reads the engine's public surface, changes
nothing, and its output is evidence about the build it ran against.

```
./gradlew :app:testDebugUnitTest --tests "*DifficultyProbeTest"
```

The report prints, and is written to `app/build/difficulty/report.md`.

It exists because a win or lose verdict cannot answer the only question that matters for tuning.
A game won with every life intact and a game won on the last life both say `WON`. The columns
that do answer it:

- **`leak` and `lost`** - enemies that reached the exit, and the lives that cost. The per-wave
  curve is the point; a total hides the shape, because zero leaks for nine waves then eight on the
  tenth and zero leaks throughout are completely different games that end identically.
- **`reach`** - how far along the route the deepest enemy got before dying, as a percentage. This
  is the one a leak count cannot give you: zero leaks can mean "comfortable" or "every enemy died
  on the last tower", and only this separates them.
- **`path`** - the route length. Since the player authors the maze, this is a lever they hold
  rather than one the game sets.
- **`inv` against `cash`** - power bought so far against money still in hand. Where cash climbs
  while `inv` sits still, the economy has run out of things to sell.

A stand-in player stands in for a human, because difficulty is a property of the *pair*. Several
are needed: `Spam` buys quantity, `Upgrader` buys quality, `Mixed` spreads across the three
towers, and `Mazer` puts every tower wherever it makes the walk longest. `Mazer` is the one worth
watching, since "build the longest possible snake" is available as a single dominant answer, and
if it is far stronger than placing towers sensibly then the maze is the whole game.

**What the tuned curve does.** Nine games - three lengths on three boards - with `Mazer`, which
is close to an optimal player:

| waves | lives left, by board | what it reads as |
|---:|---|---|
| 10 | 20/20, 17/20, 20/20 | easy; a leak or two at worst |
| 20 | 30/30, 21/30, 24/30 | costs real lives on two boards of three |
| 30 | 35/40, 12/40, 22/40 | tough everywhere; one board came down to 12 |

Two caveats worth keeping. A near-optimal player still finds the 20-wave game free on one board of
three, so "difficult at 20" is partly a property of the draw rather than the curve. And `Mazer`
plays better than a person will, so a human should expect to do worse than every row above.

**What the probe found earlier.** Three things stood out, and none of them were "enemies need more
HP":

- **The maze is the dominant lever, by a long way.** Three runs on an open field, differing by one
  thing each, separate it from the upgrades it is normally tangled up with:

  | strategy | path | peak reach | leaks |
  |---|---:|---:|---:|
  | spam, no maze, level one | 15 | 100% | 5 |
  | maze only, level one throughout | 83 | 23% | 0 |
  | maze and levels | 83 | 11% | 0 |

  Mazing alone turns the game's only leaky run into a clean sweep and drops `reach` from 100% to
  23%. Levelling then buys 23% down to 11%. So the maze is worth more than the upgrades, and the
  single scenario that leaked is the one where the player declined to build one - which is the
  real finding: the game is only difficult if you play it badly.

- **The economy has no sink.** Bullet spam saturates at 20 towers around wave 4 and then banks
  everything: by wave 20 it is sitting on 5,234 unspent gold against 400 invested. Money stops
  being a decision long before the game ends, which is why levers that squeeze income do nothing
  on their own.

- **The rush bonus is currently free money.** Taking it every wave is worth 30 gold a wave, and
  turning it off changes the outcome not at all, for the same reason: cash was never the
  constraint.

Two cautions about reading the report. The generated-board runs vary route length and tower count
together, so they suggest that shorter boards play closer but do not establish it - the open-field
triple above is the controlled comparison, and the generated ones are only a sanity check that
real boards behave like the fixtures. And every stand-in player eventually stops spending, so each
run is a *lower* bound on how strong a human could be: the numbers say the game is too easy even
against a player who wastes thousands of gold.

The thresholds behind the `verdict` line are a starting point for naming what the numbers show,
not a rule the game enforces. They are meant to be argued with.

## The three towers

They are deliberately different *shapes of answer*, not three damage numbers:

- **Bullet** homes on its target. The projectile keeps a reference to the enemy and is discarded
  if the enemy dies first, so damage is never wasted on a corpse.
- **Blast** does the opposite: the shell flies to a *fixed point* captured when it was fired. It
  explodes where it was aimed whether or not the target survived the trip, which is what makes it
  good against crowds and bad against stragglers.
- **Laser** picks a direction rather than a target. It aims at the enemy with the least ground
  left to cover, then `rayExit` extends the beam to the edge of the board and everything within
  half a cell of that line takes the hit.

Every tower picks its target the same way, by fewest steps still to walk rather than most distance
travelled. On a fixed road those are the same question. In a maze they are not: an enemy that has
walked a long way round may be further from the exit than one that has just set off down a short
way through, and the one about to get out is the one worth shooting. The flow field already knows
that number, so targeting costs nothing extra.

The laser is at its best on a long straight stretch with enemies strung out along it - and since
the player builds the maze, they can lay that stretch out on purpose and point a laser down it.

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

124 JVM tests, no emulator, a few seconds to run. Worth knowing what they are for:

- **Map generation** is checked across 300 random seeds, not one. Properties, not examples: the
  entry is on the top row and the exit on the bottom, neither is ever blocked, every blocker is on
  the board, there is always a way from one gate to the other, there are enough blockers to be
  worth navigating, there is room left to build, maps differ from each other, and the way through
  is never just a straight line down the board.
- **Routing** has its own file. That a step is a step and not a diagonal, that off-board and
  walled-off cells read as unreachable, that `next` always moves strictly closer to the exit, that
  a route runs end to end without gaps, and that a walker holds its line when two ways are equally
  short.
- **The maze rules** are the ones worth having. A tower that would seal the exit off is refused; so
  is one that would dead-end the only gap in a wall, while a tower that closes one way through
  when another remains is allowed. Building bends the route, selling opens it back up, a tower
  cannot be dropped on an enemy, and enemies already walking are never walled in.
- **The engine** is tested on hand-built maps — an open field, a two-wide lane, a single column, a
  serpentine — so towers sit at known distances and the result is deterministic. Winning, losing,
  building, selling, upgrading, piercing, and the long-frame clamp all have one.
- **The wave timer** is checked in game time: the clock counts down and starts the wave by itself,
  starting early pays exactly what the button was showing, nothing is paid when it runs out, and
  the clock neither runs during a wave nor after the game is over.
- **The music** is checked structurally: right length for the tempo, not silent, normalized
  without clipping, quiet at both ends, a kick on every beat, and busier in the middle than at the
  start so the arrangement demonstrably happens.

The tests that failed first time nearly all failed because the *test setup* was wrong, not the
game — asking a laser to pierce enemies it was not actually in line with, expecting a game to be
lost when only one wave had been started, or putting a tower on a fixture cell that turned out to
be a blocker. Worth remembering when writing tests against a simulation: it is easy to assert
something the scenario cannot produce.

The best of them was a test that asserted a legal build and got a refusal. The cell sat directly
below the only gap in the wall above it, so putting a tower there dead-ended the corridor even
though the gap itself stayed open. The engine was right and the test was wrong. It is also the
case that sold the full reachability check over a cheaper "is this cell a chokepoint" heuristic,
which would have waved that placement through — the kind of thing that is obvious in a picture and
invisible in a rule of thumb.

One fixture was wrong in a way that passed. An early serpentine left columns 7 and 8 open on every
row, so enemies ran straight down the side and skipped the snake entirely: the route measured 30
steps instead of 64, and every test built on it was quietly measuring a much easier board. A
fixture that produces a *plausible* board is more dangerous than one that crashes.

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
