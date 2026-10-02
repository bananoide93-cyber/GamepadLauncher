package com.gamepadlayout.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ConsoleBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConsoleSurface(onClick = onBack, modifier = Modifier.size(40.dp), shape = CircleShape, focusScale = 1.1f) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        modifier = Modifier.align(Alignment.Center).size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Tile de jogo/app: ícone em card focável + nome. Y / Menu / toque longo = opções. */
@Composable
fun GameTile(
    app: LibraryApp,
    tileSize: Dp,
    onClick: () -> Unit,
    onOptions: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(tileSize + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ConsoleSurface(
            onClick = onClick,
            modifier = Modifier.size(tileSize),
            shape = RoundedCornerShape(16.dp),
            onLongClick = onOptions,
            onKey = { ev ->
                if (onOptions != null && ev.type == KeyEventType.KeyUp && ev.key == Key.Menu) {
                    onOptions(); true
                } else false
            }
        ) {
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

@Composable
fun AddTile(tileSize: Dp, label: String = "Adicionar jogo", onClick: () -> Unit) {
    Column(Modifier.width(tileSize + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ConsoleSurface(onClick = onClick, modifier = Modifier.size(tileSize), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.align(Alignment.Center).size(34.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String) {
    val st = LocalConsoleStyle.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(st.accent)
        )
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
