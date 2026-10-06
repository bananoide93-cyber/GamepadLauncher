package com.gamepadlayout.app.games

import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// =============================================================================================== cena 3D

private val herbIdx: IdentityHashMap<Prop, Int> by lazy { val m = IdentityHashMap<Prop, Int>(); for ((i, h) in AdvWorld.collectHerbSpots().withIndex()) m[h] = i; m }
private val mountains: List<Prop> by lazy { AdvWorld.props.filter { it.kind == PK.MOUNTAIN } }
private val smallKinds = setOf(PK.GRASS, PK.FLOWER_P, PK.FLOWER_Y, PK.FLOWER_B, PK.MUSHROOM, PK.HERB, PK.REED, PK.LILY, PK.STUMP, PK.LOG)
private val midKinds = setOf(PK.BUSH, PK.ROCK, PK.ROCK_RUST, PK.FENCE, PK.BARREL, PK.CRATE, PK.HAY, PK.LANTERN, PK.TORCH, PK.SIGN, PK.WELL, PK.SCARECROW, PK.STALL, PK.BANNER, PK.RUNE)

private fun avgColor(a: Long, b: Long, c: Long, d: Long): Long {
    fun ch(s: Int) = ((((a shr s) and 0xFF) + ((b shr s) and 0xFF) + ((c shr s) and 0xFF) + ((d shr s) and 0xFF)) / 4)
    return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun isWaterCol(c: Long) = c == 0xFF3E7FD0L || c == 0xFF4A8BDCL

internal fun AdventureGame.drawAll(g: Gfx) {
    val v = View(g, 800f, 450f)
    v.clear(0xFF000000L)
    drawScene(v)
    when (mode) {
        Mode.TITLE -> drawTitle(v)
        Mode.STORY -> drawStory(v)
        Mode.PLAY -> { drawHud(v) }
        Mode.DIALOG -> { drawHud(v); drawDialogBox(v) }
        Mode.MENU -> { drawHud(v); drawMenuBox(v) }
        Mode.INV -> drawInv(v)
        Mode.MAP -> drawMap(v)
        Mode.SETTINGS -> drawSettings(v)
        Mode.DEAD -> { drawHud(v); drawDead(v) }
        Mode.ENDING -> drawEnding(v)
    }
    if (flashT > 0f) v.rect(0f, 0f, 800f, 450f, C.alpha(flashCol, (flashT * 3f).coerceIn(0f, 0.55f)))
    if (fade > 0f) v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, fade))
    if (bannerT > 0f && mode != Mode.TITLE) drawBanner(v)
}

private fun AdventureGame.drawScene(v: View) {
    val cam = computeCamera()
    val sh = if (shake > 0f && shakeOn) shakeAmp * (rnd.nextFloat() - 0.5f) else 0f
    r3.cys = 225f
    r3.fogColor = sFog; r3.fogStart = sFogS; r3.fogEnd = sFogE
    r3.lightR = sLr; r3.lightG = sLg; r3.lightB = sLb; r3.ambient = sAmb
    r3.begin(cam[0] + sh, cam[1] + sh * 0.6f, cam[2] + sh * 0.5f, camYaw, cam[3])
    val yh = r3.cys - tan(cam[3]) * r3.focal
    drawSky(v, yh)
    drawTerrain(cam[0], cam[2])
    drawProps(cam[0], cam[2])
    drawEntities()
    lastTris = r3.count
    r3.flush(v)
    // partículas 3D simples
    val o = FloatArray(3)
    for (q in parts) if (r3.project(q.x, q.y, q.z, o)) {
        val s = max(2f, 70f * q.size / max(1f, o[2]))
        v.rect(o[0] - s / 2f, o[1] - s / 2f, s, s, C.alpha(q.color, (q.life * 2.2f).coerceIn(0f, 1f)))
    }
    // desempenho: ajusta o alcance conforme a quantidade de triângulos
    if (lastTris > 7000) perf = max(0.55f, perf - 0.03f) else if (lastTris < 4800) perf = min(1f, perf + 0.01f)
}

private fun AdventureGame.drawSky(v: View, yh: Float) {
    val top = sSky; val bot = sFog
    val hh = max(10f, yh)
    val bands = 9
    for (i in 0 until bands) {
        val t = i.toFloat() / (bands - 1)
        v.rect(0f, i * hh / bands - 1f, 800f, hh / bands + 2f, mixColor(top, bot, t))
    }
    v.rect(0f, hh - 1f, 800f, 460f - hh, bot)
    if (zone == Z.CAVERNA && mode == Mode.PLAY) return
    val sr = wrap(2.2f - camYaw)
    if (abs(sr) < 1.2f) { val sx = 400f + tan(sr) * r3.focal; v.pixDisc(sx, hh * 0.35f, 22f, 0xFFFFF3B0L, 4f); v.pixDisc(sx, hh * 0.35f, 15f, 0xFFFFFFE0L, 4f) }
}

private fun AdventureGame.drawTerrain(cx: Float, cz: Float) {
    val C3 = AdvWorld.CELL; val N = AdvWorld.N; val n1 = N + 1
    val nearR = 36f * perf * lodBias; val farR = min(sFogE * 0.98f, 84f + 26f * perf)
    val fx = sin(camYaw); val fz = cos(camYaw)
    val ci = (cx / C3).toInt(); val cj = (cz / C3).toInt()
    // longe: blocos 2x2
    val R2 = (farR / C3).toInt() + 2
    var j = max(0, (cj - R2) and 1.inv())
    while (j < min(N - 1, cj + R2)) {
        var i = max(0, (ci - R2) and 1.inv())
        while (i < min(N - 1, ci + R2)) {
            val mx = (i + 1) * C3; val mz = (j + 1) * C3
            val dx = mx - cx; val dz = mz - cz; val d2 = dx * dx + dz * dz
            if (d2 < farR * farR && d2 > (nearR - 9f) * (nearR - 9f) && dx * fx + dz * fz > -12f) {
                val i1 = min(i + 2, N); val j1 = min(j + 2, N)
                val c00 = AdvWorld.cellCols[j * N + i]; val c10 = AdvWorld.cellCols[j * N + min(i + 1, N - 1)]
                val c01 = AdvWorld.cellCols[min(j + 1, N - 1) * N + i]; val c11 = AdvWorld.cellCols[min(j + 1, N - 1) * N + min(i + 1, N - 1)]
                var col = avgColor(c00, c10, c01, c11)
                var h00 = AdvWorld.hv[j * n1 + i]; var h10 = AdvWorld.hv[j * n1 + i1]; var h01 = AdvWorld.hv[j1 * n1 + i]; var h11 = AdvWorld.hv[j1 * n1 + i1]
                if (isWaterCol(c00) && isWaterCol(c10) && isWaterCol(c01) && isWaterCol(c11)) { h00 = AdvWorld.WATER; h10 = AdvWorld.WATER; h01 = AdvWorld.WATER; h11 = AdvWorld.WATER; col = c00 }
                else { h00 -= 0.3f; h10 -= 0.3f; h01 -= 0.3f; h11 -= 0.3f }
                val x0 = i * C3; val z0 = j * C3; val x1 = i1 * C3; val z1 = j1 * C3
                r3.group(mx, mz, 6f)
                r3.tri(x0, h00, z0, x0, h01, z1, x1, h11, z1, col, false)
                r3.tri(x0, h00, z0, x1, h11, z1, x1, h10, z0, col, false)
            }
            i += 2
        }
        j += 2
    }
    // perto: células individuais
    val R = (nearR / C3).toInt() + 1
    for (jj in max(0, cj - R)..min(N - 1, cj + R)) for (ii in max(0, ci - R)..min(N - 1, ci + R)) {
        val mx = (ii + 0.5f) * C3; val mz = (jj + 0.5f) * C3
        val dx = mx - cx; val dz = mz - cz
        if (dx * dx + dz * dz > nearR * nearR) continue
        if (dx * fx + dz * fz < -7f) continue
        val x0 = ii * C3; val z0 = jj * C3; val x1 = x0 + C3; val z1 = z0 + C3
        var h00 = AdvWorld.hv[jj * n1 + ii]; var h10 = AdvWorld.hv[jj * n1 + ii + 1]
        var h01 = AdvWorld.hv[(jj + 1) * n1 + ii]; var h11 = AdvWorld.hv[(jj + 1) * n1 + ii + 1]
        var col = AdvWorld.cellCols[jj * N + ii]
        if (isWaterCol(col)) {
            h00 = AdvWorld.WATER; h10 = AdvWorld.WATER; h01 = AdvWorld.WATER; h11 = AdvWorld.WATER
            col = C.shade(col, 1f + sin(time * 1.4f + ii * 0.8f + jj * 1.1f) * 0.07f)
        }
        r3.group(mx, mz, 1.2f)
        r3.tri(x0, h00, z0, x0, h01, z1, x1, h11, z1, col, false)
        r3.tri(x0, h00, z0, x1, h11, z1, x1, h10, z0, col, false)
    }
}

