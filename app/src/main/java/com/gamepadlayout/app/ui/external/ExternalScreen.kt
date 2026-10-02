package com.gamepadlayout.app.ui.external

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamepadlayout.app.cast.CastState
import com.gamepadlayout.app.cast.NetworkInfo
import com.gamepadlayout.app.cast.ScreenCastService
import com.gamepadlayout.app.console.findActivity
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.data.CastQuality
import com.gamepadlayout.app.data.ExternalViewModel
import com.gamepadlayout.app.data.SettingsKeys as K
import com.gamepadlayout.app.data.SettingsViewModel
import com.gamepadlayout.app.display.ExternalDisplayInfo
import com.gamepadlayout.app.display.ExternalSession
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.settings.ActionRow
import com.gamepadlayout.app.ui.settings.Header
import com.gamepadlayout.app.ui.settings.InfoCard
import com.gamepadlayout.app.ui.settings.SettingRow
import com.gamepadlayout.app.ui.settings.cycle
import com.gamepadlayout.app.system.SystemIntents

@Suppress("DEPRECATION")
private fun screenSize(a: Activity): Pair<Int, Int> =
    if (Build.VERSION.SDK_INT >= 30) {
        val b = a.windowManager.currentWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val dm = DisplayMetrics()
        a.windowManager.defaultDisplay.getRealMetrics(dm)
        dm.widthPixels to dm.heightPixels
    }

private fun mark(b: Boolean) = if (b) "✓" else "✗"

@Composable
fun ExternalScreen(
    settings: AppSettings,
    settingsVm: SettingsViewModel,
    vm: ExternalViewModel,
    tvActive: Boolean,
    onShowTv: (ExternalDisplayInfo) -> Unit,
    onStopTv: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val displays by vm.displays.collectAsStateWithLifecycle()
    val cast by vm.cast.collectAsStateWithLifecycle()
    val compat = vm.compat()
    val audio = vm.audioOutputs()
    val ip = NetworkInfo.localIp(ctx)

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        val act = ctx.findActivity()
        if (res.resultCode == Activity.RESULT_OK && data != null && act != null) {
            val (w, h) = screenSize(act)
            val dpi = ctx.resources.displayMetrics.densityDpi
            ScreenCastService.start(ctx, res.resultCode, data, w, h, dpi, settings.castQuality, settings.castFps)
        } else {
            CastState.status.value = CastState.status.value.copy(error = "Permissão de captura negada")
        }
    }
    fun requestCapture() {
        val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
        if (mpm == null) {
            Toast.makeText(ctx, "Captura de tela indisponível neste aparelho", Toast.LENGTH_SHORT).show()
            return
        }
        CastState.status.value = CastState.status.value.copy(error = null)
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestCapture() // a notificação é opcional: segue mesmo se negar
    }
    fun startCast() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else requestCapture()
    }

    ScreenScaffold("Tela externa", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Header("Estado") }
            item {
                val txt = buildString {
                    append(if (tvActive) "Home exibida na TV: sim\n" else "Home exibida na TV: não\n")
                    append(if (cast.running) "Transmissão Wi-Fi: ativa (${cast.clients} espectador(es))" else "Transmissão Wi-Fi: parada")
                    cast.error?.let { append("\nAviso: $it") }
                }
                InfoCard("Situação", txt)
            }

            item { Header("Dispositivos disponíveis (cabo HDMI / Miracast / Smart View)") }
            if (displays.isEmpty()) {
                item {
                    InfoCard(
                        "Nenhuma tela externa detectada",
                        "Conecte um cabo USB-C/HDMI ou conecte a TV pelo espelhamento do sistema. " +
                            "Quando a tela aparecer aqui, o app mostra a Home em modo TV."
                    )
                }
            } else {
                displays.forEach { d ->
                    item { ActionRow("Mostrar Home em: ${d.name} (${d.width}x${d.height})") { onShowTv(d) } }
                }
            }
            if (tvActive) item { ActionRow("Parar Home na TV", onStopTv) }
            if (compat.castSettings) {
                item { ActionRow("Abrir espelhamento do sistema (Cast / Smart View)") { SystemIntents.open(ctx, "android.settings.CAST_SETTINGS") } }
            }

            item { Header("Transmitir pelo Wi-Fi (navegador da TV ou PC)") }
            if (compat.mediaProjection) {
                item {
                    ActionRow(if (cast.running) "Parar transmissão" else "Iniciar transmissão da tela") {
                        if (cast.running) ScreenCastService.stop(ctx) else startCast()
                    }
                }
            } else {
                item { InfoCard("Captura de tela indisponível", "Este aparelho não oferece MediaProjection.") }
            }
            if (cast.running) {
                item {
                    InfoCard(
                        "Abra este endereço no navegador da TV/PC (mesma rede Wi-Fi)",
                        cast.url
                    )
                }
            }

            item { Header("Configurações de transmissão") }
            item {
                val q = CastQuality.entries
                SettingRow("Qualidade", settings.castQuality.label,
                    { settingsVm.set(K.CAST_QUALITY, cycle(q, settings.castQuality, -1).name) },
                    { settingsVm.set(K.CAST_QUALITY, cycle(q, settings.castQuality, 1).name) })
            }
            item {
                val f = listOf(15, 24, 30)
                SettingRow("Quadros por segundo", "${settings.castFps} fps",
                    { settingsVm.set(K.CAST_FPS, cycle(f, settings.castFps, -1)) },
                    { settingsVm.set(K.CAST_FPS, cycle(f, settings.castFps, 1)) })
            }
            item { InfoCard("Orientação", "O Gamepad Layout fica sempre na horizontal, também durante a transmissão.") }

            item { Header("Áudio") }
            item {
                val txt = if (audio.isEmpty()) "Nenhuma saída detectada" else
                    audio.mapIndexed { i, o -> (if (i == 0) "▶ " else "• ") + "${o.name} (${o.kind})" }.joinToString("\n") +
                        "\n\n▶ = saída mais provável. O Android não informa qual está ativa nem deixa apps forçarem a saída."
                InfoCard("Saídas de áudio", txt)
            }
            item { ActionRow("Escolher saída de áudio (seletor do sistema)") { vm.openAudioSwitcher() } }
            item { InfoCard("Importante", "A transmissão pelo Wi-Fi leva apenas vídeo. O som continua no celular ou na saída de áudio escolhida.") }

            item { Header("Informações da conexão") }
            item {
                val txt = buildString {
                    append("IP do celular: ${ip ?: "sem Wi-Fi"}\n")
                    if (cast.running) {
                        append("Resolução: ${cast.width}x${cast.height}\n")
                        append("FPS: ${cast.realFps} (alvo ${cast.targetFps})\n")
                        val secs = (System.currentTimeMillis() - cast.startedAt) / 1000
                        append("Tempo ativo: ${secs / 60}min ${secs % 60}s\n")
                    }
                    append("Latência: depende da rede; esperado de 150 a 400 ms (não é zero).")
                }
                InfoCard("Rede", txt)
            }

            item { Header("Compatibilidade deste aparelho") }
            item {
                InfoCard(
                    compat.androidVersion,
                    "${mark(compat.mediaProjection)} Captura de tela (MediaProjection)\n" +
                        "${mark(compat.castSettings)} Tela de espelhamento do sistema\n" +
                        "${mark(compat.bluetooth)} Bluetooth\n" +
                        "${mark(compat.controllerBattery)} Bateria do controle (Android 12+)\n" +
                        "${mark(compat.homeSettings)} Escolha de tela inicial"
                )
            }
        }
    }
}
