package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================================================== inimigos

internal fun AdventureGame.canParry(e: En, a: AtkDef) = a.parry && e.parryImmune <= 0f

internal fun AdventureGame.enWalk(e: En, x: Float, z: Float, fx: Float, fz: Float): Boolean {
    if (x < 3f || z < 3f || x > AdvWorld.SIZE - 3f || z > AdvWorld.SIZE - 3f) return false
    val h = AdvWorld.hAt(x, z)
    if (h < (if (e.kind == AdvEnemies.FROG) -0.9f else AdvWorld.WATER) - 0.05f && !e.def.flying) return false
    if (!e.def.flying) {
        val step = max(0.05f, dist(x, z, fx, fz))
        if ((h - AdvWorld.hAt(fx, fz)) / step > 1.9f) return false
        if (AdvWorld.blockedByProps(x, z, e.def.rad * 0.6f)) return false
    }
    for (d in AdvWorld.doors) if (!doorOpen(d)) {
        val dx = x - d.x; val dz = z - d.z
        val c = cos(d.yaw); val s = sin(d.yaw)
        val lx = dx * c - dz * s; val lz = dx * s + dz * c
        if (abs(lx) < 2.6f + e.def.rad && abs(lz) < 0.8f + e.def.rad) return false
    }
    if (e.spawnIdx >= 9000) {
        val a = AdvWorld.arenas[e.spawnIdx - 9000]
        if (dist(x, z, a[0], a[1]) > AdvWorld.arenaR[e.spawnIdx - 9000] + 0.5f) return false
    }
    return true
}

internal fun AdventureGame.enMove(e: En, dx: Float, dz: Float): Boolean {
    var moved = false
    if (enWalk(e, e.x + dx, e.z, e.x, e.z)) { e.x += dx; moved = true }
    if (enWalk(e, e.x, e.z + dz, e.x, e.z)) { e.z += dz; moved = true }
    return moved
}

internal fun AdventureGame.updateEnemies(dt: Float) {
    var anyAggro = false
    for (e in ens.toList()) {
        if (!e.alive) { e.deadT += dt; continue }
        val d = dist(e.x, e.z, px, pz)
        if (d > 80f && !e.boss && e.st != ES.FLEE) continue
        aiStep(e, dt, d)
        if (e.aggro) anyAggro = true
    }
    ens.removeAll { !it.alive && it.deadT > 3.4f }
    // evita que os inimigos se sobreponham
    for (i in 0 until ens.size) {
        val a = ens[i]
        if (!a.alive || !a.aggro || a.def.flying) continue
        for (j in i + 1 until ens.size) {
            val b = ens[j]
            if (!b.alive || !b.aggro || b.def.flying) continue
            val dx = b.x - a.x; val dz = b.z - a.z
            val dd = sqrt(dx * dx + dz * dz); val mn = (a.def.rad * a.def.sc + b.def.rad * b.def.sc) * 0.8f
            if (dd < mn && dd > 0.001f) { val k = (mn - dd) * 0.5f; enMove(a, -dx / dd * k, -dz / dd * k); enMove(b, dx / dd * k, dz / dd * k) }
        }
    }
    if (anyAggro && playSec - lastCombatTip > 1f) giveTip("tip_lock")
}

private fun AdventureGame.faceTo(e: En, a: Float, dt: Float, rate: Float = 9f) { e.yaw = lerpAng(e.yaw, a, dt * rate) }

private fun AdventureGame.startWind(e: En, idx: Int) {
    val a = e.def.atks[idx]
    e.atk = idx; e.st = ES.WIND; e.t = 0f; e.hitDone = false; e.parried = false; e.lastAtk = idx; e.actTimer = 0f
    e.tx = px; e.tz = pz; e.spikesDone = 0
    if (a.kind == AK.CHARGE || a.kind == AK.LEAP) { val d = max(0.1f, dist(e.x, e.z, px, pz)); val lead = min(d, if (a.kind == AK.CHARGE) 16f else d); e.tx = e.x + (px - e.x) / d * lead; e.tz = e.z + (pz - e.z) / d * lead }
    if (e.def.boss || a.kind == AK.CHARGE) snd(Sfx.WARN)
}

private fun AdventureGame.pickAttack(e: En, d: Float): Int {
    var total = 0
    val w = IntArray(e.def.atks.size)
    for ((i, a) in e.def.atks.withIndex()) {
        if (a.phase > e.phase || d < a.minD || d > a.maxD) continue
        w[i] = a.w
        if (e.def.boss && i == e.lastAtk) w[i] = max(0, a.w - 2)
        total += w[i]
    }
    if (total <= 0) return -1
    var r = rnd.nextInt(total)
    for (i in w.indices) { if (r < w[i]) return i; r -= w[i] }
    return -1
}

