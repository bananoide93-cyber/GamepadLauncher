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

    private val pal = mapOf('V' to P.VIOLET, 'L' to P.LILAC, 'C' to P.CYAN, 'W' to P.WHITE, 'K' to P.INK)
    private val shipSpr = Sprite(
        listOf(
            "....VV.........",
            "....VLV........",
            "VV..VLLVV......",
            "VLVVLLLLLVVVV..",
            "VLLLLLLLLCCCLV.",
            "VLVVLLLLLVVVV..",
            "VV..VLLVV......",
            "....VLV........",
            "....VV........."
        ), pal
    )
    private val alienA = Sprite(
        listOf(
            "..SSSSSS..",
            ".STTTTTTS.",
            "STTWTTWTTS",
            "STTKTTKTTS",
            "STTTTTTTTS",
            ".S.STTS.S.",
            "S..S..S..S"
        ), pal
    )
    private val alienB = Sprite(
        listOf(
            "..SSSSSS..",
            ".STTTTTTS.",
            "STTWTTWTTS",
            "STTKTTKTTS",
            "STTTTTTTTS",
            "..S.TT.S..",
            ".S..S..S.."
        ), pal
    )
    private val bigSpr = Sprite(
        listOf(
            "....SSSSSS....",
            "..SSTTTTTTSS..",
            ".STTHHTTTTTTS.",
            "STTTTTTTTTTTTS",
            "STWWTTTTTTWWTS",
            "STWKTTTTTTWKTS",
            "STTTTTTTTTTTTS",
            ".SSTTSSSSTTSS.",
            "..S.S....S.S..",
            "..S........S.."
        ), pal
    )

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(0xFF0E1230L)
        val bands = longArrayOf(0xFF0E1230L, 0xFF121838L, 0xFF161E40L, 0xFF1A2448L, 0xFF1E2A50L)
        for (i in bands.indices) v.rect(0f, i * 90f, 800f, 91f, bands[i])
        // lua distante que rola devagar
        val mx = 700f - (time * 6f) % 1000f
        v.pixDisc(if (mx < -80f) mx + 1000f else mx, 110f, 52f, 0xFF3A3F7AL)
        v.pixDisc(if (mx < -80f) mx + 1000f - 12f else mx - 12f, 100f, 36f, 0xFF4A5090L)
        for (s in stars) {
            val big = s.speed > 90f
            v.rect(s.x, s.y, if (big) 3f else 2f, if (big) 3f else 2f, C.alpha(P.WHITE, 0.25f + s.speed / 200f))
        }
        val frame = (time * 6f).toInt() % 2 == 0
        for (e in enemies) {
            if (e.kind == 1) bigSpr.draw(v, e.x - 21f, e.y - 15f, 3f, tint = P.ORANGE)
            else (if (frame) alienA else alienB).draw(v, e.x - 16f, e.y - 11f, 3.2f, tint = P.RED)
        }
        for (s in shots) {
            if (s.mine) {
                v.rect(s.x - 9f, s.y - 3f, 18f, 6f, P.ORANGE)
                v.rect(s.x - 7f, s.y - 2f, 16f, 4f, P.YELLOW)
                v.rect(s.x - 1f, s.y - 1f, 8f, 2f, P.WHITE)
            } else {
                v.rect(s.x - 3f, s.y - 6f, 6f, 12f, P.RED)
                v.rect(s.x - 6f, s.y - 3f, 12f, 6f, P.RED)
                v.rect(s.x - 2f, s.y - 2f, 4f, 4f, P.YELLOW)
            }
        }
        val blink = invuln > 0f && ((time * 12f).toInt() % 2 == 0)
        if (!blink && !over) {
            val f = 6f + (time * 30f).toInt() % 3 * 3f
            v.rect(px - 22f - f, py - 3f, f, 6f, P.ORANGE)
            v.rect(px - 22f - f / 2f, py - 1.5f, f / 2f, 3f, P.YELLOW)
            shipSpr.draw(v, px - 20f, py - 12f, 2.7f)
        }
        for (sp in sparks) {
            val a = (sp.life * 2f).coerceIn(0f, 1f)
            v.rect(sp.x - 2f, sp.y - 2f, 4f, 4f, C.alpha(sp.color, a))
        }
        v.hudBar("PONTOS $score", "VIDAS $lives  NIVEL $level")
    }
}
