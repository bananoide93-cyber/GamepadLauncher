package com.gamepadlayout.app.input

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.max

enum class PadButton { A, B, X, Y, L1, R1, L2, R2, L3, R3, START, SELECT }

/**
 * Ponte entre a Activity (KeyEvent/MotionEvent) e as telas Compose.
 * Cada botão é traduzido para uma [PadAction] conforme o mapeamento do usuário.
 */
object GamepadInput {
    @Volatile var browserActive = false
    @Volatile var overlayOpen = false
    @Volatile var keyboardOpen = false
    @Volatile var controllerConnected = false
    @Volatile var mapping: Map<PadButton, PadAction> = ControllerMapping.defaults

    @Volatile var leftX = 0f
    @Volatile var leftY = 0f
    @Volatile var rightX = 0f
    @Volatile var rightY = 0f

    /** Ações "de evento" (menu rápido, ações do navegador). */
    val events = MutableSharedFlow<PadAction>(extraBufferCapacity = 16)

    /** Botões físicos crus (usados pelo teclado do controle: X apaga, Y espaço, etc.). */
    val rawButtons = MutableSharedFlow<PadButton>(extraBufferCapacity = 16)

    /** Pedidos de "ir para o início" (botão Home do Android quando o app é a tela inicial). */
    val homeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Último botão físico pressionado (tela de teste do controle). */
    val lastButton = MutableStateFlow<PadButton?>(null)

    /** Chamado quando um gatilho analógico (L2/R2) cruza o limite de acionamento. */
    var onTrigger: ((PadButton) -> Unit)? = null

    private val lastEmit = LongArray(PadAction.entries.size)
    private var lDown = false
    private var rDown = false

    fun mapKey(code: Int): PadButton? = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> PadButton.A
        KeyEvent.KEYCODE_BUTTON_B -> PadButton.B
        KeyEvent.KEYCODE_BUTTON_X -> PadButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> PadButton.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> PadButton.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> PadButton.R1
        KeyEvent.KEYCODE_BUTTON_L2 -> PadButton.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> PadButton.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> PadButton.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> PadButton.R3
        KeyEvent.KEYCODE_BUTTON_START -> PadButton.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> PadButton.SELECT
        else -> null
    }

    fun actionFor(b: PadButton): PadAction = mapping[b] ?: PadAction.NONE

    fun emit(a: PadAction) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEmit[a.ordinal] < 150) return // evita duplicidade tecla + eixo
        lastEmit[a.ordinal] = now
        events.tryEmit(a)
    }

    fun onMotion(ev: MotionEvent) {
        leftX = ev.getAxisValue(MotionEvent.AXIS_X)
        leftY = ev.getAxisValue(MotionEvent.AXIS_Y)
        val z = ev.getAxisValue(MotionEvent.AXIS_Z)
        val rx = ev.getAxisValue(MotionEvent.AXIS_RX)
        val rz = ev.getAxisValue(MotionEvent.AXIS_RZ)
        val ry = ev.getAxisValue(MotionEvent.AXIS_RY)
        rightX = if (abs(z) >= abs(rx)) z else rx
        rightY = if (abs(rz) >= abs(ry)) rz else ry

        val lt = max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
        if (rt > 0.6f && !rDown) { rDown = true; onTrigger?.invoke(PadButton.R2) } else if (rt < 0.3f) rDown = false
        if (lt > 0.6f && !lDown) { lDown = true; onTrigger?.invoke(PadButton.L2) } else if (lt < 0.3f) lDown = false
    }
}
