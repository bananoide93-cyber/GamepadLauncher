package com.gamepadlayout.app.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Mini-estúdio de pixel art: desenha formas numa grade, aplica sombreado e contorno
 * automáticos e gera um sprite pronto para o [View].
 *
 * Índices de cor: 1 contorno, 2 corpo, 3 corpo escuro, 4 corpo claro, 5 barriga, 6 branco,
 * 7 preto, 8 destaque, 9 destaque claro, 10 dourado, 11 destaque escuro, 12 extra.
 */
class Cv(val w: Int, val h: Int) {
    val g = Array(h) { IntArray(w) }

    fun p(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) g[y][x] = c }
    fun get(x: Int, y: Int) = if (x in 0 until w && y in 0 until h) g[y][x] else 0

    fun ell(cx: Float, cy: Float, rx: Float, ry: Float, c: Int, onlyOn: Int = -1) {
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / rx; val dy = (y - cy) / ry
            if (dx * dx + dy * dy <= 1f && (onlyOn < 0 || g[y][x] == onlyOn)) g[y][x] = c
        }
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        for (y in min(y0, y1)..max(y0, y1)) for (x in min(x0, x1)..max(x0, x1)) p(x, y, c)
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            p(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Pinta [c] só onde há corpo (índice 2). */
    fun paintBody(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        for (y in y0..y1) for (x in x0..x1) if (get(x, y) == 2) p(x, y, c)
    }

    fun eye(x: Int, y: Int, glow: Int = 0) {
        if (glow > 0) { p(x, y, glow); p(x - 1, y, glow) }
        else { p(x, y, 6); p(x - 1, y, 7) }
    }

    /** Sombreado (luz de cima) + contorno. */
    fun finish() {
        for (y in 0 until h) for (x in 0 until w) if (g[y][x] == 2) {
            val up = get(x, y - 1); val dn = get(x, y + 1)
            if (up == 0) g[y][x] = 4 else if (dn == 0) g[y][x] = 3
        }
        val o = Array(h) { IntArray(w) }
        for (y in 0 until h) for (x in 0 until w) if (g[y][x] == 0) {
            if (get(x - 1, y) != 0 || get(x + 1, y) != 0 || get(x, y - 1) != 0 || get(x, y + 1) != 0) o[y][x] = 1
        }
        for (y in 0 until h) for (x in 0 until w) if (o[y][x] == 1) g[y][x] = 1
    }

    fun toSpr(pal: LongArray): Spr {
        val runs = ArrayList<IntArray>()
        for (y in 0 until h) {
            var x = 0
            while (x < w) {
                val c = g[y][x]
                if (c == 0) { x++; continue }
                var e = x
                while (e + 1 < w && g[y][e + 1] == c) e++
                runs.add(intArrayOf(x, y, e - x + 1, c))
                x = e + 1
            }
        }
        return Spr(w, h, runs, pal)
    }
}

class Spr(val w: Int, val h: Int, val runs: List<IntArray>, val pal: LongArray) {
    fun draw(v: View, x: Float, y: Float, px: Float, flip: Boolean = false, flash: Boolean = false, silhouette: Long = 0L) {
        for (r in runs) {
            val rx = if (flip) w - r[0] - r[2] else r[0]
            val c = when {
                flash -> P.WHITE
                silhouette != 0L -> silhouette
                else -> pal[r[3]]
            }
            v.rect(x + rx * px, y + r[1] * px, r[2] * px + 0.4f, px + 0.4f, c)
        }
    }
}

fun makePal(body: Long, accent: Long, accentLight: Long, accentDark: Long, extra: Long = P.SILVER): LongArray {
    val pal = LongArray(13)
    pal[1] = C.shade(body, 0.28f)
    pal[2] = body
    pal[3] = C.shade(body, 0.68f)
    pal[4] = P.light(body, 0.3f)
    pal[5] = P.light(body, 0.62f)
    pal[6] = P.WHITE
    pal[7] = P.INK
    pal[8] = accent
    pal[9] = accentLight
    pal[10] = P.YELLOW
    pal[11] = accentDark
    pal[12] = extra
    return pal
}

/** Criaturas dos monstros: 18 corpos-base, adornos por tipo e paletas por espécie. */
object MonArt {
    private class Anchor(val cx: Int, val cy: Int, val cw: Int, val tx: Int, val ty: Int)

