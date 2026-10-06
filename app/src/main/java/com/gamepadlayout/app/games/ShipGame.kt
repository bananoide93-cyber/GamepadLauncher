package com.gamepadlayout.app.games

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Nave espacial horizontal: ondas de inimigos, um CHEFE a cada 5 níveis e upgrades escolhidos após cada chefe. */
class ShipGame : MiniGame(
    "ship", "Nave Espacial", "Destrua as naves, derrote os chefes a cada 5 níveis e escolha upgrades!",
    "Analógico / D-pad: mover · A: tiro rápido · nos upgrades: esquerda/direita e A · toque: arraste"
) {
    private class Shot(var x: Float, var y: Float, var vx: Float, val mine: Boolean, var vy: Float = 0f) {
        var alive = true
        var homing = false
        var pierce = false
        var dmg = 1
    }
    private class Enemy(var x: Float, var y: Float, val kind: Int, val phase: Float, var hp: Int) {
        var shootT = 1.5f
        var alive = true
    }
    private class Boss(var x: Float, var y: Float, val maxHp: Int, val idx: Int) {
        var hp = maxHp
        var t = 0f
        var atkT = 2f
        var pat = 0
        var enterT = 0f
        var alive = true
    }
    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Long)
    private class Star(var x: Float, val y: Float, val speed: Float)
    private class Upgrade(val id: Int, val name: String, val desc: String)

    private val rnd = Random.Default
    private val shots = ArrayList<Shot>()
    private val enemies = ArrayList<Enemy>()
    private val sparks = ArrayList<Spark>()
    private val stars = ArrayList<Star>()
    private var boss: Boss? = null
    private var px = 90f
    private var py = 225f
    private var lives = 3
    private var maxLives = 3
    private var invuln = 0f
    private var fireT = 0f
    private var missileT = 0f
    private var spawnT = 1f
    private var level = 1
    private var time = 0f
    private var lastBossLevel = 0
    private var bossesKilled = 0
    private var warn = 0f

    // upgrades
    private var shotLevel = 1
    private var rateMul = 1f
    private var speedMul = 1f
    private var shield = 0
    private var missiles = false
    private var pierce = false

    // escolha de upgrade
    private var choosing = false
    private var choices = listOf<Upgrade>()
    private var cursor = 0
    private var chosenFlash = 0f
    private var lastPick = ""

    private val pool = listOf(
        Upgrade(0, "TIRO MULTIPLO", "+1 canhao (ate 3)"),
        Upgrade(1, "CADENCIA", "Atira mais rapido"),
        Upgrade(2, "PROPULSORES", "Move 20% mais rapido"),
        Upgrade(3, "CASCO", "+1 vida e cura"),
        Upgrade(4, "ESCUDO", "Absorve 1 impacto"),
        Upgrade(5, "MISSEIS", "Misseis guiados"),
        Upgrade(6, "PERFURANTE", "Atravessa inimigos")
    )

    init { reset() }

    override fun reset() {
        shots.clear(); enemies.clear(); sparks.clear(); stars.clear(); boss = null
        for (i in 0 until 50) stars.add(Star(rnd.nextFloat() * 800f, rnd.nextFloat() * 450f, 20f + rnd.nextFloat() * 120f))
        px = 90f; py = 225f
        lives = 3; maxLives = 3; invuln = 1f; fireT = 0f; missileT = 0f; spawnT = 1f; level = 1; time = 0f
        lastBossLevel = 0; bossesKilled = 0; warn = 0f
        shotLevel = 1; rateMul = 1f; speedMul = 1f; shield = 0; missiles = false; pierce = false
        choosing = false; cursor = 0; chosenFlash = 0f; lastPick = ""
        score = 0
        over = false
    }

    private fun boom(x: Float, y: Float, color: Long, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2831f
            val sp = 40f + rnd.nextFloat() * 180f
            sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp, 0.4f + rnd.nextFloat() * 0.4f, color))
        }
    }

    private fun offerUpgrades() {
        val avail = pool.filter {
            when (it.id) {
                0 -> shotLevel < 3
                5 -> !missiles
                6 -> !pierce
                else -> true
            }
        }.shuffled(rnd)
        choices = avail.take(3)
        cursor = 0
        choosing = true
    }

    private fun apply(u: Upgrade) {
        when (u.id) {
            0 -> shotLevel = minOf(3, shotLevel + 1)
            1 -> rateMul *= 0.8f
            2 -> speedMul *= 1.2f
            3 -> { maxLives++; lives = maxLives }
            4 -> shield++
            5 -> missiles = true
            6 -> pierce = true
        }
        lastPick = u.name
        chosenFlash = 1.5f
    }

    private fun firePlayer(rate: Float) {
        fireT = rate
        val base = px + 26f
        fun add(vy: Float, dy: Float) {
            val s = Shot(base, py + dy, 640f, true, vy)
            s.pierce = pierce
            shots.add(s)
        }
        when (shotLevel) {
            1 -> add(0f, 0f)
            2 -> { add(0f, -7f); add(0f, 7f) }
            else -> { add(0f, 0f); add(-140f, -6f); add(140f, 6f) }
        }
    }

    private fun spawnBoss() {
        val idx = bossesKilled
        val hp = 45 + 25 * idx
        boss = Boss(900f, 225f, hp, idx).also { it.enterT = 0f }
        lastBossLevel = level
        warn = 2.2f
        enemies.clear()
    }

    private fun enemyShot(x: Float, y: Float, tx: Float, ty: Float, speed: Float) {
        val a = atan2(ty - y, tx - x)
        shots.add(Shot(x, y, cos(a) * speed, false, sin(a) * speed))
    }

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        if (chosenFlash > 0f) chosenFlash -= dt
        if (choosing) {
            if (input.stepX != 0) cursor = (cursor + input.stepX + choices.size) % choices.size
            if (input.firePressed) {
                apply(choices[cursor])
                choosing = false
            }
            return
        }
        time += dt
        for (s in stars) {
            s.x -= s.speed * dt
            if (s.x < 0f) s.x += 800f
        }
        val spd = 380f * speedMul
        px = (px + input.dx * spd * dt).coerceIn(20f, 500f)
        py = (py + input.dy * spd * dt).coerceIn(40f, 430f)
        if (invuln > 0f) invuln -= dt
        if (warn > 0f) warn -= dt

        fireT -= dt
        val rate = (if (input.fire) 0.11f else 0.22f) * rateMul
        if (fireT <= 0f) firePlayer(rate)
        if (missiles) {
            missileT -= dt
            if (missileT <= 0f) {
                missileT = 1.1f
                shots.add(Shot(px + 10f, py - 14f, 300f, true, -80f).also { it.homing = true; it.dmg = 3 })
                shots.add(Shot(px + 10f, py + 14f, 300f, true, 80f).also { it.homing = true; it.dmg = 3 })
            }
        }

        level = 1 + score / 400
        val b = boss
        if (b == null && level % 5 == 0 && level > lastBossLevel) spawnBoss()
        if (b == null) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawnT = (1.3f - level * 0.06f).coerceAtLeast(0.4f)
                val kind = if (rnd.nextInt(4) == 0) 1 else 0
                enemies.add(Enemy(830f, 40f + rnd.nextFloat() * 380f, kind, rnd.nextFloat() * 6f, if (kind == 1) 3 else 1))
            }
        } else updateBoss(b, dt)

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
            if (s.homing) {
                var best: Enemy? = null
                var bd = 1e9f
                for (e in enemies) { val d = hypot(e.x - s.x, e.y - s.y); if (e.alive && d < bd) { bd = d; best = e } }
                val tx = best?.x ?: boss?.x
                val ty = best?.y ?: boss?.y
                if (tx != null && ty != null) {
                    val a = atan2(ty - s.y, tx - s.x)
                    s.vx += (cos(a) * 520f - s.vx) * 4f * dt
                    s.vy += (sin(a) * 520f - s.vy) * 4f * dt
                }
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.x < -20f || s.x > 840f || s.y < -20f || s.y > 470f) s.alive = false
        }

        for (s in shots) {
            if (!s.alive || !s.mine) continue
            for (e in enemies) {
                if (!e.alive) continue
                if (hypot(s.x - e.x, s.y - e.y) < 20f) {
                    if (!s.pierce) s.alive = false
                    e.hp -= s.dmg
                    if (e.hp <= 0) {
                        e.alive = false
                        score += if (e.kind == 1) 40 else 15
                        boom(e.x, e.y, if (e.kind == 1) C.ORANGE else C.RED, 14)
                    }
                    if (!s.alive) break
                }
            }
            val bb = boss
            if (s.alive && bb != null && bb.enterT >= 1f && hypot(s.x - bb.x, (s.y - bb.y) * 0.8f) < 54f) {
                if (!s.pierce) s.alive = false
                bb.hp -= s.dmg
                boom(s.x, s.y, P.YELLOW, 3)
                if (bb.hp <= 0) killBoss(bb)
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
                if (!s.mine && s.alive && hypot(s.x - px, s.y - py) < 14f) {
                    s.alive = false
                    hit()
                    break
                }
            }
            val bb = boss
            if (bb != null && bb.alive && hypot(bb.x - px, (bb.y - py) * 0.8f) < 56f) hit()
        }
        for (sp in sparks) {
            sp.x += sp.vx * dt; sp.y += sp.vy * dt; sp.life -= dt
        }
        shots.removeAll { !it.alive }
        enemies.removeAll { !it.alive }
        sparks.removeAll { it.life <= 0f }
    }

    private fun updateBoss(b: Boss, dt: Float) {
        b.t += dt
        if (b.enterT < 1f) {
            b.enterT = minOf(1f, b.enterT + dt / 2f)
            b.x = 900f - 260f * b.enterT
            return
        }
        b.y = 225f + sin(b.t * (0.9f + b.idx * 0.15f)) * 140f
        b.atkT -= dt
        if (b.atkT <= 0f) {
            b.pat = (b.pat + 1) % 4
            val fast = 1f - minOf(0.4f, b.idx * 0.1f)
            when (b.pat) {
                0 -> { // 3 tiros mirados
                    for (k in -1..1) enemyShot(b.x - 40f, b.y, px, py + k * 50f, 280f)
                    b.atkT = 1.1f * fast
                }
                1 -> { // explosão radial
                    for (k in 0 until 10) {
                        val a = k * 6.2831f / 10f + b.t
                        shots.add(Shot(b.x, b.y, cos(a) * 200f, false, sin(a) * 200f))
                    }
                    b.atkT = 1.4f * fast
                }
                2 -> { // chuva vertical
                    for (k in 0 until 6) shots.add(Shot(b.x - 80f - k * 55f, 20f + rnd.nextFloat() * 20f, -30f, false, 190f))
                    b.atkT = 1.2f * fast
                }
                else -> { // reforços
                    enemies.add(Enemy(b.x - 30f, b.y - 90f, 0, 0f, 1))
                    enemies.add(Enemy(b.x - 30f, b.y + 90f, 0, 3f, 1))
                    enemyShot(b.x - 40f, b.y, px, py, 320f)
                    b.atkT = 1.3f * fast
                }
            }
        }
    }

    private fun killBoss(b: Boss) {
        b.alive = false
        boss = null
        bossesKilled++
        score += 300
        for (i in 0 until 5) boom(b.x + (i - 2) * 22f, b.y + (i % 2) * 20f - 10f, if (i % 2 == 0) P.ORANGE else P.YELLOW, 20)
        for (sh in shots) if (!sh.mine) sh.alive = false
        for (en in enemies) en.alive = false
        offerUpgrades()
    }

    private fun hit() {
        if (shield > 0) {
            shield--
            invuln = 1.2f
            boom(px, py, P.CYAN, 14)
            return
        }
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
    private val bossSpr = Sprite(
        listOf(
            "......SSSSSSSSSS......",
            "....SSTTTTTTTTTTSS....",
            "..SSTTHHTTTTTTHHTTSS..",
            ".STTTTTTTTTTTTTTTTTTS.",
            "STTTWWWTTTTTTTWWWTTTTS",
            "STTTWKKTTTTTTTWKKTTTTS",
            "STTTTTTTTSSTTTTTTTTTTS",
            "STTTTTTTSTTSTTTTTTTTTS",
            ".STTSSSSSKSKSKSSSSTTS.",
            ".SSTTTTTTTTTTTTTTTSS..",
            "..SSS.SSTTTTSS.SSS....",
            "...S....SSSS....S....."
        ), pal
    )

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(0xFF0E1230L)
        val bands = longArrayOf(0xFF0E1230L, 0xFF121838L, 0xFF161E40L, 0xFF1A2448L, 0xFF1E2A50L)
        for (i in bands.indices) v.rect(0f, i * 90f, 800f, 91f, bands[i])
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
        val b = boss
        if (b != null) {
            val tints = longArrayOf(P.RED, P.VIOLET, P.GREEN, P.SKY, P.ORANGE)
            bossSpr.draw(v, b.x - 66f, b.y - 36f, 6f, tint = tints[b.idx % tints.size])
            // barra de vida
            v.rect(250f, 38f, 300f, 12f, P.INK)
            v.rect(252f, 40f, 296f * (b.hp.toFloat() / b.maxHp).coerceIn(0f, 1f), 8f, P.RED)
            v.pixText("CHEFE", 400f, 54f, 2f, P.WHITE, center = true)
        }
        for (s in shots) {
            if (s.mine) {
                if (s.homing) {
                    v.rect(s.x - 5f, s.y - 3f, 10f, 6f, P.CYAN)
                    v.rect(s.x - 9f, s.y - 1f, 5f, 2f, P.ORANGE)
                } else {
                    v.rect(s.x - 9f, s.y - 3f, 18f, 6f, P.ORANGE)
                    v.rect(s.x - 7f, s.y - 2f, 16f, 4f, P.YELLOW)
                    v.rect(s.x - 1f, s.y - 1f, 8f, 2f, P.WHITE)
                }
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
            if (shield > 0) v.pixDisc(px, py, 30f, C.alpha(P.CYAN, 0.25f), 3f)
        }
        for (sp in sparks) {
            val a = (sp.life * 2f).coerceIn(0f, 1f)
            v.rect(sp.x - 2f, sp.y - 2f, 4f, 4f, C.alpha(sp.color, a))
        }
        v.hudBar("PONTOS $score", "VIDAS $lives/$maxLives  NIVEL $level")
        if (warn > 0f && (warn * 4f).toInt() % 2 == 0) v.pixText("ALERTA - CHEFE!", 400f, 150f, 6f, P.RED, center = true)
        if (chosenFlash > 0f && !choosing) v.pixText("UPGRADE: $lastPick", 400f, 120f, 3f, P.LIME, center = true)
        if (choosing) drawChoice(v)
    }

    private fun drawChoice(v: View) {
        v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.7f))
        v.pixText("CHEFE DERROTADO!", 400f, 50f, 5f, P.YELLOW, center = true)
        v.pixText("ESCOLHA UM UPGRADE", 400f, 100f, 3f, P.WHITE, center = true)
        for (i in choices.indices) {
            val x = 60f + i * 240f
            val sel = i == cursor
            v.bevel(x, 160f + if (sel) -8f else 0f, 220f, 200f, if (sel) P.VIOLET else P.DUSK, 4f)
            if (sel) v.rect(x - 4f, 152f, 228f, 4f, P.YELLOW)
            v.pixText(choices[i].name, x + 110f, 190f + if (sel) -8f else 0f, 2.4f, P.WHITE, center = true)
            v.pixText(choices[i].desc, x + 110f, 250f + if (sel) -8f else 0f, 1.6f, P.LILAC, center = true, shadow = false)
        }
        v.pixText("ESQ/DIR ESCOLHE  A CONFIRMA", 400f, 400f, 2.2f, P.CYAN, center = true, shadow = false)
    }
}