private fun AdventureGame.drawProps(cx: Float, cz: Float) {
    val fx = sin(camYaw); val fz = cos(camYaw)
    val lim = min(sFogE, 96f * perf * lodBias + 20f)
    AdvWorld.forNear(cx, cz, lim) { p ->
        if (p.kind == PK.MOUNTAIN) return@forNear
        val dx = p.x - cx; val dz = p.z - cz
        val d2 = dx * dx + dz * dz
        if (d2 > lim * lim) return@forNear
        if (dx * fx + dz * fz < -(p.r * 2f + 6f)) return@forNear
        val k = p.kind
        if (k in smallKinds && d2 > 28f * 28f * perf) return@forNear
        if (k in midKinds && d2 > 55f * 55f) return@forNear
        if (k == PK.HERB) { val hi = herbIdx[p]; if (hi != null && flags.contains("hb$hi")) return@forNear }
        var m = AdvModels.propMesh(k)
        if (d2 > 40f * 40f) { val l = AdvModels.lodMesh(k); if (l != null) m = l }
        val sw = if (p.sway > 0f) sin(time * 1.6f + p.x * 0.4f + p.z * 0.3f) * 0.06f else 0f
        r3.mesh(m, p.x, p.y, p.z, p.rot, 0f, p.sc, swayX = sw, swayZ = sw * 0.6f)
    }
    for (p in mountains) {
        val dx = p.x - cx; val dz = p.z - cz
        if (dx * fx + dz * fz < -20f) continue
        r3.mesh(AdvModels.propMesh(PK.MOUNTAIN), p.x, p.y, p.z, p.rot, 0f, p.sc, bias = 40f)
    }
}

private fun AdventureGame.drawEntities() {
    val cx = r3.camX; val cz = r3.camZ
    // brasas
    for ((i, b) in AdvWorld.bonfires.withIndex()) {
        if (dist(b[0], b[1], px, pz) > 60f) continue
        val y = AdvWorld.hAt(b[0], b[1])
        r3.mesh(AdvModels.bonfireBase, b[0], y, b[1], 0f, 0f, 1f)
        if (flags.contains("bf$i")) {
            val f = 1f + 0.16f * sin(time * 11f + i)
            r3.mesh(AdvModels.flame, b[0], y, b[1], time * 2f, 0f, f, bias = -0.3f)
        } else r3.mesh(AdvModels.flameOff, b[0], y, b[1], 0f, 0f, 1f, bias = -0.3f)
    }
    for (c in AdvWorld.chests) {
        if (dist(c.x, c.z, px, pz) > 60f) continue
        val open = flags.contains("ch_${c.id}")
        r3.mesh(if (open) AdvModels.chestOpen else if (c.rare) AdvModels.chestRare else AdvModels.chest, c.x, AdvWorld.hAt(c.x, c.z), c.z, 0.6f, 0f, 1f)
        if (!open && dist(c.x, c.z, px, pz) < 18f) r3.mesh(AdvModels.sparkle, c.x, AdvWorld.hAt(c.x, c.z) + 1.4f + sin(time * 3f + c.x) * 0.15f, c.z, time * 2f, 0f, 1f, bias = -1f)
    }
    for (d in AdvWorld.doors) {
        if (dist(d.x, d.z, px, pz) > 70f) continue
        val open = doorOpen(d)
        val m = when (d.style) {
            0 -> if (open) AdvModels.doorOpen else AdvModels.doorWood
            1 -> if (open) AdvModels.doorIronOpen else AdvModels.doorIron
            2 -> if (open) AdvModels.rubble else AdvModels.crackedWall
            else -> if (open) AdvModels.gateCrystalOpen else AdvModels.gateCrystal
        }
        r3.mesh(m, d.x, AdvWorld.hAt(d.x, d.z), d.z, d.yaw, 0f, 1f)
    }
    for (s in AdvWorld.signs) { if (dist(s.x, s.z, px, pz) < 60f) r3.mesh(AdvModels.sign, s.x, AdvWorld.hAt(s.x, s.z), s.z, s.yaw, 0f, 1f) }
    if (deathGems > 0) r3.mesh(AdvModels.soulMark, deathX, AdvWorld.hAt(deathX, deathZ) + 1.2f + sin(time * 3f) * 0.2f, deathZ, time * 2f, 0f, 1f)
    // anel de arena
    val eb = engaged
    if (eb != null && eb.alive && eb.spawnIdx >= 9000) {
        val a = AdvWorld.arenas[eb.spawnIdx - 9000]; val rr = AdvWorld.arenaR[eb.spawnIdx - 9000] + 1.2f
        for (i in 0 until 18) {
            val ang = i / 18f * 2f * PI.toFloat()
            val x = a[0] + cos(ang) * rr; val z = a[1] + sin(ang) * rr
            r3.mesh(AdvChars.shardOrbit, x, AdvWorld.hAt(x, z) + 0.9f + sin(time * 2f + i) * 0.2f, z, ang + time, 0f, 1.1f)
        }
    }
    // fauna decorativa
    for (c in critters) {
        val d = dist(c.x, c.z, px, pz)
        if (d > 45f) continue
        val y = AdvWorld.hAt(c.x, c.z)
        when (c.kind) {
            0 -> { val hop = abs(sin(c.t * 7f)) * (if (c.vx * c.vx + c.vz * c.vz > 1f) 0.35f else 0f); r3.mesh(AdvChars.bunny, c.x, y + hop, c.z, atan2(c.vx, c.vz), 0f, 1f) }
            1 -> { val fl = sin(c.t * 18f) * 0.5f; r3.mesh(AdvChars.butterfly, c.x, y + 1.6f + sin(c.t * 2f) * 0.4f, c.z, c.t, 0f, 1f, rollM = fl) }
            2 -> { val ang = c.t * 0.7f + c.ph; val fl = sin(c.t * 9f) * 0.6f
                r3.mesh(AdvChars.bird, c.x, y + 7f, c.z, -ang, 0f, 1f); r3.mesh(AdvChars.birdWing, c.x, y + 7f, c.z, -ang, 0f, 1f, rollM = fl, bias = -0.2f); r3.mesh(AdvChars.birdWing, c.x, y + 7f, c.z, -ang + PI.toFloat(), 0f, 1f, rollM = -fl, bias = -0.2f) }
            3 -> { val j = max(0f, sin(c.t * 1.3f + c.ph * 2f)); r3.mesh(AdvChars.fish, c.x, AdvWorld.WATER - 0.25f + j * 0.9f, c.z, c.t, 0f, 1f) }
        }
    }
    // NPCs
    for (n in npcs) {
        if (dist(n.x, n.z, px, pz) > 70f) continue
        val y = AdvWorld.hAt(n.x, n.z)
        val lod = dist(n.x, n.z, px, pz) > 36f
        when (n.d.rig) {
            100 -> { r3.mesh(AdvChars.bird, n.x, y + 1.2f + sin(time * 2f) * 0.05f, n.z, n.yaw, 0f, 2.4f); r3.mesh(AdvModels.stump, n.x, y, n.z, 0f, 0f, 0.8f) }
            101 -> {
                val by = y + 1.6f + sin(time * 1.6f + n.d.id) * 0.25f
                r3.mesh(AdvModels.orbMagic, n.x, by, n.z, time, 0f, 1.8f)
                r3.mesh(AdvModels.sparkle, n.x + cos(time * 2f) * 0.9f, by + 0.2f, n.z + sin(time * 2f) * 0.9f, time, 0f, 1f)
                r3.shadow(n.x, y + 0.05f, n.z, 0.7f, 0.2f)
            }
            else -> {
                val rig = when (n.d.rig) { 0 -> AdvChars.elder; 1 -> AdvChars.smith; 2 -> AdvChars.lia; 3 -> AdvChars.farmer; 4 -> AdvChars.fisher; 5 -> AdvChars.guardNpc; 6 -> AdvChars.child; 7 -> AdvChars.witch; else -> AdvChars.npcExtra }
                val hand = if (n.d.rig == 1) AdvModels.hammer else if (n.d.rig == 4 && false) null else null
                AdvSkel.draw(r3, rig, n.anim.cur, n.x, y, n.z, n.yaw, if (n.d.rig == 6 || n.d.rig == 8) 0.82f else 1f, lod = lod, handItem = hand)
            }
        }
    }
    // inimigos
    for (e in ens) {
        val d = dist(e.x, e.z, px, pz)
        if (d > 80f || (!e.alive && e.deadT > 1.5f)) continue
        drawEnemy(e, d)
    }
    drawPlayer()
    // itens soltos
    for (dr in drops) {
        if (dist(dr.x, dr.z, px, pz) > 40f) continue
        val y = AdvWorld.hAt(dr.x, dr.z) + 0.8f + sin(dr.t * 4f) * 0.15f
        when (dr.kind) {
            0 -> r3.mesh(AdvModels.gemItem, dr.x, y, dr.z, dr.t * 3f, 0f, 1f)
            1 -> r3.mesh(AdvModels.heartFruit, dr.x, y, dr.z, dr.t * 2f, 0f, 0.6f)
            else -> r3.mesh(AdvModels.orb, dr.x, y, dr.z, dr.t * 3f, 0f, 0.7f)
        }
    }
    for (p in projs) {
        val y = p.y
        val yaw = atan2(p.vx, p.vz)
        when (p.kind) {
            0, 2 -> r3.mesh(AdvModels.arrowFlying, p.x, y, p.z, yaw, 0f, 1f)
            1 -> r3.mesh(if (p.mine) AdvModels.orbMagic else AdvModels.orbBad, p.x, y, p.z, time * 5f, 0f, 1f)
            else -> r3.mesh(AdvModels.orbMagic, p.x, y, p.z, time * 5f, 0f, 1f)
        }
    }
    for (b in bombs) {
        r3.mesh(AdvModels.bombM, b.x, b.y - 0.3f, b.z, time * 4f, 0f, 1f)
    }
    // espinhos de cristal e avisos
    for (s in spikes) {
        val gy = AdvWorld.hAt(s.x, s.z)
        if (s.t < 0f) ringDecal(s.x, gy + 0.1f, s.z, 1.4f, C.alpha(0xFFFF4040L, 0.55f))
        else r3.mesh(AdvChars.crystalSpike, s.x, gy - 0.5f + min(1f, s.t * 6f) * 0.5f, s.z, s.x, 0f, 1.5f * (1f - max(0f, s.t - 0.5f) * 2f))
    }
    for (r in rings) {
        val gy = AdvWorld.hAt(r.x, r.z)
        ringDecal(r.x, gy + 0.2f, r.z, r.r, C.alpha(0xFFFFE066L, 0.6f), 0.9f)
    }
    // avisos no chão dos ataques de área dos inimigos
    for (e in ens) {
        if (!e.alive || e.st != ES.WIND) continue
        val a = e.def.atks[e.atk.coerceIn(0, e.def.atks.size - 1)]
        val gy = AdvWorld.hAt(e.x, e.z)
        val k = (e.t / a.wind).coerceIn(0f, 1f)
        when (a.kind) {
            AK.SLAM -> { val cxx = e.x + sin(e.yaw) * a.reach * 0.8f; val czz = e.z + cos(e.yaw) * a.reach * 0.8f; ringDecal(cxx, AdvWorld.hAt(cxx, czz) + 0.12f, czz, a.rad, C.alpha(0xFFFF3030L, 0.18f + 0.3f * k)) }
            AK.SPIN -> ringDecal(e.x, gy + 0.12f, e.z, a.rad, C.alpha(0xFFFF3030L, 0.15f + 0.3f * k))
            AK.LEAP -> if (!a.parry) ringDecal(e.tx, AdvWorld.hAt(e.tx, e.tz) + 0.12f, e.tz, a.rad, C.alpha(0xFFFF3030L, 0.2f + 0.3f * k))
            AK.CHARGE -> lineDecal(e.x, e.z, e.tx, e.tz, 1.0f, C.alpha(0xFFFF3030L, 0.2f + 0.3f * k))
            else -> {}
        }
    }
    // rastro da arma
    if (trailT > 0f && (pAct == PA.ATK || pAct == PA.HEAVY || pAct == PA.SPECIAL)) drawTrail()
    trailT = max(0f, trailT - 1f / 60f)
}