    // tipos: 0 planta, 1 fogo, 2 água, 3 terra, 4 sombra, 5 raio
    private val bodyPals = arrayOf(
        longArrayOf(0xFF5FB04AL, 0xFF3F9A7AL, 0xFF9C6B3CL),
        longArrayOf(0xFFC8452FL, 0xFFE8892BL, 0xFF5A3B38L),
        longArrayOf(0xFF3E8EDCL, 0xFF4FC3D9L, 0xFF3A56B0L),
        longArrayOf(0xFFB08D57L, 0xFF8C7A6BL, 0xFFA0522DL),
        longArrayOf(0xFF6B49C9L, 0xFF3F3A66L, 0xFF8E4FA8L),
        longArrayOf(0xFFE8C53AL, 0xFFF29E2EL, 0xFF7C8CE8L)
    )
    private val acc = arrayOf(
        longArrayOf(0xFF2FA84FL, 0xFFB6F27AL, 0xFF1F7A3AL),
        longArrayOf(0xFFFFB02EL, 0xFFFFF0A0L, 0xFFE8531FL),
        longArrayOf(0xFF9BE6FFL, 0xFFFFFFFFL, 0xFF2A74C8L),
        longArrayOf(0xFF9AA0A6L, 0xFFD0D4D8L, 0xFF5F656BL),
        longArrayOf(0xFFB36BFFL, 0xFFF0C8FFL, 0xFF6A35B0L),
        longArrayOf(0xFFFFF35CL, 0xFFFFFFFFL, 0xFFC7A21AL)
    )

    fun palFor(type: Int, pal: Int): LongArray {
        val a = acc[type]
        return makePal(bodyPals[type][pal % 3], a[0], a[1], a[2])
    }

    // ---- adornos de elemento
    private fun crest(c: Cv, type: Int, x: Int, y: Int, w: Int, rnd: Random) {
        val maxH = y - 1
        if (maxH < 1 || w <= 0) return
        when (type) {
            1 -> { // chamas
                var i = 0
                while (i < w) {
                    val hh = min(maxH, 2 + rnd.nextInt(3))
                    for (k in 1..hh) { c.p(x + i, y - k, if (k == hh) 9 else if (k > 1) 8 else 11); if (k < hh && i + 1 < w) c.p(x + i + 1, y - k, if (k == 1) 11 else 8) }
                    i += 2
                }
            }
            0 -> { // folhas
                var i = 0
                while (i < w) {
                    val hh = min(maxH, 3)
                    c.ell((x + i + 1).toFloat(), (y - hh / 2f - 0.5f), 1.4f, hh / 2f + 0.6f, 8)
                    c.p(x + i + 1, y - 1, 11); c.p(x + i + 1, y - hh, 9)
                    i += 3
                }
            }
            2 -> { // barbatanas/ondas
                for (i in 0 until w) { val hh = min(maxH, if (i % 2 == 0) 3 else 2); for (k in 1..hh) c.p(x + i, y - k, if (k == hh) 9 else 8) }
            }
            3 -> { // pedras
                var i = 0
                while (i < w) {
                    val hh = min(maxH, 2 + rnd.nextInt(2))
                    for (k in 1..hh) { for (d in 0 until (hh - k + 1)) { c.p(x + i + d, y - k, if (d == 0) 9 else 8) } }
                    i += 3
                }
            }
            4 -> { // fumaça sombria
                for (i in 0 until w) {
                    val hh = min(maxH, 1 + (i * 7 + 3) % 4)
                    for (k in 1..hh) if ((k + i) % 2 == 0 || k == hh) c.p(x + i, y - k, if (k == hh) 9 else 8)
                }
            }
            5 -> { // raios
                var i = 0
                while (i < w) {
                    val hh = min(maxH, 4)
                    for (k in 1..hh) c.p(x + i + (k % 2), y - k, if (k == hh) 9 else 8)
                    i += 3
                }
            }
        }
    }

    private fun tail(c: Cv, type: Int, x: Int, y: Int) {
        when (type) {
            1 -> { c.ell(x.toFloat(), y - 1.5f, 1.6f, 2.6f, 8); c.p(x, y - 3, 9); c.p(x, y - 2, 9); c.p(x, y, 11) }
            0 -> { c.ell(x.toFloat(), y - 1f, 1.5f, 2.3f, 8); c.p(x, y - 2, 9) }
            2 -> { c.rect(x - 1, y - 2, x, y + 1, 8); c.p(x, y - 2, 9) }
            3 -> { c.rect(x - 1, y - 1, x, y, 8); c.p(x - 1, y - 1, 9); c.p(x, y - 2, 8) }
            4 -> { c.p(x, y, 8); c.p(x, y - 1, 9); c.p(x - 1, y - 2, 8); c.p(x, y - 3, 9) }
            5 -> { c.p(x, y, 8); c.p(x - 1, y - 1, 9); c.p(x, y - 2, 8); c.p(x - 1, y - 3, 9) }
        }
    }

