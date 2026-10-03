package com.gamepadlayout.app.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.system.SystemMonitor
import com.gamepadlayout.app.system.SystemStatus
import com.gamepadlayout.app.ui.components.AppIcon
import com.gamepadlayout.app.ui.components.ConsoleBackground
import com.gamepadlayout.app.ui.components.ConsoleCard
import com.gamepadlayout.app.ui.components.SectionTitle
import com.gamepadlayout.app.ui.home.Category
import com.gamepadlayout.app.ui.home.Hint
import com.gamepadlayout.app.ui.home.StatusBar
import com.gamepadlayout.app.ui.theme.GamepadLayoutTheme
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle

/** Altura "virtual" em dp do layout do celular; a TV escala esse mesmo layout para a tela dela. */
private const val DESIGN_HEIGHT_DP = 380f

/**
 * Home na TV: é o MESMO layout da Home do celular (barra de status, categorias, jogos, dicas),
 * apenas ampliado para a tela externa e com a seleção controlada por estado (TvNav).
 */
@Composable
fun TvHomeContent() {
    val settings by TvNav.settings.collectAsState()
    val games by TvNav.games.collectAsState()
    val controllers by TvNav.controllers.collectAsState()
    val row by TvNav.row.collectAsState()
    val col by TvNav.col.collectAsState()
    val ctx = LocalContext.current
    val status by remember { SystemMonitor(ctx).flow() }.collectAsState(initial = SystemStatus())

    GamepadLayoutTheme(settings) {
        val st = LocalConsoleStyle.current
        val cats = Category.entries.filter { it.name !in settings.hiddenCategories }
        ConsoleBackground {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Escala todo o layout: 1 dp passa a valer (altura da TV / 380) pixels.
                val density = Density(density = constraints.maxHeight / DESIGN_HEIGHT_DP, fontScale = 1f)
                CompositionLocalProvider(LocalDensity provides density) {
                    val catState = rememberLazyListState()
                    val gameState = rememberLazyListState()
                    LaunchedEffect(row, col, cats.size, games.size) {
                        if (row == 0) {
                            if (cats.isNotEmpty()) catState.animateScrollToItem(col.coerceIn(0, cats.lastIndex))
                        } else {
                            gameState.animateScrollToItem(col.coerceIn(0, games.size))
                        }
                    }
                    val cardW = (112 * st.iconScale).dp
                    val cardH = (104 * st.iconScale).dp
                    val tile = (76 * st.iconScale).dp

                    Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 44.dp)
                        ) {
                            StatusBar(status, settings.userName, controllers) {}
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                state = catState,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(cats, key = { _, c -> c.name }) { i, c ->
                                    ConsoleCard(
                                        focused = row == 0 && col == i,
                                        modifier = Modifier.size(cardW, cardH),
                                        focusScale = 1.12f,
                                        shape = RoundedCornerShape(20.dp)
                                    ) { focused ->
                                        Column(
                                            Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(c.icon, null, Modifier.size((38 * st.iconScale).dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                c.label,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.padding(start = 10.dp)) { SectionTitle("Jogos") }
                            LazyRow(
                                state = gameState,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(games, key = { _, g -> g.packageName }) { i, g ->
                                    TvGameTile(g, tile, row == 1 && col == i)
                                }
                                item {
                                    Column(Modifier.width(tile + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ConsoleCard(
                                            focused = row == 1 && col == games.size,
                                            modifier = Modifier.size(tile),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, null, Modifier.align(Alignment.Center).size(34.dp))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text("Adicionar jogo", fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            Hint("A", "Confirmar")
                            Hint("B", "Voltar ao celular")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvGameTile(app: LibraryApp, tile: Dp, focused: Boolean) {
    Column(Modifier.width(tile + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ConsoleCard(focused = focused, modifier = Modifier.size(tile), shape = RoundedCornerShape(16.dp)) {
            AppIcon(app.packageName, Modifier.fillMaxSize().padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