private fun AdventureGame.aiStep(e: En, dt: Float, d: Float) {
    val def = e.def
    if (e.flash > 0f) e.flash -= dt
    if (e.parryImmune > 0f) e.parryImmune -= dt
    e.poiseLock -= dt
    if (e.poiseLock <= 0f && e.poise < def.poise && e.st != ES.STUN) e.poise = min(def.poise, e.poise + (if (def.boss) 5f else 9f) * dt)
    e.cd -= dt
    e.t += dt
    val gy = AdvWorld.hAt(e.x, e.z)
    e.y = if (def.flying) gy + 1.7f + sin(time * 3f + e.spawnIdx) * 0.25f else gy
    val ang = atan2(px - e.x, pz - e.z)
    if (def.boss) bossPhase(e)
    e.guardUp = def.shield && def.beh == Beh.GUARD && (e.st == ES.CHASE || e.st == ES.IDLE || e.st == ES.STRAFE || e.st == ES.REC && e.t > 0.3f && false)
    if (def.training) e.hp = def.hp
    if (!e.aggro && e.st != ES.INTRO) {
        if (def.boss) { e.st = ES.IDLE }
        else if (d < def.sight && pAct != PA.DEAD) { e.aggro = true; e.seen = true; e.st = ES.CHASE; e.t = 0f; e.cd = 0.5f + rnd.nextFloat() * 0.6f }
    }
    var moving = false
    val spd = def.speed
    when (e.st) {
        ES.IDLE -> {
            if (!def.boss) {
                e.wander -= dt
                if (e.wander <= 0f) { e.wander = 2f + rnd.nextFloat() * 3f; val a = rnd.nextFloat() * 6.28f; val r = rnd.nextFloat() * 4f; e.wx = e.hx + cos(a) * r; e.wz = e.hz + sin(a) * r }
                val dd = dist(e.x, e.z, e.wx, e.wz)
                if (dd > 0.6f) { val a = atan2(e.wx - e.x, e.wz - e.z); faceTo(e, a, dt, 5f); moving = enMove(e, sin(a) * spd * 0.4f * dt, cos(a) * spd * 0.4f * dt) }
            } else faceTo(e, e.yaw, dt)
            e.amt = if (moving) 0.7f else 0f
        }
        ES.INTRO -> {
            faceTo(e, ang, dt, 4f)
            e.amt = 0f
            if (e.t >= e.introDur) { e.st = ES.CHASE; e.t = 0f; e.cd = 0.8f; e.introDone = true; e.aggro = true }
        }
        ES.CHASE, ES.STRAFE -> {
            // perder o interesse
            val homeD = dist(e.x, e.z, e.hx, e.hz)
            if (!def.boss && (d > def.sight * 2.4f || homeD > 55f)) { e.aggro = false; e.st = ES.RETREAT; e.t = 0f; return }
            faceTo(e, ang, dt, if (def.beh == Beh.AGGR) 7f else 9f)
            if (def.flying) chaseBat(e, dt, d, ang) else when (def.beh) {
                Beh.FLEE -> {
                    if (d > 1.5f) moving = enMove(e, sin(ang) * spd * dt, cos(ang) * spd * dt)
                    else if (gems > 0 && iT <= 0f) {
                        val n = max(8, (gems * 0.12f).toInt()).coerceAtMost(gems)
                        gems -= n; e.stolen += n; popupMsg("ROUBADO!", P.RED); snd(Sfx.DENIED); say("A lebre roubou $n gemas! Pegue-a!")
                        e.st = ES.FLEE; e.t = 0f
                    } else { e.st = ES.FLEE; e.t = 0f }
                }
                Beh.RANGED -> {
                    if (d < 5f) { moving = enMove(e, -sin(ang) * spd * 1.1f * dt, -cos(ang) * spd * 1.1f * dt); e.amt = 1f }
                    else if (d > 14f) { moving = enMove(e, sin(ang) * spd * dt, cos(ang) * spd * dt) }
                    else { val s = e.strafeDir; moving = enMove(e, cos(ang) * s * spd * 0.5f * dt, -sin(ang) * s * spd * 0.5f * dt) }
                    if (e.cd <= 0f) { val i = pickAttack(e, d); if (i >= 0) startWind(e, i) else e.cd = 0.3f }
                }
                else -> {
                    val minR = if (def.boss) 2.4f * def.sc * 0.8f else 1.6f * def.sc * 0.8f
                    val atkReach = def.atks.maxOf { if (it.phase <= e.phase) it.maxD else 0f }
                    if (e.cd > 0f && d < 7.5f && d > 1.8f && def.beh != Beh.AGGR && !def.training) {
                        // circular ao redor do jogador, mantendo distância
                        val s = e.strafeDir
                        moving = enMove(e, (cos(ang) * s * 0.6f + sin(ang) * (if (d > atkReach * 0.85f) 0.5f else -0.1f)) * spd * dt, (-sin(ang) * s * 0.6f + cos(ang) * (if (d > atkReach * 0.85f) 0.5f else -0.1f)) * spd * dt)
                    } else if (d > minR) {
                        moving = enMove(e, sin(ang) * spd * dt, cos(ang) * spd * dt)
                    }
                    if (e.cd <= 0f && !(def.training && d > 5f)) { val i = pickAttack(e, d); if (i >= 0) startWind(e, i) else if (!def.boss) e.cd = 0.15f }
                }
            }
            if (rnd.nextFloat() < dt * 0.25f) e.strafeDir = -e.strafeDir
            e.amt = if (moving) (if (spd > 3f) 1.6f else 1f) else 0f
            if (e.st == ES.CHASE) e.hopT += dt
        }
        ES.WIND -> {
            val a = def.atks[e.atk]
            if (e.t < a.wind * 0.55f) faceTo(e, ang, dt, 10f)
            // aproxima durante o preparo para o golpe alcançar o jogador
            if ((a.kind == AK.ARC || a.kind == AK.THRUST) && d > a.reach * 0.7f && e.t < a.wind * 0.8f) enMove(e, sin(e.yaw) * spd * 0.9f * dt, cos(e.yaw) * spd * 0.9f * dt)
            if (a.kind == AK.LEAP && e.t > a.wind * 0.5f && false) {}
            if (e.t >= a.wind) { e.st = ES.ACT; e.t = 0f; e.hitDone = false; startAct(e, a) }
        }
        ES.ACT -> actStep(e, dt)
        ES.REC -> {
            val a = def.atks[e.atk.coerceIn(0, def.atks.size - 1)]
            if (e.t >= a.rec) { e.st = ES.CHASE; e.t = 0f; e.cd = (if (def.beh == Beh.RANGED) 1.3f + rnd.nextFloat() else if (def.boss) 0.15f + rnd.nextFloat() * 0.5f else if (def.training) 2.2f else 0.35f + rnd.nextFloat() * 0.9f) }
        }
        ES.HURT -> {
            e.vx *= 0.88f; e.vz *= 0.88f
            enMove(e, e.vx * dt, e.vz * dt)
            if (e.t >= e.hurtT) { e.st = if (e.aggro) ES.CHASE else ES.IDLE; e.t = 0f; e.cd = max(e.cd, 0.25f) }
        }
        ES.STUN -> {
            e.vx *= 0.85f; e.vz *= 0.85f
            enMove(e, e.vx * dt, e.vz * dt)
            if (e.t >= e.stunT) { e.st = ES.CHASE; e.t = 0f; e.cd = 0.3f; if (def.boss) e.parryImmune = 3.5f }
        }
        ES.FLEE -> {
            val a = atan2(e.x - px, e.z - pz) + sin(e.t * 3f) * 0.6f
            faceTo(e, a, dt, 10f)
            moving = enMove(e, sin(a) * spd * 1.25f * dt, cos(a) * spd * 1.25f * dt)
            e.amt = 1.6f
            if (d > 42f) { e.alive = false; e.st = ES.DEAD; say("A lebre escapou com suas gemas...") }
        }
        ES.RETREAT -> {
            val a = atan2(e.hx - e.x, e.hz - e.z)
            faceTo(e, a, dt, 6f)
            moving = enMove(e, sin(a) * spd * 1.1f * dt, cos(a) * spd * 1.1f * dt)
            e.amt = 1f
            if (dist(e.x, e.z, e.hx, e.hz) < 1.5f || e.t > 8f) { e.st = ES.IDLE; e.hp = def.hp; e.poise = def.poise; e.t = 0f }
        }
        ES.DEAD -> {}
    }
    // animação
    e.phi += dt * (if (e.amt > 1.2f) 11f else 8f) * (if (moving || e.amt > 0.1f) 1f else 0f)
    animFor(e)
}