    // ---- corpos-base (viram para a esquerda; 20x16)
    private fun boar(c: Cv, v: Int): Anchor {
        val big = v and 2 != 0
        val brx = if (big) 6.6f else 5.8f
        c.ell(11f, 9f, brx, 4.5f, 2)
        c.ell(11f, 11f, brx - 1.8f, 2f, 5, 2)
        c.ell(5f, 9.5f, 3.6f, 3.3f, 2)
        c.rect(1, 9, 3, 11, 2); c.p(1, 10, 7)
        c.p(3, 12, 6); c.p(2, 12, 6); c.p(3, 11, 6)
        c.ell(6.5f, 5.8f, 1.3f, 1.6f, 2); c.p(6, 6, 3)
        for (x in intArrayOf(6, 9, 12, 15)) { c.rect(x, 12, x + 1, 14, 2); c.p(x, 14, 3); c.p(x + 1, 14, 3) }
        if (v and 1 != 0) { c.p(6, 4, 6); c.p(5, 3, 6); c.p(7, 4, 6); c.p(4, 2, 6) }
        c.p(17, 8, 2); c.p(18, 7, 2); c.p(18, 6, 2)
        c.eye(5, 8)
        c.p(5, 6, 3); c.p(4, 6, 3)
        return Anchor(8, 5, if (big) 9 else 8, 19, 7)
    }

    private fun canine(c: Cv, v: Int): Anchor {
        c.ell(11f, 9f, 5.6f, 3.4f, 2)
        c.ell(11f, 10.6f, 4f, 1.6f, 5, 2)
        c.ell(5f, 7.5f, 3f, 2.7f, 2)
        c.rect(2, 8, 4, 9, 2); c.p(2, 8, 7)
        c.rect(4, 4, 5, 5, 2); c.rect(7, 4, 8, 5, 2); c.p(4, 3, 2); c.p(7, 3, 2)
        if (v and 1 != 0) { c.p(4, 2, 2); c.p(7, 2, 2); c.p(4, 3, 2); c.p(7, 3, 2) }
        c.p(5, 5, 5); c.p(8, 5, 5)
        c.rect(7, 11, 8, 14, 2); c.rect(10, 12, 10, 14, 2); c.rect(13, 11, 14, 14, 2); c.rect(16, 12, 16, 14, 2)
        c.ell(17.5f, 7f, 1.8f, 2.7f, 2); c.line(15, 9, 17, 8, 2)
        c.p(17, 5, 5); c.p(17, 4, 5)
        c.eye(5, 7)
        c.p(3, 9, 6)
        return Anchor(8, 6, 6, 18, 6)
    }

    private fun rabbit(c: Cv): Anchor {
        c.ell(10f, 10f, 4.6f, 3.8f, 2)
        c.ell(9f, 11.5f, 3f, 2f, 5, 2)
        c.ell(6f, 7f, 3.1f, 2.7f, 2)
        c.ell(5.5f, 3f, 1.1f, 2.9f, 2); c.ell(8.5f, 3f, 1.1f, 2.9f, 2)
        c.p(5, 3, 5); c.p(5, 4, 5); c.p(8, 3, 5); c.p(8, 4, 5)
        c.ell(6f, 14f, 2.2f, 1f, 2); c.ell(13f, 14f, 2.4f, 1f, 2)
        c.ell(15.5f, 10f, 1.4f, 1.4f, 6)
        c.rect(7, 12, 8, 13, 2)
        c.eye(5, 7)
        c.p(3, 8, 7); c.p(3, 9, 5)
        return Anchor(9, 7, 5, 16, 9)
    }

    private fun bird(c: Cv, v: Int): Anchor {
        val owl = v == 1
        val phoenix = v == 2
        c.ell(10f, 9f, if (owl) 5f else 4.8f, if (owl) 5f else 4.4f, 2)
        c.ell(8.5f, 10f, 3f, 3f, 5, 2)
        c.ell(6f, 6f, 3.3f, 3f, 2)
        if (owl) { c.p(4, 2, 2); c.p(5, 3, 2); c.p(8, 3, 2); c.p(9, 2, 2); c.ell(5.5f, 6f, 1.9f, 1.9f, 6); c.ell(8.2f, 6f, 1.6f, 1.6f, 6); c.p(5, 6, 7); c.p(8, 6, 7); c.p(7, 8, 10) }
        else { c.rect(2, 6, 3, 7, 10); c.p(2, 7, 10); c.eye(5, 5) }
        c.ell(11.5f, 9f, 3.3f, 2.8f, 3); c.line(10, 9, 14, 11, 12)
        if (phoenix) { c.line(14, 11, 19, 14, 2); c.line(14, 10, 19, 11, 2); c.line(14, 12, 18, 15, 3); c.line(15, 9, 19, 8, 3) }
        else { c.rect(15, 10, 18, 11, 3); c.line(14, 12, 18, 13, 3) }
        c.p(9, 13, 10); c.p(9, 14, 10); c.p(8, 14, 10); c.p(12, 13, 10); c.p(12, 14, 10); c.p(11, 14, 10)
        return Anchor(5, 3, 4, if (phoenix) 19 else 18, 10)
    }

