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
    private companion object {
        val BRICK = longArrayOf(P.RED, P.ORANGE, P.YELLOW, P.GREEN, P.SKY)
    }

    private val cols = 10
    private val rows = 5
    private val bw = 70f
    private val bh = 22f
    private val gap = 6f
    private val pw = 110f
    private val ph = 12f
    private val py = 415f
    private val br = 7f

    private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Long)

    private val bits = ArrayList<Bit>()
    private val trail = FloatArray(12)
    private var anim = 0f
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
        bits.clear()
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
        anim += dt
        for (b in bits) { b.x += b.vx * dt; b.y += b.vy * dt; b.vy += 600f * dt; b.life -= dt }
        bits.removeAll { it.life <= 0f }
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
        for (i in trail.size - 2 downTo 2 step 2) { trail[i] = trail[i - 2]; trail[i + 1] = trail[i - 1] }
        trail[0] = bx; trail[1] = by
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
                    val bc = BRICK[r % BRICK.size]
                    for (k in 0 until 7) bits.add(Bit(x + bw / 2f, y + bh / 2f, (k - 3) * 45f + vx * 0.2f, -120f - k * 14f, 0.5f, bc))
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
        v.clear(P.INK)
        // fundo com grade discreta
        var gx = 0f
        while (gx <= 800f) { v.rect(gx, 30f, 1f, 420f, 0xFF20243CL); gx += 40f }
        var gy = 30f
        while (gy <= 450f) { v.rect(0f, gy, 800f, 1f, 0xFF20243CL); gy += 40f }
        for (r in 0 until rows) for (c in 0 until cols) {
            if (alive[r * cols + c]) v.bevel(brickX(c), brickY(r), bw, bh, BRICK[r % BRICK.size], 3f)
        }
        for (b in bits) v.rect(b.x - 2f, b.y - 2f, 4f, 4f, C.alpha(b.color, (b.life * 2f).coerceIn(0f, 1f)))
        // raquete
        v.bevel(px - pw / 2f, py, pw, ph, P.LILAC, 3f)
        v.rect(px - pw / 2f - 4f, py - 2f, 8f, ph + 4f, P.VIOLET)
        v.rect(px + pw / 2f - 4f, py - 2f, 8f, ph + 4f, P.VIOLET)
        // bola com rastro
        if (!stuck) for (i in 2 until trail.size step 2) {
            val a = 0.35f - i * 0.025f
            if (trail[i] > 0f) v.rect(trail[i] - 4f, trail[i + 1] - 4f, 8f, 8f, C.alpha(P.CYAN, a.coerceAtLeast(0.05f)))
        }
        v.rect(bx - br, by - br, br * 2f, br * 2f, P.WHITE)
        v.rect(bx - br, by - br, br, br, P.CYAN)
        v.hudBar("PONTOS $score", "VIDAS $lives  NIVEL $level")
        if (stuck && !over && (anim * 2f).toInt() % 2 == 0) {
            v.pixText("A OU TOQUE PARA LANCAR", 400f, 300f, 3f, P.YELLOW, center = true)
        }
    }
}