private fun AdventureGame.animFor(e: En) {
    val def = e.def
    var an = AN.IDLE; var t = 0f; var h = 0.4f
    when (e.st) {
        ES.WIND, ES.ACT, ES.REC -> {
            val a = def.atks[e.atk.coerceIn(0, def.atks.size - 1)]
            an = a.an; val tot = a.total
            val el = when (e.st) { ES.WIND -> e.t; ES.ACT -> a.wind + e.t; else -> a.wind + a.act + e.t }
            t = (el / tot).coerceIn(0f, 1f); h = a.wind / tot
        }
        ES.HURT -> { an = AN.HURT_L; t = (e.t / max(0.1f, e.hurtT)).coerceIn(0f, 1f) }
        ES.STUN -> { an = AN.STUN; t = (e.t / max(0.1f, e.stunT)).coerceIn(0f, 1f) }
        ES.DEAD -> { an = AN.DEAD; t = (e.deadT / 1.0f).coerceIn(0f, 1f) }
        ES.FLEE -> an = AN.FLEE
        ES.INTRO -> { an = AN.ROAR; t = (e.t / max(0.1f, e.introDur)).coerceIn(0f, 1f) }
        else -> { an = if (e.guardUp) AN.GUARD else AN.IDLE }
    }
    e.animId = an; e.animT = t
    val ws = when (def.weapon) { "arco" -> WS.BOW; "club" -> WS.CLUB; "machado" -> WS.AXE; else -> WS.SWORD }
    AdvAnim.target(e.anim.tgt, an, t, h, ws, e.phi, e.amt, time + e.spawnIdx * 0.37f, 0f)
    AdvAnim.blend(e.anim, 1f / 60f, if (e.st == ES.WIND || e.st == ES.ACT) 26f else 14f)
}

private fun AdventureGame.chaseBat(e: En, dt: Float, d: Float, ang: Float) {
    e.phi2 += dt * 1.6f
    val ox = px + cos(e.phi2 + e.spawnIdx) * 5f; val oz = pz + sin(e.phi2 + e.spawnIdx) * 5f
    val a = atan2(ox - e.x, oz - e.z)
    enMove(e, sin(a) * def2(e).speed * dt, cos(a) * def2(e).speed * dt)
    e.amt = 1f
    if (e.cd <= 0f && d < 9f) startWind(e, 0)
}

