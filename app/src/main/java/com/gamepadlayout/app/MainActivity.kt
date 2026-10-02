package com.gamepadlayout.app

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.gamepadlayout.app.console.ConsoleMode
import com.gamepadlayout.app.display.TvNav
import com.gamepadlayout.app.input.GamepadInput
import com.gamepadlayout.app.input.PadAction
import com.gamepadlayout.app.input.PadButton
import com.gamepadlayout.app.ui.GamepadLayoutRoot

class MainActivity : ComponentActivity() {

    private var hatDir = 0
    private var hatNextFire = 0L
    private val lastFire = LongArray(PadButton.entries.size)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Estado inicial = Modo Console (a configuração salva é aplicada logo após o carregamento).
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ConsoleMode.reapplyBars(this)
        GamepadInput.onTrigger = { activate(it) }
        setContent { GamepadLayoutRoot() }
    }

    override fun onDestroy() {
        GamepadInput.onTrigger = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ConsoleMode.reapplyBars(this)
    }

    // ---------------------------------------------------------------- teclas / botões

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val btn = GamepadInput.mapKey(code)

        if (btn == null) {
            // D-pad / Enter: controla a Home da TV enquanto a tela "Controlando a TV" está aberta
            if (TvNav.controlling && !GamepadInput.overlayOpen) {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) TvNav.move(-1); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) TvNav.move(1); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (down && event.repeatCount == 0) TvNav.confirm()
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }

        if (down && event.repeatCount == 0) GamepadInput.lastButton.value = btn
        val action = GamepadInput.actionFor(btn)
        val isTrigger = btn == PadButton.L2 || btn == PadButton.R2
        val tvDrives = TvNav.controlling && !GamepadInput.overlayOpen

        // Confirmar / Opções precisam de DOWN+UP reais para o Compose reconhecer o clique.
        if (!isTrigger && !(tvDrives && action == PadAction.CONFIRM)) {
            when (action) {
                PadAction.CONFIRM -> return super.dispatchKeyEvent(remap(event, KeyEvent.KEYCODE_DPAD_CENTER))
                PadAction.OPTIONS -> return super.dispatchKeyEvent(remap(event, KeyEvent.KEYCODE_MENU))
                else -> {}
            }
        }
        if (down && event.repeatCount == 0) activate(btn)
        return true
    }

    /** Executa a ação ligada ao botão (uma vez por aperto; gatilhos analógicos usam o mesmo caminho). */
    private fun activate(btn: PadButton) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFire[btn.ordinal] < 150) return // evita tecla + eixo duplicados
        lastFire[btn.ordinal] = now
        GamepadInput.lastButton.value = btn
        val action = GamepadInput.actionFor(btn)
        when (action) {
            PadAction.CONFIRM ->
                if (TvNav.controlling && !GamepadInput.overlayOpen) TvNav.confirm()
                else sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
            PadAction.OPTIONS -> sendKey(KeyEvent.KEYCODE_MENU)
            PadAction.BACK -> onBackPressedDispatcher.onBackPressed()
            PadAction.MENU -> GamepadInput.emit(PadAction.MENU)
            PadAction.NONE -> {}
            else -> if (GamepadInput.browserActive) GamepadInput.emit(action)
        }
    }

    private fun remap(e: KeyEvent, newCode: Int) = KeyEvent(
        e.downTime, e.eventTime, e.action, newCode, e.repeatCount,
        e.metaState, e.deviceId, e.scanCode, e.flags, e.source
    )

    private fun sendKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    // ---------------------------------------------------------------- analógicos / hat

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val joystick = (ev.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            ev.action == MotionEvent.ACTION_MOVE
        if (joystick) {
            GamepadInput.onMotion(ev)
            if (GamepadInput.browserActive) {
                // No navegador o analógico é mouse; só o D-pad (hat) vira teclas de foco.
                hatNavigate(ev)
                return true
            }
            // Fora do navegador, o Android converte analógico/hat em D-pad automaticamente.
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun hatNavigate(ev: MotionEvent) {
        val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val dir = when {
            hx < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
            hx > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
            hy < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
            hy > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> 0
        }
        if (dir == 0) { hatDir = 0; return }
        val now = SystemClock.uptimeMillis()
        if (dir != hatDir) {
            hatDir = dir
            hatNextFire = now + 380
            sendKey(dir)
        } else if (now >= hatNextFire) {
            hatNextFire = now + 140
            sendKey(dir)
        }
    }
}
