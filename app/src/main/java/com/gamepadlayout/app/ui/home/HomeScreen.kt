package com.gamepadlayout.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamepadlayout.app.controller.ControllerInfo
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.system.SystemMonitor
import com.gamepadlayout.app.system.SystemStatus
import com.gamepadlayout.app.ui.components.AddTile
import com.gamepadlayout.app.ui.components.ConsoleBackground
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.components.GameTile
import com.gamepadlayout.app.ui.components.SectionTitle
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    userName: String,
    controllers: List<ControllerInfo>,
    hiddenCategories: Set<String>,
    games: List<LibraryApp>,
    onCategory: (Category) -> Unit,
    onLaunch: (LibraryApp) -> Unit,
    onAddGame: () -> Unit,
    onGameOptions: (LibraryApp) -> Unit,
    onProfile: () -> Unit
) {
    val st = LocalConsoleStyle.current
    val ctx = LocalContext.current
    val status by remember { SystemMonitor(ctx).flow() }
        .collectAsStateWithLifecycle(initialValue = SystemStatus())

    val visible = Category.entries.filter { it.name !in hiddenCategories }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(450)
        runCatching { first.requestFocus() }
    }

    val cardW = (112 * st.iconScale).dp
    val cardH = (112 * st.iconScale).dp
    val tile = (76 * st.iconScale).dp

    ConsoleBackground {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 44.dp)
            ) {
                StatusBar(status, userName, controllers, onProfile)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(visible, key = { _, c -> c.name }) { index, c ->
                        ConsoleSurface(
                            onClick = { onCategory(c) },
                            modifier = Modifier.size(cardW, cardH),
                            focusScale = 1.12f,
                            focusRequester = if (index == 0) first else null,
                            shape = RoundedCornerShape(6.dp)
                        ) { focused ->
                            CategoryTileBody(c, focused, games.size)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.padding(start = 10.dp)) { SectionTitle("Jogos") }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(games, key = { it.packageName }) { g ->
                        GameTile(g, tile, onClick = { onLaunch(g) }, onOptions = { onGameOptions(g) })
                    }
                    item { AddTile(tile, onClick = onAddGame) }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.28f))
                    .padding(start = 30.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Hint("A", "Confirmar")
                Hint("B", "Voltar")
                Hint("Y", "Opções")
                Hint("≡", "Menu")
            }
        }
    }
}

@Composable
internal fun Hint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(key, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
internal fun StatusBar(status: SystemStatus, userName: String, controllers: List<ControllerInfo>, onProfile: () -> Unit) {
    val st = LocalConsoleStyle.current
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SportsEsports, null, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text("Gamepad Layout", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        val dim = Color.White.copy(alpha = 0.35f)
        controllers.firstOrNull()?.let { c ->
            Icon(Icons.Rounded.SportsEsports, null, Modifier.size(20.dp), tint = st.accentSoft)
            if (c.battery != null) Text(" ${c.battery}%", fontSize = 12.sp, color = st.accentSoft)
            Spacer(Modifier.width(12.dp))
        }
        Icon(if (status.wifi) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, null, Modifier.size(20.dp), tint = if (status.wifi) Color.White else dim)
        Spacer(Modifier.width(10.dp))
        Icon(
            if (status.bluetooth) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, null,
            Modifier.size(20.dp), tint = if (status.bluetooth) Color.White else dim
        )
        Spacer(Modifier.width(10.dp))
        Icon(if (status.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull, null, Modifier.size(22.dp))
        if (status.battery >= 0) Text("${status.battery}%", fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(status.time, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(status.date, fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
        }
        Spacer(Modifier.width(14.dp))
        ConsoleSurface(onClick = onProfile, modifier = Modifier.height(34.dp).focusProperties { canFocus = false }, shape = RoundedCornerShape(17.dp), focusScale = 1.06f) {
            Row(Modifier.padding(horizontal = 4.dp).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(st.accent, st.accentSoft))),
                    contentAlignment = Alignment.Center
                ) { Text(userName.take(1).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                Text(userName, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

internal fun categoryColor(c: Category): Color = when (c) {
    Category.GAMES -> Color(0xFF7B4DFF)
    Category.ARCADE -> Color(0xFFFF5C8A)
    Category.FILES -> Color(0xFFFFB84D)
    Category.DOWNLOADS -> Color(0xFF3DDC97)
    Category.BROWSER -> Color(0xFF4DA3FF)
    Category.WIFI -> Color(0xFF35D0E8)
    Category.BLUETOOTH -> Color(0xFF5B7CFF)
    Category.EXTERNAL -> Color(0xFFB36BFF)
    Category.CONTROLLER -> Color(0xFFFF7A4D)
    Category.APPS -> Color(0xFF9BE15D)
    Category.SYSTEM -> Color(0xFF8FA3C7)
    Category.SETTINGS -> Color(0xFFC9B8FF)
}

private fun subtitleOf(c: Category, gamesCount: Int): String = when (c) {
    Category.GAMES -> "$gamesCount na biblioteca"
    Category.ARCADE -> "5 jogos nativos"
    Category.FILES -> "Pastas e arquivos"
    Category.DOWNLOADS -> "Baixados"
    Category.BROWSER -> "Web com controle"
    Category.WIFI -> "Redes"
    Category.BLUETOOTH -> "Pareamento"
    Category.EXTERNAL -> "TV e transmissão"
    Category.CONTROLLER -> "Botões e teclas"
    Category.APPS -> "Todos os apps"
    Category.SYSTEM -> "Aparelho e bateria"
    Category.SETTINGS -> "Ajustes"
}

/** Conteúdo do bloco quadrado de categoria: faixa colorida, ícone grande, nome e detalhe. */
@Composable
internal fun CategoryTileBody(c: Category, focused: Boolean, gamesCount: Int) {
    val st = LocalConsoleStyle.current
    val tint = categoryColor(c)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(tint).align(Alignment.TopCenter))
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 8.dp).size(7.dp)
                .background(if (focused) Color.White else tint.copy(alpha = 0.55f))
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(c.icon, null, Modifier.size((42 * st.iconScale).dp), tint = if (focused) Color.White else tint)
            Spacer(Modifier.height(8.dp))
            Text(
                c.label, fontSize = 13.sp, maxLines = 1,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold
            )
            Text(
                subtitleOf(c, gamesCount), fontSize = 9.sp, maxLines = 1,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
