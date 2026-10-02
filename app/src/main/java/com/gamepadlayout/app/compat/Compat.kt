package com.gamepadlayout.app.compat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings

data class CompatReport(
    val androidVersion: String,
    val mediaProjection: Boolean,
    val castSettings: Boolean,
    val bluetooth: Boolean,
    val controllerBattery: Boolean,
    val homeSettings: Boolean
)

/** Verificações para nunca fingir que um recurso funciona quando o aparelho não suporta. */
object Compat {
    fun report(context: Context): CompatReport {
        val pm = context.packageManager
        fun resolves(action: String) =
            pm.resolveActivity(Intent(action), PackageManager.MATCH_DEFAULT_ONLY) != null
        return CompatReport(
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            mediaProjection = context.getSystemService(MediaProjectionManager::class.java) != null,
            castSettings = resolves("android.settings.CAST_SETTINGS"),
            bluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
            controllerBattery = Build.VERSION.SDK_INT >= 31,
            homeSettings = resolves(Settings.ACTION_HOME_SETTINGS)
        )
    }
}
