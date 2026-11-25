package org.noses.towerdefense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.noses.towerdefense.game.ALL_TOWERS
import org.noses.towerdefense.game.BoardSize
import org.noses.towerdefense.game.CIRCLE_ENEMY
import org.noses.towerdefense.game.EnemyKind
import org.noses.towerdefense.game.SQUARE_ENEMY
import org.noses.towerdefense.game.TRIANGLE_ENEMY
import org.noses.towerdefense.game.WAVE_COUNTS

/** Title screen: pick how big and how long the game should be, with a legend of the pieces. */
@Composable
fun MenuScreen(
    board: BoardSize,
    onPickBoard: (BoardSize) -> Unit,
    onStart: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background)
            // Two pickers and a six-row legend is more than a short phone has room for, and a
            // Column without this silently clips the bottom - which is where the buttons are.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            text = "GEO",
            color = Palette.text,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
        )
        Text(
            text = "DEFENSE",
            color = Palette.entry,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
        )

        Spacer(Modifier.height(34.dp))
        SectionLabel("THEY COME IN THREES")
        Spacer(Modifier.height(10.dp))
        LegendRow(
            glyph = { EnemyGlyph(EnemyKind.SQUARE, 26.dp) },
            name = "SQUARE",
            detail = SQUARE_ENEMY.baseHp.toString() + " HP, slow, hits hardest if it gets by",
        )
        LegendRow(
            glyph = { EnemyGlyph(EnemyKind.TRIANGLE, 26.dp) },
            name = "TRIANGLE",
            detail = TRIANGLE_ENEMY.baseHp.toString() + " HP, very fast, very weak",
        )
        LegendRow(
            glyph = { EnemyGlyph(EnemyKind.CIRCLE, 26.dp) },
            name = "CIRCLE",
            detail = "Armoured: blunts every hit, takes many",
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel("YOU HAVE THREE ANSWERS")
        Spacer(Modifier.height(10.dp))
        ALL_TOWERS.forEach { stats ->
            LegendRow(
                glyph = { TowerGlyph(stats.kind, 26.dp) },
                name = stats.label,
                detail = stats.description + "  ($" + stats.cost + ")",
            )
        }

        Spacer(Modifier.height(30.dp))
        SectionLabel("HOW BIG?")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardSize.entries.forEach { option ->
                val picked = option == board
                GameButton(
                    label = option.label,
                    // The picked size stays lit and the rest sit back, so the row reads as a
                    // setting rather than four things to press.
                    color = if (picked) Palette.entry else Palette.dim,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickBoard(option) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = board.columns.toString() + " x " + board.rows + " cells",
            color = Palette.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("HOW LONG?")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WAVE_COUNTS.forEach { count ->
                GameButton(
                    label = count.toString(),
                    color = Palette.entry,
                    modifier = Modifier.weight(1f),
                    onClick = { onStart(count) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "waves",
            color = Palette.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Palette.dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LegendRow(glyph: @Composable () -> Unit, name: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Palette.panel, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) { glyph() }
        Column {
            Text(
                text = name,
                color = Palette.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
            Text(
                text = detail,
                color = Palette.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
