package com.gamepadlayout.app.games

import kotlin.random.Random

/** Cobrinha clássica em grade. */
class SnakeGame : MiniGame(
    "snake", "Cobrinha", "Coma as maçãs e cresça sem bater em você ou nas paredes.",
    "D-pad / analógico: virar · toque: arraste"
) {
    private val cols = 24
    private val rows = 12
    private val body = ArrayDeque<IntArray>()
    private var dirX = 1
    private var dirY = 0
    private var nextX = 1
    private var nextY = 0
    private var appleX = 0
    private var appleY = 0
    private var acc = 0f
    private var anim = 0f
    private val rnd = Random.Default

    init { reset() }

    override fun reset() {
        body.clear()
        body.addLast(intArrayOf(6, rows / 2))
        body.addLast(intArrayOf(5, rows / 2))
        body.addLast(intArrayOf(4, rows / 2))
        dirX = 1; dirY = 0; nextX = 1; nextY = 0
        score = 0
        over = false
        acc = 0f
        placeApple()
    }

    private fun placeApple() {
        var tries = 0
        while (tries < 500) {
            val x = rnd.nextInt(cols)
            val y = rnd.nextInt(rows)
            if (body.none { it[0] == x && it[1] == y }) {
                appleX = x; appleY = y
                return
            }
            tries++
        }
        appleX = 0; appleY = 0
    }

    override fun update(dt: Float, input: GameInput) {
        anim += dt
        if (over) return
        if (input.stepX != 0 && dirX == 0) { nextX = input.stepX; nextY = 0 }
        else if (input.stepY != 0 && dirY == 0) { nextX = 0; nextY = input.stepY }
        acc += dt
        val interval = (0.15f - (score / 50) * 0.008f).coerceAtLeast(0.07f)
        while (acc >= interval && !over) {
            acc -= interval
            step()
        }
    }

    private fun step() {
        dirX = nextX; dirY = nextY
        val head = body.first()
        val nx = head[0] + dirX
        val ny = head[1] + dirY
        val eating = nx == appleX && ny == appleY
        val limit = if (eating) body.size else body.size - 1
        var hitSelf = false
        for (i in 0 until limit) {
            val seg = body[i]
            if (seg[0] == nx && seg[1] == ny) hitSelf = true
        }
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || hitSelf) {
            finish()
            return
        }
        body.addFirst(intArrayOf(nx, ny))
        if (eating) {
            score += 10
            placeApple()
        } else {
            body.removeLast()
        }
    }

    private val apple = Sprite(
        listOf(
            "....B...",
            "...BGG..",
            ".RRRBRR.",
            "RWRRRRRR",
            "RWRRRRRR",
            "RRRRRRRR",
            ".RRRRRR.",
            "..RR.RR."
        ),
        mapOf('R' to 0xFFE0455BL, 'W' to 0xFFFFB8C0L, 'G' to P.GREEN, 'B' to 0xFF6B3F2AL)
    )

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(P.INK)
        val cs = 800f / cols
        val oy = 40f
        // moldura e campo quadriculado
        v.rect(0f, oy - 4f, 800f, rows * cs + 8f, P.SLATE)
        for (x in 0 until cols) for (y in 0 until rows) {
            v.rect(x * cs, oy + y * cs, cs, cs, if ((x + y) % 2 == 0) 0xFF1D2B3FL else 0xFF22334BL)
        }
        // maçã (pulsa de leve)
        val bob = if ((anim * 4f).toInt() % 2 == 0) 0f else 1.5f
        apple.draw(v, appleX * cs + 1f, oy + appleY * cs + 1f + bob, (cs - 2f) / 8f)

        for (i in body.indices.reversed()) {
            val seg = body[i]
            val base = if (i % 2 == 0) P.GREEN else 0xFF2E9B57L
            v.bevel(seg[0] * cs + 1f, oy + seg[1] * cs + 1f, cs - 2f, cs - 2f, if (i == 0) P.LIME else base, 3f)
            if (i != 0) v.rect(seg[0] * cs + cs / 2f - 2f, oy + seg[1] * cs + cs / 2f - 2f, 4f, 4f, C.shade(base, 0.6f))
        }
        // rosto da cabeça
        val h = body.first()
        val hx = h[0] * cs
        val hy = oy + h[1] * cs
        val e1x: Float; val e1y: Float; val e2x: Float; val e2y: Float
        when {
            dirX > 0 -> { e1x = 0.62f; e1y = 0.22f; e2x = 0.62f; e2y = 0.62f }
            dirX < 0 -> { e1x = 0.22f; e1y = 0.22f; e2x = 0.22f; e2y = 0.62f }
            dirY < 0 -> { e1x = 0.2f; e1y = 0.2f; e2x = 0.62f; e2y = 0.2f }
            else -> { e1x = 0.2f; e1y = 0.55f; e2x = 0.62f; e2y = 0.55f }
        }
        for ((ex, ey) in listOf(e1x to e1y, e2x to e2y)) {
            v.rect(hx + ex * cs, hy + ey * cs, cs * 0.2f, cs * 0.2f, P.WHITE)
            v.rect(hx + ex * cs + cs * (0.08f + dirX * 0.05f), hy + ey * cs + cs * (0.08f + dirY * 0.05f), cs * 0.09f, cs * 0.09f, P.INK)
        }
        if ((anim * 3f).toInt() % 2 == 0 && !over) {
            v.rect(hx + cs * (0.42f + dirX * 0.5f), hy + cs * (0.42f + dirY * 0.5f), cs * 0.16f, cs * 0.16f, P.RED)
        }
        v.hudBar("PONTOS $score", "RECORDE $best")
    }
}
