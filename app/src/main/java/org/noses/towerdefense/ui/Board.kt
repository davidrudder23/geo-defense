package org.noses.towerdefense.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.noses.towerdefense.game.COLUMNS
import org.noses.towerdefense.game.Cell
import org.noses.towerdefense.game.EnemyKind
import org.noses.towerdefense.game.GameEngine
import org.noses.towerdefense.game.Point
import org.noses.towerdefense.game.ROWS
import org.noses.towerdefense.game.Tower
import org.noses.towerdefense.game.TowerKind
import org.noses.towerdefense.game.center
import kotlin.math.floor
import kotlin.math.min

/**
 * Maps between board cells and pixels. The board keeps square cells and is centred in whatever
 * space it is given.
 */
data class Projection(val cellSize: Float, val originX: Float, val originY: Float) {
    fun toOffset(point: Point): Offset =
        Offset(originX + point.x * cellSize, originY + point.y * cellSize)

    fun toOffset(cell: Cell): Offset = toOffset(cell.center())

    /** Pixels for a length given in cells. */
    fun lengthOf(cells: Float): Float = cells * cellSize

    /** Which cell a touch landed on, or null if it fell outside the board. */
    fun toCell(offset: Offset): Cell? {
        val col = floor((offset.x - originX) / cellSize).toInt()
        val row = floor((offset.y - originY) / cellSize).toInt()
        return if (col in 0 until COLUMNS && row in 0 until ROWS) Cell(col, row) else null
    }
}

fun projectionFor(size: Size): Projection {
    val cellSize = min(size.width / COLUMNS, size.height / ROWS)
    return Projection(
        cellSize = cellSize,
        originX = (size.width - cellSize * COLUMNS) / 2f,
        originY = (size.height - cellSize * ROWS) / 2f,
    )
}

/**
 * Draws one frame.
 *
 * Order matters: ground, then road, then the things standing on it, then the shots, and finally
 * the flashes on top so they are never hidden.
 *
 * [redrawKey] is never read. Passing the game loop's frame counter is what makes this draw
 * depend on it: the argument is evaluated inside the draw phase, so Compose records the read and
 * schedules another draw every time the counter moves. Without it the board would freeze, since
 * nothing else here is Compose state.
 */
@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawGame(
    engine: GameEngine,
    projection: Projection,
    armed: TowerKind?,
    selected: Tower?,
    redrawKey: Int,
) {
    drawGround(engine, projection, armed != null)
    drawRoad(engine, projection)
    if (selected != null) drawRange(selected, projection)
    engine.towers.forEach { drawTower(it, projection, it == selected) }
    engine.enemies.forEach { drawEnemy(it, projection) }
    engine.projectiles.forEach { drawProjectile(it, projection) }
    engine.blasts.forEach { drawBlast(it, projection) }
    engine.beams.forEach { drawBeam(it, projection) }
}

private fun DrawScope.drawGround(engine: GameEngine, projection: Projection, armed: Boolean) {
    val cell = projection.cellSize
    drawRect(
        color = Palette.board,
        topLeft = Offset(projection.originX, projection.originY),
        size = Size(cell * COLUMNS, cell * ROWS),
    )
    // A dot in the middle of every cell you could build on. While a tower is armed the free
    // dots brighten, which shows where it can go without tinting half the board.
    for (row in 0 until ROWS) {
        for (col in 0 until COLUMNS) {
            if (!engine.map.isBuildable(col, row)) continue
            val free = engine.towerAt(Cell(col, row)) == null
            val highlight = armed && free
            drawCircle(
                color = if (highlight) Palette.entry.copy(alpha = 0.5f) else Palette.gridDot,
                radius = if (highlight) cell * 0.1f else cell * 0.07f,
                center = projection.toOffset(Cell(col, row)),
            )
        }
    }
}