private fun AdventureGame.ringDecal(x: Float, y: Float, z: Float, r: Float, col: Long, thick: Float = 0f) {
    r3.group(x, z, -2f, 0)
    val seg = 16
    for (i in 0 until seg) {
        val a0 = i * 2f * PI.toFloat() / seg; val a1 = (i + 1) * 2f * PI.toFloat() / seg
        if (thick <= 0f) r3.fxTri(x, y, z, x + cos(a0) * r, y, z + sin(a0) * r, x + cos(a1) * r, y, z + sin(a1) * r, col, true)
        else {
            val r0 = max(0f, r - thick)
            r3.fxTri(x + cos(a0) * r0, y, z + sin(a0) * r0, x + cos(a0) * r, y, z + sin(a0) * r, x + cos(a1) * r, y, z + sin(a1) * r, col, true)
            r3.fxTri(x + cos(a0) * r0, y, z + sin(a0) * r0, x + cos(a1) * r, y, z + sin(a1) * r, x + cos(a1) * r0, y, z + sin(a1) * r0, col, true)
        }
    }
}

private fun AdventureGame.lineDecal(x0: Float, z0: Float, x1: Float, z1: Float, w: Float, col: Long) {
    val dx = x1 - x0; val dz = z1 - z0; val l = max(0.1f, sqrt(dx * dx + dz * dz))
    val nx = -dz / l * w; val nz = dx / l * w
    val y0 = AdvWorld.hAt(x0, z0) + 0.15f; val y1 = AdvWorld.hAt(x1, z1) + 0.15f
    r3.group((x0 + x1) / 2f, (z0 + z1) / 2f, -2f, 0)
    r3.fxTri(x0 + nx, y0, z0 + nz, x0 - nx, y0, z0 - nz, x1 - nx, y1, z1 - nz, col, true)
    r3.fxTri(x0 + nx, y0, z0 + nz, x1 - nx, y1, z1 - nz, x1 + nx, y1, z1 + nz, col, true)
}

private fun AdventureGame.drawTrail() {
    val wd = wdef()
    val k = (pT / pDur).coerceIn(0f, 1f)
    val hitK = pHitAt
    if (k < hitK - 0.1f || k > hitK + 0.35f) return
    val reach = if (pAct == PA.SPECIAL && specialKind == 0) wd.reach + 0.8f else wd.reach
    val arc = if (pAct == PA.SPECIAL && specialKind == 0) 6.2f else if (pAct == PA.HEAVY) 2.6f else 2.2f
    val prog = ((k - (hitK - 0.1f)) / 0.45f).coerceIn(0f, 1f)
    val steps = 9
    val a0 = pyaw - arc / 2f * swingFlip
    val y = py + 1.1f
    r3.group(px, pz, -1.5f, 1)
    val col = C.alpha(if (pAct == PA.HEAVY) 0xFFFFE066L else 0xFFE8F4FFL, 0.55f * (1f - prog * 0.6f))
    for (i in 0 until steps) {
        val t0 = i.toFloat() / steps; val t1 = (i + 1f) / steps
        if (t0 > prog) break
        val aa = a0 + arc * swingFlip * t0; val ab = a0 + arc * swingFlip * t1
        r3.fxTri(px, y, pz, px + sin(aa) * reach, y, pz + cos(aa) * reach, px + sin(ab) * reach, y, pz + cos(ab) * reach, col)
    }
}

private fun AdventureGame.drawPlayer() {
    if (iT > 0.2f && hurtT > 0f && (time * 20f).toInt() % 2 == 0) return
    val wd = wdef()
    val weaponLeft = wd.ws == WS.BOW
    val shield = if (wd.ws == WS.BOW || wd.ws == WS.STAFF) null else AdvModels.shieldMeshFor(eqS)
    val hand: Mesh? = when (pAct) { PA.DRINK -> if (pendingQuick == "pedra") null else AdvModels.potion; PA.THROW -> AdvModels.bombM; else -> null }
    val wm = if (pAct == PA.DRINK || pAct == PA.THROW) null else AdvModels.weaponMesh(eqW)
    val glow = parryT > 0f || parryFx > 0f
    AdvSkel.draw(
        r3, AdvChars.hero, pAnim.cur, px, py, pz, pyaw, 1f,
        flash = false, fx = if (glow) 0xFFFFE066L else if (buffT > 0f) 0xFFFF9F1CL else 0L, fxAmt = if (glow) 0.45f else if (buffT > 0f) 0.15f else 0f,
        weapon = wm, weaponLeft = weaponLeft, shield = shield, handItem = hand, lod = false, bias = -0.4f
    )
}

