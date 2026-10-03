package com.gamepadlayout.app.games

import java.text.Normalizer
import kotlin.math.min

/** Paleta retrô de 16 cores (original, inspirada em consoles de 8/16 bits). */
object P {
    const val INK = 0xFF1A1C2CL
    const val PLUM = 0xFF5D275DL
    const val RED = 0xFFB13E53L
    const val ORANGE = 0xFFEF7D57L
    const val YELLOW = 0xFFFFCD75L
    const val LIME = 0xFFA7F070L
    const val GREEN = 0xFF38B764L
    const val TEAL = 0xFF257179L
    const val NAVY = 0xFF29366FL
    const val BLUE = 0xFF3B5DC9L
    const val SKY = 0xFF41A6F6L
    const val CYAN = 0xFF73EFF7L
    const val WHITE = 0xFFF4F4F4L
    const val SILVER = 0xFF94B0C2L
    const val SLATE = 0xFF566C86L
    const val DUSK = 0xFF333C57L
    const val VIOLET = 0xFF7B4DFFL
    const val LILAC = 0xFFC9B8FFL

    /** Mistura [c] com branco (f de 0 a 1). */
    fun light(c: Long, f: Float): Long {
        val r = ((c shr 16) and 0xFF).toInt()
        val g = ((c shr 8) and 0xFF).toInt()
        val b = (c and 0xFF).toInt()
        fun m(v: Int) = (v + (255 - v) * f).toInt().coerceIn(0, 255)
        return 0xFF000000L or (m(r).toLong() shl 16) or (m(g).toLong() shl 8) or m(b).toLong()
    }
}

/**
 * Sprite em pixel art. '.' é transparente. 'T' = cor de tinta, 'S' = tinta escura, 'H' = tinta clara.
 */
class Sprite(rows: List<String>, private val pal: Map<Char, Long>) {
    val w: Int = rows.maxOf { it.length }
    val h: Int = rows.size
    private val runX = ArrayList<Int>()
    private val runY = ArrayList<Int>()
    private val runL = ArrayList<Int>()
    private val runC = ArrayList<Char>()

    /** Por coluna: pares (y, tamanho) + caractere, para raycasting. */
    private val colY = Array(w) { ArrayList<Int>() }
    private val colL = Array(w) { ArrayList<Int>() }
    private val colC = Array(w) { ArrayList<Char>() }

    init {
        for (y in rows.indices) {
            val r = rows[y]
            var x = 0
            while (x < r.length) {
                val ch = r[x]
                if (ch == '.' || ch == ' ') { x++; continue }
                var e = x
                while (e + 1 < r.length && r[e + 1] == ch) e++
                runX.add(x); runY.add(y); runL.add(e - x + 1); runC.add(ch)
                x = e + 1
            }
        }
        for (x in 0 until w) {
            var y = 0
            while (y < h) {
                val ch = rows[y].getOrElse(x) { '.' }
                if (ch == '.' || ch == ' ') { y++; continue }
                var e = y
                while (e + 1 < h && rows[e + 1].getOrElse(x) { '.' } == ch) e++
                colY[x].add(y); colL[x].add(e - y + 1); colC[x].add(ch)
                y = e + 1
            }
        }
    }

    fun colorOf(ch: Char, tint: Long): Long = when (ch) {
        'T' -> tint
        'S' -> C.shade(tint, 0.62f)
        'H' -> P.light(tint, 0.4f)
        else -> pal[ch] ?: tint
    }

    fun draw(v: View, x: Float, y: Float, px: Float, flip: Boolean = false, tint: Long = P.WHITE) {
        for (i in runX.indices) {
            val rx = if (flip) w - runX[i] - runL[i] else runX[i]
            v.rect(x + rx * px, y + runY[i] * px, runL[i] * px + 0.4f, px + 0.4f, colorOf(runC[i], tint))
        }
    }

    /** Desenha uma coluna do sprite (u de 0 a 1) esticada em [top, top+hh) com sombreamento [f]. */
    fun drawColumn(g: Gfx, u: Float, sx: Float, sw: Float, top: Float, hh: Float, f: Float, flash: Boolean) {
        val cx = (u * w).toInt().coerceIn(0, w - 1)
        val ph = hh / h
        for (i in colY[cx].indices) {
            val c = if (flash) 0xFFFFFFFFL else C.shade(colorOf(colC[cx][i], P.WHITE), f)
            g.rect(sx, top + colY[cx][i] * ph, sw, colL[cx][i] * ph + 1f, c)
        }
    }
}

