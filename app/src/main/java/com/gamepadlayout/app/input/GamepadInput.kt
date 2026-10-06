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

    /** true enquanto um jogo do Arcade está rodando: o controle vira entrada de jogo (sem navegação de foco). */
    @Volatile var gameActive = false
    @Volatile var hatX = 0f
    @Volatile var hatY = 0f
    @Volatile var dpadX = 0
    @Volatile var dpadY = 0
    val held = BooleanArray(PadButton.entries.size)
    @Volatile var controllerConnected = false

    /** "Modo só controle": com controle conectado e em uso, o toque na tela fica bloqueado. */
    @Volatile var controllerOnly = true
    @Volatile var lastPadInput = 0L

    /** Pulso a cada entrada do controle (a interface usa para garantir que há algo focado). */
    val padActivity = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Pulso a cada D-pad para baixo (garante que "baixo" sempre leva ao primeiro item da tela). */
    val downPulse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    const val TOUCH_UNLOCK_MS = 10_000L

    fun markPad() {
        lastPadInput = SystemClock.uptimeMillis()
        padActivity.tryEmit(Unit)
    }

    /** Toque bloqueado: controle conectado, modo ligado e o controle foi usado nos últimos 10 s. */
    fun touchBlocked(): Boolean =
        controllerOnly && controllerConnected && !browserTouchOk &&
            SystemClock.uptimeMillis() - lastPadInput < TOUCH_UNLOCK_MS

    /** Reservado: telas que sempre aceitam toque (nenhuma por padrão). */
    @Volatile var browserTouchOk = false
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
        val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val ax = ev.getAxisValue(MotionEvent.AXIS_X)
        val ay = ev.getAxisValue(MotionEvent.AXIS_Y)
        if (hx != 0f || hy != 0f || abs(ax) > 0.45f || abs(ay) > 0.45f ||
            abs(ev.getAxisValue(MotionEvent.AXIS_Z)) > 0.45f || abs(ev.getAxisValue(MotionEvent.AXIS_RZ)) > 0.45f
        ) markPad()
        leftX = ev.getAxisValue(MotionEvent.AXIS_X)
        leftY = ev.getAxisValue(MotionEvent.AXIS_Y)
        val z = ev.getAxisValue(MotionEvent.AXIS_Z)
        val rx = ev.getAxisValue(MotionEvent.AXIS_RX)
        val rz = ev.getAxisValue(MotionEvent.AXIS_RZ)
        val ry = ev.getAxisValue(MotionEvent.AXIS_RY)
        rightX = if (abs(z) >= abs(rx)) z else rx
        rightY = if (abs(rz) >= abs(ry)) rz else ry

        hatX = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        hatY = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val lt = max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
        held[PadButton.R2.ordinal] = rt > 0.5f
        held[PadButton.L2.ordinal] = lt > 0.5f
        if (rt > 0.6f && !rDown) { rDown = true; onTrigger?.invoke(PadButton.R2) } else if (rt < 0.3f) rDown = false
        if (lt > 0.6f && !lDown) { lDown = true; onTrigger?.invoke(PadButton.L2) } else if (lt < 0.3f) lDown = false
    }

    // ------------------------------------------------------------ entrada para jogos

    /** Direção horizontal combinada: analógico esquerdo, hat ou D-pad (-1..1). */
    fun dirX(): Float = when {
        abs(leftX) > 0.3f -> leftX
        hatX != 0f -> hatX
        else -> dpadX.toFloat()
    }

    /** Direção vertical combinada (negativo = para cima). */
    fun dirY(): Float = when {
        abs(leftY) > 0.3f -> leftY
        hatY != 0f -> hatY
        else -> dpadY.toFloat()
    }

    fun isHeld(b: PadButton): Boolean = held[b.ordinal]

    fun resetGameInput() {
        for (i in held.indices) held[i] = false
        dpadX = 0
        dpadY = 0
    }

    /** Teclas durante um jogo: guarda botões/D-pad "segurados" e consome o evento. */
    fun handleGameKey(code: Int, down: Boolean, repeat: Int): Boolean {
        val b = mapKey(code)
        if (b != null) {
            held[b.ordinal] = down
            if (down && repeat == 0) {
                lastButton.value = b
                rawButtons.tryEmit(b)
            }
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { dpadX = if (down) -1 else if (dpadX == -1) 0 else dpadX; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { dpadX = if (down) 1 else if (dpadX == 1) 0 else dpadX; return true }
            KeyEvent.KEYCODE_DPAD_UP -> { dpadY = if (down) -1 else if (dpadY == -1) 0 else dpadY; return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { dpadY = if (down) 1 else if (dpadY == 1) 0 else dpadY; return true }
        }
        return false
    }
}
