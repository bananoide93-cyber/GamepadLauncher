package com.gamepadlayout.app.games

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.input.GamepadInput
import com.gamepadlayout.app.input.PadButton
import com.gamepadlayout.app.ui.components.ConsoleBackground
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Recordes salvos no próprio aparelho (SharedPreferences privado do app). */
object ScoreStore {
    private fun prefs(c: Context) = c.getSharedPreferences("arcade_scores", Context.MODE_PRIVATE)
    fun best(c: Context, id: String): Int = prefs(c).getInt(id, 0)
    fun save(c: Context, id: String, score: Int) {
        if (score > best(c, id)) prefs(c).edit().putInt(id, score).apply()
    }
}

/** Implementação de [Gfx] sobre o DrawScope do Compose. */
class ComposeGfx : Gfx {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    var scope: DrawScope? = null

    override val width: Float get() = scope?.size?.width ?: 0f
    override val height: Float get() = scope?.size?.height ?: 0f

    override fun rect(x: Float, y: Float, w: Float, h: Float, color: Long) {
        scope?.drawRect(Color(color), Offset(x, y), Size(w, h))
    }

    override fun circle(cx: Float, cy: Float, r: Float, color: Long) {
        scope?.drawCircle(Color(color), r, Offset(cx, cy))
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Long) {
        scope?.drawLine(Color(color), Offset(x1, y1), Offset(x2, y2), strokeWidth)
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Long, center: Boolean) {
        val sc = scope ?: return
        paint.textSize = size
        paint.color = color.toInt()
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        sc.drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
    }
}

/** Permite que a tela externa (TV por cabo/Presentation) desenhe o mesmo jogo, sem codificar vídeo: atraso mínimo. */
object ArcadeMirror {
    var game by mutableStateOf<MiniGame?>(null)
    var tick by mutableLongStateOf(0L)
}

@Composable
fun ArcadeMirrorCanvas() {
    val gfx = remember { ComposeGfx() }
    Canvas(Modifier.fillMaxSize().background(Color.Black)) {
        val t = ArcadeMirror.tick
        val g = ArcadeMirror.game
        if (t >= 0L && g != null) {
            gfx.scope = this
            g.draw(gfx)
            if (g.over) g.drawOverlay(gfx)
            Retro.crt(gfx)
        }
    }
}

@Composable
fun ArcadeScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val st = LocalConsoleStyle.current
    val games = remember { Games.all() }
    ScreenScaffold("Arcade", onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(games, key = { it.id }) { g ->
                ConsoleSurface(
                    onClick = { onOpen(g.id) },
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    focusScale = 1.04f,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp)) {
                        Text(g.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            g.description,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Recorde: ${ScoreStore.best(ctx, g.id)}", fontSize = 12.sp, color = st.accentSoft)
                    }
                }
            }
        }
    }
}

private class Touch {
    var active = false
    var sx = 0f
    var sy = 0f
    var cx = 0f
    var cy = 0f
    var tap = false
}

