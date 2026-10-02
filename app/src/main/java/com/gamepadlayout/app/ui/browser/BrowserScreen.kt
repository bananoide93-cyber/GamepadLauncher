package com.gamepadlayout.app.ui.browser

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.input.GamepadInput
import com.gamepadlayout.app.input.PadAction
import com.gamepadlayout.app.ui.components.ConsoleBackground
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEADZONE = 0.12f

/** Toque sintético na WebView (clique normal ou pressionar-e-segurar = clique direito/menu de contexto). */
private suspend fun clickAt(view: WebView?, p: Offset, long: Boolean) {
    view ?: return
    val down = SystemClock.uptimeMillis()
    val e1 = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, p.x, p.y, 0)
    e1.source = InputDevice.SOURCE_TOUCHSCREEN
    view.dispatchTouchEvent(e1)
    e1.recycle()
    delay(if (long) 700L else 60L)
    val up = SystemClock.uptimeMillis()
    val e2 = MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, p.x, p.y, 0)
    e2.source = InputDevice.SOURCE_TOUCHSCREEN
    view.dispatchTouchEvent(e2)
    e2.recycle()
}

@Composable
fun BrowserScreen(settings: AppSettings, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val st = LocalConsoleStyle.current
    val cfg by rememberUpdatedState(settings)
    val exit by rememberUpdatedState(onExit)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val controller = remember { BrowserController(ctx) { cfg.zoom }.also { it.newTab() } }

    DisposableEffect(Unit) {
        GamepadInput.browserActive = true
        onDispose {
            GamepadInput.browserActive = false
            controller.destroy()
        }
    }
    LaunchedEffect(settings.zoom) { controller.setZoom(settings.zoom) }

    var cursor by remember { mutableStateOf(Offset(200f, 200f)) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var cursorVisible by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var addrFocused by remember { mutableStateOf(false) }
    val tab = controller.current

    fun goBackOrExit() {
        val wv = controller.current?.webView
        if (wv != null && wv.canGoBack()) wv.goBack() else exit()
    }

    BackHandler { goBackOrExit() }

    val tabUrl = tab?.url
    LaunchedEffect(tabUrl, tab?.id) { if (!addrFocused) address = tabUrl ?: "" }
    LaunchedEffect(area.width, area.height) {
        if (area != IntSize.Zero) cursor = Offset(area.width / 2f, area.height / 2f)
    }

    // Ações do controle (mapeadas em Controle > Mapeamento de botões)
    LaunchedEffect(Unit) {
        GamepadInput.events.collect { a ->
            val wv = controller.current?.webView
            when (a) {
                PadAction.TAB_NEXT -> controller.next()
                PadAction.TAB_PREV -> controller.prev()
                PadAction.CLICK -> scope.launch { clickAt(wv, cursor, false) }
                PadAction.RIGHT_CLICK -> scope.launch { clickAt(wv, cursor, true) }
                PadAction.RELOAD -> wv?.reload()
                PadAction.FORWARD -> wv?.let { if (it.canGoForward()) it.goForward() }
                PadAction.NEW_TAB -> controller.newTab()
                PadAction.CLOSE_TAB -> if (controller.closeTab(controller.selected)) exit()
                else -> {}
            }
        }
    }

    // Analógico esquerdo = mouse (com aceleração suave); analógico direito = rolagem
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val x = GamepadInput.leftX
            val y = GamepadInput.leftY
            val mag = hypot(x, y)
            if (mag > DEADZONE) {
                val n = ((mag - DEADZONE) / (1f - DEADZONE)).coerceIn(0f, 1f)
                val speed = 1500f * cfg.cursorSpeed * n * n
                val nx = (cursor.x + x / mag * speed * dt).coerceIn(0f, max(0, area.width - 1).toFloat())
                val ny = (cursor.y + y / mag * speed * dt).coerceIn(0f, max(0, area.height - 1).toFloat())
                cursor = Offset(nx, ny)
                cursorVisible = true
            }
            val rx = GamepadInput.rightX
            val ry = GamepadInput.rightY
            if (abs(rx) > 0.2f || abs(ry) > 0.2f) {
                controller.current?.webView?.scrollBy((rx * 900f * dt).roundToInt(), (ry * 900f * dt).roundToInt())
            }
        }
    }

    ConsoleBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Barra de endereço
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolButton(Icons.Rounded.Home) { exit() }
                ToolButton(Icons.AutoMirrored.Rounded.ArrowBack, tab?.canGoBack == true) { tab?.webView?.goBack() }
                ToolButton(Icons.AutoMirrored.Rounded.ArrowForward, tab?.canGoForward == true) { tab?.webView?.goForward() }
                ToolButton(Icons.Rounded.Refresh) { tab?.webView?.reload() }
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(
                            if (addrFocused) 2.dp else 1.dp,
                            if (addrFocused) st.accentSoft else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(19.dp)
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            controller.current?.webView?.loadUrl(normalizeUrl(address))
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { addrFocused = it.isFocused }
                    )
                }
                ToolButton(Icons.Rounded.Add) { controller.newTab() }
            }
            Spacer(Modifier.height(6.dp))
            // Abas
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(controller.tabs, key = { _, t -> t.id }) { i, t ->
                    ConsoleSurface(
                        onClick = { controller.selected = i },
                        modifier = Modifier.height(32.dp).widthIn(max = 190.dp),
                        shape = RoundedCornerShape(16.dp),
                        focusScale = 1.05f,
                        background = if (i == controller.selected) st.accent.copy(alpha = 0.45f) else null
                    ) {
                        Row(
                            Modifier.fillMaxHeight().padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                t.title.ifBlank { "Nova aba" },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 130.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Close, null,
                                Modifier
                                    .size(16.dp)
                                    .focusProperties { canFocus = false }
                                    .clickable { if (controller.closeTab(i)) exit() }
                            )
                        }
                    }
                }
            }
            if (tab != null && tab.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = st.accent,
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
            // Área da página + cursor virtual
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .onSizeChanged { area = it }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> FrameLayout(c) },
                    update = { frame ->
                        val wv = controller.current?.webView
                        if (wv != null && frame.getChildAt(0) !== wv) {
                            frame.removeAllViews()
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            frame.addView(
                                wv,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
                if (cursorVisible) {
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (cursor.x - 10.dp.toPx()).roundToInt(),
                                    (cursor.y - 10.dp.toPx()).roundToInt()
                                )
                            }
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .border(3.dp, st.accent, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    ConsoleSurface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(38.dp).alpha(if (enabled) 1f else 0.4f),
        shape = CircleShape,
        focusScale = 1.12f
    ) {
        Icon(icon, null, Modifier.align(Alignment.Center).size(20.dp))
    }
}
