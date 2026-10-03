package com.gamepadlayout.app.games

import kotlin.random.Random

/** Cobrinha clássica em grade. */
class SnakeGame : MiniGame(
    "snake", "Cobrinha", "Coma as maçãs e cresça sem bater em você ou nas paredes.",
    "D-pad / analógico: virar · toque: arraste"
) {
    private val cols = 24
    private val rows = 13
    private val body = ArrayDeque<IntArray>()
    private var dirX = 1
    private var dirY = 0
    private var nextX = 1
    private var nextY = 0
    private var appleX = 0
    private var appleY = 0
    private var acc = 0f
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

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(C.BG)
        val cs = 800f / cols
        val oy = (450f - rows * cs) / 2f + 10f
        v.rect(0f, oy, cols * cs, rows * cs, 0xFF140F44L)
        for (x in 0 until cols) for (y in 0 until rows) {
            if ((x + y) % 2 == 0) v.rect(x * cs, oy + y * cs, cs, cs, 0x10FFFFFFL)
        }
        v.circle(appleX * cs + cs / 2, oy + appleY * cs + cs / 2, cs * 0.38f, C.RED)
        v.rect(appleX * cs + cs / 2 - 1.5f, oy + appleY * cs + 2f, 3f, 6f, C.GREEN)
        for (i in body.indices) {
            val seg = body[i]
            val color = if (i == 0) C.LILAC else C.PURPLE
            v.rect(seg[0] * cs + 1.5f, oy + seg[1] * cs + 1.5f, cs - 3f, cs - 3f, color)
        }
        val h = body.first()
        v.circle(h[0] * cs + cs * 0.35f, oy + h[1] * cs + cs * 0.38f, 2.5f, C.BLACK)
        v.circle(h[0] * cs + cs * 0.65f, oy + h[1] * cs + cs * 0.38f, 2.5f, C.BLACK)
        v.text("Pontos: $score", 12f, 22f, 20f, C.WHITE, false)
        v.text("Recorde: $best", 700f, 22f, 16f, C.LILAC, true)
    }
}
