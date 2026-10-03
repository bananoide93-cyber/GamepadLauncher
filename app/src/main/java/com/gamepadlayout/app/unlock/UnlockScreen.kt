package com.gamepadlayout.app.unlock

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.settings.ActionRow
import com.gamepadlayout.app.ui.settings.Header
import com.gamepadlayout.app.ui.settings.InfoCard
import com.gamepadlayout.app.ui.settings.SettingRow
import com.gamepadlayout.app.ui.settings.ToggleRow
import com.gamepadlayout.app.ui.settings.cycle
import kotlinx.coroutines.delay

/** Tela do "Abrir ao desbloquear": risco explicado, travas configuráveis e saídas de emergência. */
@Composable
fun UnlockScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(UnlockPrefs.enabled(ctx)) }
    var armed by remember { mutableStateOf(false) }
    var delaySec by remember { mutableIntStateOf(UnlockPrefs.delaySec(ctx)) }
    var cooldown by remember { mutableIntStateOf(UnlockPrefs.cooldownMin(ctx)) }
    var onlyCtl by remember { mutableStateOf(UnlockPrefs.onlyController(ctx)) }
    var onlyChg by remember { mutableStateOf(UnlockPrefs.onlyCharging(ctx)) }
    var overlay by remember { mutableStateOf(UnlockLaunch.canDrawOverlays(ctx)) }
    var paused by remember { mutableStateOf(UnlockPrefs.pausedUntil(ctx) > System.currentTimeMillis()) }

    // Reconfere a permissão quando o jogador volta da tela de ajustes do sistema.
    LaunchedEffect(Unit) {
        while (true) {
            overlay = UnlockLaunch.canDrawOverlays(ctx)
            delay(1000)
        }
    }

    ScreenScaffold("Abrir ao desbloquear", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                InfoCard(
                    "⚠ Recurso de risco",
                    "Com isto ligado, o Gamepad Layout abre sozinho toda vez que você desbloqueia o celular. " +
                        "Pode atrapalhar o uso normal. As travas abaixo existem para você controlar isso, " +
                        "e a saída está sempre disponível (veja \"Como sair\")."
                )
            }
            item { Header("1) Permissão") }
            item {
                ActionRow(if (overlay) "✔ Exibir sobre outros apps: permitido" else "Permitir \"exibir sobre outros apps\" (necessário)") {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Abra Ajustes > Apps > Gamepad Layout > Exibir sobre outros apps", Toast.LENGTH_LONG).show()
                    }
                }
            }
            item { Header("2) Ativar") }
            item {
                ToggleRow(
                    when {
                        enabled -> "Abrir ao desbloquear: LIGADO"
                        armed -> "Toque de novo para CONFIRMAR (entendi o risco)"
                        else -> "Abrir ao desbloquear: desligado"
                    },
                    enabled || armed
                ) {
                    if (enabled) {
                        enabled = false; armed = false
                        UnlockPrefs.setEnabled(ctx, false); UnlockLaunch.sync(ctx)
                    } else if (!overlay) {
                        Toast.makeText(ctx, "Primeiro permita \"exibir sobre outros apps\"", Toast.LENGTH_LONG).show()
                    } else if (!armed) {
                        armed = true
                    } else {
                        armed = false; enabled = true
                        UnlockPrefs.setEnabled(ctx, true); UnlockPrefs.clearPause(ctx); paused = false
                        UnlockLaunch.sync(ctx)
                    }
                }
            }
            item { Header("3) Travas") }
            item {
                val opts = listOf(0, 1, 2, 3, 5, 10)
                SettingRow("Esperar antes de abrir", "$delaySec s",
                    { delaySec = cycle(opts, delaySec, -1); UnlockPrefs.setDelaySec(ctx, delaySec) },
                    { delaySec = cycle(opts, delaySec, 1); UnlockPrefs.setDelaySec(ctx, delaySec) })
            }
            item {
                val opts = listOf(0, 1, 5, 10, 30, 60, 180)
                SettingRow("Intervalo mínimo entre aberturas", if (cooldown == 0) "sem intervalo" else "$cooldown min",
                    { cooldown = cycle(opts, cooldown, -1); UnlockPrefs.setCooldownMin(ctx, cooldown) },
                    { cooldown = cycle(opts, cooldown, 1); UnlockPrefs.setCooldownMin(ctx, cooldown) })
            }
            item { ToggleRow("Só abrir com controle conectado", onlyCtl) { onlyCtl = it; UnlockPrefs.setOnlyController(ctx, it) } }
            item { ToggleRow("Só abrir enquanto carrega", onlyChg) { onlyChg = it; UnlockPrefs.setOnlyCharging(ctx, it) } }
            item { Header("Pausar") }
            item {
                ActionRow(if (paused) "Pausado — toque para retomar" else "Pausar por 1 hora") {
                    if (paused) { UnlockPrefs.clearPause(ctx); paused = false }
                    else { UnlockPrefs.pauseFor(ctx, 60); paused = true }
                }
            }
            item { ActionRow("Pausar por 8 horas") { UnlockPrefs.pauseFor(ctx, 480); paused = true } }
            item { Header("Como sair se enjoar ou travar") }
            item {
                InfoCard(
                    "Sempre funciona",
                    "• Botão Início ou gesto do Android: o app nunca bloqueia a saída.\n" +
                        "• Menu rápido (START): \"Sair do app\" e \"Pausar abrir ao desbloquear\".\n" +
                        "• Notificação fixa: botões \"Pausar 1 h\" e \"Desativar\".\n" +
                        "• Última opção: desinstalar o app."
                )
            }
        }
    }
}