/** Fonte pixelada 5x7 (maiúsculas, números e pontuação básica). */
object PixelFont {
    private val raw = mapOf(
        'A' to ".###. #...# #...# ##### #...# #...# #...#",
        'B' to "####. #...# #...# ####. #...# #...# ####.",
        'C' to ".#### #.... #.... #.... #.... #.... .####",
        'D' to "####. #...# #...# #...# #...# #...# ####.",
        'E' to "##### #.... #.... ####. #.... #.... #####",
        'F' to "##### #.... #.... ####. #.... #.... #....",
        'G' to ".#### #.... #.... #..## #...# #...# .###.",
        'H' to "#...# #...# #...# ##### #...# #...# #...#",
        'I' to "##### ..#.. ..#.. ..#.. ..#.. ..#.. #####",
        'J' to "..### ...#. ...#. ...#. ...#. #..#. .##..",
        'K' to "#...# #..#. #.#.. ##... #.#.. #..#. #...#",
        'L' to "#.... #.... #.... #.... #.... #.... #####",
        'M' to "#...# ##.## #.#.# #.#.# #...# #...# #...#",
        'N' to "#...# ##..# #.#.# #..## #...# #...# #...#",
        'O' to ".###. #...# #...# #...# #...# #...# .###.",
        'P' to "####. #...# #...# ####. #.... #.... #....",
        'Q' to ".###. #...# #...# #...# #.#.# #..#. .##.#",
        'R' to "####. #...# #...# ####. #.#.. #..#. #...#",
        'S' to ".#### #.... #.... .###. ....# ....# ####.",
        'T' to "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
        'U' to "#...# #...# #...# #...# #...# #...# .###.",
        'V' to "#...# #...# #...# #...# #...# .#.#. ..#..",
        'W' to "#...# #...# #...# #.#.# #.#.# ##.## #...#",
        'X' to "#...# #...# .#.#. ..#.. .#.#. #...# #...#",
        'Y' to "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..",
        'Z' to "##### ....# ...#. ..#.. .#... #.... #####",
        '0' to ".###. #...# #..## #.#.# ##..# #...# .###.",
        '1' to "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
        '2' to ".###. #...# ....# ...#. ..#.. .#... #####",
        '3' to "####. ....# ....# .###. ....# ....# ####.",
        '4' to "...#. ..##. .#.#. #..#. ##### ...#. ...#.",
        '5' to "##### #.... ####. ....# ....# #...# .###.",
        '6' to ".###. #.... #.... ####. #...# #...# .###.",
        '7' to "##### ....# ...#. ..#.. .#... .#... .#...",
        '8' to ".###. #...# #...# .###. #...# #...# .###.",
        '9' to ".###. #...# #...# .#### ....# ....# .###.",
        ':' to "..... ..#.. ..#.. ..... ..#.. ..#.. .....",
        '.' to "..... ..... ..... ..... ..... .##.. .##..",
        ',' to "..... ..... ..... ..... ..#.. ..#.. .#...",
        '!' to "..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..",
        '?' to ".###. #...# ....# ...#. ..#.. ..... ..#..",
        '-' to "..... ..... ..... .###. ..... ..... .....",
        '+' to "..... ..#.. ..#.. ##### ..#.. ..#.. .....",
        '/' to "....# ....# ...#. ..#.. .#... #.... #....",
        '(' to "...#. ..#.. .#... .#... .#... ..#.. ...#.",
        ')' to ".#... ..#.. ...#. ...#. ...#. ..#.. .#...",
        '%' to "##..# ##..# ...#. ..#.. .#... #..## #..##",
        '\'' to "..#.. ..#.. .#... ..... ..... ..... .....",
        '>' to "#.... .#... ..#.. ...#. ..#.. .#... #....",
        '<' to "....# ...#. ..#.. .#... ..#.. ...#. ....#",
        '=' to "..... ..... ##### ..... ##### ..... ....."
    )

