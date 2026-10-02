package com.gamepadlayout.app.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.data.SettingsKeys as K
import com.gamepadlayout.app.data.SettingsViewModel
import com.gamepadlayout.app.data.ThemeMode
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.home.Category
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle
import kotlin.math.roundToInt

internal fun step(v: Float, d: Float, min: Float, max: Float): Float =
    (((v + d).coerceIn(min, max)) * 100).roundToInt() / 100f

internal fun <T> cycle(list: List<T>, cur: T, dir: Int): T =
    list[(list.indexOf(cur) + dir + list.size) % list.size]

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    vm: SettingsViewModel,
    onOpenController: () -> Unit,
    onOpenExternal: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val cats = Category.entries.filter { it != Category.SETTINGS && it != Category.GAMES }

    ScreenScaffold("Configurações", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Header("Console") }
            item { ToggleRow("Modo Console (tela cheia, esconde as barras)", settings.consoleMode) { vm.set(K.CONSOLE_MODE, it) } }
            item { ToggleRow("Modo Jogo (preparar antes de abrir o jogo)", settings.gameMode) { vm.set(K.GAME_MODE, it) } }
            item { ToggleRow("Mostrar Home na TV automaticamente", settings.autoTv) { vm.set(K.AUTO_TV, it) } }
            item { ToggleRow("Modo economia (menos efeitos e animações)", settings.powerSaver) { vm.set(K.POWER_SAVER, it) } }
            item { ActionRow("Controle e mapeamento de botões", onOpenController) }
            item { ActionRow("Tela externa e transmissão", onOpenExternal) }
            item {
                ActionRow("Definir Gamepad Layout como tela inicial (sistema)") {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Esta opção não existe neste aparelho", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            item { Header("Aparência") }
            item {
                val modes = ThemeMode.entries
                SettingRow("Tema", settings.theme.label,
                    { vm.set(K.THEME, cycle(modes, settings.theme, -1).name) },
                    { vm.set(K.THEME, cycle(modes, settings.theme, 1).name) })
            }
            item {
                SettingRow("Intensidade dos efeitos", pct(settings.effects),
                    { vm.set(K.EFFECTS, step(settings.effects, -0.1f, 0f, 1f)) },
                    { vm.set(K.EFFECTS, step(settings.effects, 0.1f, 0f, 1f)) })
            }
            item {
                SettingRow("Transparência dos cards", pct(settings.transparency),
                    { vm.set(K.TRANSPARENCY, step(settings.transparency, -0.1f, 0f, 1f)) },
                    { vm.set(K.TRANSPARENCY, step(settings.transparency, 0.1f, 0f, 1f)) })
            }
            item { ToggleRow("Animações", settings.animations) { vm.set(K.ANIMATIONS, it) } }

            item { Header("Interface") }
            item {
                SettingRow("Tamanho dos ícones", pct(settings.iconScale),
                    { vm.set(K.ICON_SCALE, step(settings.iconScale, -0.1f, 0.8f, 1.4f)) },
                    { vm.set(K.ICON_SCALE, step(settings.iconScale, 0.1f, 0.8f, 1.4f)) })
            }
            item {
                SettingRow("Velocidade das animações", "${settings.animSpeed}x",
                    { vm.set(K.ANIM_SPEED, step(settings.animSpeed, -0.25f, 0.5f, 2f)) },
                    { vm.set(K.ANIM_SPEED, step(settings.animSpeed, 0.25f, 0.5f, 2f)) })
            }
            item { ToggleRow("Detectar jogos automaticamente", settings.autoDetectGames) { vm.set(K.AUTO_DETECT, it) } }
            cats.forEach { c ->
                item {
                    ToggleRow("Mostrar na Home: ${c.label}", c.name !in settings.hiddenCategories) { show ->
                        vm.setCategoryHidden(c.name, !show)
                    }
                }
            }

            item { Header("Navegador") }
            item {
                SettingRow("Velocidade do cursor", "${settings.cursorSpeed}x",
                    { vm.set(K.CURSOR_SPEED, step(settings.cursorSpeed, -0.25f, 0.5f, 3f)) },
                    { vm.set(K.CURSOR_SPEED, step(settings.cursorSpeed, 0.25f, 0.5f, 3f)) })
            }
            item {
                SettingRow("Zoom das páginas", "${settings.zoom}%",
                    { vm.set(K.ZOOM, (settings.zoom - 10).coerceIn(50, 200)) },
                    { vm.set(K.ZOOM, (settings.zoom + 10).coerceIn(50, 200)) })
            }
            item {
                InfoCard(
                    "Controles no navegador",
                    "Analógico esquerdo: cursor · Analógico direito: rolar a página.\n" +
                        "Os botões (clique, abas, atualizar...) são configurados em Controle > Mapeamento."
                )
            }
        }
    }
}

@Composable
internal fun Header(text: String) {
    val st = LocalConsoleStyle.current
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = st.accentSoft,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
    )
}

@Composable
internal fun InfoCard(title: String, body: String) {
    ConsoleSurface(onClick = {}, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
internal fun ActionRow(title: String, onClick: () -> Unit) {
    ConsoleSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        focusScale = 1.02f,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, if (checked) "Ligado" else "Desligado", { onChange(!checked) }, { onChange(!checked) })
}

/** Linha de configuração: D-pad esquerda/direita ajusta, A/toque avança. */
@Composable
internal fun SettingRow(
    title: String,
    value: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val st = LocalConsoleStyle.current
    ConsoleSurface(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        focusScale = 1.02f,
        shape = RoundedCornerShape(14.dp),
        onKey = { ev ->
            if (ev.type == KeyEventType.KeyDown) {
                when (ev.key) {
                    Key.DirectionLeft -> { onPrev(); true }
                    Key.DirectionRight -> { onNext(); true }
                    else -> false
                }
            } else false
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Rounded.ChevronLeft, null,
                Modifier.size(24.dp).focusProperties { canFocus = false }.clickable { onPrev() }
            )
            Text(
                value,
                fontSize = 14.sp,
                color = st.accentSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 96.dp)
            )
            Icon(
                Icons.Rounded.ChevronRight, null,
                Modifier.size(24.dp).focusProperties { canFocus = false }.clickable { onNext() }
            )
        }
    }
}