private fun AdventureGame.drawEnemy(e: En, d: Float) {
    val def = e.def
    var sc = def.sc
    if (!e.alive) { val k = (e.deadT / 1.2f).coerceIn(0f, 1f); if (e.deadT > 0.7f) sc *= (1f - (e.deadT - 0.7f) / 0.5f).coerceIn(0.05f, 1f) }
    val flash = e.flash > 0f
    var fx = 0L; var amt = 0f
    if (e.alive && e.st == ES.WIND) {
        val a = def.atks[e.atk.coerceIn(0, def.atks.size - 1)]
        if (e.t > a.wind * 0.45f) { fx = if (canParry(e, a)) 0xFFFFE066L else 0xFFFF2A2AL; amt = 0.35f + 0.3f * sin(time * 30f) }
    } else if (e.alive && e.st == ES.STUN) { fx = 0xFF9BE6FFL; amt = 0.25f }
    else if (e.alive && e.guardUp && false) {}
    val lod = d > 38f
    when (e.kind) {
        AdvEnemies.SLIME, AdvEnemies.SLIMEB, AdvEnemies.FROG, AdvEnemies.HARE -> {
            val m = when (e.kind) { AdvEnemies.SLIME -> AdvChars.slimeG; AdvEnemies.SLIMEB -> AdvChars.slimeB; AdvEnemies.FROG -> AdvChars.frog; else -> AdvChars.hare }
            val sq = if (e.st == ES.WIND) 1f - 0.3f * (e.t / max(0.1f, def.atks[0].wind)) else if (e.st == ES.ACT) 1.3f else 1f + 0.08f * sin(time * 8f + e.spawnIdx)
            val hop = if (e.amt > 0.1f && e.kind != AdvEnemies.HARE) abs(sin(e.phi * 0.7f)) * 0.35f else if (e.kind == AdvEnemies.HARE && e.amt > 0.1f) abs(sin(e.phi)) * 0.4f else 0f
            r3.group(e.x, e.z, 0f)
            if (e.alive) r3.shadow(e.x, e.y + 0.05f, e.z, 0.9f * sc)
            r3.meshKeep(m, e.x, e.y + hop, e.z, e.yaw, 0f, sc, flash, fx, amt)
            if (e.st == ES.STUN) r3.meshKeep(AdvModels.sparkle, e.x, e.y + 1.4f * sc, e.z, time * 4f, 0f, 1f)
        }
        AdvEnemies.BAT -> {
            r3.group(e.x, e.z, 0f)
            val fl = sin(time * 22f + e.spawnIdx) * 0.7f
            if (e.alive) r3.shadow(e.x, AdvWorld.hAt(e.x, e.z) + 0.05f, e.z, 0.6f, 0.2f)
            r3.meshKeep(AdvChars.batBody, e.x, e.y, e.z, e.yaw, 0f, sc, flash, fx, amt)
            r3.meshKeep(AdvChars.batWing, e.x, e.y, e.z, e.yaw, fl, sc, flash, fx, amt)
            r3.meshKeep(AdvChars.batWing, e.x, e.y, e.z, e.yaw + PI.toFloat(), -fl, sc, flash, fx, amt)
        }
        AdvEnemies.AUREL -> drawAurel(e, sc, flash, fx, amt)
        else -> {
            val rig = when (e.kind) {
                AdvEnemies.COGU -> AdvChars.cogu; AdvEnemies.CASC -> AdvChars.casc; AdvEnemies.FERR -> AdvChars.ferr; AdvEnemies.ARCHER -> AdvChars.archer
                AdvEnemies.STATUE -> AdvChars.statueGuard; AdvEnemies.ELITE -> AdvChars.elite; AdvEnemies.TRAINER -> AdvChars.guardNpc
                AdvEnemies.MUSGRIM -> AdvChars.troll; AdvEnemies.SENTINEL -> AdvChars.sentinel; AdvEnemies.CAPTAIN -> AdvChars.captain
                else -> AdvChars.crystalGuard
            }
            val wm = when (def.weapon) { "" -> null; "club" -> AdvModels.club; else -> AdvModels.weaponMesh(def.weapon) }
            val sh = if (def.shield) AdvModels.shieldMeshFor(if (e.kind == AdvEnemies.STATUE) "ferro" else "ferrugem_s") else null
            val pose = e.anim.cur
            if (e.guardUp) pose.v[PC.SHIELD_G] = 1f
            AdvSkel.draw(r3, rig, pose, e.x, e.y, e.z, e.yaw, sc, flash, fx, amt, weapon = wm, weaponLeft = def.weapon == "arco", shield = sh, lod = lod, shadow = if (e.alive) 0.8f else 0f)
            if (e.kind == AdvEnemies.CRYSTAL && e.alive) r3.mesh(AdvChars.shardOrbit, e.x + cos(time * 1.5f) * 2.6f, e.y + 2.6f, e.z + sin(time * 1.5f) * 2.6f, time * 2f, 0f, 1f)
            if (e.st == ES.STUN && e.alive) r3.mesh(AdvModels.sparkle, e.x + cos(time * 5f) * 0.6f, e.y + 2.5f * sc, e.z + sin(time * 5f) * 0.6f, time * 4f, 0f, 1.2f)
        }
    }
}

private fun AdventureGame.drawAurel(e: En, sc: Float, flash: Boolean, fx: Long, amt: Float) {
    val body = if (e.phase >= 1) AdvChars.kingBodyFree else AdvChars.kingBody
    var lean = 0f; var stretch = 1f
    if (e.st == ES.WIND) { val a = e.def.atks[e.atk]; val k = e.t / a.wind; lean = -0.25f * k; stretch = 1f + 0.08f * k }
    else if (e.st == ES.ACT) { lean = 0.45f; stretch = 0.92f }
    else if (e.st == ES.STUN) { lean = 0.3f + 0.1f * sin(time * 12f) }
    val by = e.y + 0.4f + sin(time * 1.8f) * 0.25f
    r3.group(e.x, e.z, 0f)
    if (e.alive) r3.shadow(e.x, e.y + 0.05f, e.z, 2.2f * sc, 0.3f)
    r3.meshKeep(body, e.x, by, e.z, e.yaw, lean, sc, flash, fx, amt)
    if (e.phase >= 1) for (i in 0 until 3) {
        val a = time * 1.6f + i * 2.094f
        r3.meshKeep(AdvChars.shardOrbit, e.x + cos(a) * 3.2f, by + 2.4f + sin(a * 2f) * 0.4f, e.z + sin(a) * 3.2f, a, 0f, 1f)
    }
    if (e.phase >= 2) r3.meshKeep(AdvChars.goldenHalo, e.x, by + 4.2f, e.z, time * 1.2f, 0f, 1.4f)
}

// =============================================================================================== HUD

internal fun btnName(b: Int) = Btn.NAMES[b.coerceIn(0, Btn.COUNT - 1)]

private fun View.bar(x: Float, y: Float, w: Float, h: Float, f: Float, c: Long, bg: Long = P.INK) {
    rect(x - 2f, y - 2f, w + 4f, h + 4f, P.INK)
    rect(x, y, w, h, bg)
    rect(x, y, w * f.coerceIn(0f, 1f), h, c)
    rect(x, y, w * f.coerceIn(0f, 1f), max(1f, h * 0.3f), P.light(c, 0.45f))
}

private fun View.panel(x: Float, y: Float, w: Float, h: Float) {
    rect(x, y, w, h, C.alpha(P.INK, 0.94f))
    rect(x, y, w, 3f, P.VIOLET); rect(x, y + h - 3f, w, 3f, P.VIOLET)
    rect(x, y, 3f, h, P.VIOLET); rect(x + w - 3f, y, 3f, h, P.VIOLET)
}

private fun View.wrapText(t: String, x: Float, y: Float, maxW: Float, px: Float, color: Long, lh: Float): Float {
    var line = ""; var yy = y
    for (w in PixelFont.clean(t).split(" ")) {
        val tr = if (line.isEmpty()) w else "$line $w"
        if (PixelFont.width(tr, px) > maxW && line.isNotEmpty()) { pixText(line, x, yy, px, color, shadow = false); yy += lh; line = w } else line = tr
    }
    if (line.isNotEmpty()) { pixText(line, x, yy, px, color, shadow = false); yy += lh }
    return yy
}

private fun View.chip(x: Float, y: Float, t: String, col: Long = P.VIOLET) {
    val w = max(16f, PixelFont.width(PixelFont.clean(t), 1.5f) + 8f)
    rect(x, y, w, 14f, col); rect(x, y, w, 2f, P.light(col, 0.4f))
    pixText(t, x + w / 2f, y + 3f, 1.5f, P.WHITE, center = true, shadow = false)
}

private fun AdventureGame.objectivePos(): FloatArray? {
    val m = AdvQuests.currentMain(this) ?: return null
    return when (m.id) {
        "M1" -> floatArrayOf(94f, 156f)
        "M2" -> if (!has("chave_celeiro") && !flag("dr_celeiro")) floatArrayOf(100f, 132f) else floatArrayOf(100f, 123f)
        "M3" -> AdvWorld.arenas[0]
        "M4" -> AdvWorld.arenas[1]
        "M5" -> AdvWorld.arenas[2]
        else -> if (shards() < 3) null else if (!flag("dr_coracao")) floatArrayOf(100f, 34f) else AdvWorld.arenas[4]
    }
}

private val miniPos = FloatArray(17 * 17 * 12); private val miniCol = IntArray(17 * 17 * 2)