private fun def2(e: En) = e.def

private fun AdventureGame.startAct(e: En, a: AtkDef) {
    when (a.kind) {
        AK.SHOT -> {
            val d = max(0.1f, dist(e.x, e.z, px, pz)); val sp = 15f
            val lx = px + pvx * (d / sp) * 0.5f; val lz = pz + pvz * (d / sp) * 0.5f
            val dd = max(0.1f, dist(e.x, e.z, lx, lz))
            val p = Proj(e.x + sin(e.yaw) * 0.9f, e.y + 1.3f, e.z + cos(e.yaw) * 0.9f, (lx - e.x) / dd * sp, (lz - e.z) / dd * sp, a.dmg, 2.2f, 0, a.parry); p.src = e
            projs.add(p); snd(Sfx.SHOOT)
        }
        AK.ORBS -> {
            val n = if (e.phase >= 2) 5 else 3
            val base = atan2(px - e.x, pz - e.z)
            for (i in 0 until n) {
                val ang = base + (i - (n - 1) / 2f) * 0.28f
                val p = Proj(e.x + sin(ang) * 1.5f, e.y + 1.8f, e.z + cos(ang) * 1.5f, sin(ang) * 8.5f, cos(ang) * 8.5f, a.dmg, 3.5f, 1, a.parry); p.src = e; projs.add(p)
            }
            snd(Sfx.MAGIC)
        }
        AK.SUMMON -> {
            val alive = ens.count { it.alive && it.spawnIdx >= 8000 && it.spawnIdx < 9000 }
            if (alive < 4) for (i in 0 until 2) {
                val ang = rnd.nextFloat() * 6.28f
                val x = e.x + cos(ang) * 4f; val z = e.z + sin(ang) * 4f
                if (!enWalk(e, x, z, e.x, e.z)) continue
                val s = En(AdvEnemies.defs[AdvEnemies.SLIME]!!, x, z, 8000 + rnd.nextInt(900)); s.aggro = true; s.st = ES.CHASE; s.y = AdvWorld.hAt(x, z)
                ens.add(s); spark(x, s.y + 0.5f, z, 8, 0xFF6BCB5BL, 3f)
            }
            snd(Sfx.BOSS_ROAR); doShake(0.5f, 0.4f)
        }
        AK.WAVE -> { rings.add(Ring(e.x, e.z, 1f, a.rad, a.dmg)); snd(Sfx.BOSS_ROAR); doShake(0.6f, 0.5f); spark(e.x, e.y + 1f, e.z, 20, 0xFFFFE066L, 7f, 1.5f) }
        AK.SPIKES -> {
            val d = max(0.1f, dist(e.x, e.z, px, pz))
            val ux = (px - e.x) / d; val uz = (pz - e.z) / d
            for (i in 1..7) spikes.add(Spike(e.x + ux * (2.4f + i * 2.0f), e.z + uz * (2.4f + i * 2.0f), -0.55f - i * 0.12f, a.dmg))
            snd(Sfx.MAGIC)
        }
        AK.SLAM -> {}
    }
    if (a.kind == AK.LEAP || a.kind == AK.CHARGE) snd(Sfx.LEAP)
    else if (a.kind != AK.SHOT && a.kind != AK.ORBS && a.kind != AK.SUMMON && a.kind != AK.WAVE && a.kind != AK.SPIKES) snd(if (a.dmg >= 20f) Sfx.SWING_HEAVY else Sfx.SWING)
}

