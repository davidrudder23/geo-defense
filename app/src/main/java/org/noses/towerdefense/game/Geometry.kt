package org.noses.towerdefense.game

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

fun distanceBetween(a: Point, b: Point): Float = hypot(b.x - a.x, b.y - a.y)

/** [target] seen from [from], scaled to length 1. Returns straight up if the two coincide. */
fun directionTo(from: Point, target: Point): Point {
    val dx = target.x - from.x
    val dy = target.y - from.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1e-5f) return Point(0f, -1f)
    return Point(dx / length, dy / length)
}

/**
 * Where a ray leaving [origin] along the unit vector [dir] crosses the edge of a
 * [width] x [height] board. Used to run the laser off the end of the board.
 */
fun rayExit(origin: Point, dir: Point, width: Float, height: Float): Point {
    var t = Float.MAX_VALUE
    if (dir.x > 1e-6f) t = min(t, (width - origin.x) / dir.x)
    if (dir.x < -1e-6f) t = min(t, -origin.x / dir.x)
    if (dir.y > 1e-6f) t = min(t, (height - origin.y) / dir.y)
    if (dir.y < -1e-6f) t = min(t, -origin.y / dir.y)
    if (t == Float.MAX_VALUE) t = 0f
    return Point(origin.x + dir.x * t, origin.y + dir.y * t)
}

/**
 * How far along a ray leaving [origin] towards [dir] the [point] sits, or null if it is behind
 * the origin. Paired with [offsetFromRay] to ask whether something is both in line and in reach.
 */
fun alongRay(point: Point, origin: Point, dir: Point): Float? {
    val along = (point.x - origin.x) * dir.x + (point.y - origin.y) * dir.y
    return if (along < 0f) null else along
}

/**
 * How far [point] sits off a ray leaving [origin] along the unit vector [dir], or null if it is
 * behind the origin and so cannot be hit.
 */
fun offsetFromRay(point: Point, origin: Point, dir: Point): Float? {
    val vx = point.x - origin.x
    val vy = point.y - origin.y
    val along = vx * dir.x + vy * dir.y
    if (along < 0f) return null
    return hypot(vx - along * dir.x, vy - along * dir.y)
}