    private fun bat(c: Cv): Anchor {
        c.ell(10f, 8f, 2.6f, 3.2f, 2)
        c.ell(10f, 5f, 2.2f, 1.9f, 2)
        c.p(8, 3, 2); c.p(12, 3, 2); c.p(8, 2, 2); c.p(12, 2, 2)
        for (x in 1..7) {
            val t = (x - 1) / 6f
            val y0 = 4 + (t * 3).toInt()
            val y1 = 11 - (t * 4).toInt() + (if (x % 3 == 0) 1 else 0)
            for (y in y0..y1) { c.p(x, y, 3); c.p(19 - x, y, 3) }
            c.p(x, y0, 2); c.p(19 - x, y0, 2)
        }
        c.p(9, 5, 6); c.p(11, 5, 6); c.p(9, 7, 6); c.p(11, 7, 6)
        c.p(10, 5, 5)
        c.p(9, 11, 2); c.p(11, 11, 2); c.p(9, 12, 3); c.p(11, 12, 3)
        return Anchor(9, 3, 2, 10, 13)
    }

    private fun frog(c: Cv): Anchor {
        c.ell(10f, 10f, 6.6f, 4f, 2)
        c.ell(9f, 11.5f, 4.6f, 2.5f, 5, 2)
        c.ell(6f, 6f, 2.2f, 2.2f, 2); c.ell(13f, 6f, 2.2f, 2.2f, 2)
        c.p(6, 6, 6); c.p(5, 6, 7); c.p(13, 6, 6); c.p(12, 6, 7)
        c.line(3, 10, 16, 10, 3); c.p(3, 9, 3); c.p(16, 9, 3)
        c.rect(5, 13, 7, 14, 2); c.rect(12, 13, 14, 14, 2)
        c.ell(3f, 13f, 2.2f, 1.6f, 2); c.ell(17f, 13f, 2.2f, 1.6f, 2)
        c.p(8, 8, 3); c.p(11, 8, 3); c.p(10, 7, 3)
        return Anchor(8, 6, 4, 18, 12)
    }

    private fun blob(c: Cv, v: Int): Anchor {
        c.ell(10f, 10f, 6.3f, 4.6f, 2)
        c.ell(10f, 6f, 3.4f, 3.2f, 2)
        c.p(10, 2, 2)
        c.ell(10f, 12f, 4.8f, 2f, 5, 2)
        if (v == 1) { c.ell(10f, 6f, 6.5f, 3.4f, 8); for (s in intArrayOf(6, 10, 13)) c.p(s, 5 + (s % 2), 6); c.rect(8, 8, 12, 12, 5) }
        c.eye(8, 10); c.eye(13, 10)
        c.p(10, 12, 7); c.p(9, 12, 7); c.p(11, 12, 7)
        c.p(7, 8, 9)
        c.p(7, 14, 3); c.p(12, 14, 3)
        return Anchor(8, 3, 4, 16, 12)
    }

    private fun turtle(c: Cv, v: Int): Anchor {
        c.ell(11f, 8f, 6.2f, 4.8f, 2)
        for (y in 4..11) for (x in 6..17) if (c.get(x, y) == 2 && (x + y * 2) % 5 == 0) c.p(x, y, 3)
        c.rect(6, 12, 16, 12, 5)
        c.ell(4f, 10f, 2.6f, 2.2f, 2)
        c.eye(3, 9)
        if (v == 1) { c.p(1, 10, 2); c.p(2, 11, 2); for (x in 7..16 step 3) c.line(x, 4, x - 1, 11, 3) }
        c.rect(6, 12, 7, 14, 2); c.rect(14, 12, 15, 14, 2)
        c.p(17, 12, 2); c.p(18, 13, 2)
        return Anchor(8, 4, 6, 18, 13)
    }

    private fun crab(c: Cv): Anchor {
        c.ell(10f, 10f, 5.6f, 3.3f, 2)
        c.ell(10f, 11.5f, 3.5f, 1.5f, 5, 2)
        c.ell(3.2f, 6f, 2.4f, 2.1f, 2); c.line(5, 9, 4, 7, 2); c.p(3, 4, 0); c.p(3, 5, 0)
        c.ell(16.8f, 6f, 2.4f, 2.1f, 2); c.line(14, 9, 15, 7, 2); c.p(16, 4, 0); c.p(16, 5, 0)
        c.line(8, 7, 8, 5, 2); c.line(12, 7, 12, 5, 2)
        c.p(8, 4, 6); c.p(12, 4, 6); c.p(8, 5, 7); c.p(12, 5, 7)
        for (i in 0..2) { c.line(5 + i, 12, 3 + i, 14, 3); c.line(14 - i, 12, 16 - i, 14, 3) }
        return Anchor(8, 7, 5, 17, 10)
    }