private fun AdventureGame.actStep(e: En, dt: Float) {
    val a = e.def.atks[e.atk]
    val d = dist(e.x, e.z, px, pz)
    val rE = e.def.rad * e.def.sc
    when (a.kind) {
        AK.ARC -> if (!e.hitDone && d - rE < a.reach && abs(wrap(atan2(px - e.x, pz - e.z) - e.yaw)) < a.arc / 2f + 0.15f) hitP(e, a, e.x, e.z, AK.ARC)
        AK.THRUST -> if (!e.hitDone) {
            val dx = px - e.x; val dz = pz - e.z
            val fwd = dx * sin(e.yaw) + dz * cos(e.yaw); val side = abs(dx * cos(e.yaw) - dz * sin(e.yaw))
            if (fwd > 0f && fwd - rE < a.reach && side < 1.0f) hitP(e, a, e.x, e.z, AK.THRUST)
        }
        AK.SLAM -> if (!e.hitDone && e.t >= 0.02f) {
            e.hitDone = true
            val cx = e.x + sin(e.yaw) * a.reach * 0.8f; val cz = e.z + cos(e.yaw) * a.reach * 0.8f
            snd(Sfx.SLAM); doShake(0.45f, 0.3f); spark(cx, e.y + 0.3f, cz, 14, 0xFFD8C8A0L, 5f, 1.6f)
            if (dist(px, pz, cx, cz) < a.rad) hitP(e, a, cx, cz, AK.SLAM, true)
        }
        AK.SPIN -> {
            val ticks = if (a.act > 0.6f) 2 else 1
            if (e.spikesDone < ticks && e.t >= a.act * (0.2f + 0.5f * e.spikesDone)) {
                e.spikesDone++
                snd(Sfx.SWING_HEAVY)
                if (d - 0.4f < a.rad) { val r = enemyHitsPlayer(e, a.dmg, e.x, e.z, AK.SPIN, false, a.knock); if (r != 0) e.hitDone = true }
            }
            e.yaw += dt * 9f
        }
        AK.LEAP -> {
            val k = (e.t / max(0.05f, a.act)).coerceIn(0f, 1f)
            val dx = e.tx - e.x; val dz = e.tz - e.z; val dd = max(0.05f, sqrt(dx * dx + dz * dz))
            val sp = min(dd / max(0.05f, a.act * (1f - k) + 0.02f), 18f)
            enMove(e, dx / dd * sp * dt, dz / dd * sp * dt)
            e.y += sin(k * PI.toFloat()) * (if (e.def.boss) 3f else 1.1f)
            if (a.parry) { if (!e.hitDone && d - rE < a.rad) hitP(e, a, e.x, e.z, AK.LEAP) }
            else if (!e.hitDone && k >= 0.85f) {
                e.hitDone = true
                snd(Sfx.SLAM); doShake(0.5f, 0.3f); spark(e.x, e.y + 0.3f, e.z, 14, 0xFFD8C8A0L, 5f, 1.6f)
                if (d < a.rad) hitP(e, a, e.x, e.z, AK.LEAP, true)
            }
        }
        AK.CHARGE -> {
            val dx = e.tx - e.x; val dz = e.tz - e.z; val dd = max(0.05f, sqrt(dx * dx + dz * dz))
            val sp = if (e.def.boss) 15f else if (e.kind == AdvEnemies.BAT) 11f else 12f
            val ux = dx / dd; val uz = dz / dd
            e.yaw = lerpAng(e.yaw, atan2(ux, uz), 0.3f)
            if (dd > 0.6f) { if (!enMove(e, ux * sp * dt, uz * sp * dt)) { e.t = a.act; spark(e.x, e.y + 1f, e.z, 8, 0xFFD8C8A0L, 4f); if (e.boss) doShake(0.4f, 0.3f) } }
            if (!e.hitDone && d - rE < 1.0f) hitP(e, a, e.x, e.z, AK.CHARGE, !a.parry)
        }
        else -> {}
    }
    if (e.t >= a.act) {
        // encadeia o próximo golpe do combo
        if (a.chain >= 0 && rnd.nextFloat() < a.chainP && d < e.def.atks[a.chain].maxD * 1.25f && e.def.atks[a.chain].phase <= e.phase) { startWind(e, a.chain); return }
        e.st = ES.REC; e.t = 0f
    }
}

/** O ataque do inimigo acerta (ou tenta acertar) o jogador. */
private fun AdventureGame.hitP(e: En, a: AtkDef, sx: Float, sz: Float, ak: Int, area: Boolean = false) {
    val res = enemyHitsPlayer(e, a.dmg * (if (e.def.training) 1f else 1f), sx, sz, ak, canParry(e, a) && !area, a.knock)
    if (res != 0) {
        e.hitDone = true
        if (res == 3 && e.alive) { /* aparado: o inimigo já foi atordoado */ }
    }
}

// ---------------------------------------------------------------------------------------------- dano no jogador

private fun AdventureGame.frontal(sx: Float, sz: Float, lim: Float = 1.35f) = abs(wrap(angTo(sx, sz) - pyaw)) < lim

/** 0 = errou (invencível), 1 = ferido, 2 = bloqueado, 3 = aparado. */
internal fun AdventureGame.enemyHitsPlayer(e: En?, dmgIn: Float, sx: Float, sz: Float, ak: Int, parryable: Boolean, knock: Float): Int {
    if (pAct == PA.DEAD || mode != Mode.PLAY) return 0
    if (iT > 0f) {
        if (pAct == PA.ROLL || pAct == PA.BACK) { if (e != null && e.def.training) flags.add("tr_roll"); if (!flags.contains("dodge_pop")) { popupMsg("ESQUIVOU!", P.CYAN); flags.add("dodge_pop") } }
        return 0
    }
    val dmg = dmgIn * (if (e != null && e.def.training) 1f else 1f)
    val blockable = ak == AK.ARC || ak == AK.THRUST || ak == AK.SHOT || ak == AK.ORBS || ak == AK.CHARGE
    val fr = frontal(sx, sz) || (e != null && dist(e.x, e.z, px, pz) < 1.2f && frontal(e.x, e.z))
    // aparo
    if (parryable && parryT > 0f && fr) {
        doParry(e)
        return 3
    }
    // bloqueio
    if (guardHeld && blockable && fr && pAct == PA.FREE) {
        val sd = sdef()
        val cost = sd.cost * (1f + dmg / 30f) * (if (parryable) 1f else 1.5f)
        pvx = (px - sx) / max(0.1f, dist(px, pz, sx, sz)) * 3.2f; pvz = (pz - sz) / max(0.1f, dist(px, pz, sx, sz)) * 3.2f
        stam -= cost; stamDelay = 1.0f
        if (stam <= 0f) {
            stam = 0f
            hp -= dmg * 0.5f; pAct = PA.STUN; pT = 0f; pDur = 1.0f; guardHeld = false; snd(Sfx.GUARD_BREAK); doShake(0.5f, 0.3f); hitStop = 0.1f
            popupMsg("GUARDA QUEBRADA!", P.RED)
            return 1
        }
        hp -= dmg * (1f - sd.block)
        iT = 0.25f; snd(Sfx.BLOCK); hitStop = 0.05f; doShake(0.15f, 0.1f)
        spark(px + sin(pyaw) * 0.8f, py + 1.2f, pz + cos(pyaw) * 0.8f, 8, 0xFFFFF3B0L, 5f)
        popupMsg("BLOQUEADO", P.SILVER)
        return 2
    }
    // ferimento
    hp -= dmg
    val dd = max(0.1f, dist(px, pz, sx, sz))
    val k = knock * (if (dmg >= 20f) 1.3f else 1f)
    pvx = (px - sx) / dd * k; pvz = (pz - sz) / dd * k
    iT = 0.65f; hurtT = 0.4f
    pAct = PA.HURT; pT = 0f; pDur = if (dmg >= 20f) 0.7f else 0.38f
    combo = 0; comboQueued = false; parryT = 0f; guardHeld = false; chargeT = 0f; heavyHeld = 0f
    snd(Sfx.HURT); hitStop = 0.07f; doShake(0.35f, 0.25f); flash(0xFFFF3030L, 0.18f)
    spark(px, py + 1.2f, pz, 8, 0xFFFF5252L, 4f)
    if (parryable) giveTip("tip_guard")
    return 1
}

