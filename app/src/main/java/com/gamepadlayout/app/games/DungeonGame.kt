package com.gamepadlayout.app.games

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dungeon 3D: atirador em primeira pessoa no estilo dos clássicos dos anos 90 (raycasting).
 * Mapa, monstros e arte são originais.
 */
class DungeonGame : MiniGame(
    "dungeon", "Dungeon 3D", "Explore as masmorras em primeira pessoa e elimine todos os monstros.",
    "Analógico/D-pad: andar e girar · analógico direito: girar · L1/R1: lado · A ou R2: atirar · toque: arraste e toque para atirar"
) {
    private class Monster(var x: Float, var y: Float, var hp: Int) {
        var cool = 0.6f
        var hurt = 0f
        var phase = 0f
    }

    private val map = arrayOf(
        "1111111111111111",
        "1000000010000001",
        "1011110010111101",
        "1010000000100001",
        "1010111111101101",
        "1000100000001001",
        "1110101111101011",
        "1000001000001001",
        "1011101011111001",
        "1010001000000101",
        "1010111111110101",
        "1000100000000001",
        "1110101111111011",
        "1000000100000001",
        "1000110000011001",
        "1111111111111111"
    )
    private val mw = map[0].length
    private val mh = map.size

    private val rnd = Random.Default
    private val monsters = ArrayList<Monster>()
    private var px = 1.5f
    private var py = 1.5f
    private var angle = 0.4f
    private var hp = 100
    private var level = 1
    private var fireT = 0f
    private var muzzle = 0f
    private var hurtFlash = 0f
    private var banner = 0f
    private var time = 0f
    private var zbuf = FloatArray(0)
    private val spawnCells = ArrayList<IntArray>()

    init {
        computeSpawnCells()
        reset()
    }

    /** Só nascem monstros em células alcançáveis a partir do início e longe dele. */
    private fun computeSpawnCells() {
        val dist = Array(mh) { IntArray(mw) { -1 } }
        val queue = ArrayDeque<IntArray>()
        dist[1][1] = 0
        queue.addLast(intArrayOf(1, 1))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
            for (d in dirs) {
                val nx = c[0] + d[0]
                val ny = c[1] + d[1]
                if (nx < 0 || ny < 0 || nx >= mw || ny >= mh) continue
                if (map[ny][nx] != '0' || dist[ny][nx] >= 0) continue
                dist[ny][nx] = dist[c[1]][c[0]] + 1
                queue.addLast(intArrayOf(nx, ny))
            }
        }
        spawnCells.clear()
        for (y in 0 until mh) for (x in 0 until mw) if (dist[y][x] >= 5) spawnCells.add(intArrayOf(x, y))
    }

    override fun reset() {
        score = 0
        over = false
        hp = 100
        level = 1
        fireT = 0f; muzzle = 0f; hurtFlash = 0f
        banner = 2.5f
        px = 1.5f; py = 1.5f; angle = 0.4f
        spawnLevel()
    }

    private fun spawnLevel() {
        monsters.clear()
        val count = min(spawnCells.size, min(14, 3 + level * 2))
        val pool = ArrayList(spawnCells)
        for (i in 0 until count) {
            if (pool.isEmpty()) break
            val c = pool.removeAt(rnd.nextInt(pool.size))
            monsters.add(Monster(c[0] + 0.5f, c[1] + 0.5f, 2 + level / 3))
        }
    }

    private fun wall(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return true
        val mx = x.toInt()
        val my = y.toInt()
        if (mx >= mw || my >= mh) return true
        return map[my][mx] != '0'
    }

    private fun canStand(x: Float, y: Float): Boolean {
        val r = 0.22f
        return !wall(x - r, y - r) && !wall(x + r, y - r) && !wall(x - r, y + r) && !wall(x + r, y + r)
    }

    private fun los(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val dx = x1 - x0
        val dy = y1 - y0
        val n = (hypot(dx, dy) / 0.1f).toInt() + 1
        for (i in 1 until n) {
            val t = i.toFloat() / n
            if (wall(x0 + dx * t, y0 + dy * t)) return false
        }
        return true
    }

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        time += dt
        if (banner > 0f) banner -= dt
        if (hurtFlash > 0f) hurtFlash -= dt
        if (muzzle > 0f) muzzle -= dt
        fireT -= dt

        angle += (input.dx + input.turnX).coerceIn(-1.5f, 1.5f) * 2.3f * dt
        val dirX = cos(angle)
        val dirY = sin(angle)
        val fwd = -input.dy * 3.2f
        val str = ((if (input.strafeRight) 1f else 0f) - (if (input.strafeLeft) 1f else 0f)) * 2.6f
        val mvx = (dirX * fwd - dirY * str) * dt
        val mvy = (dirY * fwd + dirX * str) * dt
        if (canStand(px + mvx, py)) px += mvx
        if (canStand(px, py + mvy)) py += mvy

        if ((input.fire || input.firePressed) && fireT <= 0f) {
            fireT = 0.3f
            muzzle = 0.1f
            shoot(dirX, dirY)
        }

        for (m in monsters) {
            if (m.hp <= 0) continue
            m.cool -= dt
            if (m.hurt > 0f) m.hurt -= dt
            val dx = px - m.x
            val dy = py - m.y
            val d = hypot(dx, dy)
            if (d < 9f && los(m.x, m.y, px, py)) {
                if (d > 0.85f) {
                    val sp = 1.1f + level * 0.05f
                    val nx = m.x + dx / d * sp * dt
                    val ny = m.y + dy / d * sp * dt
                    if (!wall(nx, m.y)) m.x = nx
                    if (!wall(m.x, ny)) m.y = ny
                    m.phase += dt * 6f
                } else if (m.cool <= 0f) {
                    m.cool = 0.9f
                    hp -= 10
                    hurtFlash = 0.3f
                    if (hp <= 0) {
                        hp = 0
                        finish()
                        return
                    }
                }
            }
        }

        if (monsters.all { it.hp <= 0 }) {
            level++
            banner = 2.5f
            hp = min(100, hp + 25)
            px = 1.5f; py = 1.5f; angle = 0.4f
            spawnLevel()
        }
    }

    private fun shoot(dirX: Float, dirY: Float) {
        val k = 0.66f
        val planeX = -dirY * k
        val planeY = dirX * k
        val invDet = 1f / (planeX * dirY - dirX * planeY)
        var best: Monster? = null
        var bestDepth = 1e9f
        for (m in monsters) {
            if (m.hp <= 0) continue
            val sx = m.x - px
            val sy = m.y - py
            val tx = invDet * (dirY * sx - dirX * sy)
            val ty = invDet * (-planeY * sx + planeX * sy)
            if (ty < 0.2f || ty >= bestDepth) continue
            if (abs(tx) / ty > 0.14f) continue
            if (!los(px, py, m.x, m.y)) continue
            best = m
            bestDepth = ty
        }
        val m = best ?: return
        m.hp--
        m.hurt = 0.15f
        if (m.hp <= 0) score += 50 + level * 5
    }

    override fun draw(g: Gfx) {
        val w = g.width
        val h = g.height
        for (i in 0 until 6) {
            val t = i / 5f
            g.rect(0f, h / 2f * i / 6f, w, h / 12f + 1f, C.shade(0xFF2A2A5CL, 0.5f + 0.5f * t))
            g.rect(0f, h / 2f + h / 2f * i / 6f, w, h / 12f + 1f, C.shade(0xFF5A4636L, 0.35f + 0.65f * t))
        }
        val cols = (w / 4f).toInt().coerceIn(80, 260)
        val cw = w / cols
        if (zbuf.size != cols) zbuf = FloatArray(cols)

        val dirX = cos(angle)
        val dirY = sin(angle)
        val k = 0.66f
        val planeX = -dirY * k
        val planeY = dirX * k
        val wallColors = longArrayOf(0xFF9C3B2EL, 0xFF5C6B8AL, 0xFF6A4BA8L)

        for (col in 0 until cols) {
            val camX = 2f * col / cols - 1f
            val rdx = dirX + planeX * camX
            val rdy = dirY + planeY * camX
            var mx = px.toInt()
            var my = py.toInt()
            val ddx = if (rdx == 0f) 1e30f else abs(1f / rdx)
            val ddy = if (rdy == 0f) 1e30f else abs(1f / rdy)
            val stepX: Int
            val stepY: Int
            var sdx: Float
            var sdy: Float
            if (rdx < 0f) { stepX = -1; sdx = (px - mx) * ddx } else { stepX = 1; sdx = (mx + 1f - px) * ddx }
            if (rdy < 0f) { stepY = -1; sdy = (py - my) * ddy } else { stepY = 1; sdy = (my + 1f - py) * ddy }
            var side = 0
            var hit = false
            var guard = 0
            while (!hit && guard < 64) {
                if (sdx < sdy) { sdx += ddx; mx += stepX; side = 0 } else { sdy += ddy; my += stepY; side = 1 }
                if (mx < 0 || my < 0 || mx >= mw || my >= mh) hit = true
                else if (map[my][mx] != '0') hit = true
                guard++
            }
            var perp = if (side == 0) sdx - ddx else sdy - ddy
            if (perp < 0.05f) perp = 0.05f
            zbuf[col] = perp

            var wallX = if (side == 0) py + perp * rdy else px + perp * rdx
            wallX -= floor(wallX)
            val mortar = (wallX * 4f) % 1f < 0.07f
            val base = wallColors[((mx + my) % 3 + 3) % 3]
            var f = (if (side == 1) 0.72f else 1f) / (1f + perp * perp * 0.07f)
            if (mortar) f *= 0.7f
            val lineH = h / perp
            val y0 = max(0f, h / 2f - lineH / 2f)
            val y1 = min(h, h / 2f + lineH / 2f)
            g.rect(col * cw, y0, cw + 1f, y1 - y0, C.shade(base, f.coerceIn(0.12f, 1f)))
        }

        drawMonsters(g, cols, cw, dirX, dirY, planeX, planeY)
        drawHud(g)
    }

    private fun drawMonsters(g: Gfx, cols: Int, cw: Float, dirX: Float, dirY: Float, planeX: Float, planeY: Float) {
        val w = g.width
        val h = g.height
        val invDet = 1f / (planeX * dirY - dirX * planeY)
        val vis = ArrayList<FloatArray>()
        for (i in monsters.indices) {
            val m = monsters[i]
            if (m.hp <= 0) continue
            val sx = m.x - px
            val sy = m.y - py
            val tx = invDet * (dirY * sx - dirX * sy)
            val ty = invDet * (-planeY * sx + planeX * sy)
            if (ty < 0.2f) continue
            vis.add(floatArrayOf(i.toFloat(), tx, ty))
        }
        vis.sortByDescending { it[2] }
        for (v in vis) {
            val m = monsters[v[0].toInt()]
            val tx = v[1]
            val ty = v[2]
            val sprH = abs(h / ty)
            val eh = sprH * 0.85f
            val ew = eh * 0.62f
            val cx = (cols / 2f) * (1f + tx / ty) * cw
            val top = h / 2f + sprH / 2f - eh
            val left = cx - ew / 2f
            val c0 = max(0, (left / cw).toInt())
            val c1 = min(cols - 1, ((left + ew) / cw).toInt())
            val f = (1f / (1f + ty * ty * 0.07f)).coerceIn(0.25f, 1f)
            val flash = m.hurt > 0f
            val body = if (flash) 0xFFFFFFFFL else C.shade(0xFF8E2A22L, f)
            val head = if (flash) 0xFFFFFFFFL else C.shade(0xFFC0463AL, f)
            val horn = C.shade(0xFFEDE0C8L, f)
            val eye = C.shade(C.YELLOW, f)
            val legSwing = sin(m.phase) * eh * 0.03f
            for (c in c0..c1) {
                if (zbuf[c] < ty) continue
                val u = ((c * cw + cw / 2f - left) / ew).coerceIn(0f, 1f)
                val x = c * cw
                if (u in 0.12f..0.88f) g.rect(x, top + eh * 0.30f, cw + 1f, eh * 0.60f, body)
                if (u in 0.20f..0.45f) g.rect(x, top + eh * 0.90f + legSwing, cw + 1f, eh * 0.10f, body)
                if (u in 0.55f..0.80f) g.rect(x, top + eh * 0.90f - legSwing, cw + 1f, eh * 0.10f, body)
                if (u in 0.28f..0.72f) g.rect(x, top + eh * 0.05f, cw + 1f, eh * 0.25f, head)
                if (u in 0.30f..0.36f || u in 0.64f..0.70f) g.rect(x, top, cw + 1f, eh * 0.08f, horn)
                if (u in 0.34f..0.44f || u in 0.56f..0.66f) g.rect(x, top + eh * 0.13f, cw + 1f, eh * 0.05f, eye)
            }
        }
    }

    private fun drawHud(g: Gfx) {
        val w = g.width
        val h = g.height
        if (hurtFlash > 0f) g.rect(0f, 0f, w, h, C.alpha(C.RED, (hurtFlash * 1.4f).coerceIn(0f, 0.45f)))

        val kick = if (muzzle > 0f) h * 0.02f else 0f
        if (muzzle > 0f) g.circle(w / 2f, h * 0.60f, h * 0.05f, C.alpha(C.YELLOW, 0.9f))
        g.rect(w / 2f - w * 0.028f, h * 0.68f + kick, w * 0.056f, h * 0.32f, 0xFF3A3A3AL)
        g.rect(w / 2f - w * 0.018f, h * 0.64f + kick, w * 0.036f, h * 0.10f, 0xFF6A6A6AL)

        g.line(w / 2f - 8f, h / 2f, w / 2f + 8f, h / 2f, 2f, C.alpha(C.WHITE, 0.8f))
        g.line(w / 2f, h / 2f - 8f, w / 2f, h / 2f + 8f, 2f, C.alpha(C.WHITE, 0.8f))

        val bw = w * 0.26f
        val by = h - h * 0.07f
        g.rect(w * 0.03f, by, bw, h * 0.03f, C.alpha(C.BLACK, 0.6f))
        val frac = hp / 100f
        val hc = if (frac > 0.5f) C.GREEN else if (frac > 0.25f) C.ORANGE else C.RED
        g.rect(w * 0.03f, by, bw * frac, h * 0.03f, hc)
        g.text("Vida $hp", w * 0.03f, by - 6f, h * 0.04f, C.WHITE, false)

        val alive = monsters.count { it.hp > 0 }
        g.text("Pontos: $score   Nível $level   Monstros: $alive", w - 12f - w * 0.34f, h * 0.06f, h * 0.04f, C.WHITE, false)

        val ms = min(w, h) * 0.011f
        val ox = 8f
        val oy = 8f
        g.rect(ox, oy, mw * ms, mh * ms, C.alpha(C.BLACK, 0.45f))
        for (y in 0 until mh) for (x in 0 until mw) {
            if (map[y][x] != '0') g.rect(ox + x * ms, oy + y * ms, ms, ms, C.alpha(C.LILAC, 0.55f))
        }
        for (m in monsters) if (m.hp > 0) g.circle(ox + m.x * ms, oy + m.y * ms, ms * 0.45f, C.RED)
        g.circle(ox + px * ms, oy + py * ms, ms * 0.5f, C.YELLOW)
        g.line(ox + px * ms, oy + py * ms, ox + (px + cos(angle) * 1.2f) * ms, oy + (py + sin(angle) * 1.2f) * ms, 1.5f, C.YELLOW)

        if (banner > 0f) {
            val msg = if (level == 1) "Nível 1 — elimine todos os monstros" else "Nível $level"
            g.text(msg, w / 2f, h * 0.25f, h * 0.07f, C.alpha(C.WHITE, min(1f, banner)), true)
        }
    }
}