    private fun insect(c: Cv, v: Int): Anchor {
        when (v) {
            2 -> { // aranha
                c.ell(12f, 8f, 3.3f, 3.2f, 2); c.ell(7.5f, 8f, 2.2f, 2f, 2)
                for (i in 0..3) { val y = 6 + i; c.line(8, y, 3 - i / 2, 4 + i * 3, 3); c.line(13, y, 17 + i / 2, 4 + i * 3, 3) }
                c.eye(7, 7); c.p(9, 7, 6); c.p(9, 8, 7)
                c.p(12, 7, 8); c.p(13, 9, 8); c.p(11, 9, 8)
                return Anchor(10, 5, 4, 15, 8)
            }
            3 -> { // escorpião
                c.ell(8f, 11f, 4.3f, 2.6f, 2); c.ell(4f, 11f, 2f, 1.8f, 2)
                c.ell(2.5f, 8f, 2f, 1.6f, 2); c.ell(4.5f, 14f, 1.6f, 1f, 2)
                for (p in listOf(13 to 10, 14 to 9, 15 to 8, 15 to 7, 14 to 6, 13 to 6, 12 to 6)) { c.p(p.first, p.second, 2); c.p(p.first + 1, p.second, 2) }
                c.p(11, 7, 6); c.p(11, 8, 8)
                c.eye(4, 10)
                for (i in 0..3) c.line(6 + i * 2, 13, 5 + i * 2, 15, 3)
                return Anchor(7, 8, 5, 16, 8)
            }
            else -> { // besouro / joaninha / vaga-lume
                c.ell(11f, 9f, 5.6f, 4.6f, 2)
                c.ell(4.3f, 10f, 2.4f, 2.2f, 3)
                c.line(11, 5, 11, 13, 3)
                if (v == 1) { for (s in listOf(8 to 7, 14 to 7, 9 to 11, 14 to 11)) { c.p(s.first, s.second, 7); c.p(s.first + 1, s.second, 7) }; c.ell(11f, 9f, 5.6f, 4.6f, 8, 2) }
                if (v == 4) { c.ell(15f, 10f, 2.4f, 2.3f, 9); c.p(15, 10, 6) }
                if (v == 0) { c.p(2, 8, 3); c.p(2, 7, 3); c.p(3, 6, 3) }
                c.eye(4, 9)
                c.p(2, 7, 3); c.p(1, 6, 3)
                for (i in 0..2) c.line(7 + i * 3, 13, 6 + i * 3, 15, 3)
                return Anchor(8, 5, 6, 16, 9)
            }
        }
    }

    private fun lizard(c: Cv, v: Int): Anchor {
        c.ell(10f, 10f, 5f, 2.6f, 2)
        c.ell(10f, 11f, 4f, 1.4f, 5, 2)
        c.ell(4.6f, 9f, 2.6f, 2.2f, 2); c.p(2, 10, 2)
        for (i in 0..7) c.ell(14f + i * 0.7f, 10f - i * 0.42f, max(1f, 2.2f - i * 0.25f), max(0.8f, 1.9f - i * 0.2f), 2)
        for (x in intArrayOf(6, 12)) { c.rect(x, 12, x + 1, 14, 2) }
        c.rect(8, 12, 8, 13, 3); c.rect(14, 12, 14, 13, 3)
        c.eye(4, 8)
        if (v == 1) { c.ell(5f, 6f, 1.5f, 2f, 8); c.p(5, 5, 9) }
        return Anchor(7, 8, 8, 19, 7)
    }

    private fun golem(c: Cv, v: Int): Anchor {
        val huge = v and 2 != 0
        c.rect(6, 6, 13, 12, 2); c.rect(7, 12, 13, 12, 2)
        c.rect(8, 2, 11, 5, 2); c.rect(7, 3, 12, 4, 2)
        c.rect(3, 6, 5, 11, 2); c.rect(14, 6, 16, 11, 2)
        c.rect(2, 11, 5, 13, 2); c.rect(14, 11, 17, 13, 2)
        c.rect(7, 13, 9, 14, 2); c.rect(11, 13, 13, 14, 2)
        if (huge) { c.rect(2, 4, 4, 6, 2); c.rect(15, 4, 17, 6, 2) }
        val r = Random(v * 31 + 7)
        for (i in 0 until 14) { val x = 3 + r.nextInt(14); val y = 3 + r.nextInt(10); if (c.get(x, y) == 2) c.p(x, y, 3) }
        c.line(8, 8, 10, 10, 3); c.line(10, 10, 9, 12, 3)
        c.p(8, 3, 9); c.p(11, 3, 9)
        if (v and 1 != 0) { c.rect(9, 8, 10, 9, 9) }
        c.rect(9, 5, 10, 5, 7)
        return Anchor(6, 6, 8, 17, 12)
    }