private fun AdventureGame.doParry(e: En?) {
    parryT = 0f; parryRec = 0f; parryFx = 0.3f
    snd(Sfx.PARRY); hitStop = 0.15f; doShake(0.3f, 0.2f); flash(0xFFFFFFFFL, 0.07f)
    popupMsg("APARADO!", P.YELLOW)
    iT = 0.4f; stam = min(100f, stam + 18f)
    spark(px + sin(pyaw) * 0.9f, py + 1.3f, pz + cos(pyaw) * 0.9f, 18, 0xFFFFF3B0L, 7f, 1.6f)
    if (e != null && e.alive) {
        spark(e.x, e.y + 1.4f, e.z, 10, 0xFFFFFFFFL, 5f, 1.2f)
        if (e.def.training) { flags.add("tr_parry"); checkQuests() }
        if (e.boss) {
            e.poise -= 45f
            if (e.poise <= 0f) { stunEnemy(e, 3.0f); e.poise = e.def.poise * 0.8f }
            else stunEnemy(e, 1.5f)
        } else stunEnemy(e, 2.2f)
        riposteT = 1.6f; riposteTarget = e
        giveTip("tip_riposte")
        giveTip("tip_stun")
    }
}

internal fun AdventureGame.stunEnemy(e: En, t: Float) {
    e.st = ES.STUN; e.t = 0f; e.stunT = t; e.guardUp = false
    snd(Sfx.STUN)
    spark(e.x, e.y + 2f * e.def.sc, e.z, 6, 0xFFFFE066L, 2f)
}

// ---------------------------------------------------------------------------------------------- dano no inimigo
internal fun AdventureGame.hurtEnemy(e: En, dmgIn: Float, poiseDmg: Float, knock: Float, heavy: Boolean, fx: Float, fz: Float, crit: Boolean) {
    if (!e.alive) return
    if (e.st == ES.INTRO) return
    var dmg = dmgIn
    val fromFront = abs(wrap(atan2(fx - e.x, fz - e.z) - e.yaw)) < 1.2f
    if (e.guardUp && fromFront && !heavy && !crit) {
        snd(Sfx.BLOCK); spark(e.x + sin(e.yaw) * 0.8f, e.y + 1.3f, e.z + cos(e.yaw) * 0.8f, 6, 0xFFFFF3B0L, 4f)
        e.flash = 0.1f; e.poise -= poiseDmg * 0.3f
        e.aggro = true
        if (e.poise <= 0f) { e.poise = e.def.poise; stunEnemy(e, 1.8f); popupMsg("GUARDA ABERTA", P.ORANGE) }
        return
    }
    if (e.guardUp && fromFront && heavy) { stunEnemy(e, 1.6f); popupMsg("GUARDA QUEBRADA", P.ORANGE); dmg *= 0.6f }
    val back = !fromFront && abs(wrap(atan2(fx - e.x, fz - e.z) - e.yaw)) > 2.2f
    if (back && !e.boss) dmg *= 1.25f
    if (e.st == ES.STUN && !crit) { dmg *= 1.6f }
    e.hp -= dmg; e.flash = 0.16f; e.hits++
    e.aggro = true
    if (e.st == ES.IDLE) { e.st = ES.CHASE; e.t = 0f }
    snd(if (heavy || crit) Sfx.HIT_HEAVY else Sfx.HIT)
    spark(e.x, e.y + 1.2f * e.def.sc, e.z, if (heavy) 12 else 6, if (crit) 0xFFFFE066L else 0xFFFFFFFFL, 5f, if (heavy) 1.4f else 1f)
    val dd = max(0.1f, dist(e.x, e.z, fx, fz))
    val kb = knock * (if (e.boss) 0.12f else 1f / max(1f, e.def.sc * e.def.sc * 0.8f))
    e.vx = (e.x - fx) / dd * kb * 2f; e.vz = (e.z - fz) / dd * kb * 2f
    if (e.def.training) {
        e.hp = e.def.hp
        if (e.hits >= 5 && !flags.contains("tr_hit")) { flags.add("tr_hit"); say("Instrutor: bom! Agora apare um golpe meu."); snd(Sfx.QUEST) }
        checkQuests()
    }
    if (e.hp <= 0f && !e.def.training) { killEnemy(e); return }
    e.poise -= poiseDmg; e.poiseLock = 2.5f
    if (e.poise <= 0f && e.st != ES.STUN) { e.poise = if (e.boss) e.def.poise * 0.8f else e.def.poise; stunEnemy(e, if (e.boss) 2.8f else 2.0f); if (e.boss) popupMsg("DESEQUILIBRADO!", P.ORANGE) }
    else if (!e.boss && e.st != ES.STUN && e.st != ES.FLEE && (heavy || e.def.poise < 30f || e.st == ES.WIND && e.def.poise < 45f)) {
        e.st = ES.HURT; e.t = 0f; e.hurtT = if (heavy) 0.55f else 0.35f
    }
    if (e.def.training && heavy && e.st != ES.STUN) { e.st = ES.HURT; e.t = 0f; e.hurtT = 0.5f }
}

