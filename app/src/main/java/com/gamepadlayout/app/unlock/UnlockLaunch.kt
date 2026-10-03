package com.gamepadlayout.app.unlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gamepadlayout.app.MainActivity

/** Preferências do "abrir ao desbloquear" (SharedPreferences: o serviço lê sem depender da interface). */
object UnlockPrefs {
    private fun p(c: Context) = c.getSharedPreferences("unlock_launch", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()
    fun delaySec(c: Context) = p(c).getInt("delay", 1)
    fun setDelaySec(c: Context, v: Int) = p(c).edit().putInt("delay", v).apply()
    fun cooldownMin(c: Context) = p(c).getInt("cooldown", 10)
    fun setCooldownMin(c: Context, v: Int) = p(c).edit().putInt("cooldown", v).apply()
    fun onlyController(c: Context) = p(c).getBoolean("only_controller", true)
    fun setOnlyController(c: Context, v: Boolean) = p(c).edit().putBoolean("only_controller", v).apply()
    fun onlyCharging(c: Context) = p(c).getBoolean("only_charging", false)
    fun setOnlyCharging(c: Context, v: Boolean) = p(c).edit().putBoolean("only_charging", v).apply()
    fun pausedUntil(c: Context) = p(c).getLong("paused_until", 0L)
    fun pauseFor(c: Context, minutes: Int) =
        p(c).edit().putLong("paused_until", System.currentTimeMillis() + minutes * 60_000L).apply()
    fun clearPause(c: Context) = p(c).edit().putLong("paused_until", 0L).apply()
    fun lastOpen(c: Context) = p(c).getLong("last_open", 0L)
    fun markOpened(c: Context) = p(c).edit().putLong("last_open", System.currentTimeMillis()).apply()
}

object UnlockLaunch {
    fun canDrawOverlays(c: Context) = Settings.canDrawOverlays(c)

    /** Liga ou desliga o serviço conforme a preferência salva. */
    fun sync(c: Context) {
        if (UnlockPrefs.enabled(c) && canDrawOverlays(c)) {
            runCatching { ContextCompat.startForegroundService(c, Intent(c, UnlockService::class.java)) }
        } else {
            c.stopService(Intent(c, UnlockService::class.java))
        }
    }

    fun controllerPresent(c: Context): Boolean {
        val im = c.getSystemService(Context.INPUT_SERVICE) as InputManager
        return im.inputDeviceIds.any { id ->
            val s = im.getInputDevice(id)?.sources ?: 0
            (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
    }

    private fun charging(c: Context): Boolean {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    /** Todas as travas passam? Retorna o motivo de bloqueio (ou null se pode abrir). */
    fun blockReason(c: Context): String? {
        val now = System.currentTimeMillis()
        return when {
            !UnlockPrefs.enabled(c) -> "desativado"
            !canDrawOverlays(c) -> "sem permissão de sobreposição"
            now < UnlockPrefs.pausedUntil(c) -> "pausado"
            now - UnlockPrefs.lastOpen(c) < UnlockPrefs.cooldownMin(c) * 60_000L -> "intervalo mínimo"
            UnlockPrefs.onlyController(c) && !controllerPresent(c) -> "sem controle conectado"
            UnlockPrefs.onlyCharging(c) && !charging(c) -> "fora do carregador"
            else -> null
        }
    }
}

/** Serviço em primeiro plano que escuta o desbloqueio (USER_PRESENT) e abre o app, respeitando as travas. */
class UnlockService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            if (UnlockLaunch.blockReason(context) != null) return
            val delay = UnlockPrefs.delaySec(context) * 1000L
            handler.postDelayed({
                // Reconfere: o jogador pode ter pausado ou desativado durante o atraso.
                if (UnlockLaunch.blockReason(applicationContext) == null) {
                    UnlockPrefs.markOpened(applicationContext)
                    runCatching {
                        startActivity(
                            Intent(applicationContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                }
            }, delay)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> UnlockPrefs.pauseFor(this, 60)
            ACTION_DISABLE -> {
                UnlockPrefs.setEnabled(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        showNotification()
        return START_STICKY
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Abrir ao desbloquear", NotificationManager.IMPORTANCE_LOW))
        fun act(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, UnlockService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Gamepad Layout")
            .setContentText("Abre o app ao desbloquear o celular")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Pausar 1 h", act(ACTION_PAUSE, 1)).build())
            .addAction(Notification.Action.Builder(null, "Desativar", act(ACTION_DISABLE, 2)).build())
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 2, n, type)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "unlock_launch"
        const val ACTION_PAUSE = "com.gamepadlayout.app.UNLOCK_PAUSE"
        const val ACTION_DISABLE = "com.gamepadlayout.app.UNLOCK_DISABLE"
    }
}

/** Reativa o serviço depois de reiniciar o aparelho (só se o jogador deixou ligado). */
class UnlockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) UnlockLaunch.sync(context)
    }
}