    private fun ghost(c: Cv): Anchor {
        c.ell(10f, 7f, 5.2f, 5f, 2)
        for (x in 5..14) { val bot = 13 + (if (x % 3 == 0) 1 else 0); for (y in 8..bot) c.p(x, y, 2) }
        c.p(4, 9, 2); c.p(3, 10, 2); c.p(15, 9, 2); c.p(16, 10, 2); c.p(17, 11, 2)
        c.ell(8f, 7f, 1.1f, 1.6f, 7); c.ell(12f, 7f, 1.1f, 1.6f, 7)
        c.p(8, 7, 9); c.p(12, 7, 9)
        c.ell(10f, 10f, 1f, 1.2f, 7)
        c.ell(10f, 12f, 3f, 1.5f, 5, 2)
        return Anchor(7, 3, 6, 16, 12)
    }

    private fun dragon(c: Cv, v: Int): Anchor {
        c.ell(9f, 10f, 4.8f, 3.3f, 2)
        c.ell(9f, 11.5f, 3.2f, 1.6f, 5, 2)
        c.ell(5.8f, 6.6f, 1.7f, 2.8f, 2)
        c.ell(4f, 4.5f, 2.8f, 2.2f, 2); c.rect(1, 4, 3, 5, 2); c.p(1, 4, 7)
        c.p(5, 2, 6); c.p(6, 1, 6); if (v == 1) { c.p(7, 1, 6); c.p(4, 1, 6) }
        for (x in 10..18) {
            val t = (x - 10) / 8f
            val top = max(1, (7f - 6f * kotlin.math.sin(Math.PI.toFloat() * (0.12f + 0.7f * t))).toInt())
            val bot = 8 + (t * 2.5f).toInt()
            for (y in top..bot) c.p(x, y, if (y == top) 2 else 3)
        }
        for (i in 0..2) c.line(10, 8, 16 + i, 3 + i * 2, 2)
        c.rect(7, 12, 8, 14, 2); c.rect(11, 12, 12, 14, 2)
        for (i in 0..5) c.ell(13.5f + i * 1.0f, 11.5f + i * 0.2f, max(0.9f, 2f - i * 0.28f), max(0.8f, 1.7f - i * 0.2f), 2)
        c.eye(4, 4)
        return Anchor(8, 7, 4, 19, 12)
    }

    private fun fish(c: Cv, v: Int): Anchor {
        c.ell(9f, 8f, 6.6f, 4.2f, 2)
        c.ell(9f, 10f, 5.3f, 2.1f, 5, 2)
        for (x in 15..19) { val hh = x - 14; for (y in 8 - hh..8 + hh) c.p(x, y, 3) }
        for (x in 7..11) c.p(x, 3 - (if (x in 8..10) 1 else 0) + 1, 3)
        c.rect(8, 11, 10, 13, 3)
        c.eye(5, 7)
        c.line(1, 9, 4, 9, 3)
        if (v == 1) {
            for (p in listOf(6 to 4, 5 to 3, 4 to 2, 3 to 2, 2 to 3)) c.p(p.first, p.second, 3)
            c.ell(1.8f, 4.2f, 1.3f, 1.3f, 9); c.p(2, 4, 6)
            c.p(2, 9, 6); c.p(4, 9, 6); c.p(3, 10, 6)
        }
        return Anchor(8, 4, 4, 19, 8)
    }