internal fun AdventureGame.killEnemy(e: En) {
    e.alive = false; e.st = ES.DEAD; e.deadT = 0f
    if (lockT === e) lockT = null
    snd(Sfx.ENEMY_DIE)
    spark(e.x, e.y + 1f * e.def.sc, e.z, 22, 0xFFFFFFFFL, 6f, 1.4f)
    onKill(e)
    val g = e.def.gems
    if (e.stolen > 0) drops.add(Drop(e.x, e.z, 0, e.stolen))
    if (g > 0) {
        val n = if (g >= 100) 6 else if (g >= 30) 3 else 2
        for (i in 0 until n) { val a = i * 6.28f / n; drops.add(Drop(e.x + cos(a) * 0.9f, e.z + sin(a) * 0.9f, 0, max(1, g / n))) }
    }
    if (!e.boss) {
        if (rnd.nextFloat() < 0.28f) drops.add(Drop(e.x, e.z + 0.6f, 1, 15))
        if (e.def.mat.isNotEmpty() && rnd.nextFloat() < e.def.matCh) drops.add(Drop(e.x + 0.5f, e.z, 2, 1, e.def.mat))
        if (e.def.beh == Beh.RANGED && has("arco") && rnd.nextFloat() < 0.4f) drops.add(Drop(e.x - 0.5f, e.z, 2, 4, "flecha"))
        if (e.spawnIdx < 8000) addCnt("kills")
        hitStop = max(hitStop, 0.1f)
    } else {
        hitStop = 0.35f; doShake(0.7f, 0.6f)
        val idx = e.spawnIdx - 9000
        if (idx in 0..4) {
            val rw = AdvEnemies.rewards[idx]
            flags.add(rw.flag)
            give(rw.item); if (rw.extra.isNotEmpty()) give(rw.extra)
            earn(rw.gems)
            engaged = null
            projs.removeAll { !it.mine }; spikes.clear(); rings.clear()
            ens.removeAll { it.spawnIdx in 8000..8999 }
            showBanner("${e.def.name.uppercase()} DERROTADO", "Recebeu ${AdvData.items[rw.item]?.name}${if (rw.extra.isNotEmpty()) " e ${AdvData.items[rw.extra]?.name}" else ""}")
            snd(Sfx.QUEST)
            if (e.kind == AdvEnemies.AUREL) endCountdown = 7f
            if (rw.item.startsWith("frag")) say("Estilhacos do Coracao: ${shards()}/3")
            saveDirty = true; checkQuests()
        }
    }
}

// ---------------------------------------------------------------------------------------------- chefes
internal fun AdventureGame.updateBossFlow(dt: Float) {
    if (engaged == null) {
        for (i in 0 until 5) {
            val b = arenaBoss[i] ?: continue
            if (!b.alive || b.st != ES.IDLE) continue
            val a = AdvWorld.arenas[i]
            if (dist(px, pz, a[0], a[1]) < AdvWorld.arenaR[i] * 0.8f) engageBoss(b)
        }
    } else {
        val b = engaged!!
        if (!b.alive) engaged = null
    }
}

internal fun AdventureGame.engageBoss(b: En) {
    engaged = b; b.st = ES.INTRO; b.t = 0f; b.introDur = 2.6f; b.aggro = true
    showBanner(b.def.name.uppercase(), b.def.title)
    snd(Sfx.BOSS_INTRO); doShake(0.5f, 0.6f)
    lockT = b
}

