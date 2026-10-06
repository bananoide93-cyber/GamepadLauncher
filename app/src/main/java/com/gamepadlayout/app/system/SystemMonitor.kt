package com.gamepadlayout.app.system

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SystemStatus(
    val time: String = "",
    val date: String = "",
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val battery: Int = -1,
    val charging: Boolean = false
)

/** Lê hora, bateria, Wi-Fi e Bluetooth com APIs oficiais (sem permissões perigosas). */
class SystemMonitor(private val context: Context) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

    fun flow(): Flow<SystemStatus> = flow {
        while (true) {
            emit(read())
            delay(1000)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private var tick = 0
    private var cached = SystemStatus()

    private fun read(): SystemStatus {
        val now = LocalDateTime.now()
        if (tick++ % 5 == 0) cached = readHeavy()
        return cached.copy(time = now.format(timeFmt), date = now.format(dateFmt))
    }

    /** Bateria, Wi-Fi e Bluetooth mudam devagar: consulta a cada 5 s (menos CPU/bateria). */
    private fun readHeavy(): SystemStatus {
        val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val st = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL

        val wifi = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)

        val bt = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        }.getOrDefault(false)

        return SystemStatus(wifi = wifi, bluetooth = bt, battery = pct, charging = charging)
    }
}
