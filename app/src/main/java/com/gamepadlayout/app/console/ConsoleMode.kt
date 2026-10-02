package com.gamepadlayout.app.console

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Modo Console: tela cheia imersiva (barras do sistema escondidas, voltam com swipe)
 * e tela acesa só quando faz sentido (controle conectado / transmitindo).
 * A orientação é sempre horizontal (fixada no AndroidManifest).
 * Usa apenas APIs oficiais (WindowInsetsController, requestedOrientation, FLAG_KEEP_SCREEN_ON).
 */
object ConsoleMode {
    @Volatile var enabled = true

    fun update(activity: Activity, enabled: Boolean, keepAwake: Boolean) {
        this.enabled = enabled
        val w = activity.window
        WindowCompat.setDecorFitsSystemWindows(w, false)
        val c = WindowInsetsControllerCompat(w, w.decorView)
        if (enabled) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
        if (enabled && keepAwake) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    }

    /** Reaplica o modo imersivo (o Android mostra as barras de novo ao voltar o foco da janela). */
    fun reapplyBars(activity: Activity) {
        if (!enabled) return
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
