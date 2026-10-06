package com.gamepadlayout.app.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class ExternalDisplayInfo(val id: Int, val name: String, val width: Int, val height: Int)

/** Telas externas (HDMI/USB-C, Miracast/Smart View) que aceitam Presentation. */
class ExternalDisplays(context: Context) {
    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun flow(): Flow<List<ExternalDisplayInfo>> = callbackFlow {
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { trySend(read()) }
            override fun onDisplayRemoved(displayId: Int) { trySend(read()) }
            override fun onDisplayChanged(displayId: Int) { trySend(read()) }
        }
        dm.registerDisplayListener(l, Handler(Looper.getMainLooper()))
        trySend(read())
        awaitClose { dm.unregisterDisplayListener(l) }
    }.distinctUntilChanged()

    fun get(id: Int): Display? = dm.getDisplay(id)

    private fun read(): List<ExternalDisplayInfo> =
        dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .map {
                val m = it.mode
                ExternalDisplayInfo(it.displayId, it.name ?: "Tela externa", m.physicalWidth, m.physicalHeight)
            }
}