    private fun plant(c: Cv, v: Int): Anchor {
        when (v) {
            1 -> { // cogumelo
                c.ell(10f, 6f, 6.6f, 4f, 8); for (s in listOf(6 to 5, 10 to 3, 14 to 6, 12 to 7)) { c.p(s.first, s.second, 6); c.p(s.first + 1, s.second, 6) }
                c.rect(8, 9, 12, 13, 2); c.rect(7, 13, 13, 14, 2)
                c.eye(9, 11); c.eye(12, 11); c.p(10, 13, 7); c.p(11, 13, 7)
            }
            2 -> { // cacto
                c.rect(8, 4, 12, 13, 2); c.rect(9, 3, 11, 3, 2)
                c.rect(4, 7, 7, 8, 2); c.rect(4, 5, 5, 8, 2); c.rect(13, 6, 16, 7, 2); c.rect(15, 4, 16, 7, 2)
                c.p(10, 2, 9); c.p(9, 2, 8); c.p(11, 2, 8); c.p(10, 1, 8)
                for (y in intArrayOf(5, 8, 11)) { c.p(8, y, 3); c.p(12, y, 3) }
                c.eye(10, 8); c.eye(12, 8); c.p(11, 10, 7)
                c.rect(8, 13, 12, 14, 3)
            }
            3 -> { // treant
                c.rect(6, 6, 13, 13, 2); c.rect(5, 7, 14, 12, 2)
                for (y in 6..13) for (x in 6..13) if ((x * 3 + y) % 4 == 0 && c.get(x, y) == 2) c.p(x, y, 3)
                c.rect(3, 6, 4, 10, 2); c.rect(15, 6, 16, 10, 2); c.p(2, 5, 2); c.p(17, 5, 2)
                c.ell(10f, 4f, 6.8f, 3f, 8); c.p(7, 3, 9); c.p(12, 4, 9); c.p(9, 2, 9)
                c.eye(9, 8); c.eye(12, 8); c.rect(9, 10, 11, 10, 7)
                c.rect(5, 13, 7, 14, 2); c.rect(12, 13, 14, 14, 2)
            }
            4 -> { // rainha-flor
                c.ell(10f, 6f, 6.2f, 5.2f, 8)
                for (a in listOf(4 to 3, 16 to 3, 10 to 0, 6 to 1, 14 to 1)) { c.p(a.first, a.second, 9) }
                c.ell(10f, 6f, 3.4f, 3f, 10); c.ell(10f, 6f, 3.4f, 3f, 2)
                c.eye(9, 6); c.eye(12, 6); c.p(10, 8, 7); c.p(11, 8, 7)
                c.rect(9, 10, 10, 13, 2); c.ell(6f, 12f, 2.6f, 1.1f, 2); c.ell(14f, 12f, 2.6f, 1.1f, 2)
                c.rect(7, 14, 12, 14, 3)
            }
            else -> { // broto
                c.ell(10f, 10f, 4.8f, 4.2f, 2)
                c.ell(10f, 11.5f, 3.3f, 2.4f, 5, 2)
                c.line(10, 6, 10, 4, 2)
                c.ell(7f, 3.5f, 3f, 1.6f, 8); c.ell(13f, 3.5f, 3f, 1.6f, 8); c.p(6, 3, 9); c.p(13, 3, 9)
                c.eye(8, 9); c.eye(12, 9); c.p(10, 11, 7); c.p(11, 11, 7)
                c.rect(7, 14, 9, 14, 3); c.rect(11, 14, 13, 14, 3)
            }
        }
        return Anchor(0, 0, 0, 0, 0)
    }

    private fun deer(c: Cv, v: Int): Anchor {
        c.ell(11f, 9f, 5.5f, 3.2f, 2)
        c.ell(11f, 10.5f, 4f, 1.4f, 5, 2)
        c.rect(5, 5, 6, 8, 2)
        c.ell(4f, 4.8f, 2.6f, 2.1f, 2); c.rect(2, 5, 3, 6, 2); c.p(2, 5, 7)
        c.rect(7, 11, 7, 14, 2); c.rect(9, 11, 9, 14, 2); c.rect(14, 11, 14, 14, 2); c.rect(16, 11, 16, 14, 2)
        c.p(7, 14, 3); c.p(9, 14, 3); c.p(14, 14, 3); c.p(16, 14, 3)
        c.p(17, 8, 2); c.p(18, 7, 5)
        when (v) {
            1 -> { c.ell(10f, 5.5f, 2.6f, 2.6f, 2); c.rect(4, 3, 4, 4, 2); c.p(5, 2, 3) }
            2 -> { for (p in listOf(3 to 2, 3 to 1, 2 to 0, 4 to 1, 6 to 2, 7 to 1, 8 to 0, 5 to 1, 6 to 1)) c.p(p.first, p.second, 6) }
            else -> { for (p in listOf(3 to 2, 3 to 1, 2 to 1, 4 to 1, 6 to 2, 7 to 1, 5 to 1)) c.p(p.first, p.second, 6) }
        }
        c.eye(4, 4)
        return Anchor(9, if (v == 1) 4 else 6, 5, 18, 7)
    }

    private fun crystal(c: Cv, v: Int): Anchor {
        if (v == 1) { // geodo
            c.ell(10f, 9f, 6.8f, 5.5f, 2); c.rect(4, 13, 15, 14, 2)
            c.ell(10f, 8f, 4.4f, 3.4f, 8)
            for (p in listOf(7 to 5, 9 to 4, 12 to 5, 14 to 7, 8 to 10)) { c.p(p.first, p.second, 9); c.p(p.first, p.second + 1, 9) }
            c.eye(9, 8); c.eye(12, 8); c.p(10, 10, 7); c.p(11, 10, 7)
            return Anchor(7, 4, 6, 17, 12)
        }
        for (y in 2..13) {
            val half = if (y <= 7) ((y - 2) * 1.1f + 1f) else ((13 - y) * 0.95f + 1.4f)
            for (x in 0 until 20) if (abs(x - 9.5f) <= half) c.p(x, y, 2)
        }
        c.line(10, 2, 14, 7, 3); c.line(9, 2, 5, 7, 4)
        c.p(10, 3, 9); c.p(10, 4, 9)
        for (p in listOf(2 to 12, 3 to 11, 3 to 12, 17 to 12, 16 to 11, 16 to 12, 17 to 11)) c.p(p.first, p.second, 8)
        c.eye(8, 8); c.eye(12, 8); c.p(10, 10, 7)
        c.p(8, 14, 3); c.p(11, 14, 3)
        return Anchor(8, 2, 3, 17, 12)
    }

