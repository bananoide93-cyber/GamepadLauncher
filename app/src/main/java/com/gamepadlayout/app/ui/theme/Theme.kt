package com.gamepadlayout.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.data.ThemeMode

@Immutable
data class ConsoleStyle(
    val accent: Color,
    val accentSoft: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val effects: Float,
    val transparency: Float,
    val animations: Boolean,
    val animSpeed: Float,
    val iconScale: Float
) {
    /** Cor dos cards: quanto maior a transparência, mais translúcido. */
    val cardColor: Color get() = Color.White.copy(alpha = 0.18f - 0.14f * transparency)

    fun dur(ms: Int): Int = if (!animations) 0 else (ms / animSpeed).toInt().coerceAtLeast(1)
}

fun buildStyle(s: AppSettings): ConsoleStyle {
    val (top, bottom, accent) = when (s.theme) {
        ThemeMode.PURPLE -> Triple(Color(0xFF2A0F8F), Color(0xFF0B0830), Color(0xFF7B4DFF))
        ThemeMode.DARK -> Triple(Color(0xFF1E1633), Color(0xFF0C0A14), Color(0xFF8B5CF6))
        ThemeMode.BLACK -> Triple(Color(0xFF0A0A0C), Color(0xFF000000), Color(0xFF9D6BFF))
    }
    return ConsoleStyle(
        accent = accent,
        accentSoft = Color(0xFFC9B8FF),
        bgTop = top,
        bgBottom = bottom,
        effects = if (s.powerSaver) minOf(s.effects, 0.25f) else s.effects,
        transparency = s.transparency,
        animations = s.animations,
        animSpeed = s.animSpeed,
        iconScale = s.iconScale
    )
}

val LocalConsoleStyle = staticCompositionLocalOf { buildStyle(AppSettings()) }

@Composable
fun GamepadLayoutTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val style = remember(settings) { buildStyle(settings) }
    val scheme = darkColorScheme(
        primary = style.accent,
        onPrimary = Color.White,
        secondary = style.accentSoft,
        background = style.bgBottom,
        surface = style.bgBottom,
        onSurface = Color.White,
        onBackground = Color.White
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalConsoleStyle provides style,
            LocalContentColor provides Color.White
        ) {
            content()
        }
    }
}