private fun AdventureGame.drawMinimap(v: View) {
    val cxm = 738f; val cym = 96f; val R = 60f
    val cell = 7f
    val s = v.s; val ox = v.ox; val oy = v.oy
    v.rect(cxm - R - 4f, cym - R - 4f, R * 2f + 8f, R * 2f + 8f, P.INK)
    val cs = cos(camYaw); val sn = sin(camYaw)
    val ci = (px / AdvWorld.CELL).toInt(); val cj = (pz / AdvWorld.CELL).toInt()
    var n = 0
    val span = 8
    for (j in cj - span..cj + span) for (i in ci - span..ci + span) {
        if (i !in 0 until AdvWorld.N || j !in 0 until AdvWorld.N) continue
        val wx = (i + 0.5f) * AdvWorld.CELL - px; val wz = (j + 0.5f) * AdvWorld.CELL - pz
        val sx = wx * cs - wz * sn; val sy = wx * sn + wz * cs
        val scx = sx / AdvWorld.CELL * cell; val scy = -sy / AdvWorld.CELL * cell
        if (abs(scx) > R - 2f || abs(scy) > R - 2f) continue
        var col = AdvWorld.cellCols[j * AdvWorld.N + i]
        col = C.shade(col, 0.95f)
        val h = cell * 0.74f
        // quadrado girado
        val ex = cs * h; val ez = sn * h
        val c0x = scx - ex + ez; val c0y = scy - ez - ex
        val c1x = scx + ex + ez; val c1y = scy + ez - ex
        val c2x = scx + ex - ez; val c2y = scy + ez + ex
        val c3x = scx - ex - ez; val c3y = scy - ez + ex
        val o = n * 12
        fun put(k: Int, x: Float, y: Float) { miniPos[o + k * 2] = ox + (cxm + x) * s; miniPos[o + k * 2 + 1] = oy + (cym + y) * s }
        put(0, c0x, c0y); put(1, c1x, c1y); put(2, c2x, c2y); put(3, c0x, c0y); put(4, c2x, c2y); put(5, c3x, c3y)
        miniCol[n * 2] = col.toInt(); miniCol[n * 2 + 1] = col.toInt()
        n++
    }
    v.g.triBatch(miniPos, miniCol, n * 2)
    // marcadores
    fun mark(x: Float, z: Float, c: Long, r: Float) {
        val wx = x - px; val wz = z - pz
        val sx = (wx * cs - wz * sn) / AdvWorld.CELL * cell; val sy = -(wx * sn + wz * cs) / AdvWorld.CELL * cell
        if (abs(sx) < R - 3f && abs(sy) < R - 3f) v.circle(cxm + sx, cym + sy, r, c)
    }
    for ((i, b) in AdvWorld.bonfires.withIndex()) mark(b[0], b[1], if (flags.contains("bf$i")) P.ORANGE else P.SLATE, 2.6f)
    for (c in AdvWorld.chests) if (!flags.contains("ch_${c.id}")) mark(c.x, c.z, P.CYAN, 1.8f)
    for (nn in npcs) if (nn.d.rig < 100) mark(nn.x, nn.z, P.YELLOW, 2.2f)
    for (e in ens) if (e.alive && e.aggro) mark(e.x, e.z, if (e.boss) P.VIOLET else P.RED, if (e.boss) 3.6f else 2.2f)
    val ob = objectivePos()
    if (ob != null) {
        val wx = ob[0] - px; val wz = ob[1] - pz
        var sx = (wx * cs - wz * sn) / AdvWorld.CELL * cell; var sy = -(wx * sn + wz * cs) / AdvWorld.CELL * cell
        val m = max(abs(sx), abs(sy))
        if (m > R - 6f) { sx *= (R - 6f) / m; sy *= (R - 6f) / m }
        v.tri(cxm + sx, cym + sy - 5f, cxm + sx + 4f, cym + sy, cxm + sx, cym + sy + 5f, P.LIME); v.tri(cxm + sx, cym + sy - 5f, cxm + sx - 4f, cym + sy, cxm + sx, cym + sy + 5f, P.LIME)
    }
    // jogador (sempre aponta para cima)
    v.tri(cxm, cym - 6f, cxm - 4f, cym + 4f, cxm + 4f, cym + 4f, P.WHITE)
    // moldura
    v.rect(cxm - R - 4f, cym - R - 4f, R * 2f + 8f, 4f, P.VIOLET); v.rect(cxm - R - 4f, cym + R, R * 2f + 8f, 4f, P.VIOLET)
    v.rect(cxm - R - 4f, cym - R - 4f, 4f, R * 2f + 8f, P.VIOLET); v.rect(cxm + R, cym - R - 4f, 4f, R * 2f + 8f, P.VIOLET)
    v.pixText(zoneName.ifEmpty { AdvWorld.styles[zone].name }, cxm, cym + R + 8f, 1.4f, P.LILAC, center = true, shadow = false)
}

private var hpTrail = 100f

internal fun AdventureGame.drawHud(v: View) {
    if (mode == Mode.TITLE) return
    val mh = maxHp()
    hpTrail += (hp - hpTrail) * 0.05f; if (hpTrail < hp) hpTrail = hp
    // vida e energia
    v.rect(10f, 8f, 232f, 46f, C.alpha(P.INK, 0.6f))
    v.bar(18f, 14f, 220f * (mh / 160f).coerceIn(0.6f, 1.6f), 12f, hpTrail / mh, P.ORANGE, 0xFF3A1010L)
    v.bar(18f, 14f, 220f * (mh / 160f).coerceIn(0.6f, 1.6f), 12f, hp / mh, if (hp / mh < 0.3f) P.RED else 0xFFE04848L, 0x00000000L)
    v.bar(18f, 34f, 170f, 8f, stam / 100f, if (stam < 25f) P.ORANGE else P.LIME, 0xFF143014L)
    v.pixText("${max(0, hp.toInt())}/${mh.toInt()}", 22f, 16f, 1.5f, P.WHITE)
    // gemas
    v.pixText("GEMAS ${gems}", 794f - PixelFont.width("GEMAS $gems", 2f), 6f, 2f, P.CYAN)
    drawMinimap(v)
    // objetivo
    val m = AdvQuests.currentMain(this)
    if (m != null && mode == Mode.PLAY) {
        val txt = "OBJETIVO: " + AdvQuests.stepText(m, this)
        val w = min(520f, PixelFont.width(PixelFont.clean(txt), 1.6f) + 16f)
        v.rect(400f - w / 2f, 6f, w, 17f, C.alpha(P.INK, 0.7f))
        v.pixText(txt, 400f, 11f, 1.6f, P.LIME, center = true, shadow = false)
        val ob = objectivePos()
        if (ob != null) { val dd = dist(px, pz, ob[0], ob[1]); v.pixText("${dd.toInt()} M", 400f, 25f, 1.4f, P.SILVER, center = true, shadow = false) }
    }
    drawQuickSlots(v)
    // chefe
    val eb = engaged
    if (eb != null && eb.alive && eb.spawnIdx >= 9000) {
        v.rect(180f, 394f, 440f, 44f, C.alpha(P.INK, 0.65f))
        v.pixText(eb.def.name.uppercase(), 400f, 397f, 2f, P.YELLOW, center = true)
        v.bar(190f, 414f, 420f, 11f, eb.hp / eb.def.hp, P.RED, 0xFF300808L)
        for (ph in eb.def.phases) v.rect(190f + 420f * ph - 1f, 412f, 2f, 15f, P.WHITE)
        v.bar(190f, 429f, 420f, 3f, eb.poise / eb.def.poise, P.ORANGE, 0xFF301808L)
        for (i in 0..eb.def.phases.size) v.rect(578f + i * 10f, 400f, 7f, 7f, if (i <= eb.phase) P.ORANGE else P.SLATE)
    }
    // alvo travado
    val lk = lockT
    if (lk != null && lk.alive) {
        val o = FloatArray(3)
        if (r3.project(lk.x, lk.y + 2.1f * lk.def.sc, lk.z, o)) {
            val x = o[0]; val y = o[1] - 6f
            val c = if (lk.st == ES.STUN) P.CYAN else P.YELLOW
            val sz = 9f + sin(time * 6f) * 1.5f
            v.tri(x - sz, y - 12f, x + sz, y - 12f, x, y, c)
            v.tri(x - sz + 2f, y - 11f, x + sz - 2f, y - 11f, x, y - 3f, P.INK)
            if (lk.spawnIdx < 9000 && !lk.def.training) { v.pixText(lk.def.name.uppercase(), x, y - 36f, 1.5f, P.WHITE, center = true); v.bar(x - 28f, y - 26f, 56f, 5f, lk.hp / lk.def.hp, P.RED, 0xFF300808L) }
            else if (lk.def.training) v.pixText("INSTRUTOR", x, y - 26f, 1.5f, P.WHITE, center = true)
        }
    }
    // aviso do aparo (janela aberta)
    if (parryT > 0f) v.pixText("APARAR", 400f, 300f, 2f, C.alpha(P.YELLOW, 0.7f), center = true, shadow = false)
    if (riposteT > 0f && riposteTarget?.alive == true) v.pixText("A: CONTRA-ATAQUE", 400f, 282f, 2.4f, P.YELLOW, center = true)
    if (popupT > 0f) v.pixText(popup, 400f, 230f - (0.9f - popupT) * 16f, 3.4f, C.alpha(popupCol, (popupT * 3f).coerceIn(0f, 1f)), center = true)
    // toast
    if (toastT > 0f) {
        val w = min(700f, PixelFont.width(PixelFont.clean(toast), 2f) + 20f)
        v.rect(400f - w / 2f, 40f + (if (m != null) 8f else 0f), w, 22f, C.alpha(P.INK, 0.85f))
        v.pixText(toast, 400f, 46f + (if (m != null) 8f else 0f), 2f, P.WHITE, center = true, shadow = false)
    }
    // dica
    if (tipT > 0f && showHints) {
        val w = 560f
        v.rect(400f - w / 2f, 318f, w, 40f, C.alpha(P.INK, 0.88f)); v.rect(400f - w / 2f, 318f, 4f, 40f, P.CYAN)
        v.wrapText(tipText, 400f - w / 2f + 14f, 324f, w - 24f, 1.7f, P.CYAN, 16f)
    }
    // interação
    if (prompt.isNotEmpty() && mode == Mode.PLAY) {
        val t = prompt
        val w = PixelFont.width(PixelFont.clean(t), 2f) + 56f
        v.rect(400f - w / 2f, 366f, w, 24f, C.alpha(P.INK, 0.88f))
        v.chip(400f - w / 2f + 6f, 371f, btnName(bind[Act.INTERACT]), P.VIOLET)
        v.pixText(t, 400f - w / 2f + 40f, 372f, 2f, P.WHITE, shadow = false)
    }
    // nome da região
    if (zoneBanner > 0f) v.pixText(zoneName.uppercase(), 400f, 70f, 3.6f, C.alpha(P.YELLOW, min(1f, zoneBanner)), center = true)
    // pouca vida
    if (hp / mh < 0.25f && mode == Mode.PLAY) v.rect(0f, 0f, 800f, 450f, C.alpha(0xFFFF0000L, 0.06f + 0.05f * sin(time * 6f)))
}