@Composable
fun ArcadeGameScreen(id: String, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val game = remember(id) { Games.create(id) ?: SnakeGame() }
    var tick by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    val input = remember { GameInput() }
    val touch = remember { Touch() }
    val gfx = remember { ComposeGfx() }
    val focus = remember { FocusRequester() }
    val stickPx = with(LocalDensity.current) { 70.dp.toPx() }

    DisposableEffect(game) {
        ArcadeMirror.game = game
        onDispose { ArcadeMirror.game = null }
    }
    DisposableEffect(Unit) {
        onDispose {
            GamepadInput.gameActive = false
            GamepadInput.resetGameInput()
        }
    }
    // Em jogo o controle vira entrada de jogo; na pausa volta a navegar pelos botões do menu.
    LaunchedEffect(paused) {
        GamepadInput.gameActive = !paused
        GamepadInput.resetGameInput()
        if (paused) {
            delay(120)
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(game) {
        game.best = ScoreStore.best(ctx, game.id)
        game.reset()
    }
    // Voltar (gesto do sistema): pausa; na pausa, sai.
    BackHandler { if (paused) onExit() else paused = true }
    LaunchedEffect(Unit) {
        GamepadInput.rawButtons.collect { b ->
            if (b == PadButton.START) paused = !paused
            else if (b == PadButton.B && !paused) paused = true
        }
    }

    LaunchedEffect(game) {
        var last = withFrameNanos { it }
        var prevFire = false
        var prevSx = 0
        var prevSy = 0
        var saved = false
        var overFor = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            if (!paused) {
                var dx = GamepadInput.dirX()
                var dy = GamepadInput.dirY()
                if (touch.active) {
                    val tx = ((touch.cx - touch.sx) / stickPx).coerceIn(-1f, 1f)
                    val ty = ((touch.cy - touch.sy) / stickPx).coerceIn(-1f, 1f)
                    if (abs(tx) > 0.15f || abs(ty) > 0.15f) {
                        dx = tx
                        dy = ty
                    }
                }
                input.dx = dx
                input.dy = dy
                val rx = GamepadInput.rightX
                input.turnX = if (abs(rx) > 0.2f) rx else 0f
                val padFire = GamepadInput.isHeld(PadButton.A) || GamepadInput.isHeld(PadButton.R2)
                input.fire = padFire
                if (padFire && !prevFire) input.firePressed = true
                prevFire = padFire
                if (touch.tap) {
                    input.firePressed = true
                    touch.tap = false
                }
                input.strafeLeft = GamepadInput.isHeld(PadButton.L1)
                input.strafeRight = GamepadInput.isHeld(PadButton.R1)
                val sx = if (dx > 0.5f) 1 else if (dx < -0.5f) -1 else 0
                val sy = if (dy > 0.5f) 1 else if (dy < -0.5f) -1 else 0
                if (sx != 0 && sx != prevSx) input.stepX = sx
                if (sy != 0 && sy != prevSy) input.stepY = sy
                prevSx = sx
                prevSy = sy

                if (game.over) {
                    overFor += dt
                    if (overFor > 0.8f && input.firePressed) {
                        game.reset()
                        saved = false
                        overFor = 0f
                    }
                } else {
                    overFor = 0f
                    game.update(dt, input)
                }
                if (game.over && !saved) {
                    saved = true
                    ScoreStore.save(ctx, game.id, game.score)
                }
                input.clearEdges()
            }
            tick++
            ArcadeMirror.tick = tick
        }
    }

    ConsoleBackground {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touch.active = true
                        touch.sx = down.position.x
                        touch.sy = down.position.y
                        touch.cx = touch.sx
                        touch.cy = touch.sy
                        val t0 = down.uptimeMillis
                        var moved = 0f
                        var lastTime = t0
                        do {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull() ?: break
                            touch.cx = ch.position.x
                            touch.cy = ch.position.y
                            moved = max(moved, hypot(touch.cx - touch.sx, touch.cy - touch.sy))
                            lastTime = ch.uptimeMillis
                            ch.consume()
                        } while (event.changes.any { it.pressed })
                        touch.active = false
                        if (lastTime - t0 < 260L && moved < 24f) touch.tap = true
                    }
                }
        ) {
            val frame = tick // lido aqui para redesenhar a cada quadro sem recompor a tela
            if (frame >= 0L) {
                gfx.scope = this
                game.draw(gfx)
                if (game.over) game.drawOverlay(gfx)
                Retro.crt(gfx)
                val hv = View(gfx, size.width, size.height)
                val hp = size.height * 0.0058f
                hv.pixText("START PAUSA", size.width - PixelFont.width("START PAUSA", hp) - 8f, size.height - hp * 9f, hp, C.alpha(P.WHITE, 0.55f), shadow = false)
            }
        }
        if (paused) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1B1245))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(game.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(game.controls, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    ConsoleSurface(
                        onClick = { paused = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        focusRequester = focus,
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Continuar", Modifier.align(Alignment.Center), fontSize = 14.sp) }
                    ConsoleSurface(
                        onClick = {
                            game.reset()
                            paused = false
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Reiniciar", Modifier.align(Alignment.Center), fontSize = 14.sp) }
                    ConsoleSurface(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Sair do jogo", Modifier.align(Alignment.Center), fontSize = 14.sp) }
                }
            }
        }
    }
}
