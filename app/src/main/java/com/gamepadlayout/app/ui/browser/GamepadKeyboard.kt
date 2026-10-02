package com.gamepadlayout.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepadlayout.app.input.GamepadInput
import com.gamepadlayout.app.input.PadButton
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle
import kotlinx.coroutines.delay

/**
 * Teclado na tela feito para controle: D-pad/analógico move entre as teclas, A digita.
 * Atalhos: X apaga · Y espaço · R1 maiúscula · L1 troca 123/ABC · START envia · B fecha.
 */
@Composable
fun GamepadKeyboard(
    title: String,
    preview: String,
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val st = LocalConsoleStyle.current
    var symbols by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val charCb by rememberUpdatedState(onChar)
    val backCb by rememberUpdatedState(onBackspace)
    val submitCb by rememberUpdatedState(onSubmit)

    fun type(s: String) {
        charCb(s)
        if (shift) shift = false
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(Unit) {
        GamepadInput.rawButtons.collect { b ->
            when (b) {
                PadButton.X -> backCb()
                PadButton.Y -> type(" ")
                PadButton.R1 -> shift = !shift
                PadButton.L1 -> {
                    symbols = !symbols
                    delay(80)
                    runCatching { first.requestFocus() }
                }
                PadButton.START -> submitCb()
                else -> {}
            }
        }
    }

    val rows: List<String> = if (!symbols) {
        listOf("qwertyuiop", "asdfghjklç", "zxcvbnm")
    } else {
        listOf("!?@#\$%&*+=", "-_()/:;'\"~", "áàâãéêíóôõúü")
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xF00B0830))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "$title: $preview▏",
            fontSize = 13.sp,
            color = st.accentSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            "1234567890".forEach { c -> Key(c.toString()) { type(c.toString()) } }
        }
        rows.forEachIndexed { idx, r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (idx == 2 && !symbols) Key(if (shift) "⇧•" else "⇧", 1.4f) { shift = !shift }
                r.forEachIndexed { i, c ->
                    val s = if (shift && !symbols) c.uppercase() else c.toString()
                    Key(s, requester = if (idx == 0 && i == 0) first else null) { type(s) }
                }
                if (idx == 2) Key("⌫", 1.4f) { backCb() }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Key(if (symbols) "ABC" else "123", 1.3f) { symbols = !symbols }
            Key(",") { type(",") }
            Key("Espaço", 4f) { type(" ") }
            Key(".") { type(".") }
            Key("/") { type("/") }
            Key(".com", 1.4f) { type(".com") }
            Key("Ir ↵", 1.6f) { submitCb() }
            Key("✕") { onClose() }
        }
        Text(
            "X apaga · Y espaço · R1 maiúscula · L1 123/ABC · START envia · B fecha",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float = 1f,
    requester: FocusRequester? = null,
    onClick: () -> Unit
) {
    ConsoleSurface(
        onClick = onClick,
        modifier = Modifier.weight(weight).height(30.dp),
        shape = RoundedCornerShape(8.dp),
        focusScale = 1.08f,
        focusRequester = requester
    ) {
        Text(label, Modifier.align(Alignment.Center), fontSize = 14.sp, maxLines = 1)
    }
}