private fun AdventureGame.bossPhase(e: En) {
    while (e.phase < e.def.phases.size && e.hp / e.def.hp < e.def.phases[e.phase] && e.alive) {
        e.phase++
        e.st = ES.INTRO; e.t = 0f; e.introDur = 1.8f
        projs.removeAll { !it.mine }; spikes.clear(); rings.clear()
        snd(Sfx.BOSS_ROAR); doShake(0.6f, 0.6f); flash(0xFFFFE0A0L, 0.15f)
        popupMsg("FASE ${e.phase + 1}", P.ORANGE)
        spark(e.x, e.y + 2f, e.z, 30, 0xFFFFE066L, 8f, 1.8f)
        if (e.kind == AdvEnemies.MUSGRIM) for (i in 0 until 2) {
            val ang = i * 3.14f + 0.5f; val x = e.x + cos(ang) * 5f; val z = e.z + sin(ang) * 5f
            val s = En(AdvEnemies.defs[AdvEnemies.SLIME]!!, x, z, 8000 + rnd.nextInt(900)); s.aggro = true; s.st = ES.CHASE; s.y = AdvWorld.hAt(x, z); ens.add(s)
        }
    }
}

// ---------------------------------------------------------------------------------------------- projéteis e perigos
internal fun AdventureGame.updateProjectiles(dt: Float) {
    val it = projs.iterator()
    while (it.hasNext()) {
        val p = it.next()
        p.life -= dt
        p.x += p.vx * dt; p.z += p.vz * dt
        if (p.kind == 1 && !p.mine) p.y += (py + 1.2f - p.y) * dt * 1.2f
        if (p.life <= 0f || !p.alive) { it.remove(); continue }
        if (p.y < AdvWorld.hAt(p.x, p.z) + 0.1f || AdvWorld.blockedByProps(p.x, p.z, 0.15f) && p.y < AdvWorld.hAt(p.x, p.z) + 3f) {
            spark(p.x, p.y, p.z, 4, 0xFFD8C8A0L, 2f); snd(Sfx.ARROW_HIT); it.remove(); continue
        }
        if (!p.mine) {
            val d = dist(p.x, p.z, px, pz)
            if (p.parry && d < 1.9f && parryT > 0f && abs(wrap(angTo(p.x, p.z) - pyaw)) < 1.4f) {
                // aparo de projétil: devolve para quem atirou
                p.mine = true
                val s = p.src
                val tx = if (s != null && s.alive) s.x - p.x else -p.vx
                val tz = if (s != null && s.alive) s.z - p.z else -p.vz
                val l = max(0.1f, sqrt(tx * tx + tz * tz))
                val sp = max(14f, sqrt(p.vx * p.vx + p.vz * p.vz) * 1.6f)
                p.vx = tx / l * sp; p.vz = tz / l * sp; p.dmg = max(p.dmg * 3f, 40f); p.life = 2.2f
                p.reflected = true
                parryT = 0f; parryFx = 0.3f; snd(Sfx.PARRY); hitStop = 0.12f; popupMsg("DEVOLVIDO!", P.YELLOW)
                spark(p.x, p.y, p.z, 14, 0xFFFFF3B0L, 6f, 1.4f); doShake(0.25f, 0.15f)
                continue
            }
            if (d < 0.95f && abs(p.y - (py + 1.1f)) < 1.5f) {
                val r = enemyHitsPlayer(p.src, p.dmg, p.x - p.vx, p.z - p.vz, if (p.kind == 1) AK.ORBS else AK.SHOT, p.parry, 3.2f)
                if (r != 0) { p.alive = false; spark(p.x, p.y, p.z, 6, 0xFFFFFFFFL, 3f); it.remove(); continue }
            }
        } else {
            var hit = false
            for (e in ens) {
                if (!e.alive || e.st == ES.INTRO) continue
                if (dist(p.x, p.z, e.x, e.z) < e.def.rad * e.def.sc + 0.5f && abs(p.y - (e.y + e.def.sc)) < 2.8f) {
                    hurtEnemy(e, p.dmg, if (p.reflected) 70f else 8f, 3f, p.reflected, p.x - p.vx, p.z - p.vz, p.reflected)
                    if (p.reflected && e.alive && !e.boss) stunEnemy(e, 2f)
                    hit = true; snd(Sfx.ARROW_HIT); break
                }
            }
            if (hit) { it.remove(); continue }
        }
    }
}

internal fun AdventureGame.updateHazards(dt: Float) {
    // espinhos de cristal
    val si = spikes.iterator()
    while (si.hasNext()) {
        val s = si.next()
        val before = s.t
        s.t += dt
        if (before < 0f && s.t >= 0f) {
            snd(Sfx.SLAM); spark(s.x, AdvWorld.hAt(s.x, s.z) + 0.4f, s.z, 6, 0xFFB38CFFL, 4f)
            if (dist(s.x, s.z, px, pz) < 1.5f) enemyHitsPlayer(null, s.dmg, s.x, s.z, AK.SPIKES, false, 5f)
        }
        if (s.t > 0.9f) si.remove()
    }
    // ondas de choque
    val ri = rings.iterator()
    while (ri.hasNext()) {
        val r = ri.next()
        r.r += 9f * dt
        if (!r.hit) {
            val d = dist(r.x, r.z, px, pz)
            if (abs(d - r.r) < 0.9f) { r.hit = true; enemyHitsPlayer(null, r.dmg, r.x, r.z, AK.WAVE, false, 6f) }
        }
        if (r.r >= r.maxR) ri.remove()
    }
}