private fun DrawScope.drawRoad(engine: GameEngine, projection: Projection) {
    val cell = projection.cellSize
    // Filling whole cells makes the road join up into one continuous band.
    engine.map.path.forEach { roadCell ->
        drawRect(
            color = Palette.road,
            topLeft = Offset(
                projection.originX + roadCell.col * cell,
                projection.originY + roadCell.row * cell,
            ),
            size = Size(cell, cell),
        )
    }
    // A dashed line down the middle of the road, which also shows the direction of travel.
    val centreLine = androidx.compose.ui.graphics.Path().apply {
        engine.map.path.forEachIndexed { index, roadCell ->
            val at = projection.toOffset(roadCell)
            if (index == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
        }
    }
    drawPath(
        path = centreLine,
        color = Palette.roadEdge,
        style = Stroke(
            width = cell * 0.08f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.22f, cell * 0.22f)),
        ),
    )
    // Where they come in, and where they get out.
    drawPath(
        path = trianglePath(projection.toOffset(engine.map.entry), cell * 0.3f, Math.PI.toFloat() / 2f),
        color = Palette.entry,
    )
    drawPath(
        path = hexagonPath(projection.toOffset(engine.map.exit), cell * 0.3f),
        color = Palette.exit,
        style = Stroke(width = cell * 0.1f),
    )
}

private fun DrawScope.drawRange(tower: Tower, projection: Projection) {
    if (tower.kind == TowerKind.PLUS) return // The laser reaches the whole board.
    drawCircle(
        color = colorOf(tower.kind).copy(alpha = 0.1f),
        radius = projection.lengthOf(tower.range),
        center = projection.toOffset(tower.center),
    )
    drawCircle(
        color = colorOf(tower.kind).copy(alpha = 0.4f),
        radius = projection.lengthOf(tower.range),
        center = projection.toOffset(tower.center),
        style = Stroke(width = projection.cellSize * 0.03f),
    )
}

private fun DrawScope.drawTower(tower: Tower, projection: Projection, isSelected: Boolean) {
    val cell = projection.cellSize
    val at = projection.toOffset(tower.center)
    val color = colorOf(tower.kind)

    // Plate under every tower, so they sit on the board rather than float over it.
    drawPath(squarePath(at, cell * 0.44f), Palette.panelEdge)
    if (isSelected) {
        drawPath(
            path = squarePath(at, cell * 0.46f),
            color = Palette.selection,
            style = Stroke(width = cell * 0.05f),
        )
    }

    when (tower.kind) {
        TowerKind.SQUARE -> {
            // The barrel swings round to track a target; the body stays square, because a
            // rotating square just reads as a diamond.
            drawLine(
                color = color,
                start = at,
                end = Offset(
                    at.x + kotlin.math.cos(tower.facing) * cell * 0.46f,
                    at.y + kotlin.math.sin(tower.facing) * cell * 0.46f,
                ),
                strokeWidth = cell * 0.13f,
                cap = StrokeCap.Round,
            )
            drawPath(squarePath(at, cell * 0.3f), color)
            drawPath(squarePath(at, cell * 0.14f), Palette.panelEdge)
        }

        TowerKind.HEXAGON -> {
            drawPath(hexagonPath(at, cell * 0.34f), color)
            drawPath(hexagonPath(at, cell * 0.18f), Palette.panelEdge)
        }

        TowerKind.PLUS -> {
            drawPath(plusPath(at, cell * 0.36f, tower.facing), color)
        }
    }

    drawLevelPips(tower, at, cell)
}

/** One pip per upgrade level, tucked under the tower. */
private fun DrawScope.drawLevelPips(tower: Tower, at: Offset, cell: Float) {
    if (tower.level <= 1) return
    val spacing = cell * 0.15f
    val start = at.x - spacing * (tower.level - 1) / 2f
    repeat(tower.level) { pip ->
        drawCircle(
            color = Palette.text,
            radius = cell * 0.045f,
            center = Offset(start + pip * spacing, at.y + cell * 0.36f),
        )
    }
}