private fun shortName(id: String) = when (id) { "ferrugem" -> "FERRUGEM"; "ferrugem_s" -> "ESC.FERR"; else -> PixelFont.clean(AdvData.items[id]?.name ?: id).substringBefore(" ").take(9) }

private fun itemColor(id: String): Long = when (id) { "seiva" -> P.LIME; "bomba" -> 0xFF2A2C34L; "pedra" -> P.SILVER; "fruta" -> 0xFFFF5A70L; else -> P.WHITE }

private fun AdventureGame.drawQuickSlots(v: View) {
    val x = 10f; val y = 372f
    v.rect(x, y, 232f, 70f, C.alpha(P.INK, 0.72f))
    // arma
    val wd = wdef()
    v.rect(x + 6f, y + 6f, 56f, 56f, P.DUSK); v.rect(x + 6f, y + 6f, 56f, 2f, P.VIOLET)
    val cx = x + 34f; val cy = y + 34f
    when (wd.ws) {
        WS.BOW -> { v.line(cx - 12f, cy - 18f, cx + 4f, cy, 3f, 0xFFA06C32L); v.line(cx + 4f, cy, cx - 12f, cy + 18f, 3f, 0xFFA06C32L); v.line(cx - 12f, cy - 18f, cx - 12f, cy + 18f, 1f, P.WHITE) }
        WS.STAFF -> { v.line(cx - 12f, cy + 20f, cx + 10f, cy - 14f, 4f, 0xFF8A5A2BL); v.circle(cx + 12f, cy - 18f, 6f, 0xFF9BE6FFL) }
        WS.AXE -> { v.line(cx - 12f, cy + 20f, cx + 8f, cy - 14f, 4f, 0xFF8A5A2BL); v.tri(cx + 2f, cy - 18f, cx + 18f, cy - 8f, cx + 6f, cy + 2f, P.SILVER) }
        WS.SPEAR -> { v.line(cx - 14f, cy + 20f, cx + 12f, cy - 18f, 3f, 0xFF8A5A2BL); v.tri(cx + 8f, cy - 14f, cx + 18f, cy - 24f, cx + 14f, cy - 8f, P.SILVER) }
        else -> { v.line(cx - 12f, cy + 16f, cx + 12f, cy - 18f, 5f, if (eqW == "ferrugem") 0xFFB5651DL else if (eqW == "aurora") 0xFFFFE066L else if (eqW == "galho") 0xFF8A5A2BL else P.SILVER); v.line(cx - 12f, cy + 6f, cx - 2f, cy + 12f, 3f, 0xFF8A5A2BL) }
    }
    v.pixText(shortName(eqW), x + 6f, y + 60f, 1.0f, P.LILAC, shadow = false)
    val lv = wlv[eqW] ?: 0
    if (lv > 0) v.pixText("+$lv", x + 52f, y + 8f, 1.4f, P.YELLOW, shadow = false)
    v.chip(x + 6f, y - 8f, btnName(bind[Act.ATK]), P.BLUE)
    // item
    val q = quickItem()
    v.rect(x + 70f, y + 6f, 56f, 56f, P.DUSK); v.rect(x + 70f, y + 6f, 56f, 2f, P.VIOLET)
    val ix = x + 98f; val iy = y + 34f
    val col = itemColor(q)
    when (q) {
        "bomba" -> { v.circle(ix, iy + 4f, 12f, col); v.line(ix + 4f, iy - 8f, ix + 10f, iy - 16f, 2f, 0xFFC9A66BL); v.circle(ix + 11f, iy - 17f, 3f, P.ORANGE) }
        "pedra" -> v.tri(ix - 14f, iy + 10f, ix, iy - 14f, ix + 14f, iy + 10f, col)
        "fruta" -> { v.circle(ix - 6f, iy, 9f, col); v.circle(ix + 6f, iy, 9f, col); v.tri(ix - 14f, iy + 2f, ix + 14f, iy + 2f, ix, iy + 16f, col) }
        else -> { v.rect(ix - 8f, iy - 6f, 16f, 18f, 0xFFB6E3F0L); v.rect(ix - 6f, iy - 2f, 12f, 12f, col); v.rect(ix - 4f, iy - 12f, 8f, 7f, 0xFF8A5A2BL) }
    }
    val n = if (q == "seiva") "$flask/$flaskMax" else "x${count(q)}"
    v.pixText(n, x + 124f - PixelFont.width(n, 1.6f), y + 50f, 1.6f, P.WHITE)
    v.pixText(shortName(q), x + 70f, y + 60f, 1.0f, P.LILAC, shadow = false)
    v.chip(x + 70f, y - 8f, btnName(bind[Act.ITEM]), P.GREEN)
    // trocas rápidas
    v.pixText("CIMA: MOCHILA", x + 134f, y + 6f, 1.15f, P.SILVER, shadow = false)
    v.pixText("BAIXO: SEIVA", x + 134f, y + 18f, 1.15f, P.SILVER, shadow = false)
    v.pixText("ESQ/DIR: ARMA", x + 134f, y + 30f, 1.15f, P.SILVER, shadow = false)
    v.pixText("R3: TROCA ITEM", x + 134f, y + 42f, 1.15f, P.SILVER, shadow = false)
}

private fun AdventureGame.drawBanner(v: View) {
    val a = (min(bannerT, 1f) * min(1f, (4.5f - bannerT) * 3f)).coerceIn(0f, 1f)
    v.rect(0f, 150f, 800f, 80f, C.alpha(P.INK, 0.65f * a))
    v.pixText(banner, 400f, 162f, 4.4f, C.alpha(P.YELLOW, a), center = true)
    v.pixText(bannerSub, 400f, 206f, 2.2f, C.alpha(P.LILAC, a), center = true, shadow = false)
}

// =============================================================================================== telas

private fun AdventureGame.drawTitle(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.3f))
    v.pixText("VALE DE AURORA", 400f, 62f, 7.4f, P.YELLOW, center = true)
    v.pixText("A LENDA DO CORACAO PARTIDO", 400f, 124f, 2.6f, P.LILAC, center = true)
    val opts = titleOpts()
    for ((i, o) in opts.withIndex()) {
        val y = 250f + i * 40f
        if (i == titleCur) v.rect(250f, y - 8f, 300f, 34f, P.VIOLET)
        val label = if (o == "NOVO JOGO" && confirmNew && i == titleCur) "TEM CERTEZA? A" else o
        v.pixText(label, 400f, y, 2.8f, P.WHITE, center = true)
    }
    v.pixText("D-PAD ESCOLHER   A CONFIRMAR", 400f, 410f, 2f, P.CYAN, center = true, shadow = false)
}

private fun AdventureGame.drawStory(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.82f))
    val i = pageIdx.coerceIn(0, AdvData.prologue.size - 1)
    v.wrapText(AdvData.prologue[i], 90f, 150f, 620f, 2.8f, P.WHITE, 34f)
    v.pixText("A: CONTINUAR   (${i + 1}/${AdvData.prologue.size})", 400f, 400f, 2f, P.CYAN, center = true, shadow = false)
}

private fun AdventureGame.drawEnding(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.86f))
    val i = pageIdx.coerceIn(0, AdvData.epilogue.size - 1)
    v.wrapText(AdvData.epilogue[i], 90f, 150f, 620f, 2.8f, P.WHITE, 34f)
    v.pixText("A: CONTINUAR", 400f, 400f, 2f, P.CYAN, center = true, shadow = false)
    v.pixText("TEMPO ${playSec.toInt() / 60} MIN", 400f, 60f, 2f, P.LILAC, center = true, shadow = false)
}

