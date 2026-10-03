package com.gamepadlayout.app.games

import kotlin.math.min

/** Superfície de desenho mínima: os jogos só dependem disto (fácil de testar e sem Android). */
interface Gfx {
    val width: Float
    val height: Float
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Long)
    fun circle(cx: Float, cy: Float, r: Float, color: Long)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Long)
    fun text(s: String, x: Float, y: Float, size: Float, color: Long, center: Boolean)
}

/** Entrada já normalizada (controle ou toque) entregue a cada quadro. */
class GameInput {
    var dx = 0f
    var dy = 0f
    var turnX = 0f
    var fire = false
    var firePressed = false
    var strafeLeft = false
    var strafeRight = false

    /** Passos discretos (-1, 0, 1) disparados uma vez por aperto; usados em jogos de grade. */
    var stepX = 0
    var stepY = 0

    fun clearEdges() {
        firePressed = false
        stepX = 0
        stepY = 0
    }
}

/** Mapeia uma área lógica (ex.: 800x450) para a tela, mantendo a proporção. */
class View(val g: Gfx, val lw: Float, val lh: Float) {
    val s: Float = min(g.width / lw, g.height / lh)
    val ox: Float = (g.width - lw * s) / 2f
    val oy: Float = (g.height - lh * s) / 2f

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Long) =
        g.rect(ox + x * s, oy + y * s, w * s, h * s, color)

    fun circle(cx: Float, cy: Float, r: Float, color: Long) =
        g.circle(ox + cx * s, oy + cy * s, r * s, color)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Long) =
        g.line(ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, w * s, color)

    fun text(t: String, x: Float, y: Float, size: Float, color: Long, center: Boolean = true) =
        g.text(t, ox + x * s, oy + y * s, size * s, color, center)

    /** Fundo que cobre a tela inteira (inclusive as faixas fora da área lógica). */
    fun clear(color: Long) = g.rect(0f, 0f, g.width, g.height, color)
}

object C {
    const val WHITE = 0xFFFFFFFFL
    const val BLACK = 0xFF000000L
    const val PURPLE = 0xFF7B4DFFL
    const val LILAC = 0xFFC9B8FFL
    const val BG = 0xFF0B0830L
    const val RED = 0xFFE53935L
    const val GREEN = 0xFF43A047L
    const val YELLOW = 0xFFFFD54FL
    const val ORANGE = 0xFFFF9800L
    const val BLUE = 0xFF42A5F5L
    const val GRAY = 0xFF9E9E9EL

    /** Escurece/clareia uma cor ARGB (f < 1 escurece). */
    fun shade(c: Long, f: Float): Long {
        val r = (((c shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
        val gr = (((c shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
        return 0xFF000000L or (r.toLong() shl 16) or (gr.toLong() shl 8) or b.toLong()
    }

    fun alpha(c: Long, a: Float): Long {
        val al = (a * 255f).toInt().coerceIn(0, 255)
        return (c and 0x00FFFFFFL) or (al.toLong() shl 24)
    }
}

abstract class MiniGame(
    val id: String,
    val title: String,
    val description: String,
    val controls: String
) {
    var score = 0
    var best = 0
    var over = false

    abstract fun reset()
    abstract fun update(dt: Float, input: GameInput)
    abstract fun draw(g: Gfx)

    protected fun finish() {
        over = true
        if (score > best) best = score
    }

    fun drawOverlay(g: Gfx) {
        val v = View(g, 800f, 450f)
        g.rect(0f, 0f, g.width, g.height, C.alpha(C.BLACK, 0.6f))
        v.rect(150f, 140f, 500f, 170f, P.INK)
        v.rect(150f, 140f, 500f, 4f, P.VIOLET)
        v.rect(150f, 306f, 500f, 4f, P.VIOLET)
        v.pixText("FIM DE JOGO", 400f, 160f, 6f, P.YELLOW, center = true)
        v.pixText("PONTOS $score", 400f, 218f, 3.2f, P.WHITE, center = true)
        v.pixText("RECORDE $best", 400f, 248f, 2.6f, P.LILAC, center = true)
        v.pixText("A OU TOQUE PARA JOGAR", 400f, 282f, 2.2f, P.CYAN, center = true, shadow = false)
    }
}