    private val table: Map<String, Triple<Int, Int, Int>> = mapOf(
        "Folhito" to Triple(15, 0, 0), "Brasito" to Triple(0, 0, 0), "Gotinha" to Triple(6, 0, 0), "Pipino" to Triple(3, 0, 0),
        "Joaninha" to Triple(9, 1, 0), "Sapito" to Triple(5, 0, 1), "Coelhudo" to Triple(2, 0, 0), "Florarainha" to Triple(15, 4, 0),
        "Cogulim" to Triple(15, 1, 1), "Mofado" to Triple(6, 1, 0), "Corujin" to Triple(3, 1, 0), "Raizão" to Triple(15, 3, 2),
        "Vagalume" to Triple(9, 4, 2), "Aranhel" to Triple(9, 2, 0), "Lobrume" to Triple(1, 1, 0), "Morcegão" to Triple(4, 0, 1),
        "Cervelua" to Triple(16, 0, 1), "Espectrel" to Triple(12, 0, 0),
        "Pedrinho" to Triple(11, 0, 0), "Geodão" to Triple(17, 1, 0), "Cristalim" to Triple(17, 0, 0), "Fagulhita" to Triple(1, 0, 0),
        "Lamparino" to Triple(14, 1, 0), "Tatuferro" to Triple(7, 1, 0), "Topazito" to Triple(17, 0, 1), "Morcristal" to Triple(4, 0, 0),
        "Ametisto" to Triple(17, 0, 2), "Gemeon" to Triple(11, 1, 1), "Estalactor" to Triple(10, 1, 1), "Rochedrake" to Triple(13, 1, 0),
        "Escarabel" to Triple(9, 0, 0), "Cactuso" to Triple(15, 2, 0), "Escorpix" to Triple(9, 3, 1), "Areião" to Triple(11, 0, 1),
        "Solzito" to Triple(3, 0, 1), "Camelume" to Triple(16, 1, 0), "Miragem" to Triple(12, 1, 1), "Fenixito" to Triple(3, 2, 0),
        "Dunhorn" to Triple(0, 3, 0), "Faraonix" to Triple(1, 1, 2),
        "Brasinha" to Triple(6, 0, 0), "Magmito" to Triple(0, 3, 1), "Cinzel" to Triple(1, 0, 2), "Fumacinha" to Triple(12, 0, 1),
        "Caranguelo" to Triple(8, 0, 0), "Salamandro" to Triple(10, 0, 0), "Lavador" to Triple(7, 0, 0), "Raiolume" to Triple(3, 2, 0),
        "Tsunamito" to Triple(14, 0, 0), "Obsidiano" to Triple(11, 1, 2), "Trovão" to Triple(16, 2, 0), "Vulcanis" to Triple(0, 3, 2),
        "Pirodrake" to Triple(13, 0, 0), "Titanvulc" to Triple(11, 2, 0)
    )

    fun build(name: String, id: Int, type: Int, rarity: Int): Spr {
        val (arch, v, pal) = table[name] ?: Triple(6, 0, 0)
        val c = Cv(20, 16)
        val rnd = Random(id * 131 + 5)
        val a = when (arch) {
            0 -> boar(c, v); 1 -> canine(c, v); 2 -> rabbit(c); 3 -> bird(c, v); 4 -> bat(c); 5 -> frog(c)
            6 -> blob(c, v); 7 -> turtle(c, v); 8 -> crab(c); 9 -> insect(c, v); 10 -> lizard(c, v); 11 -> golem(c, v)
            12 -> ghost(c); 13 -> dragon(c, v); 14 -> fish(c, v); 15 -> plant(c, v); 16 -> deer(c, v); else -> crystal(c, v)
        }
        if (arch == 15) { if (type != 0) crest(c, type, 7, 3, 6, rnd) }
        else { crest(c, type, a.cx, a.cy, a.cw, rnd); if (a.tx > 0) tail(c, type, a.tx, a.ty) }
        // olhos brilhantes para sombra
        if (type == 4) for (y in 0 until 16) for (x in 0 until 20) if (c.g[y][x] == 6) c.g[y][x] = 9
        c.finish()
        // brilhos para raros
        if (rarity >= 3) { for (p in listOf(1 to 1, 18 to 2, 2 to 13, 17 to 14)) if (c.get(p.first, p.second) == 0) c.p(p.first, p.second, 10) }
        if (rarity >= 4) { for (p in listOf(9 to 0, 0 to 7, 19 to 8, 10 to 15, 6 to 0, 14 to 0)) if (c.get(p.first, p.second) == 0) c.p(p.first, p.second, 10) }
        return c.toSpr(palFor(type, pal))
    }
}