private fun AdventureGame.drawDead(v: View) {
    val a = (deadT / 1.2f).coerceIn(0f, 0.85f)
    v.rect(0f, 0f, 800f, 450f, C.alpha(0xFF3A0000L, a))
    if (deadT > 0.6f) v.pixText("VOCE CAIU", 400f, 190f, 7f, C.alpha(P.RED, (deadT - 0.6f).coerceIn(0f, 1f)), center = true)
    if (deadT > 1.2f && gems > 0) v.pixText("AS GEMAS FICAM ONDE VOCE CAIU", 400f, 270f, 2f, P.LILAC, center = true, shadow = false)
    if (deadT > 2.2f) v.pixText("A: VOLTAR A BRASA", 400f, 320f, 2.2f, P.CYAN, center = true, shadow = false)
}

private fun AdventureGame.drawDialogBox(v: View) {
    val c = convo ?: return
    v.panel(40f, 322f, 720f, 112f)
    val line = if (dlgChoice) null else c.lines[dlgIdx.coerceIn(0, c.lines.size - 1)]
    val col = when (line?.emo ?: 0) { 1 -> P.LIME; 2 -> P.ORANGE; 3 -> P.RED; 4 -> P.SKY; else -> P.YELLOW }
    v.rect(52f, 314f, PixelFont.width(PixelFont.clean(c.who), 2.4f) + 20f, 22f, P.VIOLET)
    v.pixText(c.who, 62f, 319f, 2.4f, P.WHITE, shadow = false)
    if (line != null) {
        val shown = line.text.substring(0, min(line.text.length, dlgT.toInt()))
        v.wrapText(shown, 60f, 346f, 680f, 2.1f, P.WHITE, 22f)
        if (dlgT >= line.text.length) v.pixText(">", 735f, 412f, 2.4f, P.CYAN)
        v.rect(52f + PixelFont.width(PixelFont.clean(c.who), 2.4f) + 24f, 322f, 8f, 8f, col)
    } else {
        for ((i, ch) in c.choices.withIndex()) {
            val y = 340f + i * 24f
            if (i == cursor) v.rect(56f, y - 4f, 688f, 22f, P.VIOLET)
            v.pixText(ch.first, 70f, y, 2.1f, P.WHITE, shadow = false)
        }
    }
}

private fun AdventureGame.drawMenuBox(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.5f))
    v.panel(130f, 36f, 540f, 372f)
    v.pixText(menuTitle, 400f, 50f, 2.8f, P.YELLOW, center = true)
    v.pixText("GEMAS $gems", 400f, 82f, 2f, P.CYAN, center = true)
    for ((i, o) in menuOpts.withIndex()) {
        val y = 112f + i * 32f
        if (y > 340f) break
        if (i == cursor) v.rect(146f, y - 7f, 508f, 28f, P.VIOLET)
        v.pixText(o.label, 160f, y, 2f, if (o.ok) P.WHITE else P.SLATE)
    }
    val cur = menuOpts.getOrNull(cursor)
    if (cur != null && cur.desc.isNotEmpty()) v.wrapText(cur.desc, 150f, 350f, 500f, 1.6f, P.LILAC, 17f)
    v.pixText("A: ESCOLHER   X: VOLTAR", 400f, 388f, 1.5f, P.SILVER, center = true, shadow = false)
}

internal fun AdventureGame.tabNames(): List<String> = Cat.names + listOf("DIARIO", "AJUSTES")

private fun rarityCol(r: Int) = when (r) { 1 -> P.SKY; 2 -> P.YELLOW; else -> P.WHITE }

private fun AdventureGame.drawInv(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.74f))
    v.panel(24f, 16f, 752f, 418f)
    val tabs = tabNames()
    val tw = 736f / tabs.size
    for ((i, nm) in tabs.withIndex()) {
        val x = 32f + i * tw
        v.rect(x, 24f, tw - 4f, 24f, if (i == invTab) P.VIOLET else P.DUSK)
        v.pixText(nm, x + tw / 2f - 2f, 31f, 1.3f, if (i == invTab) P.WHITE else P.SILVER, center = true, shadow = false)
        if (i < Cat.names.size && AdvData.items.values.any { it.cat == i && isNew(it.id) }) v.rect(x + tw - 14f, 26f, 8f, 8f, P.RED)
    }
    val qTab = Cat.names.size
    if (invTab == qTab) { drawQuestLog(v); return }
    if (invTab == qTab + 1) { v.pixText("A: ABRIR AJUSTES", 400f, 200f, 2.2f, P.CYAN, center = true); return }
    val list = invItems()
    for ((i, it) in list.withIndex()) {
        val y = 58f + i * 25f
        if (y > 300f) break
        if (i == invCur) v.rect(36f, y - 4f, 440f, 22f, P.VIOLET)
        val eq = it.id == eqW || it.id == eqS || it.id == eqC
        var label = it.name
        val n = count(it.id)
        if (it.cat == Cat.CONSUMO || it.cat == Cat.MATERIAL || it.cat == Cat.CHAVE) label += "  x$n"
        if (it.cat == Cat.ARMA) label += "  +${wlv[it.id] ?: 0}"
        v.pixText(label, 48f, y, 1.8f, if (eq) P.YELLOW else rarityCol(it.rarity), shadow = false)
        if (isNew(it.id)) v.chip(396f, y - 1f, "NOVO", P.RED)
        if (eq) v.pixText("EQUIPADO", 468f - PixelFont.width("EQUIPADO", 1.3f), y + 3f, 1.3f, P.LIME, shadow = false)
    }
    if (list.isEmpty()) v.pixText("VAZIO", 52f, 80f, 2f, P.SLATE)
    // painel de equipamento
    v.pixText("EQUIPAMENTO", 620f, 58f, 1.8f, P.LILAC, center = true)
    fun nm(id: String) = if (id.isEmpty()) "-" else (AdvData.items[id]?.name ?: id)
    v.pixText("ARMA", 490f, 78f, 1.3f, P.SLATE, shadow = false); v.pixText(nm(eqW), 490f, 92f, 1.5f, P.WHITE, shadow = false)
    v.pixText("ESCUDO", 490f, 112f, 1.3f, P.SLATE, shadow = false); v.pixText(nm(eqS), 490f, 126f, 1.5f, P.WHITE, shadow = false)
    v.pixText("AMULETO", 490f, 146f, 1.3f, P.SLATE, shadow = false); v.pixText(nm(eqC), 490f, 160f, 1.5f, P.WHITE, shadow = false)
    val wd = wdef()
    v.pixText("DANO ${wDmg().toInt()}   ALCANCE ${wd.reach}", 490f, 188f, 1.4f, P.ORANGE, shadow = false)
    v.pixText("VIDA ${maxHp().toInt()}   APARO ${(parryWindow() * 1000).toInt()} MS", 490f, 206f, 1.4f, P.RED, shadow = false)
    v.pixText("SEIVA $flask/$flaskMax   GEMAS $gems", 490f, 224f, 1.4f, P.LIME, shadow = false)
    v.pixText("BLOQUEIO ${(sdef().block * 100).toInt()}%", 490f, 242f, 1.4f, P.SKY, shadow = false)
    v.pixText("${wd.trait.uppercase()}", 490f, 260f, 1.4f, P.YELLOW, shadow = false)
    val cur = list.getOrNull(invCur.coerceIn(0, max(0, list.size - 1)))
    v.rect(36f, 306f, 728f, 2f, P.VIOLET)
    if (cur != null) {
        v.pixText(cur.name, 48f, 316f, 2.1f, rarityCol(cur.rarity))
        v.wrapText(cur.desc, 48f, 342f, 700f, 1.6f, P.LILAC, 17f)
        if (cur.stat.isNotEmpty()) v.pixText(cur.stat, 48f, 392f, 1.6f, P.CYAN, shadow = false)
    }
    v.pixText("ESQ/DIR: ABA   A: EQUIPAR/USAR   SELECT: MAPA   X: FECHAR", 400f, 412f, 1.3f, P.SILVER, center = true, shadow = false)
}

private fun AdventureGame.questList(): List<QDef> = AdvQuests.all.filter { val s = AdvQuests.state(it, this); s == 1 || s == 2 || s == 3 }.sortedWith(compareByDescending<QDef> { it.main }.thenBy { AdvQuests.state(it, this) == 3 })

private fun AdventureGame.drawQuestLog(v: View) {
    val l = questList()
    if (l.isEmpty()) { v.pixText("NENHUMA MISSAO AINDA", 400f, 200f, 2f, P.SLATE, center = true); return }
    for ((i, q) in l.withIndex()) {
        val y = 58f + i * 22f
        if (y > 290f) break
        if (i == invCur) v.rect(36f, y - 3f, 330f, 20f, P.VIOLET)
        val st = AdvQuests.state(q, this)
        v.pixText((if (q.main) "* " else "") + q.title, 46f, y, 1.6f, if (st == 3) P.SLATE else if (st == 2) P.LIME else P.WHITE, shadow = false)
    }
    val q = l.getOrNull(invCur.coerceIn(0, l.size - 1)) ?: return
    val st = AdvQuests.state(q, this)
    v.pixText(q.title.uppercase(), 390f, 58f, 2f, P.YELLOW)
    v.pixText(if (q.main) "MISSAO PRINCIPAL" else "MISSAO SECUNDARIA", 390f, 80f, 1.3f, P.LILAC, shadow = false)
    v.wrapText(q.summary, 390f, 100f, 370f, 1.5f, P.WHITE, 17f)
    var y = 160f
    for (s in q.steps) { val d = s.done(this); v.pixText((if (d) "[X] " else "[ ] ") + PixelFont.clean(s.text.replace("\$n", shards().toString())), 390f, y, 1.3f, if (d) P.LIME else P.SILVER, shadow = false); y += 16f }
    v.pixText(if (st == 3) "CONCLUIDA" else if (st == 2) "VOLTE A ${q.giver.uppercase()}" else "ATUAL: " + AdvQuests.stepText(q, this), 390f, y + 8f, 1.4f, if (st == 2) P.LIME else P.CYAN, shadow = false)
    if (q.rewardText.isNotEmpty()) v.pixText("RECOMPENSA: ${q.rewardText}", 390f, y + 26f, 1.3f, P.YELLOW, shadow = false)
    v.pixText("ESQ/DIR: ABA   X: FECHAR", 400f, 412f, 1.3f, P.SILVER, center = true, shadow = false)
}

