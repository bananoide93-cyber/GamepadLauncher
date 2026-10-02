package com.gamepadlayout.app.system

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Funções que um app comum não pode executar diretamente: abre a tela oficial do Android. */
object SystemIntents {
    fun open(ctx: Context, action: String) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Não foi possível abrir essa tela do sistema", Toast.LENGTH_SHORT).show()
        }
    }

    fun wifi(ctx: Context) = open(ctx, Settings.ACTION_WIFI_SETTINGS)
    fun bluetooth(ctx: Context) = open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
    fun downloads(ctx: Context) = open(ctx, DownloadManager.ACTION_VIEW_DOWNLOADS)
}
