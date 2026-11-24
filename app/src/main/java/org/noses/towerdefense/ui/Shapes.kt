package org.noses.towerdefense.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shape vocabulary of the game. Everything on screen is built from these, so enemies and
 * towers of the same family read as related.
 */

/** A regular polygon with [sides] corners, the first corner placed at [rotation] radians. */
fun polygonPath(center: Offset, radius: Float, sides: Int, rotation: Float = 0f): Path {
    val path = Path()
    for (corner in 0 until sides) {
        val angle = rotation + corner * 2f * PI.toFloat() / sides
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** An axis-aligned square, which is a 4-gon turned an eighth of a turn. */
fun squarePath(center: Offset, radius: Float, rotation: Float = 0f): Path =
    polygonPath(center, radius, 4, rotation + PI.toFloat() / 4f)

fun hexagonPath(center: Offset, radius: Float, rotation: Float = 0f): Path =
    polygonPath(center, radius, 6, rotation)

fun trianglePath(center: Offset, radius: Float, rotation: Float = 0f): Path =
    polygonPath(center, radius, 3, rotation)

/** A plus sign, [radius] from the middle to the tip of each arm. */
fun plusPath(center: Offset, radius: Float, rotation: Float = 0f): Path {
    val arm = radius * 0.38f
    val corners = listOf(
        -arm to -radius, arm to -radius,
        arm to -arm, radius to -arm,
        radius to arm, arm to arm,
        arm to radius, -arm to radius,
        -arm to arm, -radius to arm,
        -radius to -arm, -arm to -arm,
    )
    return rotatedPath(corners, center, rotation)
}

/** Turns local (x, y) corners into a path rotated by [rotation] about [center]. */
private fun rotatedPath(
    corners: List<Pair<Float, Float>>,
    center: Offset,
    rotation: Float,
): Path {
    val path = Path()
    val cosine = cos(rotation)
    val sine = sin(rotation)
    corners.forEachIndexed { index, (x, y) ->
        val px = center.x + x * cosine - y * sine
        val py = center.y + x * sine + y * cosine
        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}