private val mapPos = FloatArray(33 * 33 * 12); private val mapColArr = IntArray(33 * 33 * 2)

private fun AdventureGame.drawMap(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.85f))
    v.panel(24f, 16f, 752f, 418f)
    v.pixText("MAPA DO VALE", 400f, 24f, 2.4f, P.YELLOW, center = true)
    val cell = 11f; val ox0 = 50f; val oy0 = 56f
    val s = v.s; val vox = v.ox; val voy = v.oy
    var n = 0
    for (j in 0 until 33) for (i in 0 until 33) {
        val seen = mapSeen[j * 33 + i]
        var col = if (seen) AdvWorld.cellCols[min(AdvWorld.N - 1, j * 2) * AdvWorld.N + min(AdvWorld.N - 1, i * 2)] else 0xFF14122EL
        if (seen) col = C.shade(col, 0.95f)
        val x0 = vox + (ox0 + i * cell) * s; val y0 = voy + (oy0 + j * cell) * s; val x1 = x0 + cell * s; val y1 = y0 + cell * s
        val o = n * 12
        mapPos[o] = x0; mapPos[o + 1] = y0; mapPos[o + 2] = x1; mapPos[o + 3] = y0; mapPos[o + 4] = x1; mapPos[o + 5] = y1
        mapPos[o + 6] = x0; mapPos[o + 7] = y0; mapPos[o + 8] = x1; mapPos[o + 9] = y1; mapPos[o + 10] = x0; mapPos[o + 11] = y1
        mapColArr[n * 2] = col.toInt(); mapColArr[n * 2 + 1] = col.toInt(); n++
    }
    v.g.triBatch(mapPos, mapColArr, n * 2)
    fun pt(x: Float, z: Float) = floatArrayOf(ox0 + x / AdvWorld.SIZE * 33f * cell, oy0 + z / AdvWorld.SIZE * 33f * cell)
    fun seenAt(x: Float, z: Float) = mapSeen[((z / AdvWorld.CELL / 2f).toInt().coerceIn(0, 32)) * 33 + (x / AdvWorld.CELL / 2f).toInt().coerceIn(0, 32)]
    for ((i, b) in AdvWorld.bonfires.withIndex()) if (flags.contains("bf$i")) { val p = pt(b[0], b[1]); v.circle(p[0], p[1], 4f, P.ORANGE); v.circle(p[0], p[1], 2f, P.YELLOW) }
    for ((i, a) in AdvWorld.arenas.withIndex()) if (seenAt(a[0], a[1]) && !flags.contains(AdvEnemies.rewards[i].flag)) { val p = pt(a[0], a[1]); v.pixText("!", p[0], p[1] - 5f, 2f, P.VIOLET, center = true) }
    for (d in AdvWorld.doors) if (!doorOpen(d) && seenAt(d.x, d.z)) { val p = pt(d.x, d.z); v.rect(p[0] - 3f, p[1] - 3f, 6f, 6f, P.RED) }
    for (c in AdvWorld.chests) if (!flags.contains("ch_${c.id}") && seenAt(c.x, c.z)) { val p = pt(c.x, c.z); v.rect(p[0] - 2f, p[1] - 2f, 4f, 4f, P.CYAN) }
    val ob = objectivePos()
    if (ob != null) { val p = pt(ob[0], ob[1]); v.tri(p[0], p[1] - 7f, p[0] + 5f, p[1], p[0], p[1] + 7f, P.LIME); v.tri(p[0], p[1] - 7f, p[0] - 5f, p[1], p[0], p[1] + 7f, P.LIME) }
    val pp = pt(px, pz)
    val a = pyaw
    v.tri(pp[0] + sin(a) * 7f, pp[1] + cos(a) * 7f, pp[0] + sin(a + 2.5f) * 5f, pp[1] + cos(a + 2.5f) * 5f, pp[0] + sin(a - 2.5f) * 5f, pp[1] + cos(a - 2.5f) * 5f, P.WHITE)
    // legenda
    var y = 60f
    v.pixText("LEGENDA", 440f, y, 1.8f, P.LILAC); y += 20f
    v.circle(448f, y + 5f, 4f, P.ORANGE); v.pixText("BRASA", 460f, y, 1.5f, P.WHITE, shadow = false); y += 16f
    v.pixText("!", 448f, y, 1.8f, P.VIOLET, center = true); v.pixText("GUARDIAO", 460f, y, 1.5f, P.WHITE, shadow = false); y += 16f
    v.rect(445f, y + 1f, 6f, 6f, P.RED); v.pixText("PORTA TRANCADA", 460f, y, 1.5f, P.WHITE, shadow = false); y += 16f
    v.rect(446f, y + 2f, 4f, 4f, P.CYAN); v.pixText("BAU", 460f, y, 1.5f, P.WHITE, shadow = false); y += 16f
    v.tri(448f, y, 453f, y + 6f, 443f, y + 6f, P.LIME); v.pixText("OBJETIVO", 460f, y, 1.5f, P.WHITE, shadow = false); y += 26f
    v.pixText("BRASAS ACESAS", 440f, y, 1.6f, P.LILAC); y += 18f
    for (i in litBonfires()) { if (y > 380f) break; v.pixText(AdvWorld.bonfireNames[i], 440f, y, 1.3f, P.SILVER, shadow = false); y += 14f }
    v.pixText("REGIAO: ${AdvWorld.styles[zone].name}", 440f, 396f, 1.4f, P.YELLOW, shadow = false)
    v.pixText("SELECT/X: FECHAR   R3: MOCHILA", 400f, 418f, 1.3f, P.SILVER, center = true, shadow = false)
}

private fun AdventureGame.drawSettings(v: View) {
    v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.82f))
    v.panel(80f, 14f, 640f, 424f)
    v.pixText(if (remapPage) "REMAPEAR BOTOES" else "AJUSTES", 400f, 24f, 2.8f, P.YELLOW, center = true)
    if (remapPage) {
        for (a in 0 until Act.COUNT) {
            val y = 60f + a * 28f
            if (a == remapCur) v.rect(94f, y - 5f, 612f, 25f, P.VIOLET)
            v.pixText(Act.names[a], 110f, y, 1.9f, P.WHITE, shadow = false)
            val t = if (setRemap == a) "APERTE UM BOTAO..." else btnName(bind[a])
            v.pixText(t, 690f - PixelFont.width(t, 1.9f), y, 1.9f, if (setRemap == a) P.YELLOW else P.CYAN, shadow = false)
        }
        v.pixText("A: TROCAR   X: VOLTAR   (B e START sao reservados)", 400f, 400f, 1.3f, P.SILVER, center = true, shadow = false)
        return
    }
    val vals = listOf(
        Act.presets.getOrElse(presetIdx) { "PERSONALIZADO" }, "${(sens * 100).toInt()}%", if (invertY) "SIM" else "NAO", if (lockHold) "SEGURAR" else "ALTERNAR",
        when (parryDiff) { 0 -> "FACIL"; 2 -> "DIFICIL"; else -> "NORMAL" } + "  ${(parryWindow() * 1000).toInt()} MS", if (autoSwitch) "SIM" else "NAO", if (shakeOn) "SIM" else "NAO", if (showHints) "SIM" else "NAO",
        "${(musicVolume * 100).toInt()}%", "${(sfxVolume * 100).toInt()}%", if (GfxFlags.compat3d) "COMPATIVEL" else "RAPIDA", ">", if (Crumb.diagOn()) "SIM" else "NAO", ""
    )
    for ((i, nm) in setItems.withIndex()) {
        val y = 50f + i * 25f
        if (i == setCur) v.rect(94f, y - 5f, 612f, 24f, P.VIOLET)
        v.pixText(nm, 110f, y, 1.8f, P.WHITE, shadow = false)
        v.pixText(vals[i], 690f - PixelFont.width(PixelFont.clean(vals[i]), 1.8f), y, 1.8f, P.CYAN, shadow = false)
    }
    v.pixText("ESQ/DIR: MUDAR   A: ALTERNAR   X: VOLTAR", 400f, 410f, 1.4f, P.SILVER, center = true, shadow = false)
}
