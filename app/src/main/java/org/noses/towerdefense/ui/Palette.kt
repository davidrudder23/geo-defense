package org.noses.towerdefense.ui

import androidx.compose.ui.graphics.Color
import org.noses.towerdefense.game.EnemyKind
import org.noses.towerdefense.game.TowerKind

/** Every colour in the game. Bold and flat, no gradients or textures. */
object Palette {
    val background = Color(0xFF0D0F17)
    val panel = Color(0xFF141826)
    val panelEdge = Color(0xFF232a40)

    val board = Color(0xFF11141F)
    val road = Color(0xFF272E42)
    val roadEdge = Color(0xFF39425D)
    val gridDot = Color(0xFF1E2434)

    val entry = Color(0xFF2DE2A8)
    val exit = Color(0xFFFF4D6D)

    val squareEnemy = Color(0xFFFF6B35)
    val triangleEnemy = Color(0xFFFFD23F)
    val circleEnemy = Color(0xFF4ECDC4)

    val squareTower = Color(0xFF5B8CFF)
    val hexagonTower = Color(0xFFC77DFF)
    val plusTower = Color(0xFFFF5D8F)

    val text = Color(0xFFE8EBF5)
    val dim = Color(0xFF8E97B3)
    val health = Color(0xFF2DE2A8)
    val healthLow = Color(0xFFFF4D6D)
    val selection = Color(0xFFE8EBF5)
}

fun colorOf(kind: EnemyKind): Color = when (kind) {
    EnemyKind.SQUARE -> Palette.squareEnemy
    EnemyKind.TRIANGLE -> Palette.triangleEnemy
    EnemyKind.CIRCLE -> Palette.circleEnemy
}

fun colorOf(kind: TowerKind): Color = when (kind) {
    TowerKind.SQUARE -> Palette.squareTower
    TowerKind.HEXAGON -> Palette.hexagonTower
    TowerKind.PLUS -> Palette.plusTower
}
