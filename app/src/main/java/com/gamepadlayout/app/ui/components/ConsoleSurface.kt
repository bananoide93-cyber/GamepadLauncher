package com.gamepadlayout.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle

/** Fundo estilo dashboard de console: gradiente + onda roxa suave + brilho. */
@Composable
fun ConsoleBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val st = LocalConsoleStyle.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(st.bgTop, st.bgBottom)))
            .drawBehind {
                val w = size.width
                val h = size.height
                val glowCenter = Offset(w * 0.85f, h * 0.05f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(st.accent.copy(alpha = 0.28f * st.effects), Color.Transparent),
                        center = glowCenter,
                        radius = w * 0.5f
                    ),
                    radius = w * 0.5f,
                    center = glowCenter
                )
                val wave = Path().apply {
                    moveTo(w * 0.35f, h)
                    cubicTo(w * 0.70f, h * 0.97f, w * 0.85f, h * 0.60f, w, h * 0.22f)
                    lineTo(w, h)
                    close()
                }
                drawPath(wave, st.accent.copy(alpha = 0.20f * st.effects))
            },
        content = content
    )
}

/**
 * Elemento base focável da interface (D-pad / analógico / toque).
 * Selecionado: brilho roxo, borda, leve ampliação e animação suave.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    focusScale: Float = 1.08f,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    background: Color? = null,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    val st = LocalConsoleStyle.current
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused || pressed) focusScale else 1f,
        animationSpec = tween(st.dur(150)),
        label = "scale"
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(st.dur(200)),
        label = "glow"
    )
    val base = background ?: st.cardColor
    val fill = lerp(base, st.accent.copy(alpha = 0.38f), glow)

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onKeyEvent { ev -> onKey?.invoke(ev) ?: false }
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused?.invoke()
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (glow > 0.01f) {
                    val cr = 10.dp.toPx()
                    for (i in 3 downTo 1) {
                        val g = i * 5.dp.toPx()
                        drawRoundRect(
                            color = st.accent.copy(alpha = 0.10f * st.effects * glow),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + g * 2, size.height + g * 2),
                            cornerRadius = CornerRadius(cr + g)
                        )
                    }
                }
            }
            .clip(shape)
            .background(fill)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) st.accentSoft else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        content(focused)
    }
}

/** Mesmo visual do [ConsoleSurface], mas o destaque é controlado por estado (usado na Home da TV). */
@Composable
fun ConsoleCard(
    focused: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    focusScale: Float = 1.08f,
    background: Color? = null,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    val st = LocalConsoleStyle.current
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = tween(st.dur(150)),
        label = "cardScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(st.dur(200)),
        label = "cardGlow"
    )
    val base = background ?: st.cardColor
    val fill = lerp(base, st.accent.copy(alpha = 0.38f), glow)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (glow > 0.01f) {
                    val cr = 10.dp.toPx()
                    for (i in 3 downTo 1) {
                        val g = i * 5.dp.toPx()
                        drawRoundRect(
                            color = st.accent.copy(alpha = 0.10f * st.effects * glow),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + g * 2, size.height + g * 2),
                            cornerRadius = CornerRadius(cr + g)
                        )
                    }
                }
            }
            .clip(shape)
            .background(fill)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) st.accentSoft else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
    ) {
        content(focused)
    }
}
