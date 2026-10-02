package com.gamepadlayout.app.controller

import android.content.Context
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ControllerInfo(
    val id: Int,
    val name: String,
    val battery: Int?,
    val charging: Boolean
)

/**
 * Detecta controles via InputManager/InputDevice (APIs oficiais).
 * Bateria: só existe em Android 12+ (InputDevice.getBatteryState) e se o controle informar.
 */
class ControllerManager(private val context: Context) {

    fun flow(): Flow<List<ControllerInfo>> = callbackFlow {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { trySend(read()) }
            override fun onInputDeviceRemoved(deviceId: Int) { trySend(read()) }
            override fun onInputDeviceChanged(deviceId: Int) { trySend(read()) }
        }
        im.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        trySend(read())
        val poll = launch {
            while (true) {
                delay(30_000) // atualiza a bateria sem gastar CPU
                trySend(read())
            }
        }
        awaitClose {
            poll.cancel()
            im.unregisterInputDeviceListener(listener)
        }
    }.distinctUntilChanged()

    private fun read(): List<ControllerInfo> =
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { isGamepad(it) }
            .map { toInfo(it) }

    private fun isGamepad(d: InputDevice): Boolean {
        if (d.isVirtual) return false
        val s = d.sources
        return (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun toInfo(d: InputDevice): ControllerInfo {
        var battery: Int? = null
        var charging = false
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                val bs = d.batteryState
                if (bs.isPresent) {
                    val cap = bs.capacity
                    if (!cap.isNaN()) battery = (cap * 100).roundToInt()
                    charging = bs.status == BatteryState.STATUS_CHARGING
                }
            }
        }
        return ControllerInfo(d.id, d.name ?: "Controle", battery, charging)
    }
}
