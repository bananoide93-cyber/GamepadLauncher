package com.gamepadlayout.app.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Quebra-tijolos. */
class BreakoutGame : MiniGame(
    "breakout", "Tijolinhos", "Rebata a bolinha e destrua todos os tijolos.",
    "Analógico / D-pad: mover · A ou toque: lançar a bola"
) {
    private val cols = 10
    private val rows = 5
    private val bw = 70f
    private val bh = 22f
    private val gap = 6f
    private val pw = 110f
    private val ph = 12f
    private val py = 415f
    private val br = 7f

    private val alive = BooleanArray(cols * rows)
    private var px = 400f
    private var bx = 400f
    private var by = 400f
    private var vx = 0f
    private var vy = 0f
    private var stuck = true
    private var lives = 3
    private var level = 1
    private var speed = 330f
    private var left = 0

    init { reset() }

    override fun reset() {
        score = 0
        over = false
        lives = 3
        level = 1
        speed = 330f
        buildBricks()
        stick()
    }

    private fun buildBricks() {
        for (i in alive.indices) alive[i] = true
        left = alive.size
    }

    private fun stick() {
        stuck = true
        vx = 0f; vy = 0f
        bx = px
        by = py - br - 1f
    }

    private fun brickX(c: Int): Float = (800f - (cols * (bw + gap) - gap)) / 2f + c * (bw + gap)
    private fun brickY(r: Int): Float = 50f + r * (bh + gap)

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        px = (px + input.dx * 560f * dt).coerceIn(pw / 2f, 800f - pw / 2f)
        if (stuck) {
            bx = px
            by = py - br - 1f
            if (input.firePressed) {
                stuck = false
                vx = 120f
                vy = -sqrt(max(speed * speed - vx * vx, 10000f))
            }
            return
        }
        // sub-passos evitam atravessar tijolos em bolas rápidas
        val steps = 3
        for (i in 0 until steps) {
            move(dt / steps)
            if (stuck || over) return
        }
    }

    private fun move(dt: Float) {
        bx += vx * dt
        by += vy * dt
        if (bx < br) { bx = br; vx = abs(vx) }
        if (bx > 800f - br) { bx = 800f - br; vx = -abs(vx) }
        if (by < br) { by = br; vy = abs(vy) }

        if (vy > 0f && by + br >= py && by + br <= py + ph + 10f &&
            bx >= px - pw / 2f - br && bx <= px + pw / 2f + br
        ) {
            val off = ((bx - px) / (pw / 2f)).coerceIn(-1f, 1f)
            vx = off * 320f
            vy = -sqrt(max(speed * speed - vx * vx, 10000f))
            by = py - br - 0.5f
        }
        hitBricks()
        if (by > 470f) {
            lives--
            if (lives <= 0) finish() else stick()
        }
    }

    private fun hitBricks() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (!alive[idx]) continue
                val x = brickX(c)
                val y = brickY(r)
                if (bx + br > x && bx - br < x + bw && by + br > y && by - br < y + bh) {
                    alive[idx] = false
                    left--
                    score += 10 * (rows - r)
                    val oL = bx + br - x
                    val oR = x + bw - (bx - br)
                    val oT = by + br - y
                    val oB = y + bh - (by - br)
                    if (min(oL, oR) < min(oT, oB)) vx = -vx else vy = -vy
                    if (left <= 0) {
                        level++
                        speed += 40f
                        buildBricks()
                        stick()
                    }
                    return
                }
            }
        }
    }

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(C.BG)
        val colors = longArrayOf(0xFFE53935L, 0xFFFF9800L, 0xFFFFD54FL, 0xFF66BB6AL, 0xFF42A5F5L)
        for (r in 0 until rows) for (c in 0 until cols) {
            if (alive[r * cols + c]) v.rect(brickX(c), brickY(r), bw, bh, colors[r % colors.size])
        }
        v.rect(px - pw / 2f, py, pw, ph, C.LILAC)
        v.circle(bx, by, br, C.WHITE)
        v.text("Pontos: $score", 12f, 24f, 20f, C.WHITE, false)
        v.text("Vidas: $lives   Nível: $level", 790f - 150f, 24f, 16f, C.LILAC, false)
        if (stuck && !over) v.text("A ou toque para lançar", 400f, 300f, 20f, C.alpha(C.WHITE, 0.7f), true)
    }
}
