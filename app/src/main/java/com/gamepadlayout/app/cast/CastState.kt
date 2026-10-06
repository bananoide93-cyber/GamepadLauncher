package com.gamepadlayout.app.cast

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.Inet4Address

data class CastStatus(
    val running: Boolean = false,
    val url: String = "",
    val clients: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val targetFps: Int = 0,
    val realFps: Int = 0,
    val startedAt: Long = 0L,
    val error: String? = null
)

object CastState {
    val status = MutableStateFlow(CastStatus())
}

object NetworkInfo {
    /** IPv4 local da rede ativa (Wi-Fi), usando só ConnectivityManager. */
    fun localIp(context: Context): String? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val lp = cm?.getLinkProperties(cm.activeNetwork)
        lp?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