    private val cache = HashMap<Char, List<IntArray>>()

    private fun runs(ch: Char): List<IntArray> = cache.getOrPut(ch) {
        val rows = (raw[ch] ?: return@getOrPut emptyList()).split(' ')
        val out = ArrayList<IntArray>()
        for (y in rows.indices) {
            var x = 0
            val r = rows[y]
            while (x < r.length) {
                if (r[x] != '#') { x++; continue }
                var e = x
                while (e + 1 < r.length && r[e + 1] == '#') e++
                out.add(intArrayOf(x, y, e - x + 1))
                x = e + 1
            }
        }
        out
    }

    /** Remove acentos e padroniza símbolos para caber na fonte. */
    fun clean(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return n.uppercase().replace('·', '-').replace('—', '-').replace('–', '-')
    }

    fun width(t: String, px: Float): Float = if (t.isEmpty()) 0f else (t.length * 6 - 1) * px

    fun draw(v: View, text: String, x: Float, y: Float, px: Float, color: Long, center: Boolean, shadow: Boolean) {
        val t = clean(text)
        val ox = if (center) x - width(t, px) / 2f else x
        if (shadow) drawRuns(v, t, ox + px, y + px, px, C.alpha(C.BLACK, 0.7f))
        drawRuns(v, t, ox, y, px, color)
    }

    private fun drawRuns(v: View, t: String, x0: Float, y: Float, px: Float, color: Long) {
        var ox = x0
        for (ch in t) {
            for (r in runs(ch)) v.rect(ox + r[0] * px, y + r[1] * px, r[2] * px + 0.3f, px + 0.3f, color)
            ox += 6 * px
        }
    }
}

fun View.pixText(t: String, x: Float, y: Float, px: Float, color: Long = P.WHITE, center: Boolean = false, shadow: Boolean = true) =
    PixelFont.draw(this, t, x, y, px, color, center, shadow)

/** Retângulo com relevo (luz em cima/esquerda, sombra embaixo/direita). */
fun View.bevel(x: Float, y: Float, w: Float, h: Float, color: Long, edge: Float = 2f) {
    rect(x, y, w, h, color)
    rect(x, y, w, edge, P.light(color, 0.45f))
    rect(x, y, edge, h, P.light(color, 0.3f))
    rect(x, y + h - edge, w, edge, C.shade(color, 0.55f))
    rect(x + w - edge, y, edge, h, C.shade(color, 0.7f))
}

/** Disco "pixelado" feito de faixas horizontais. */
fun View.pixDisc(cx: Float, cy: Float, r: Float, color: Long, step: Float = 4f) {
    var dy = -r
    while (dy < r) {
        val my = dy + step / 2f
        val half = kotlin.math.sqrt((r * r - my * my).coerceAtLeast(0f))
        rect(cx - half, cy + dy, half * 2f, step, color)
        dy += step
    }
}

/** Barra superior (HUD) comum aos jogos de tela lateral. */
fun View.hudBar(left: String, right: String) {
    rect(0f, 0f, lw, 28f, C.alpha(P.INK, 0.85f))
    rect(0f, 28f, lw, 2f, P.VIOLET)
    pixText(left, 10f, 7f, 2.5f, P.WHITE)
    pixText(right, lw - 10f - PixelFont.width(PixelFont.clean(right), 2.5f), 7f, 2.5f, P.LILAC)
}

object Retro {
    /** Efeito de monitor antigo: linhas de varredura + cantos escurecidos. Barato e sem imagens. */
    fun crt(g: Gfx) {
        val w = g.width
        val h = g.height
        if (w <= 0f || h <= 0f) return
        val step = (h / 150f).coerceAtLeast(2.5f)
        val line = C.alpha(C.BLACK, 0.10f)
        var y = 0f
        while (y < h) {
            g.rect(0f, y, w, step * 0.4f, line)
            y += step
        }
        val b = min(w, h) * 0.06f
        val shade = C.alpha(C.BLACK, 0.07f)
        for (i in 1..5) {
            val t = b * i / 5f
            g.rect(0f, 0f, w, t, shade)
            g.rect(0f, h - t, w, t, shade)
            g.rect(0f, 0f, t, h, shade)
            g.rect(w - t, 0f, t, h, shade)
        }
    }
}
