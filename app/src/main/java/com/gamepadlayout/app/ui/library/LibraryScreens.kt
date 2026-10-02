package com.gamepadlayout.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.data.GameTag
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.ui.components.AddTile
import com.gamepadlayout.app.ui.components.AppIcon
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.components.GameTile
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.components.SectionTitle
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle

@Composable
fun LibraryScreen(
    games: List<LibraryApp>,
    tags: Map<String, GameTag>,
    onBack: () -> Unit,
    onLaunch: (LibraryApp) -> Unit,
    onAdd: () -> Unit,
    onOptions: (LibraryApp) -> Unit
) {
    val st = LocalConsoleStyle.current
    val tile = (84 * st.iconScale).dp
    val groups = GameTag.entries
        .map { t -> t to games.filter { (tags[it.packageName] ?: GameTag.UNKNOWN) == t } }
        .filter { it.second.isNotEmpty() }
    ScreenScaffold("Jogos", onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tile + 24.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groups.forEach { (tag, list) ->
                item(key = "header-" + tag.name, span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle(tag.label)
                        Text(tag.hint, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
                items(list, key = { it.packageName }) { g ->
                    GameTile(g, tile, onClick = { onLaunch(g) }, onOptions = { onOptions(g) })
                }
            }
            item(key = "add-tile") { AddTile(tile, onClick = onAdd) }
        }
    }
}

/** Seletor de aplicativos instalados: toque/A alterna entre adicionar e remover da biblioteca. */
@Composable
fun AppPickerScreen(
    allApps: List<LibraryApp>,
    library: List<LibraryApp>,
    onToggle: (LibraryApp, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val st = LocalConsoleStyle.current
    val inLib = library.map { it.packageName }.toSet()
    ScreenScaffold("Adicionar jogo ou app", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allApps, key = { it.packageName }) { a ->
                val on = a.packageName in inLib
                ConsoleSurface(
                    onClick = { onToggle(a, on) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    focusScale = 1.02f,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(a.packageName, Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontSize = 14.sp, maxLines = 1)
                            Text(
                                if (a.isGame) "Jogo detectado" else "Aplicativo",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        if (on) Icon(Icons.Rounded.Check, null, tint = st.accentSoft)
                    }
                }
            }
        }
    }
}

/** Todos os aplicativos instalados com ícone de launcher. */
@Composable
fun AppsScreen(apps: List<LibraryApp>, onBack: () -> Unit, onLaunch: (LibraryApp) -> Unit) {
    val st = LocalConsoleStyle.current
    val tile = (76 * st.iconScale).dp
    ScreenScaffold("Aplicativos", onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tile + 24.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps, key = { it.packageName }) { a ->
                GameTile(a, tile, onClick = { onLaunch(a) }, onOptions = null)
            }
        }
    }
}
