package com.gamepadlayout.app.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Papéis de parede: dois desenhados por código (leves) e uma foto escolhida pelo jogador. */
object Wallpapers {
    val presets = listOf(
        "default" to "Roxo clássico",
        "synth" to "Synthwave retrô",
        "stars" to "Céu estrelado",
        "custom" to "Minha foto"
    )

    fun file(c: Context) = File(c.filesDir, "wallpaper.jpg")

    /** Copia a imagem escolhida, reduzindo para no máximo 1920 px (economiza memória). */
    suspend fun import(c: Context, uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching false
            file(c).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }.getOrDefault(false)
    }

    suspend fun load(c: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(c)
            if (!f.exists()) null else BitmapFactory.decodeFile(f.path)?.asImageBitmap()
        }.getOrNull()
    }

    /** Preenche a tela inteira mantendo a proporção (corta o excesso). */
    fun drawCover(scope: DrawScope, img: ImageBitmap) = with(scope) {
        val s = max(size.width / img.width, size.height / img.height)
        val dw = (img.width * s).toInt()
        val dh = (img.height * s).toInt()
        drawImage(
            img,
            dstOffset = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
            dstSize = IntSize(dw, dh)
        )
    }

    fun drawSynth(scope: DrawScope) = with(scope) {
        val w = size.width
        val h = size.height
        val hz = h * 0.58f
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF14083A), Color(0xFF5B1B8F), Color(0xFFD1428B), Color(0xFFFF9A5A)),
                startY = 0f, endY = hz
            ),
            size = Size(w, hz)
        )
        // sol em faixas
        val r = h * 0.2f
        var y = hz - r * 1.9f
        var i = 0
        while (y < hz) {
            val dy = (y + 2f) - (hz - r * 0.95f)
            val half = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
            if (half > 0f && !(i > 3 && i % 2 == 0)) {
                drawRect(Color(0xFFFFD46A), Offset(w * 0.5f - half, y), Size(half * 2, 5f))
            }
            y += 6f; i++
        }
        drawRect(Color(0xFF0B0830), Offset(0f, hz), Size(w, h - hz))
        // grade em perspectiva
        val line = Color(0xFFB36BFF).copy(alpha = 0.55f)
        for (k in -12..12) {
            drawLine(line, Offset(w * 0.5f, hz), Offset(w * 0.5f + k * w * 0.16f, h), 1.5f)
        }
        var gy = hz
        var step = 4f
        while (gy < h) {
            drawLine(line, Offset(0f, gy), Offset(w, gy), 1.5f)
            gy += step
            step *= 1.28f
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFF6AB5).copy(alpha = 0.35f), Color.Transparent), startY = hz, endY = hz + h * 0.1f), Offset(0f, hz), Size(w, h * 0.1f))
    }

    fun drawStars(scope: DrawScope) = with(scope) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF05061E), Color(0xFF1B1058), Color(0xFF3A1E7A))))
        var seed = 12345L
        fun rnd(): Float {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return (seed % 10000) / 10000f
        }
        for (i in 0 until 160) {
            val x = rnd() * w
            val y = rnd() * h * 0.9f
            val big = rnd() > 0.9f
            val a = 0.35f + rnd() * 0.65f
            val sz = if (big) 4f else 2f
            drawRect(Color.White.copy(alpha = a), Offset(x, y), Size(sz, sz))
            if (big) {
                drawRect(Color.White.copy(alpha = a * 0.5f), Offset(x - 4f, y + 1f), Size(12f, 2f))
                drawRect(Color.White.copy(alpha = a * 0.5f), Offset(x + 1f, y - 4f), Size(2f, 12f))
            }
        }
        drawCircle(Color(0xFFC9B8FF).copy(alpha = 0.9f), h * 0.1f, Offset(w * 0.82f, h * 0.2f))
        drawCircle(Color(0xFF05061E).copy(alpha = 0.35f), h * 0.1f, Offset(w * 0.82f + h * 0.035f, h * 0.2f - h * 0.01f))
    }
}
