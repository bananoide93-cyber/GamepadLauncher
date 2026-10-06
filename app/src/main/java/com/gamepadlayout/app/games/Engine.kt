package com.gamepadlayout.app.games

import kotlin.math.min

/** Opções globais de renderização 3D (o jogo de aventura grava aqui; o app lê). */
object GfxFlags {
    /** true = desenha triângulo por triângulo (mais compatível); false = lote único com drawVertices (mais rápido). */
    @Volatile var compat3d: Boolean = false
}

/** Superfície de desenho mínima: os jogos só dependem disto (fácil de testar e sem Android). */
interface Gfx {
    val width: Float
    val height: Float
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Long)
    fun circle(cx: Float, cy: Float, r: Float, color: Long)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Long)
    fun text(s: String, x: Float, y: Float, size: Float, color: Long, center: Boolean)

    /**
     * Lote de triângulos já em ordem de pintura (do mais longe ao mais perto): pos = x1,y1,x2,y2,x3,y3 por triângulo,
     * colors = ARGB. O Compose sobrescreve com uma única chamada de desenho (muito mais rápido que um Path por triângulo).
     */
    fun triBatch(pos: FloatArray, colors: IntArray, n: Int) {
        for (i in 0 until n) {
            val o = i * 6
            tri(pos[o], pos[o + 1], pos[o + 2], pos[o + 3], pos[o + 4], pos[o + 5], colors[i].toLong() and 0xFFFFFFFFL)
        }
    }

    /** Triângulo preenchido (jogos 3D). A implementação padrão usa faixas horizontais; o Compose sobrescreve com Path. */
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Long) {
        val top = min(y1, min(y2, y3)); val bot = maxOf(y1, y2, y3)
        var y = top
        while (y <= bot) {
            var xl = Float.MAX_VALUE; var xr = -Float.MAX_VALUE
            fun edge(ax: Float, ay: Float, bx: Float, by: Float) {
                if ((y < minOf(ay, by)) || (y > maxOf(ay, by)) || ay == by) return
                val t = (y - ay) / (by - ay)
                val x = ax + (bx - ax) * t
                if (x < xl) xl = x
                if (x > xr) xr = x
            }
            edge(x1, y1, x2, y2); edge(x2, y2, x3, y3); edge(x3, y3, x1, y1)
            if (xr >= xl) rect(xl, y, xr - xl + 1f, 1.2f, color)
            y += 1f
        }
    }
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

    /** Botão secundário (X ou Y) e o aperto único dele. */
    var alt = false
    var altPressed = false

    /** Passos discretos (-1, 0, 1) disparados uma vez por aperto; usados em jogos de grade. */
    var stepX = 0
    var stepY = 0

    /** Analógico direito, eixo vertical (câmera). */
    var turnY = 0f

    /** Botões individuais (ver [Btn]): segurado, aperto e soltura neste quadro. */
    val bHeld = BooleanArray(Btn.COUNT)
    val bPressed = BooleanArray(Btn.COUNT)
    val bReleased = BooleanArray(Btn.COUNT)

    /** Recebe o estado atual dos botões e calcula os eventos de aperto/soltura. */
    fun setButtons(now: BooleanArray) {
        for (i in 0 until Btn.COUNT) {
            if (now[i] && !bHeld[i]) bPressed[i] = true
            if (!now[i] && bHeld[i]) bReleased[i] = true
            bHeld[i] = now[i]
        }
    }

    fun clearEdges() {
        firePressed = false
        altPressed = false
        stepX = 0
        stepY = 0
        for (i in 0 until Btn.COUNT) { bPressed[i] = false; bReleased[i] = false }
    }
}

/** Identificadores dos botões físicos entregues aos jogos (B e START ficam reservados para a pausa do app). */
object Btn {
    const val A = 0
    const val X = 1
    const val Y = 2
    const val L1 = 3
    const val R1 = 4
    const val L2 = 5
    const val R2 = 6
    const val L3 = 7
    const val R3 = 8
    const val SELECT = 9
    const val DUP = 10
    const val DDOWN = 11
    const val DLEFT = 12
    const val DRIGHT = 13
    const val COUNT = 14
    val NAMES = arrayOf("A", "X", "Y", "L1", "R1", "L2", "R2", "L3", "R3", "SELECT", "CIMA", "BAIXO", "ESQ", "DIR")
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

    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Long) =
        g.tri(ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, ox + x3 * s, oy + y3 * s, color)

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

    /** Jogos com progresso salvo (ex.: coleção de monstros): o app grava [save] quando [saveDirty] é true. */
    open val persistent: Boolean = false
    var saveDirty = false
    open fun save(): String? = null
    open fun load(data: String) {}

    /** Jogos com som sintetizado: o app esvazia [sfxQueue] a cada quadro e toca [musicId] (-1 = silêncio). */
    open val usesAudio: Boolean = false
    val sfxQueue = java.util.concurrent.ConcurrentLinkedQueue<Int>()
    @Volatile var musicId: Int = -1
    @Volatile var sfxVolume: Float = 1f
    @Volatile var musicVolume: Float = 1f

    /** Efeito de monitor antigo por cima da imagem (jogos 3D desligam). */
    open val crtEffect: Boolean = true

    /** true = o D-pad e lido como botoes (Btn.DUP...) e nao move o personagem. */
    open val dpadButtons: Boolean = false

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
