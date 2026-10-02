package com.gamepadlayout.app.display

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.system.SystemMonitor
import com.gamepadlayout.app.system.SystemStatus
import com.gamepadlayout.app.ui.components.AppIcon
import com.gamepadlayout.app.ui.components.ConsoleBackground
import com.gamepadlayout.app.ui.theme.GamepadLayoutTheme
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle

/**
 * Home em modo TV: ícones e textos grandes, poucos elementos, seleção bem destacada.
 * Tamanhos escalam pela altura da tela externa (densidades de TV variam muito).
 */
@Composable
fun TvHomeContent() {
    val settings by TvNav.settings.collectAsState()
    val games by TvNav.games.collectAsState()
    val selected by TvNav.selected.collectAsState()
    val ctx = LocalContext.current
    val status by remember { SystemMonitor(ctx).flow() }.collectAsState(initial = SystemStatus())

    GamepadLayoutTheme(settings) {
        ConsoleBackground {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val s = maxHeight.value / 720f
                val listState = rememberLazyListState()
                LaunchedEffect(selected, games.size) {
                    if (games.isNotEmpty()) listState.animateScrollToItem(selected.coerceIn(0, games.lastIndex))
                }
                Column(Modifier.fillMaxSize().padding(horizontal = (80 * s).dp, vertical = (48 * s).dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Gamepad Layout", fontSize = (34 * s).sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(status.time, fontSize = (34 * s).sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    if (games.isEmpty()) {
                        Text("Nenhum jogo na biblioteca", fontSize = (30 * s).sp)
                    } else {
                        LazyRow(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = (30 * s).dp, vertical = (50 * s).dp),
                            horizontalArrangement = Arrangement.spacedBy((44 * s).dp)
                        ) {
                            itemsIndexed(games, key = { _, g -> g.packageName }) { i, g ->
                                TvTile(g, i == selected, s)
                            }
                        }
                        Text(
                            games.getOrNull(selected)?.label ?: "",
                            fontSize = (42 * s).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "A  Jogar      B  Voltar ao celular      ◀ ▶  Escolher",
                        fontSize = (22 * s).sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTile(app: LibraryApp, selected: Boolean, s: Float) {
    val st = LocalConsoleStyle.current
    val scale by animateFloatAsState(if (selected) 1.18f else 1f, tween(st.dur(180)), label = "tvScale")
    val size = (190 * s).dp
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (selected) {
                    for (i in 3 downTo 1) {
                        val g = i * 8.dp.toPx()
                        drawRoundRect(
                            color = st.accent.copy(alpha = 0.12f * st.effects),
                            topLeft = Offset(-g, -g),
                            size = Size(this.size.width + g * 2, this.size.height + g * 2),
                            cornerRadius = CornerRadius(30.dp.toPx() + g)
                        )
                    }
                }
            }
            .border(
                width = if (selected) (4 * s).dp else 1.dp,
                color = if (selected) st.accentSoft else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape((28 * s).dp)
            )
            .padding((8 * s).dp)
    ) {
        AppIcon(app.packageName, Modifier.fillMaxSize())
    }
}