private fun DrawScope.drawEnemy(enemy: org.noses.towerdefense.game.Enemy, projection: Projection) {
    val cell = projection.cellSize
    val at = projection.toOffset(enemy.position)
    val radius = projection.lengthOf(enemy.stats.radius)
    val color = colorOf(enemy.kind)

    when (enemy.kind) {
        // A blunt, slowly turning block.
        EnemyKind.SQUARE -> {
            drawPath(squarePath(at, radius, enemy.spin), color)
            drawPath(squarePath(at, radius * 0.5f, enemy.spin), Palette.board.copy(alpha = 0.55f))
        }
        // Points the way it is running.
        EnemyKind.TRIANGLE -> drawPath(trianglePath(at, radius * 1.15f, enemy.heading), color)
        // Ringed, to show it is armoured.
        EnemyKind.CIRCLE -> {
            drawCircle(color, radius, at)
            drawCircle(Palette.board, radius * 0.45f, at)
            drawCircle(color, radius * 0.72f, at, style = Stroke(width = cell * 0.045f))
        }
    }

    drawHealthBar(enemy, at, radius, cell)
}

private fun DrawScope.drawHealthBar(
    enemy: org.noses.towerdefense.game.Enemy,
    at: Offset,
    radius: Float,
    cell: Float,
) {
    if (enemy.hp >= enemy.maxHp) return
    val width = cell * 0.62f
    val height = cell * 0.08f
    val top = at.y - radius - height * 2f
    drawRect(
        color = Palette.board,
        topLeft = Offset(at.x - width / 2f, top),
        size = Size(width, height),
    )
    val fraction = enemy.healthFraction
    drawRect(
        color = if (fraction < 0.35f) Palette.healthLow else Palette.health,
        topLeft = Offset(at.x - width / 2f, top),
        size = Size(width * fraction, height),
    )
}

private fun DrawScope.drawProjectile(
    shot: org.noses.towerdefense.game.Projectile,
    projection: Projection,
) {
    val cell = projection.cellSize
    val at = projection.toOffset(shot.position)
    when (shot.kind) {
        TowerKind.SQUARE -> drawPath(squarePath(at, cell * 0.09f, shot.spin), Palette.squareTower)
        TowerKind.HEXAGON -> drawPath(hexagonPath(at, cell * 0.14f, shot.spin), Palette.hexagonTower)
        TowerKind.PLUS -> Unit // The laser is instant; there is nothing in flight.
    }
}

private fun DrawScope.drawBlast(blast: org.noses.towerdefense.game.Blast, projection: Projection) {
    val progress = blast.progress
    val radius = projection.lengthOf(blast.radius) * (0.35f + 0.65f * progress)
    val fade = 1f - progress
    drawCircle(
        color = Palette.hexagonTower.copy(alpha = 0.22f * fade),
        radius = radius,
        center = projection.toOffset(blast.center),
    )
    drawCircle(
        color = Palette.hexagonTower.copy(alpha = 0.9f * fade),
        radius = radius,
        center = projection.toOffset(blast.center),
        style = Stroke(width = projection.cellSize * 0.1f * fade),
    )
}

private fun DrawScope.drawBeam(beam: org.noses.towerdefense.game.Beam, projection: Projection) {
    val fade = 1f - beam.progress
    val from = projection.toOffset(beam.from)
    val to = projection.toOffset(beam.to)
    // Wide soft core with a bright centre, so it reads as a laser.
    drawLine(
        color = Palette.plusTower.copy(alpha = 0.3f * fade),
        start = from,
        end = to,
        strokeWidth = projection.cellSize * 0.4f * fade,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White.copy(alpha = 0.95f * fade),
        start = from,
        end = to,
        strokeWidth = projection.cellSize * 0.1f * fade,
        cap = StrokeCap.Round,
    )
}
