package com.gamepadlayout.app.ui.modules

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamepadlayout.app.system.SystemMonitor
import com.gamepadlayout.app.system.SystemStatus
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.settings.ActionRow
import com.gamepadlayout.app.ui.settings.Header
import com.gamepadlayout.app.ui.settings.InfoCard
import java.io.File
import java.util.Locale

private fun human(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return String.format(Locale.getDefault(), "%.1f %s", v, u[i])
}

private fun openFile(ctx: Context, f: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "Nenhum app para abrir este arquivo", Toast.LENGTH_SHORT).show()
    }
}

/** Gerenciador de arquivos simples, navegável pelo controle. */
@Composable
fun FilesScreen(title: String, startPath: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val root = remember { File(Environment.getExternalStorageDirectory().path) }
    var dir by remember { mutableStateOf(File(startPath)) }
    var tick by remember { mutableIntStateOf(0) }
    val needsAll = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    val entries = remember(dir, tick) {
        (dir.listFiles()?.toList() ?: emptyList())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    BackHandler(enabled = dir.path != root.path && dir.parentFile != null && dir.path.startsWith(root.path)) {
        dir = dir.parentFile ?: root
    }
    ScreenScaffold(title, onBack) {
        Header(dir.path.removePrefix(root.path).ifEmpty { "/" } + "  (" + entries.size + " itens)")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (needsAll) item {
                ActionRow("⚠ Permitir acesso a todos os arquivos") {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            if (dir.path != root.path && dir.parentFile != null) item {
                ActionRow("⬆  Subir uma pasta") { dir = dir.parentFile ?: root }
            }
            item { ActionRow("⟳  Atualizar") { tick++ } }
            items(entries, key = { it.path }) { f ->
                if (f.isDirectory) ActionRow("📁  ${f.name}") { dir = f }
                else ActionRow("📄  ${f.name}   ·   ${human(f.length())}") { openFile(ctx, f) }
            }
        }
    }
}

private fun intentOrToast(ctx: Context, i: Intent) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Não disponível neste aparelho", Toast.LENGTH_SHORT).show()
    }
}

/** Informações do aparelho, bateria, armazenamento e atalhos (galeria, música...). */
@Composable
fun SystemScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val status by remember { SystemMonitor(ctx).flow() }
        .collectAsStateWithLifecycle(initialValue = SystemStatus())
    val info = remember {
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volt = bat?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val sf = StatFs(Environment.getDataDirectory().path)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        @Suppress("DEPRECATION")
        val d = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(m)
        listOf(
            "Aparelho" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Tela" to "${m.widthPixels}x${m.heightPixels} · ${"%.0f".format(d.refreshRate)} Hz",
            "Memória RAM" to "${human(mi.availMem)} livres de ${human(mi.totalMem)}",
            "Armazenamento" to "${human(sf.availableBytes)} livres de ${human(sf.totalBytes)}",
            "Bateria (temperatura)" to "%.1f °C · %d mV".format(temp, volt)
        )
    }
    ScreenScaffold("Sistema", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Header("Bateria e conexões") }
            item {
                InfoCard(
                    "Bateria: ${if (status.battery >= 0) status.battery.toString() + "%" else "—"}" +
                        if (status.charging) " (carregando)" else "",
                    "Wi-Fi: ${if (status.wifi) "conectado" else "desligado"} · Bluetooth: ${if (status.bluetooth) "ligado" else "desligado"}"
                )
            }
            item { Header("Aparelho") }
            items(info) { (t, b) -> InfoCard(t, b) }
            item { Header("Atalhos") }
            item {
                ActionRow("Abrir galeria") {
                    intentOrToast(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
                }
            }
            item {
                ActionRow("Abrir música") {
                    intentOrToast(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
                }
            }
            item { ActionRow("Notificações do app") { intentOrToast(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } }
            item { ActionRow("Bateria do sistema") { intentOrToast(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } }
            item { ActionRow("Painel de volume") { intentOrToast(ctx, Intent(Settings.Panel.ACTION_VOLUME)) } }
        }
    }
}

/** Wi-Fi / Bluetooth: estado atual e atalhos para os painéis oficiais do Android. */
@Composable
fun ConnectScreen(bluetooth: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val status by remember { SystemMonitor(ctx).flow() }
        .collectAsStateWithLifecycle(initialValue = SystemStatus())
    ScreenScaffold(if (bluetooth) "Bluetooth" else "Wi-Fi", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bluetooth) {
                item { InfoCard("Bluetooth", if (status.bluetooth) "Ligado" else "Desligado") }
                item { ActionRow("Parear controle / fones (configurações)") { intentOrToast(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
            } else {
                item { InfoCard("Wi-Fi", if (status.wifi) "Conectado" else "Desligado ou sem rede") }
                item {
                    ActionRow("Painel rápido de internet") {
                        intentOrToast(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    }
                }
                item { ActionRow("Configurações de Wi-Fi") { intentOrToast(ctx, Intent(Settings.ACTION_WIFI_SETTINGS)) } }
            }
            item { ActionRow("Painel de volume") { intentOrToast(ctx, Intent(Settings.Panel.ACTION_VOLUME)) } }
        }
    }
}
