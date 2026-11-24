package org.noses.towerdefense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.noses.towerdefense.game.ALL_TOWERS
import org.noses.towerdefense.game.EnemyKind
import org.noses.towerdefense.game.MAX_TOWER_LEVEL
import org.noses.towerdefense.game.Tower
import org.noses.towerdefense.game.TowerKind

/** A single flat shape, used for the palette, the legend and the menu. */
@Composable
fun TowerGlyph(kind: TowerKind, glyphSize: Dp, color: Color = colorOf(kind)) {
    Canvas(Modifier.size(glyphSize)) {
        val middle = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        when (kind) {
            TowerKind.SQUARE -> drawPath(squarePath(middle, radius * 0.78f), color)
            TowerKind.HEXAGON -> {
                drawPath(hexagonPath(middle, radius * 0.95f), color)
                drawPath(hexagonPath(middle, radius * 0.5f), Palette.panel)
            }
            TowerKind.PLUS -> drawPath(plusPath(middle, radius), color)
        }
    }
}

@Composable
fun EnemyGlyph(kind: EnemyKind, glyphSize: Dp, color: Color = colorOf(kind)) {
    Canvas(Modifier.size(glyphSize)) {
        val middle = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        when (kind) {
            EnemyKind.SQUARE -> drawPath(squarePath(middle, radius * 0.85f), color)
            EnemyKind.TRIANGLE -> drawPath(
                trianglePath(middle, radius, -Math.PI.toFloat() / 2f),
                color,
            )
            EnemyKind.CIRCLE -> {
                drawCircle(color, radius * 0.9f, middle)
                drawCircle(Palette.panel, radius * 0.4f, middle)
                drawCircle(color, radius * 0.65f, middle, style = Stroke(width = radius * 0.12f))
            }
        }
    }
}

/** Lives, cash and wave progress across the top of the screen. */
@Composable
fun TopBar(hud: HudState, musicOn: Boolean, onToggleMusic: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(
            label = "LIVES",
            value = hud.lives.toString(),
            color = if (hud.lives <= 5) Palette.healthLow else Palette.health,
        )
        Spacer(Modifier.width(18.dp))
        Stat("CASH", hud.money.toString(), Palette.text)
        Spacer(Modifier.width(18.dp))
        Stat("WAVE", hud.wave.coerceAtLeast(1).toString() + "/" + hud.totalWaves, Palette.text)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Palette.background, RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleMusic),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (musicOn) "MUS" else "OFF",
                color = if (musicOn) Palette.entry else Palette.dim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            color = Palette.dim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = value,
            color = color,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** The three towers you can build, along the bottom. Tap one to arm it, tap it again to disarm. */
@Composable
fun TowerPalette(
    money: Int,
    armed: TowerKind?,
    onArm: (TowerKind?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.panel)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ALL_TOWERS.forEach { stats ->
            val affordable = money >= stats.cost
            val isArmed = armed == stats.kind
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isArmed) colorOf(stats.kind).copy(alpha = 0.18f) else Palette.background,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 2.dp,
                        color = if (isArmed) colorOf(stats.kind) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onArm(if (isArmed) null else stats.kind) }
                    .alpha(if (affordable) 1f else 0.4f)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TowerGlyph(stats.kind, 30.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stats.label,
                    color = Palette.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "$" + stats.cost,
                    color = if (affordable) Palette.dim else Palette.healthLow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Replaces the palette while a built tower is selected. */
@Composable
fun TowerInfoPanel(
    money: Int,
    tower: Tower,
    onUpgrade: () -> Unit,
    onSell: () -> Unit,
    onClose: () -> Unit,
) {
    val upgradeCost = tower.upgradeCost
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.panel)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TowerGlyph(tower.kind, 26.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = tower.stats.label + "  LV" + tower.level + "/" + MAX_TOWER_LEVEL,
                    color = Palette.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "DMG " + tower.damage + "   EVERY " + formatSeconds(tower.fireInterval) + "s",
                    color = Palette.dim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Palette.background, RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "X",
                    color = Palette.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(
                label = if (upgradeCost == null) "MAX LEVEL" else "UPGRADE " + "$" + upgradeCost,
                color = colorOf(tower.kind),
                enabled = upgradeCost != null && money >= upgradeCost,
                modifier = Modifier.weight(1f),
                onClick = onUpgrade,
            )
            GameButton(
                label = "SELL " + "$" + tower.sellValue,
                color = Palette.dim,
                modifier = Modifier.weight(1f),
                onClick = onSell,
            )
        }
    }
}

/** Two decimal places, without pulling in locale-sensitive formatting. */
fun formatSeconds(value: Float): String {
    val hundredths = (value * 100f).toInt()
    return (hundredths / 100).toString() + "." + (hundredths % 100).toString().padStart(2, '0')
}

/** The one button style used everywhere: a flat bold slab. */
@Composable
fun GameButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(color.copy(alpha = if (enabled) 1f else 0.25f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Palette.background else Palette.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
    }
}
