package com.gamepadlayout.app.games

import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Nave espacial horizontal: desvie, atire e sobreviva às ondas. */
class ShipGame : MiniGame(
    "ship", "Nave Espacial", "Destrua as naves inimigas. O tiro é automático; segure A para tiro rápido.",
    "Analógico / D-pad: mover · A: tiro rápido · toque: arraste"
) {
    private class Shot(var x: Float, var y: Float, var vx: Float, val mine: Boolean, var alive: Boolean = true)
    private class Enemy(var x: Float, var y: Float, val kind: Int, val phase: Float, var hp: Int) {
        var shootT = 1.5f
        var alive = true
    }
    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Long)
    private class Star(var x: Float, val y: Float, val speed: Float)

    private val rnd = Random.Default
    private val shots = ArrayList<Shot>()
    private val enemies = ArrayList<Enemy>()
    private val sparks = ArrayList<Spark>()
    private val stars = ArrayList<Star>()
    private var px = 90f
    private var py = 225f
    private var lives = 3
    private var invuln = 0f
    private var fireT = 0f
    private var spawnT = 1f
    private var level = 1
    private var time = 0f

    init { reset() }

    override fun reset() {
        shots.clear(); enemies.clear(); sparks.clear(); stars.clear()
        for (i in 0 until 50) stars.add(Star(rnd.nextFloat() * 800f, rnd.nextFloat() * 450f, 20f + rnd.nextFloat() * 120f))
        px = 90f; py = 225f
        lives = 3; invuln = 1f; fireT = 0f; spawnT = 1f; level = 1; time = 0f
        score = 0
        over = false
    }

    private fun boom(x: Float, y: Float, color: Long, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2831f
            val sp = 40f + rnd.nextFloat() * 180f
            sparks.add(Spark(x, y, kotlin.math.cos(a) * sp, sin(a) * sp, 0.4f + rnd.nextFloat() * 0.4f, color))
        }
    }

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        time += dt
        for (s in stars) {
            s.x -= s.speed * dt
            if (s.x < 0f) s.x += 800f
        }
        px = (px + input.dx * 380f * dt).coerceIn(20f, 500f)
        py = (py + input.dy * 380f * dt).coerceIn(20f, 430f)
        if (invuln > 0f) invuln -= dt

        fireT -= dt
        val rate = if (input.fire) 0.11f else 0.22f
        if (fireT <= 0f) {
            fireT = rate
            shots.add(Shot(px + 26f, py, 620f, true))
        }

        level = 1 + score / 400
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = (1.3f - level * 0.07f).coerceAtLeast(0.45f)
            val kind = if (rnd.nextInt(4) == 0) 1 else 0
            enemies.add(Enemy(830f, 30f + rnd.nextFloat() * 390f, kind, rnd.nextFloat() * 6f, if (kind == 1) 3 else 1))
        }

        for (e in enemies) {
            val sp = (110f + level * 12f) * (if (e.kind == 1) 0.7f else 1f)
            e.x -= sp * dt
            e.y += sin(time * 2.2f + e.phase) * 60f * dt
            if (e.kind == 1) {
                e.shootT -= dt
                if (e.shootT <= 0f) {
                    e.shootT = 1.6f
                    shots.add(Shot(e.x - 18f, e.y, -260f, false))
                }
            }
            if (e.x < -40f) e.alive = false
        }
        for (s in shots) {
            s.x += s.vx * dt
            if (s.x < -20f || s.x > 840f) s.alive = false
        }

        for (s in shots) {
            if (!s.alive || !s.mine) continue
            for (e in enemies) {
                if (!e.alive) continue
                if (hypot(s.x - e.x, s.y - e.y) < 20f) {
                    s.alive = false
                    e.hp--
                    if (e.hp <= 0) {
                        e.alive = false
                        score += if (e.kind == 1) 40 else 15
                        boom(e.x, e.y, if (e.kind == 1) C.ORANGE else C.RED, 14)
                    }
                    break
                }
            }
        }
        if (invuln <= 0f) {
            for (e in enemies) {
                if (e.alive && hypot(e.x - px, e.y - py) < 28f) {
                    e.alive = false
                    hit()
                    break
                }
            }
            for (s in shots) {
                if (!s.mine && s.alive && hypot(s.x - px, s.y - py) < 16f) {
                    s.alive = false
                    hit()
                    break
                }
            }
        }
        for (sp in sparks) {
            sp.x += sp.vx * dt; sp.y += sp.vy * dt; sp.life -= dt
        }
        shots.removeAll { !it.alive }
        enemies.removeAll { !it.alive }
        sparks.removeAll { it.life <= 0f }
    }

    private fun hit() {
        boom(px, py, C.BLUE, 20)
        lives--
        invuln = 1.6f
        if (lives <= 0) finish()
    }

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(C.BG)
        for (s in stars) v.rect(s.x, s.y, 2f, 2f, C.alpha(C.WHITE, 0.25f + s.speed / 200f))
        for (e in enemies) {
            if (e.kind == 1) {
                v.circle(e.x, e.y, 18f, C.ORANGE)
                v.circle(e.x - 4f, e.y, 8f, 0xFF5D2A00L)
            } else {
                v.circle(e.x, e.y, 14f, C.RED)
                v.rect(e.x - 18f, e.y - 3f, 10f, 6f, C.shade(C.RED, 0.7f))
            }
        }
        for (s in shots) {
            if (s.mine) v.rect(s.x - 8f, s.y - 2f, 16f, 4f, C.YELLOW)
            else v.circle(s.x, s.y, 5f, C.RED)
        }
        val blink = invuln > 0f && ((time * 12f).toInt() % 2 == 0)
        if (!blink && !over) {
            v.rect(px - 18f, py - 10f, 36f, 20f, C.LILAC)
            v.rect(px + 10f, py - 5f, 16f, 10f, C.PURPLE)
            v.rect(px - 18f, py - 18f, 16f, 8f, C.PURPLE)
            v.rect(px - 18f, py + 10f, 16f, 8f, C.PURPLE)
            v.circle(px - 22f, py, 5f + (time * 30f).toInt() % 3, C.ORANGE)
        }
        for (sp in sparks) v.circle(sp.x, sp.y, 2.5f, C.alpha(sp.color, (sp.life * 2f).coerceIn(0f, 1f)))
        v.text("Pontos: $score", 12f, 24f, 20f, C.WHITE, false)
        v.text("Vidas: $lives   Nível: $level", 790f - 150f, 24f, 16f, C.LILAC, false)
    }
}
