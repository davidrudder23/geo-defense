package org.noses.towerdefense.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The board size is a menu choice, so it has to change the board and not the game.
 *
 * "Not the game" is the part worth testing: a bigger board is a longer walk and more places to
 * build, which makes a game easier on its own, so each size carries a correction. Measurement
 * found the uncorrected gap was wide enough to turn the size picker into a difficulty picker.
 */
class BoardSizeTest {

    @Test
    fun `every size generates a board of its own dimensions`() {
        BoardSize.entries.forEach { size ->
            val map = generateMap(size, Random(3))
            assertEquals(size.label + " columns", size.columns, map.columns)
            assertEquals(size.label + " rows", size.rows, map.rows)
            assertEquals(size.label + " remembers its size", size, map.size)
        }
    }

    @Test
    fun `the sizes are ordered, and the medium one is what the curve was tuned against`() {
        val cells = BoardSize.entries.map { it.cells }
        assertEquals("sizes are not in increasing order", cells.sorted(), cells)
        assertEquals(BoardSize.MEDIUM, DEFAULT_BOARD_SIZE)
        // Medium is the reference, so correcting it would mean correcting the thing it is
        // measured against.
        assertEquals(1f, BoardSize.MEDIUM.difficulty, 0.0001f)
    }

    @Test
    fun `entry and exit stay on opposite edges whatever the size`() {
        BoardSize.entries.forEach { size ->
            val map = generateMap(size, Random(11))
            assertEquals(size.label + " entry row", 0, map.entry.row)
            assertEquals(size.label + " exit row", size.rows - 1, map.exit.row)
            assertTrue(size.label + " entry column", map.entry.col in 0 until size.columns)
            assertTrue(size.label + " exit column", map.exit.col in 0 until size.columns)
        }
    }

    @Test
    fun `a bigger board gets a longer walk`() {
        // Not a tautology: the route floor is a fraction of the board rather than a fixed count,
        // precisely so that it scales. A fixed 55 steps is more cells than the small board has.
        val lengths = BoardSize.entries.map { size ->
            val map = generateMap(size, Random(7))
            flowFieldOn(map) { map.isOpen(it) }[map.entry]
        }
        assertTrue("walks did not grow with the board: $lengths", lengths == lengths.sorted())
        assertTrue("the small board has no walk to speak of: $lengths", lengths.first() > 10)
    }

    /**
     * The health of the first enemy of wave one, which is what the curve comes out as.
     *
     * Read from a spawned enemy rather than from the wave, because the wave is the engine's own
     * business and a test is not a reason to open it up.
     */
    private fun firstEnemyHp(engine: GameEngine): Int {
        engine.startWave()
        repeat(2000) {
            engine.update(0.05f)
            engine.enemies.firstOrNull()?.let { return it.maxHp }
        }
        throw AssertionError("nothing ever spawned")
    }

    @Test
    fun `the size correction reaches the waves`() {
        // Same wave number, same curve, different board: only the correction can explain a
        // difference, which is what makes it a correction rather than a coincidence.
        val curve = DifficultyCurve()
        val small = firstEnemyHp(
            GameEngine(20, generateMap(BoardSize.SMALL, Random(7)), Random(1), curve = curve),
        )
        val large = firstEnemyHp(
            GameEngine(20, generateMap(BoardSize.LARGE, Random(7)), Random(1), curve = curve),
        )
        assertTrue("small board should ask for less: $small against $large", small < large)
    }

    @Test
    fun `a hand built board plays the curve as given`() {
        // Fixtures carry no size, so nothing should be applied to them.
        val map = Fixtures.openField()
        assertEquals(null, map.size)
        val plain = firstEnemyHp(GameEngine(20, map, Random(1)))
        val expected = Enemy(SQUARE_ENEMY, buildWave(1, 20, Random(1)).hpScale).maxHp
        assertEquals(expected, plain)
    }

    @Test
    fun `the projection shrinks the cells as the board grows`() {
        val space = androidx.compose.ui.geometry.Size(1080f, 1920f)
        val sizes = BoardSize.entries.map {
            org.noses.towerdefense.ui.projectionFor(space, it.columns, it.rows).cellSize
        }
        assertTrue("cells did not shrink: $sizes", sizes == sizes.sortedDescending())
        // The whole board has to fit in the space it was given, or part of it is off screen.
        BoardSize.entries.forEach { size ->
            val projection = org.noses.towerdefense.ui.projectionFor(space, size.columns, size.rows)
            assertTrue(
                size.label + " is wider than the screen",
                projection.cellSize * size.columns <= space.width + 0.01f,
            )
            assertTrue(
                size.label + " is taller than the screen",
                projection.cellSize * size.rows <= space.height + 0.01f,
            )
        }
    }
}
