package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Corredor Veloz: plataforma de velocidade em 2D. Pegue embalo nas descidas, role (baixo), pule (A),
 * pise nos bichinhos, junte anéis e não caia nos buracos. Personagem e fases originais.
 */
class RunnerGame : MiniGame(
    "runner", "Corredor Veloz", "Corra, pule e role pelas colinas. Junte anéis e pise nos inimigos!",
    "A: pular (segure = mais alto) · baixo: rolar · direita/esquerda: acelerar/frear · toque: tocar para pular"
) {
    private class Ring(val x: Float, val y: Float) { var alive = true }
    private class Bug(var x: Float, val minX: Float, val maxX: Float) {
        var dir = 1f
        var alive = true
    }
    private class Spike(val x: Float)
    private class Spring(val x: Float) { var squish = 0f }
    private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

    private val rnd = Random.Default
    private val h = ArrayList<Float>()
    private val rings = ArrayList<Ring>()
    private val bugs = ArrayList<Bug>()
    private val spikes = ArrayList<Spike>()
    private val springs = ArrayList<Spring>()
    private val bits = ArrayList<Bit>()
    private var curH = 330f
    private var genCell = 0

    private var x = 100f
    private var y = 330f
    private var vx = 0f
    private var vy = 0f
    private var onGround = true
    private var rolling = false
    private var airSpin = false
    private var ringCount = 0
    private var totalRings = 0
    private var kills = 0
    private var invuln = 0f
    private var time = 0f
    private var runAnim = 0f

    private companion object {
        const val CELL = 16f
        const val GRAV = 1500f
        val NAN = Float.NaN
    }

    init { reset() }

    override fun reset() {
        h.clear(); rings.clear(); bugs.clear(); spikes.clear(); springs.clear(); bits.clear()
        curH = 330f; genCell = 0
        for (i in 0 until 40) h.add(curH).also { genCell++ }
        x = 100f; y = 330f; vx = 0f; vy = 0f
        onGround = true; rolling = false; airSpin = false
        ringCount = 0; totalRings = 0; kills = 0; invuln = 0f; time = 0f; runAnim = 0f
        score = 0
        over = false
        ensure(2000f)
    }

    private fun ensure(toX: Float) {
        while (genCell * CELL < toX) addPattern()
    }

    private fun addFlat(len: Int, withStuff: Boolean) {
        val start = genCell
        repeat(len) { h.add(curH); genCell++ }
        if (!withStuff) return
        val midX = (start + len / 2) * CELL
        val r = rnd.nextInt(10)
        when {
            r < 3 -> bugs.add(Bug(midX, midX - 70f, midX + 70f))
            r < 4 -> spikes.add(Spike(midX))
            r < 5 -> springs.add(Spring(midX))
        }
        if (rnd.nextInt(3) != 0) for (k in 0 until min(len - 4, 8)) rings.add(Ring((start + 2 + k) * CELL, curH - 34f))
    }

    private fun addHill(len: Int, delta: Float) {
        val from = curH
        val to = (curH + delta).coerceIn(240f, 380f)
        val start = genCell
        for (k in 0 until len) {
            val t = k / (len - 1f)
            val s = t * t * (3f - 2f * t)
            h.add(from + (to - from) * s)
            genCell++
        }
        curH = to
        if (rnd.nextBoolean()) for (k in 3 until len - 3 step 2) {
            val cx = (start + k) * CELL
            rings.add(Ring(cx, h[start + k] - 36f))
        }
    }

    private fun addPit(len: Int) {
        val start = genCell
        repeat(len) { h.add(NAN); genCell++ }
        val x0 = start * CELL
        val x1 = (start + len) * CELL
        for (k in 0..6) {
            val t = k / 6f
            rings.add(Ring(x0 - 20f + (x1 - x0 + 40f) * t, curH - 40f - sin(t * PI.toFloat()) * 70f))
        }
        addFlat(8, false)
    }

    private fun addPattern() {
        if (genCell < 60) { addFlat(20, false); return }
        when (rnd.nextInt(7)) {
            0, 1 -> addFlat(14 + rnd.nextInt(20), true)
            2, 3 -> addHill(24 + rnd.nextInt(16), -(60f + rnd.nextInt(60)))
            4 -> addHill(24 + rnd.nextInt(16), 60f + rnd.nextInt(50))
            5 -> { addFlat(8, false); addPit(4 + rnd.nextInt(3)) }
            else -> { addHill(20 + rnd.nextInt(10), if (curH > 320f) -80f else 80f); addFlat(10, true) }
        }
    }

    private fun gy(px: Float): Float {
        if (px < 0f) return h[0]
        val i = floor(px / CELL).toInt()
        if (i >= h.size) return curH
        val a = h[i]
        if (a.isNaN()) return NAN
        val b = h.getOrElse(i + 1) { a }
        val f = (px - i * CELL) / CELL
        return if (b.isNaN()) a else a + (b - a) * f
    }

    private fun spawnBits(bx: Float, by: Float, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.28f
            bits.add(Bit(bx, by, cos(a) * (60f + rnd.nextFloat() * 180f), sin(a) * (60f + rnd.nextFloat() * 180f) - 60f, 0.6f))
        }
    }

    private fun hurt() {
        if (invuln > 0f) return
        if (ringCount > 0) {
            for (i in 0 until min(ringCount, 10)) {
                val a = rnd.nextFloat() * 6.28f
                bits.add(Bit(x, y - 20f, cos(a) * 220f, -abs(sin(a)) * 320f - 80f, 1.0f))
            }
            ringCount = 0
            invuln = 1.6f
            vy = -380f
            vx = max(160f, vx * 0.5f)
            onGround = false
            rolling = false
        } else {
            finish()
        }
    }

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        time += dt
        if (invuln > 0f) invuln -= dt
        ensure(x + 1800f)

        val base = 250f + min(280f, x / 45f)
        val wantRoll = input.dy > 0.5f
        if (onGround) {
            rolling = wantRoll
            val slope = (gy(x + 8f).let { if (it.isNaN()) 0f else it } - gy(x - 8f).let { if (it.isNaN()) 0f else it }) / 16f
            val target = base + input.dx * 140f + (if (rolling) 90f else 0f)
            vx += (target - vx) * (if (rolling) 0.6f else 1.6f) * dt
            vx += slope * (if (rolling) 1500f else 750f) * dt
            vx = vx.coerceIn(90f, 980f)
            if (input.firePressed) {
                vy = -560f
                onGround = false
                airSpin = true
                rolling = false
            }
        } else {
            vx += (base + input.dx * 140f - vx) * 0.5f * dt
            vy += GRAV * dt
            if (!input.fire && vy < -240f && airSpin) vy = -240f
        }

        x += vx * dt
        if (onGround) {
            val g = gy(x)
            if (g.isNaN() || g - y > 14f) {
                onGround = false
            } else {
                y = g
                vy = 0f
            }
            runAnim += dt * (vx / 40f)
        } else {
            y += vy * dt
            val g = gy(x)
            if (!g.isNaN() && y >= g && vy >= 0f && y - g < 34f) {
                y = g
                vy = 0f
                onGround = true
                airSpin = false
            }
        }
        if (y > 520f) { finish(); return }

        val cx = x
        val cy = y - (if (rolling || !onGround) 14f else 18f)
        val ballForm = rolling || !onGround
        for (r in rings) {
            if (r.alive && hypot(r.x - cx, r.y - cy) < 26f) {
                r.alive = false
                ringCount++
                totalRings++
            }
        }
        for (b in bugs) {
            if (!b.alive) continue
            b.x += b.dir * 55f * dt
            if (b.x < b.minX) { b.x = b.minX; b.dir = 1f }
            if (b.x > b.maxX) { b.x = b.maxX; b.dir = -1f }
            val by = gy(b.x).let { if (it.isNaN()) 330f else it } - 10f
            if (hypot(b.x - cx, by - cy) < 24f) {
                if (ballForm) {
                    b.alive = false
                    kills++
                    spawnBits(b.x, by, 10)
                    if (!onGround) vy = -380f
                } else hurt()
            }
        }
        for (sp in spikes) {
            val gyv = gy(sp.x)
            if (gyv.isNaN()) continue
            if (abs(sp.x - cx) < 18f && y > gyv - 20f && y < gyv + 20f) hurt()
        }
        for (s in springs) {
            if (s.squish > 0f) s.squish -= dt
            val gyv = gy(s.x)
            if (gyv.isNaN()) continue
            if (abs(s.x - cx) < 20f && y >= gyv - 6f && y <= gyv + 10f && vy >= 0f) {
                vy = -900f
                onGround = false
                airSpin = true
                s.squish = 0.25f
            }
        }
        for (b in bits) { b.x += b.vx * dt; b.y += b.vy * dt; b.vy += 900f * dt; b.life -= dt }
        bits.removeAll { it.life <= 0f }
        rings.removeAll { !it.alive || it.x < x - 500f }
        bugs.removeAll { !it.alive || it.maxX < x - 500f }
        spikes.removeAll { it.x < x - 500f }
        springs.removeAll { it.x < x - 500f }

        score = (x / 12f).toInt() + totalRings * 10 + kills * 50
    }

    private val pal = mapOf('O' to P.ORANGE, 'W' to P.WHITE, 'K' to P.INK, 'Y' to P.YELLOW)
    private val foxA = Sprite(
        listOf(
            "...O..O.....",
            "..OOOOOO....",
            ".OOWKOWKO...",
            ".OOOOOOKK...",
            "..OOOOOO....",
            "..WWWWWW.OO.",
            "..WWWWWW.OOO",
            "..OOOOOO.OO.",
            "..OO..OO....",
            ".OO....OO..."
        ), pal
    )
    private val foxB = Sprite(
        listOf(
            "...O..O.....",
            "..OOOOOO....",
            ".OOWKOWKO...",
            ".OOOOOOKK...",
            "..OOOOOO....",
            "..WWWWWW.OO.",
            "..WWWWWW.OOO",
            "..OOOOOO.OO.",
            "...OOOO.....",
            "...OO.OO...."
        ), pal
    )
    private val bugSpr = Sprite(
        listOf(
            "..SSSSSS..",
            ".STTTTTTS.",
            "STWKTTWKTS",
            "STTTTTTTTS",
            ".SSSSSSSS.",
            "..S.SS.S.."
        ), pal
    )

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        val camX = x - 260f
        val sky = longArrayOf(0xFF2748B8L, 0xFF3366D0L, 0xFF4286E0L, 0xFF5AA6EAL, 0xFF7FC4F0L, 0xFFA8DCF5L)
        v.clear(sky[0])
        val bh = 330f / sky.size
        for (i in sky.indices) v.rect(0f, i * bh, 800f, bh + 1f, sky[i])
        // nuvens
        for (i in 0 until 6) {
            val cxp = ((i * 260f - camX * 0.15f) % 1560f + 1560f) % 1560f - 200f
            val cyp = 40f + (i * 37 % 90)
            v.rect(cxp, cyp, 90f, 18f, P.WHITE)
            v.rect(cxp + 14f, cyp - 12f, 50f, 14f, P.WHITE)
            v.rect(cxp + 8f, cyp + 18f, 70f, 6f, 0xFFD6EAF8L)
        }
        // colinas distantes
        for (layer in 0 until 2) {
            val par = 0.25f + layer * 0.25f
            val col = if (layer == 0) 0xFF3E8E7EL else 0xFF2F7A4AL
            var sx = -16f
            while (sx < 816f) {
                val wx = sx + camX * par
                val hh = 70f + layer * 18f + 36f * sin(wx / (120f - layer * 30f)) + 16f * sin(wx / 47f)
                v.rect(sx, 380f - hh - layer * 20f, 17f, hh + layer * 20f + 70f, col)
                sx += 16f
            }
        }
        // chão
        val c0 = floor(camX / CELL).toInt() - 1
        val c1 = c0 + (800f / CELL).toInt() + 3
        for (c in c0..c1) {
            if (c < 0 || c >= h.size) continue
            val top = h[c]
            if (top.isNaN()) continue
            val sx = c * CELL - camX
            v.rect(sx, top, CELL + 1f, 8f, P.GREEN)
            v.rect(sx, top, CELL + 1f, 3f, P.LIME)
            var yy = top + 8f
            var row = 0
            while (yy < 450f) {
                val dark = (c + row) % 2 == 0
                v.rect(sx, yy, CELL + 1f, 16f, if (dark) 0xFF8A5A3AL else 0xFF9B6A44L)
                yy += 16f; row++
            }
        }
        // molas, espinhos, inimigos, anéis
        for (s in springs) {
            val gyv = gy(s.x)
            if (gyv.isNaN()) continue
            val sx = s.x - camX
            val sq = if (s.squish > 0f) 6f else 0f
            v.rect(sx - 12f, gyv - 6f + sq, 24f, 6f, P.RED)
            v.rect(sx - 8f, gyv - 14f + sq, 16f, 8f, P.YELLOW)
            v.rect(sx - 10f, gyv - 18f + sq, 20f, 5f, P.SILVER)
        }
        for (sp in spikes) {
            val gyv = gy(sp.x)
            if (gyv.isNaN()) continue
            val sx = sp.x - camX
            for (k in 0 until 4) {
                val px = sx - 16f + k * 8f
                v.rect(px, gyv - 6f, 8f, 6f, P.SILVER)
                v.rect(px + 2f, gyv - 12f, 4f, 6f, P.WHITE)
            }
        }
        for (b in bugs) {
            val gyv = gy(b.x).let { if (it.isNaN()) 330f else it }
            bugSpr.draw(v, b.x - camX - 15f, gyv - 18f, 3f, flip = b.dir < 0f, tint = P.TEAL)
        }
        val spin = abs(cos(time * 7f))
        for (r in rings) {
            val sx = r.x - camX
            if (sx < -20f || sx > 820f) continue
            val wd = 3f + 6f * spin
            v.rect(sx - wd, r.y - 8f, wd * 2f, 3f, P.YELLOW)
            v.rect(sx - wd, r.y + 5f, wd * 2f, 3f, P.YELLOW)
            v.rect(sx - wd - 2f, r.y - 5f, 3f, 10f, P.ORANGE)
            v.rect(sx + wd - 1f, r.y - 5f, 3f, 10f, P.ORANGE)
        }
        // jogador
        val blink = invuln > 0f && (time * 14f).toInt() % 2 == 0
        if (!blink) {
            val sx = 260f
            if (rolling || !onGround) {
                v.rect(sx - 13f, y - 27f, 26f, 26f, P.ORANGE)
                v.rect(sx - 9f, y - 31f, 18f, 4f, P.ORANGE)
                v.rect(sx - 9f, y - 1f, 18f, 4f, P.ORANGE)
                v.rect(sx - 17f, y - 23f, 4f, 18f, P.ORANGE)
                v.rect(sx + 13f, y - 23f, 4f, 18f, P.ORANGE)
                val a = time * 18f
                v.line(sx - 9f * cos(a), y - 14f - 9f * sin(a), sx + 9f * cos(a), y - 14f + 9f * sin(a), 3f, P.WHITE)
            } else {
                val spr = if ((runAnim.toInt() and 1) == 0) foxA else foxB
                v.rect(sx - 16f, y - 2f, 32f, 4f, C.alpha(C.BLACK, 0.25f))
                spr.draw(v, sx - 18f, y - 40f, 3.6f)
            }
        }
        for (b in bits) {
            v.rect(b.x - camX - 3f, b.y - 3f, 6f, 6f, C.alpha(P.YELLOW, (b.life * 2f).coerceIn(0f, 1f)))
        }
        v.hudBar("PONTOS $score", "ANEIS $ringCount  VEL ${(vx / 3f).toInt()}")
        if (time < 3f) v.pixText("A PULA - BAIXO ROLA", 400f, 70f, 3.5f, P.WHITE, center = true)
    }
}
