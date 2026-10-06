package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================================================== jogador

internal fun AdventureGame.facing(): Float = pyaw

internal fun AdventureGame.angTo(x: Float, z: Float) = atan2(x - px, z - pz)

internal fun AdventureGame.updateWorldLite(dt: Float) {
    updateParticles(dt)
    for (n in npcs) updateNpc(n, dt, true)
}

internal fun AdventureGame.updatePlay(dt0: Float, input: GameInput) {
    playSec += dt0
    // parada de quadro nos impactos
    var dt = dt0
    if (hitStop > 0f) { hitStop -= dt0; dt = 0f }

    // mapa e mochila
    if (pAct == PA.FREE || pAct == PA.ATK) {
        if (pressBtn(Act.MAP, input)) { mode = Mode.MAP; snd(Sfx.MENU_OPEN); return }
        if (pressBtn(Act.BAG, input)) { mode = Mode.INV; invTab = 0; invCur = 0; snd(Sfx.MENU_OPEN); return }
    }

    if (dt > 0f) {
        if (iT > 0f) iT -= dt
        if (hurtT > 0f) hurtT -= dt
        if (parryCd > 0f) parryCd -= dt
        if (parryRec > 0f) parryRec -= dt
        if (comboWin > 0f) { comboWin -= dt; if (comboWin <= 0f) combo = 0 }
        if (riposteT > 0f) { riposteT -= dt; if (riposteT <= 0f) riposteTarget = null }
        if (buffT > 0f) buffT -= dt
        if (stamDelay > 0f) stamDelay -= dt
        if (parryT > 0f) {
            parryT -= dt
            if (parryT <= 0f) { parryT = 0f; parryRec = 0.38f }
        }
    }

    updateLock(dt, input)
    updateCamera(dt0, dt, input)

    if (dt > 0f) {
        updatePlayerAction(dt, input)
        updateEnemies(dt)
        updateProjectiles(dt)
        updateHazards(dt)
        updateBombs(dt)
        updateDrops(dt)
        for (n in npcs) updateNpc(n, dt, false)
        updateCritters(dt)
        updateZone(dt)
        checkT += dt
        if (checkT > 0.6f) { checkT = 0f; checkQuests(); revealMap() }
        updateBossFlow(dt)
        if (endCountdown > 0f) { endCountdown -= dt; if (endCountdown <= 0f) { mode = Mode.ENDING; pageIdx = 0; pageT = 0f; saveDirty = true } }
        if (hp <= 0f && pAct != PA.DEAD) die()
    }
    updateParticles(dt0)
    updateStyle(dt0)
    findPrompt()
    updatePlayerAnim(dt0)
}

// ---------------------------------------------------------------------------------------------- mira
internal fun AdventureGame.targets(maxD: Float): List<En> = ens.filter { it.alive && it.st != ES.DEAD && !(it.def.training && false) && dist(it.x, it.z, px, pz) < maxD && (it.spawnIdx < 9000 || it.introDone || true) }

internal fun AdventureGame.acquireLock() {
    val c = targets(20f)
    if (c.isEmpty()) { lockT = null; return }
    var best: En? = null; var bs = 1e9f
    for (e in c) {
        val d = dist(e.x, e.z, px, pz)
        val rel = abs(wrap(angTo(e.x, e.z) - camYaw))
        val s = d + rel * 6f
        if (s < bs) { bs = s; best = e }
    }
    lockT = best
    if (best != null) { snd(Sfx.LOCK); giveTip("tip_guard") }
}

internal fun AdventureGame.updateLock(dt: Float, input: GameInput) {
    val lp = pressBtn(Act.LOCK, input)
    if (lockHold) {
        if (hasBtn(Act.LOCK, input)) { if (lockT == null) acquireLock() } else lockT = null
    } else if (lp) {
        if (lockT != null) { lockT = null; snd(Sfx.LOCK) } else acquireLock()
    }
    val lk = lockT
    if (lk != null) {
        if (!lk.alive || lk.st == ES.DEAD || dist(lk.x, lk.z, px, pz) > 30f) {
            lockT = null
            if (autoSwitch && !lockHold) { val c = targets(14f).minByOrNull { dist(it.x, it.z, px, pz) }; lockT = c }
        } else {
            // trocar de alvo com um golpe do analógico direito
            val tx = input.turnX
            if (abs(tx) < 0.35f) flickReady = true
            else if (flickReady && abs(tx) > 0.8f) {
                flickReady = false
                val cur = wrap(angTo(lk.x, lk.z) - camYaw)
                var best: En? = null; var bd = 9f
                for (e in targets(22f)) {
                    if (e === lk) continue
                    val a = wrap(angTo(e.x, e.z) - camYaw)
                    val d = (a - cur) * (if (tx > 0f) 1f else -1f) // camera: direita = angulo maior
                    if (d > 0.05f && d < bd) { bd = d; best = e }
                }
                if (best != null) { lockT = best; snd(Sfx.LOCK) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- câmera
internal fun AdventureGame.updateCamera(dt0: Float, dt: Float, input: GameInput) {
    val lk = lockT
    val turn = if (abs(input.turnX) > 0.15f) input.turnX else 0f
    val turnY = if (abs(input.turnY) > 0.15f) input.turnY else 0f
    if (lk != null && lk.alive) {
        val want = atan2(lk.x - px, lk.z - pz)
        camYaw = lerpAng(camYaw, want, dt0 * 4.5f)
        val d = dist(lk.x, lk.z, px, pz)
        camPitch += ((0.30f + min(0.2f, d * 0.012f)) - camPitch) * min(1f, dt0 * 3f)
    } else {
        if (turn != 0f) camYaw -= turn * 2.4f * sens * dt0 * -1f
        else if (abs(input.dx) > 0.3f && abs(input.dy) < 0.6f && pAct == PA.FREE) camYaw += input.dx * 0.55f * dt0
        if (turnY != 0f) camPitch = (camPitch + turnY * 1.5f * sens * dt0 * (if (invertY) 1f else -1f)).coerceIn(0.04f, 0.9f)
        if (pressBtn(Act.CAMRESET, input)) camYaw = pyaw
    }
    val eb = engaged
    val wantDist = if (eb != null && eb.alive) 11.5f else if (lk != null) 9.2f else 8.5f
    camDist += (wantDist - camDist) * min(1f, dt0 * 2f)
}

/** Posição final da câmera com colisão com o terreno e o cenário. */
internal fun AdventureGame.computeCamera(): FloatArray {
    var tx = px; var tz = pz; var ty = py + 1.6f
    if (mode == Mode.TITLE || mode == Mode.STORY || mode == Mode.ENDING) {
        tx = 100f; tz = 144f; ty = 5f
        val d = 34f
        val cx = tx - sin(camYaw) * d; val cz = tz - cos(camYaw) * d
        return floatArrayOf(cx, max(AdvWorld.hAt(cx, cz) + 3f, 12f), cz, 0.28f)
    }
    val lk = lockT
    if (lk != null && lk.alive) { tx += (lk.x - px) * 0.12f; tz += (lk.z - pz) * 0.12f }
    val cp = cos(camPitch)
    var d = camDist
    val dirx = -sin(camYaw) * cp; val dirz = -cos(camYaw) * cp; val diry = sin(camPitch)
    var k = 1
    var hitT = d
    while (k <= 14) {
        val t = d * k / 14f
        val sx = tx + dirx * t; val sz = tz + dirz * t; val sy = ty + diry * t
        if (sy < AdvWorld.hAt(sx, sz) + 0.55f || camBlocked(sx, sy, sz)) { hitT = max(1.6f, t - 0.9f); break }
        k++
    }
    d = hitT
    val cx = tx + dirx * d; val cz = tz + dirz * d; var cy = ty + diry * d
    cy = max(cy, AdvWorld.hAt(cx, cz) + 0.9f)
    return floatArrayOf(cx, cy, cz, camPitch)
}

// ---------------------------------------------------------------------------------------------- ação do jogador
internal fun AdventureGame.faceAssist(input: GameInput, mx: Float, mz: Float, moving: Boolean) {
    val lk = lockT
    if (lk != null && lk.alive) { pyaw = angTo(lk.x, lk.z); return }
    if (moving) pyaw = atan2(mx, mz)
    // auxílio de mira: se há inimigo próximo à frente, vira para ele
    var best: En? = null; var bd = 3.6f
    for (e in ens) {
        if (!e.alive) continue
        val d = dist(e.x, e.z, px, pz)
        if (d < bd && abs(wrap(angTo(e.x, e.z) - pyaw)) < 0.9f) { bd = d; best = e }
    }
    if (best != null) pyaw = angTo(best.x, best.z)
}

internal fun AdventureGame.updatePlayerAction(dt: Float, input: GameInput) {
    val wd = wdef()
    // movimento desejado
    val fx = sin(camYaw); val fz = cos(camYaw)
    val rx = cos(camYaw); val rz = -sin(camYaw)
    var mx = rx * input.dx + fx * (-input.dy)
    var mz = rz * input.dx + fz * (-input.dy)
    val mag = min(1f, sqrt(input.dx * input.dx + input.dy * input.dy))
    val moving = mag > 0.14f
    if (moving) { val l = sqrt(mx * mx + mz * mz); if (l > 0.001f) { mx /= l; mz /= l } } else { mx = 0f; mz = 0f }

    // regeneração de fôlego
    val free = pAct == PA.FREE
    if (stamDelay <= 0f && (free || pAct == PA.CHARGE)) stam = min(100f, stam + (if (guardHeld) 12f else stamRegen()) * dt)

    var speed = 0f
    var autoFace = false
    val lk = lockT
    when (pAct) {
        PA.FREE -> {
            // defesa e aparo: o aparo só abre ao APERTAR o botão (segurar não apara sozinho)
            guardHeld = hasBtn(Act.GUARD, input) && parryRec <= 0f
            if (pressBtn(Act.GUARD, input) && parryRec <= 0f && parryCd <= 0f) {
                parryT = parryWindow(); parryCd = 0.7f; snd(Sfx.SWITCH)
            }
            if (!hasBtn(Act.GUARD, input)) guardHeld = false

            speed = if (guardHeld || parryT > 0f) 3.4f else if (lk != null) 5.4f else 6.2f
            if (moving && lk == null) pyaw = lerpAng(pyaw, atan2(mx, mz), dt * 14f)
            else if (lk != null && lk.alive) pyaw = lerpAng(pyaw, angTo(lk.x, lk.z), dt * 12f)
            else if (guardHeld) { /* mantém a direção */ }

            // trocas rápidas: D-pad baixo = seiva, esquerda/direita = arma, R3 = trocar item (cima abre a mochila)
            if (input.bPressed[Btn.DDOWN] && bind[Act.BAG] != Btn.DDOWN) drinkFlask()
            if (pAct == PA.FREE && (input.bPressed[Btn.DLEFT] || input.bPressed[Btn.DRIGHT])) {
                val ws = ownedWeapons()
                if (ws.size > 1) {
                    val i = ws.indexOf(eqW).coerceAtLeast(0)
                    eqW = ws[(i + (if (input.bPressed[Btn.DRIGHT]) 1 else ws.size - 1)) % ws.size]; snd(Sfx.EQUIP); combo = 0
                    say(AdvData.items[eqW]?.name ?: eqW)
                } else say("So uma arma")
            }
            if (input.bPressed[Btn.R3] && bind[Act.BAG] != Btn.R3) {
                val q = quickList()
                if (q.size > 1) { quickIdx = (quickIdx + 1) % q.size; snd(Sfx.SWITCH) }
            }

            // rolar / esquivar
            if (pressBtn(Act.ROLL, input) && !(pressBtn(Act.INTERACT, input) && prompt.isNotEmpty())) {
                if (moving && stam >= 12f) startRoll(mx, mz, true)
                else if (!moving && stam >= 10f) startRoll(0f, 0f, false)
            }
            else if (pressBtn(Act.INTERACT, input) && prompt.isNotEmpty()) { doInteract(); return }
            else if (pressBtn(Act.ATK, input) && riposteT > 0f && riposteTarget?.alive == true && dist(riposteTarget!!.x, riposteTarget!!.z, px, pz) < 5f) startRiposte()
            else if (pressBtn(Act.ATK, input)) startAttack(mx, mz, moving, input)
            else if (comboWin > 0f && false) {}
            if (pAct == PA.FREE) {
                if (hasBtn(Act.HEAVY, input)) { heavyHeld += dt; if (heavyHeld > 0.16f && stam >= 28f) { pAct = PA.CHARGE; pT = 0f; chargeT = 0f; faceAssist(input, mx, mz, moving); giveTip("tip_heavy") } }
                else heavyHeld = 0f
                if (pressBtn(Act.SPECIAL, input) && stam >= 35f) startSpecial(mx, mz, moving, input)
                if (pressBtn(Act.ITEM, input)) useQuickItem()
            }
            // passos
            if (moving && speed > 0f) { pwalk += dt * (if (guardHeld) 7f else 10.5f) * (if (lk != null) 0.9f else 1f); if (sin(pwalk) * sin(pwalk - dt * 10f) < 0f) snd(Sfx.STEP) }
        }
        PA.ATK -> {
            pT += dt
            speed = if (pT < pDur * pHitAt) 3.2f else 0.8f
            if (!pHitDone && pT >= pDur * pHitAt) { pHitDone = true; meleeHit(wd, combo) }
            if (pressBtn(Act.ATK, input) && pT > pDur * 0.25f) comboQueued = true
            // cancelar em rolagem depois do impacto
            if (pHitDone && pT > pDur * pHitAt + 0.06f && pressBtn(Act.ROLL, input) && stam >= 12f) { if (moving) startRoll(mx, mz, true) else startRoll(0f, 0f, false); return }
            if (pT >= pDur) {
                if (comboQueued && combo < wd.comboLen - 1 && stam >= wd.cost * 0.4f) { combo++; beginSwing(mx, mz, moving) }
                else { pAct = PA.FREE; comboWin = if (combo < wd.comboLen - 1) 0.4f else 0f; if (combo >= wd.comboLen - 1) combo = 0 else comboWinNext() }
            }
        }
        PA.CHARGE -> {
            chargeT += dt; speed = 2.2f
            if (lk != null && lk.alive) pyaw = lerpAng(pyaw, angTo(lk.x, lk.z), dt * 10f)
            if (!hasBtn(Act.HEAVY, input)) {
                val c = (chargeT / 0.8f).coerceIn(0f, 1f)
                if (chargeT < 0.12f) { pAct = PA.FREE; heavyHeld = 0f }
                else {
                    heavyMul = 1.3f + (wd.heavy - 1.3f) * c
                    pAct = PA.HEAVY; pT = 0f; pDur = 0.62f; pHitAt = 0.48f; pHitDone = false
                    stam -= 28f; stamDelay = 0.8f; heavyHeld = 0f; snd(Sfx.SWING_HEAVY)
                }
            } else if (chargeT > 0.8f && (chargeT * 6f).toInt() % 2 == 0 && chargeT < 0.84f) { snd(Sfx.SWITCH); spark(px, py + 1.2f, pz, 4, P.YELLOW, 2f) }
        }
        PA.HEAVY -> {
            pT += dt; speed = if (pT < pDur * pHitAt) 2.2f else 0f
            if (!pHitDone && pT >= pDur * pHitAt) { pHitDone = true; heavyHit(wd) }
            if (pT >= pDur) { pAct = PA.FREE; combo = 0 }
        }
        PA.SPECIAL -> {
            pT += dt
            specialStep(wd, dt)
            speed = if (specialKind == 2 && pT < pDur * 0.6f) 11f else 0f
            if (specialKind == 2) { pvx = sin(pyaw) * speed; pvz = cos(pyaw) * speed }
            if (pT >= pDur) { pAct = PA.FREE; combo = 0 }
        }
        PA.ROLL -> {
            pT += dt
            val k = pT / pDur
            // 0,08 a 0,38 s de invencibilidade
            if (pT >= 0.06f && pT <= 0.40f) iT = max(iT, 0.05f)
            rollSpd = 10.5f * (1f - k * 0.55f)
            pvx = rollX * rollSpd; pvz = rollZ * rollSpd; speed = 0f
            if (pT >= pDur) { pAct = PA.FREE }
        }
        PA.BACK -> {
            pT += dt
            if (pT >= 0.03f && pT <= 0.2f) iT = max(iT, 0.05f)
            pvx = rollX * 7.5f * (1f - pT / pDur * 0.5f); pvz = rollZ * 7.5f * (1f - pT / pDur * 0.5f); speed = 0f
            if (pT >= pDur) pAct = PA.FREE
        }
        PA.RIPOSTE -> {
            pT += dt
            val t = riposteTarget
            if (t != null && t.alive && pT < pDur * 0.4f) { val d = dist(t.x, t.z, px, pz); if (d > 1.8f) { pvx = sin(pyaw) * 9f; pvz = cos(pyaw) * 9f } else { pvx = 0f; pvz = 0f } }
            else { pvx *= 0.8f; pvz *= 0.8f }
            if (!pHitDone && pT >= pDur * 0.42f) {
                pHitDone = true
                if (t != null && t.alive) {
                    val big = if (t.boss) 2.6f else 3.8f
                    hurtEnemy(t, wDmg() * big, 40f, 6f, true, px, pz, true)
                    hitStop = 0.16f; doShake(0.5f, 0.25f); snd(Sfx.HIT_HEAVY)
                    spark(t.x, t.y + 1.3f, t.z, 16, 0xFFFFE066L, 6f, 1.6f)
                }
                riposteT = 0f; riposteTarget = null
            }
            if (pT >= pDur) pAct = PA.FREE
        }
        PA.HURT, PA.STUN -> {
            pT += dt
            pvx *= exp(-6f * dt); pvz *= exp(-6f * dt)
            if (pT >= pDur) pAct = PA.FREE
        }
        PA.DRINK -> {
            pT += dt; speed = 0.4f
            if (!pHitDone && pT >= 0.55f) { pHitDone = true; finishQuickItem() }
            if (pT >= pDur) pAct = PA.FREE
        }
        PA.THROW -> {
            pT += dt; speed = 0.5f
            if (!pHitDone && pT >= 0.3f) {
                pHitDone = true; take("bomba")
                val tx = if (lk != null && lk.alive) lk.x - px else sin(pyaw) * 7f
                val tz = if (lk != null && lk.alive) lk.z - pz else cos(pyaw) * 7f
                val l = max(1f, sqrt(tx * tx + tz * tz)); val sp = min(11f, l * 1.4f)
                bombs.add(Bomb(px, py + 1.4f, pz, tx / l * sp, tz / l * sp, 1.6f)); snd(Sfx.SWING)
            }
            if (pT >= pDur) pAct = PA.FREE
        }
        PA.PICK, PA.OPEN -> {
            pT += dt; speed = 0f
            if (!pHitDone && pT >= pDur * 0.6f) { pHitDone = true; pendingAct?.invoke(); pendingAct = null }
            if (pT >= pDur) pAct = PA.FREE
        }
        PA.SHOOT, PA.CAST -> {
            pT += dt; speed = 0.6f
            if (lk != null && lk.alive) pyaw = lerpAng(pyaw, angTo(lk.x, lk.z), dt * 12f)
            if (!pHitDone && pT >= pDur * pHitAt) { pHitDone = true; rangedShot(wd, if (specialKind == 3 && pAct == PA.SHOOT) 3 else 1) }
            if (pT >= pDur) pAct = PA.FREE
        }
        else -> {}
    }
    if (pAct != PA.FREE) { guardHeld = false; if (pAct != PA.PARRY) { /* o aparo é cancelado por ações */ } }
    if (pAct != PA.FREE && parryT > 0f && pAct != PA.HURT) parryT = 0f

    // velocidade e colisão
    if (pAct == PA.FREE || pAct == PA.ATK || pAct == PA.CHARGE || pAct == PA.HEAVY || pAct == PA.DRINK || pAct == PA.THROW || pAct == PA.SHOOT || pAct == PA.CAST) {
        val tvx = if (moving || pAct == PA.ATK) (if (pAct == PA.ATK) sin(pyaw) * speed else mx * speed * mag) else 0f
        val tvz = if (moving || pAct == PA.ATK) (if (pAct == PA.ATK) cos(pyaw) * speed else mz * speed * mag) else 0f
        val k = min(1f, dt * 16f)
        pvx += (tvx - pvx) * k; pvz += (tvz - pvz) * k
    }
    val r = moveBy(px, pz, pvx * dt, pvz * dt, 0.45f)
    px = r[0]; pz = r[1]
    // empurrão de inimigos
    for (e in ens) {
        if (!e.alive) continue
        val d = dist(e.x, e.z, px, pz); val mn = e.def.rad * e.def.sc * 0.8f + 0.4f
        if (d < mn && d > 0.001f && !(e.def.flying)) { val push = (mn - d) * 0.6f; val q = moveBy(px, pz, (px - e.x) / d * push, (pz - e.z) / d * push, 0.45f); px = q[0]; pz = q[1] }
    }
    val gy = AdvWorld.hAt(px, pz)
    py += (gy - py) * min(1f, dt * 20f)
    if (abs(py - gy) > 0.8f) py = gy
    pamt = min(1.6f, if (pAct == PA.FREE) (sqrt(pvx * pvx + pvz * pvz) / 6.2f) * 1.0f else sqrt(pvx * pvx + pvz * pvz) / 6.2f)
    if (autoFace) {}
}

internal fun AdventureGame.comboWinNext() {}

internal fun AdventureGame.startRoll(mx: Float, mz: Float, rolling: Boolean) {
    val lk = lockT
    if (rolling) {
        rollX = mx; rollZ = mz; pAct = PA.ROLL; pT = 0f; pDur = 0.52f; stam -= 18f
        pyaw = atan2(mx, mz); snd(Sfx.ROLL)
    } else {
        // passo para trás (afasta do alvo ou da direção em que olha)
        val bx: Float; val bz: Float
        if (lk != null && lk.alive) { val a = angTo(lk.x, lk.z); bx = -sin(a); bz = -cos(a) } else { bx = -sin(pyaw); bz = -cos(pyaw) }
        rollX = bx; rollZ = bz; pAct = PA.BACK; pT = 0f; pDur = 0.3f; stam -= 10f; snd(Sfx.ROLL)
    }
    stamDelay = 0.55f; parryT = 0f; guardHeld = false; combo = 0; comboQueued = false; heavyHeld = 0f
    giveTip("tip_roll")
}

internal fun AdventureGame.startAttack(mx: Float, mz: Float, moving: Boolean, input: GameInput) {
    val wd = wdef()
    if (stam < wd.cost * 0.4f) { snd(Sfx.DENIED); return }
    if (wd.ranged) {
        faceAssist(input, mx, mz, moving)
        val ammo = wd.id == "arco"
        if (ammo && count("flecha") <= 0) { say("Sem flechas"); snd(Sfx.DENIED); return }
        if (!ammo && stam < wd.cost) { snd(Sfx.DENIED); return }
        pAct = if (ammo) PA.SHOOT else PA.CAST; pT = 0f; pDur = wd.dur; pHitAt = wd.h; pHitDone = false; specialKind = 0
        stam -= wd.cost; stamDelay = 0.5f
        return
    }
    if (comboWin <= 0f) combo = 0
    comboQueued = false
    faceAssist(input, mx, mz, moving)
    beginSwing(mx, mz, moving)
}

internal fun AdventureGame.beginSwing(mx: Float, mz: Float, moving: Boolean) {
    val wd = wdef()
    pAct = PA.ATK; pT = 0f; pHitDone = false; comboQueued = false
    pDur = wd.dur * (when (combo) { 1 -> 0.95f; 2 -> 1.2f; else -> 1f })
    pHitAt = wd.h
    stam -= wd.cost * (if (combo == 2) 1.25f else 1f); stamDelay = 0.55f
    // atualiza direção no começo de cada golpe
    val lk = lockT
    if (lk != null && lk.alive) pyaw = angTo(lk.x, lk.z) else if (moving) pyaw = atan2(mx, mz)
    snd(if (combo == 2) Sfx.SWING_HEAVY else Sfx.SWING)
    trailT = pDur
    swingFlip = if (combo % 2 == 0) 1f else -1f
}

internal fun AdventureGame.inArc(e: En, reach: Float, arc: Float, extra: Float = 0f): Boolean {
    val d = dist(e.x, e.z, px, pz) - e.def.rad * e.def.sc
    if (d > reach + extra) return false
    if (abs((if (e.def.flying) e.y - py - 1.2f else 0f)) > 2.6f) return false
    if (d < 0.9f) return true
    return abs(wrap(angTo(e.x, e.z) - pyaw)) <= arc / 2f
}

internal fun AdventureGame.meleeHit(wd: WDef, idx: Int) {
    val mult = when (idx) { 1 -> 1.1f; 2 -> 1.5f; else -> 1f }
    val arc = if (idx == 2) 3.0f else 2.2f
    var hit = false
    for (e in ens.toList()) {
        if (!e.alive) continue
        if (inArc(e, wd.reach, arc)) {
            hit = true
            hurtEnemy(e, wDmg() * mult, (if (idx == 2) 14f else 6f) * (if (wd.ws == WS.AXE) 1.6f else 1f), if (idx == 2) 4.5f else 2f, idx == 2, px, pz, false)
        }
    }
    if (!hit) doorHit(wd.reach, false)
    if (hit) { hitStop = if (idx == 2) 0.08f else 0.05f; doShake(0.12f + 0.1f * idx, 0.1f) }
    if (hit && eqW == "aurora") stam = min(100f, stam + 6f)
}

internal fun AdventureGame.heavyHit(wd: WDef) {
    var hit = false
    for (e in ens.toList()) {
        if (!e.alive) continue
        if (inArc(e, wd.reach + 0.5f, 2.6f)) {
            hit = true
            hurtEnemy(e, wDmg() * heavyMul, 30f * heavyMul, 7f, true, px, pz, false)
        }
    }
    doorHit(wd.reach + 0.6f, wd.ws == WS.AXE)
    snd(Sfx.SLAM); doShake(0.35f, 0.2f)
    if (hit) hitStop = 0.1f
    spark(px + sin(pyaw) * 2f, py + 0.3f, pz + cos(pyaw) * 2f, 8, 0xFFE0D0B0L, 3f)
}

/** Golpes fortes de machado e explosões abrem paredes rachadas. */
internal fun AdventureGame.doorHit(reach: Float, axeHeavy: Boolean) {
    for (d in AdvWorld.doors) {
        if (d.req.type != 2 || doorOpen(d)) continue
        if (axeHeavy && dist(px + sin(pyaw) * reach * 0.7f, pz + cos(pyaw) * reach * 0.7f, d.x, d.z) < 3.6f) openDoor(d, true)
        else if (!axeHeavy && dist(px, pz, d.x, d.z) < 4f && eqW == "machado" && false) {}
    }
}

internal fun AdventureGame.openDoor(d: DoorDef, blast: Boolean) {
    flags.add("dr_${d.id}"); saveDirty = true
    snd(if (blast) Sfx.EXPLODE else Sfx.DOOR); doShake(0.5f, 0.4f)
    spark(d.x, AdvWorld.hAt(d.x, d.z) + 1.5f, d.z, 24, 0xFFC0B8A8L, 6f, 1.8f)
    say("${d.label}: aberto")
    checkQuests()
}

internal fun AdventureGame.startSpecial(mx: Float, mz: Float, moving: Boolean, input: GameInput) {
    val wd = wdef()
    if (wd.id == "arco" && count("flecha") < 3) { say("Precisa de 3 flechas"); snd(Sfx.DENIED); return }
    faceAssist(input, mx, mz, moving)
    specialKind = wd.special
    stam -= 35f; stamDelay = 0.9f; pT = 0f; pHitDone = false; snd(Sfx.SPECIAL)
    giveTip("tip_special")
    when (wd.special) {
        0 -> { pAct = PA.SPECIAL; pDur = 0.7f; pHitAt = 0.4f }
        1 -> { pAct = PA.SPECIAL; pDur = 0.85f; pHitAt = 0.55f }
        2 -> { pAct = PA.SPECIAL; pDur = 0.62f; pHitAt = 0.3f }
        3 -> { pAct = PA.SHOOT; pDur = 0.65f; pHitAt = 0.4f }
        else -> { pAct = PA.CAST; pDur = 0.8f; pHitAt = 0.45f }
    }
}

internal fun AdventureGame.specialStep(wd: WDef, dt: Float) {
    if (pHitDone || pT < pDur * pHitAt) return
    pHitDone = true
    val dmg = wDmg()
    when (specialKind) {
        0 -> { // giro
            var hit = false
            for (e in ens.toList()) if (e.alive && dist(e.x, e.z, px, pz) - e.def.rad * e.def.sc < wd.reach + 0.8f) { hit = true; hurtEnemy(e, dmg * 1.6f, 22f, 6f, true, px, pz, false) }
            if (hit) { hitStop = 0.09f; doShake(0.3f, 0.2f) }
            spark(px, py + 1f, pz, 14, 0xFFE8F4FFL, 6f); snd(Sfx.SWING_HEAVY)
        }
        1 -> { // pancada no chão
            val cx = px + sin(pyaw) * 2.6f; val cz = pz + cos(pyaw) * 2.6f
            var hit = false
            for (e in ens.toList()) if (e.alive && dist(e.x, e.z, cx, cz) - e.def.rad * e.def.sc < 3.4f) { hit = true; hurtEnemy(e, dmg * 2.0f, 40f, 8f, true, px, pz, false) }
            for (d in AdvWorld.doors) if (d.req.type == 2 && !doorOpen(d) && dist(cx, cz, d.x, d.z) < 5.4f) openDoor(d, true)
            snd(Sfx.SLAM); doShake(0.5f, 0.3f); if (hit) hitStop = 0.12f
            spark(cx, py + 0.3f, cz, 16, 0xFFE0D0B0L, 5f, 1.4f)
        }
        2 -> { // estocada
            var hit = false
            for (e in ens.toList()) if (e.alive) {
                val dx = e.x - px; val dz = e.z - pz
                val fwd = dx * sin(pyaw) + dz * cos(pyaw); val side = abs(dx * cos(pyaw) - dz * sin(pyaw))
                if (fwd > -0.5f && fwd < wd.reach + 2.4f && side < 1.4f + e.def.rad * e.def.sc) { hit = true; hurtEnemy(e, dmg * 1.9f, 24f, 6f, true, px, pz, false) }
            }
            if (hit) { hitStop = 0.09f; doShake(0.25f, 0.15f) }
        }
    }
}

internal fun AdventureGame.rangedShot(wd: WDef, n: Int) {
    val lk = lockT
    var ang = pyaw
    if (lk != null && lk.alive) ang = angTo(lk.x, lk.z)
    else {
        var bd = 22f
        for (e in ens) if (e.alive) { val d = dist(e.x, e.z, px, pz); if (d < bd && abs(wrap(angTo(e.x, e.z) - pyaw)) < 0.35f) { bd = d; ang = angTo(e.x, e.z) } }
    }
    val dmg = wDmg()
    if (wd.id == "arco") {
        val k = if (n == 3) 3 else 1
        take("flecha", k)
        for (i in 0 until k) {
            val a = ang + (i - (k - 1) / 2f) * 0.16f
            val p = Proj(px + sin(a) * 0.8f, py + 1.3f, pz + cos(a) * 0.8f, sin(a) * 26f, cos(a) * 26f, dmg * (if (k == 3) 0.9f else 1.1f), 1.4f, 2, false); p.mine = true; projs.add(p)
        }
        snd(Sfx.SHOOT)
    } else {
        if (specialKind == 4 && pAct == PA.CAST && pT > 0f && wd.special == 4 && stam < 0f) {}
        if (specialKind == 4) {
            // onda de energia
            var hit = false
            for (e in ens.toList()) if (e.alive && dist(e.x, e.z, px, pz) - e.def.rad * e.def.sc < 5.2f) { hit = true; hurtEnemy(e, dmg * 1.5f, 24f, 8f, true, px, pz, false) }
            if (hit) hitStop = 0.08f
            spark(px, py + 1f, pz, 24, 0xFF9BE6FFL, 8f, 1.6f); snd(Sfx.MAGIC); doShake(0.3f, 0.2f)
        } else {
            val p = Proj(px + sin(ang) * 0.9f, py + 1.4f, pz + cos(ang) * 0.9f, sin(ang) * 18f, cos(ang) * 18f, dmg * 1.1f, 1.6f, 3, false); p.mine = true; projs.add(p)
            snd(Sfx.MAGIC)
        }
    }
    specialKind = 0
}

// ---------------------------------------------------------------------------------------------- itens
internal fun AdventureGame.useQuickItem() {
    when (quickItem()) {
        "seiva" -> if (flask <= 0) { say("Sem seiva"); snd(Sfx.DENIED) } else if (hp >= maxHp() - 1f) { say("Vida cheia"); snd(Sfx.DENIED) }
        else { pAct = PA.DRINK; pT = 0f; pDur = 0.95f; pHitDone = false; pendingQuick = "seiva" }
        "bomba" -> if (count("bomba") > 0) { pAct = PA.THROW; pT = 0f; pDur = 0.6f; pHitDone = false } else snd(Sfx.DENIED)
        "pedra" -> if (count("pedra") > 0) { pAct = PA.DRINK; pT = 0f; pDur = 0.7f; pHitDone = false; pendingQuick = "pedra" } else snd(Sfx.DENIED)
        "fruta" -> if (count("fruta") > 0) { pAct = PA.DRINK; pT = 0f; pDur = 0.9f; pHitDone = false; pendingQuick = "fruta" } else snd(Sfx.DENIED)
    }
    if (pAct == PA.DRINK) { parryT = 0f; guardHeld = false; giveTip("tip_item") }
}

internal fun AdventureGame.drinkFlask() {
    if (pAct != PA.FREE) return
    if (flask <= 0) { say("Sem seiva"); snd(Sfx.DENIED) }
    else if (hp >= maxHp() - 1f) { say("Vida cheia"); snd(Sfx.DENIED) }
    else { pAct = PA.DRINK; pT = 0f; pDur = 0.95f; pHitDone = false; pendingQuick = "seiva"; parryT = 0f; guardHeld = false; giveTip("tip_item") }
}

internal fun AdventureGame.finishQuickItem() {
    when (pendingQuick) {
        "seiva" -> { flask--; hp = min(maxHp(), hp + maxHp() * 0.45f); snd(Sfx.HEAL); spark(px, py + 1.4f, pz, 14, 0xFF7BDA6BL, 3f, 1.3f) }
        "pedra" -> { take("pedra"); buffT = 90f; snd(Sfx.SPECIAL); say("A lamina brilha por 90 s") }
        "fruta" -> { take("fruta"); fruits++; hp = maxHp(); snd(Sfx.HEAL); say("Vida maxima +10"); spark(px, py + 1.4f, pz, 18, 0xFFFF6B8AL, 4f, 1.4f) }
    }
    pendingQuick = ""; saveDirty = true
}

internal fun AdventureGame.updateBombs(dt: Float) {
    val it = bombs.iterator()
    while (it.hasNext()) {
        val b = it.next()
        b.t -= dt
        b.vy2(dt)
        b.x += b.vx * dt; b.z += b.vz * dt
        val g = AdvWorld.hAt(b.x, b.z) + 0.3f
        if (b.y <= g) { b.y = g; b.vx *= 0.82f; b.vz *= 0.82f }
        if (b.t <= 0f) {
            it.remove()
            explode(b.x, b.y, b.z)
        }
    }
}

private fun Bomb.vy2(dt: Float) { y += (if (vx * vx + vz * vz > 0.5f) -2.5f * dt else -6f * dt) }

internal fun AdventureGame.explode(x: Float, y: Float, z: Float) {
    snd(Sfx.EXPLODE); doShake(0.6f, 0.45f); flash(0xFFFFE0A0L, 0.12f)
    spark(x, y + 0.5f, z, 30, 0xFFFF9F1CL, 9f, 2f); spark(x, y + 0.5f, z, 14, 0xFFFFE066L, 6f, 1.6f)
    for (e in ens.toList()) if (e.alive && dist(e.x, e.z, x, z) < 4.6f) hurtEnemy(e, if (e.boss) 30f else 55f, 40f, 9f, true, x, z, false)
    for (d in AdvWorld.doors) if (d.req.type == 2 && !doorOpen(d) && dist(x, z, d.x, d.z) < 6f) openDoor(d, true)
}

// ---------------------------------------------------------------------------------------------- interação
internal fun AdventureGame.findPrompt() {
    prompt = ""; curInter = null
    if (pAct != PA.FREE || mode != Mode.PLAY) return
    var best: Inter? = null; var bd = 99f
    fun cand(t: Int, i: Int, x: Float, z: Float, lim: Float) { val d = dist(px, pz, x, z); if (d < min(bd, lim)) { bd = d; best = Inter(t, i, x, z) } }
    for ((i, n) in npcs.withIndex()) cand(0, i, n.x, n.z, 2.8f)
    for ((i, c) in AdvWorld.chests.withIndex()) if (!flags.contains("ch_${c.id}")) cand(1, i, c.x, c.z, 2.4f)
    for ((i, b) in AdvWorld.bonfires.withIndex()) cand(2, i, b[0], b[1], 2.9f)
    for ((i, d) in AdvWorld.doors.withIndex()) if (!doorOpen(d)) cand(3, i, d.x, d.z, 4.2f)
    for ((i, s) in AdvWorld.signs.withIndex()) cand(5, i, s.x, s.z, 2.2f)
    for ((i, h) in herbs.withIndex()) if (!flags.contains("hb$i")) cand(4, i, h.x, h.z, 2.0f)
    if (deathGems > 0) cand(6, 0, deathX, deathZ, 1.8f)
    val b = best ?: return
    curInter = b
    prompt = when (b.type) {
        0 -> "Falar com ${npcs[b.idx].d.name}"
        1 -> "Abrir bau"
        2 -> if (flags.contains("bf${b.idx}")) "Descansar na Brasa" else "Acender a Brasa"
        3 -> AdvWorld.doors[b.idx].label
        4 -> "Colher erva"
        5 -> "Ler placa"
        else -> "Recuperar gemas"
    }
}

internal fun AdventureGame.doInteract() {
    val i = curInter ?: return
    when (i.type) {
        0 -> {
            val n = npcs[i.idx]
            startConvo(AdvDialogs.talk(n.d.id, this), n)
        }
        1 -> {
            val c = AdvWorld.chests[i.idx]
            pyaw = angTo(c.x, c.z)
            pAct = PA.OPEN; pT = 0f; pDur = 0.95f; pHitDone = false; pvx = 0f; pvz = 0f
            flags.add("ch_${c.id}")
            pendingAct = { openChestLoot(c) }
            snd(Sfx.CHEST)
        }
        2 -> {
            val near = ens.any { it.alive && it.aggro && dist(it.x, it.z, px, pz) < 10f }
            if (near) { say("Ha inimigos por perto"); snd(Sfx.DENIED); return }
            val lit = flags.contains("bf${i.idx}")
            if (!lit) { flags.add("bf${i.idx}"); showBanner("BRASA ACESA", AdvWorld.bonfireNames[i.idx]); snd(Sfx.BONFIRE); saveDirty = true }
            lastBf = i.idx
            openMenu("BRASA: ${AdvWorld.bonfireNames[i.idx]}") { bonfireMenu(i.idx) }
        }
        3 -> {
            val d = AdvWorld.doors[i.idx]
            pyaw = angTo(d.x, d.z)
            when (d.req.type) {
                0 -> if (has(d.req.id)) { take(d.req.id); openDoor(d, false) } else { say("Trancado: voce precisa de ${AdvData.items[d.req.id]?.name}"); snd(Sfx.DENIED) }
                1 -> if (flags.contains(d.req.id)) openDoor(d, false) else { say("Algo ainda precisa acontecer"); snd(Sfx.DENIED) }
                2 -> { say("Parede rachada: use uma bomba ou um golpe forte de machado"); snd(Sfx.DENIED) }
                3 -> if (shards() >= 3) { openDoor(d, false); showBanner("PORTAO DO CORACAO", "Os tres estilhacos brilham") } else { say("Faltam estilhacos do Coracao (${shards()}/3)"); snd(Sfx.DENIED) }
                else -> if (flags.contains("qd_${d.req.id}")) openDoor(d, false) else { say("Trancado"); snd(Sfx.DENIED) }
            }
        }
        4 -> {
            val h = herbs[i.idx]
            pyaw = angTo(h.x, h.z)
            pAct = PA.PICK; pT = 0f; pDur = 0.8f; pHitDone = false; pvx = 0f; pvz = 0f
            flags.add("hb${i.idx}")
            pendingAct = { give("erva"); say("Erva de Aurora (${count("erva")})") }
        }
        5 -> {
            val s = AdvWorld.signs[i.idx]
            startConvo(Convo("Placa", listOf(Ln(s.text))))
        }
        6 -> { gems += deathGems; say("Recuperou $deathGems gemas"); deathGems = 0; snd(Sfx.COIN); saveDirty = true }
    }
}

internal fun AdventureGame.openChestLoot(c: ChestDef) {
    val l = AdvData.chestLoot[c.id]
    val parts = ArrayList<String>()
    if (l != null) {
        if (l.gems > 0) { earn(l.gems); parts.add("${l.gems} gemas") }
        for (it in l.items) {
            give(it.id, it.n)
            parts.add(AdvData.items[it.id]?.name ?: it.id)
            if (AdvData.items[it.id]?.cat == Cat.ARMA && AdvData.items[it.id]?.rarity != 0 && false) eqW = it.id
        }
    }
    if (c.rare) showBanner("TESOURO RARO", parts.joinToString(", ")) else say("Encontrou: ${parts.joinToString(", ")}")
    spark(c.x, AdvWorld.hAt(c.x, c.z) + 1.2f, c.z, 14, 0xFFFFE066L, 4f, 1.3f)
    saveDirty = true; checkQuests()
}

// ---------------------------------------------------------------------------------------------- animação do jogador
internal fun AdventureGame.updatePlayerAnim(dt: Float) {
    val wd = wdef()
    var an = AN.IDLE
    var t = 0f
    when (pAct) {
        PA.FREE -> {
            an = if (parryT > 0f) AN.PARRY else if (guardHeld) AN.GUARD else AN.IDLE
            if (parryT > 0f) t = ((parryWindow() - parryT) / parryWindow()).coerceIn(0f, 1f)
            else if (parryFx > 0f) { an = AN.PARRY; t = 0.55f }
        }
        PA.ATK -> {
            an = when (combo) { 0 -> AN.ATK1; 1 -> AN.ATK2; else -> AN.ATK3 }
            t = (pT / pDur).coerceIn(0f, 1f)
        }
        PA.CHARGE -> { an = AN.HEAVY_CHARGE; t = (chargeT / 0.8f).coerceIn(0f, 1f) }
        PA.HEAVY -> { an = AN.HEAVY_HIT; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.SPECIAL -> { an = when (specialKind) { 0 -> AN.SPIN; 1 -> AN.HEAVY_HIT; else -> AN.ATK3 }; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.ROLL -> { an = AN.ROLL; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.BACK -> { an = AN.ROLL; t = (pT / pDur * 0.45f).coerceIn(0f, 1f) }
        PA.RIPOSTE -> { an = AN.RIPOSTE; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.HURT -> { an = if (pDur > 0.5f) AN.HURT_H else AN.HURT_L; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.STUN -> { an = AN.STUN; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.DRINK -> { an = AN.DRINK; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.THROW -> { an = AN.SHOOT; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.PICK -> { an = AN.PICKUP; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.OPEN -> { an = AN.OPEN; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.SHOOT -> { an = AN.SHOOT; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.CAST -> { an = AN.CAST; t = (pT / pDur).coerceIn(0f, 1f) }
        PA.DEAD -> { an = AN.DEAD; t = (deadT / 1.2f).coerceIn(0f, 1f) }
    }
    if (parryFx > 0f) parryFx -= dt
    val ws = if (wd.ws == WS.NONE) WS.SWORD else wd.ws
    AdvAnim.target(pAnim.tgt, an, t, pHitAt, ws, pwalk, if (pAct == PA.FREE) pamt else 0f, time, 0f)
    val rate = when (pAct) { PA.ATK, PA.HEAVY, PA.SPECIAL, PA.RIPOSTE -> 26f; PA.ROLL, PA.BACK -> 22f; PA.PARRY -> 30f; else -> 16f }
    AdvAnim.blend(pAnim, dt, if (an == AN.PARRY) 34f else rate)
}

// ---------------------------------------------------------------------------------------------- morte
internal fun AdventureGame.die() {
    pAct = PA.DEAD; mode = Mode.DEAD; deadT = 0f
    snd(Sfx.DEATH); lockT = null; engaged = engaged
    hitStop = 0f
}

// ---------------------------------------------------------------------------------------------- NPCs e fauna
internal fun AdventureGame.updateNpc(n: NpcRt, dt: Float, talking: Boolean) {
    val d = dist(n.x, n.z, px, pz)
    if (d > 80f) return
    n.talkT += 0f
    val roam = n.d.roam
    var moving = false
    if (roam > 0f && dlgNpc !== n) {
        n.wait -= dt
        val dd = dist(n.x, n.z, n.tx, n.tz)
        if (dd > 0.5f && n.wait <= 0f) {
            val a = atan2(n.tx - n.x, n.tz - n.z)
            n.yaw = lerpAng(n.yaw, a, dt * 6f)
            val r = moveBy2(n.x, n.z, sin(a) * 1.8f * dt, cos(a) * 1.8f * dt)
            if (r[0] == n.x && r[1] == n.z) { n.tx = n.x; n.tz = n.z }
            n.x = r[0]; n.z = r[1]; moving = true
        } else if (dd <= 0.5f && n.wait <= 0f) {
            n.wait = 2f + rnd.nextFloat() * 4f
            val ang = rnd.nextFloat() * 6.28f; val rr = rnd.nextFloat() * roam
            n.tx = n.d.x + cos(ang) * rr; n.tz = n.d.z + sin(ang) * rr
        }
    }
    // olhar para o jogador quando perto
    val near = d < 7f
    if (near && !moving) n.look = (wrap(angTo2(n.x, n.z) - n.yaw)).coerceIn(-0.9f, 0.9f) / 0.8f
    else n.look *= 0.9f
    val hasTalk = dlgNpc === n
    val an = if (hasTalk && n.emoAn >= 0) n.emoAn else if (near && n.an == AN.IDLE && n.d.rig < 100) AN.IDLE else n.an
    n.phi += dt * 7f * (if (moving) 1f else 0f)
    n.amt += ((if (moving) 1f else 0f) - n.amt) * min(1f, dt * 6f)
    n.talkT += dt
    if (n.d.rig < 100) {
        AdvAnim.target(n.anim.tgt, if (moving) AN.IDLE else an, (n.talkT * 0.6f) % 1f, 0.4f, WS.NONE, n.phi, n.amt, time + n.d.id * 1.7f, n.look.coerceIn(-1f, 1f))
        AdvAnim.blend(n.anim, dt, 10f)
    }
}

internal fun AdventureGame.angTo2(x: Float, z: Float) = atan2(px - x, pz - z)

internal fun AdventureGame.moveBy2(x: Float, z: Float, dx: Float, dz: Float): FloatArray {
    var nx = x; var nz = z
    if (npcWalk(x + dx, z, x, z)) nx = x + dx
    if (npcWalk(nx, z + dz, nx, z)) nz = z + dz
    return floatArrayOf(nx, nz)
}

internal fun AdventureGame.npcWalk(x: Float, z: Float, fx: Float, fz: Float): Boolean {
    if (AdvWorld.hAt(x, z) < -0.3f) return false
    if (AdvWorld.blockedByProps(x, z, 0.5f)) return false
    if (abs(AdvWorld.hAt(x, z) - AdvWorld.hAt(fx, fz)) > 0.6f) return false
    return true
}

internal fun AdventureGame.updateCritters(dt: Float) {
    for (c in critters) {
        if (dist(c.x, c.z, px, pz) > 60f) continue
        c.t += dt
        val dp = dist(c.x, c.z, px, pz)
        when (c.kind) {
            0 -> { // coelho: foge do jogador
                if (dp < 6f) { val a = atan2(c.x - px, c.z - pz); c.vx = sin(a) * 6f; c.vz = cos(a) * 6f }
                else if ((c.t * 1.3f + c.ph).toInt() % 5 == 0) { c.vx = sin(c.ph + c.t) * 0.9f; c.vz = cos(c.ph * 2f + c.t) * 0.9f } else { c.vx *= 0.9f; c.vz *= 0.9f }
                val nx = c.x + c.vx * dt; val nz = c.z + c.vz * dt
                if (AdvWorld.hAt(nx, nz) > -0.3f && !AdvWorld.blockedByProps(nx, nz, 0.3f)) { c.x = nx; c.z = nz }
            }
            1 -> { c.x = c.hx + sin(c.t * 0.4f + c.ph) * 5f; c.z = c.hz + cos(c.t * 0.3f + c.ph * 2f) * 5f }
            2 -> { c.x = c.hx + sin(c.t * 0.7f + c.ph) * 4f; c.z = c.hz + sin(c.t * 0.5f + c.ph * 3f) * 4f }
            3 -> { c.x = c.hx + sin(c.t * 0.5f + c.ph) * 3f; c.z = c.hz + cos(c.t * 0.4f + c.ph) * 3f }
        }
    }
}

internal fun AdventureGame.updateDrops(dt: Float) {
    val it = drops.iterator()
    while (it.hasNext()) {
        val d = it.next()
        d.life -= dt; d.t += dt
        val dd = dist(d.x, d.z, px, pz)
        if (d.life <= 0f) { it.remove(); continue }
        if (dd < 1.0f) {
            when (d.kind) {
                0 -> { earn(d.amount); snd(Sfx.COIN) }
                1 -> { hp = min(maxHp(), hp + d.amount); snd(Sfx.HEAL) }
                else -> { give(d.item, max(1, d.amount)); say("Pegou ${AdvData.items[d.item]?.name ?: d.item}") }
            }
            it.remove()
        }
    }
    // moedas "voam" até o jogador
    for (d in drops) if (d.kind != 2) { /* atração desenhada no render */ }
}

internal fun AdventureGame.updateZone(dt: Float) {
    val z = AdvWorld.zoneAt(px, pz)
    val zz = if (flags.contains("dr_caverna") && px > 130f && px < 178f && pz > 96f && pz < 132f && dist(px, pz, 152f, 114f) < 24f) Z.CAVERNA else z
    var zone2 = zz
    // zonas especiais definidas por proximidade
    if (dist(px, pz, 22f, 30f) < 20f) zone2 = Z.JARDIM
    else if (pz < 50f && px > 70f && px < 130f) zone2 = Z.SANTUARIO
    else if (dist(px, pz, 152f, 114f) < 22f) zone2 = Z.CAVERNA
    if (zone2 != zone) {
        zone = zone2; zoneName = AdvWorld.styles[zone].name; zoneBanner = 3.2f
        if (flags.add("zv_$zone")) { saveDirty = true; checkQuests() }
    }
    if (zoneBanner > 0f) zoneBanner -= dt
}

internal fun AdventureGame.updateStyle(dt: Float) {
    val st = AdvWorld.styles[if (mode == Mode.TITLE || mode == Mode.STORY || mode == Mode.ENDING) Z.VILA else zone]
    val k = min(1f, dt * 1.4f)
    sAmb += (st.ambient - sAmb) * k; sLr += (st.lr - sLr) * k; sLg += (st.lg - sLg) * k; sLb += (st.lb - sLb) * k
    sFogS += (st.fogStart - sFogS) * k; sFogE += (st.fogEnd - sFogE) * k
    sFog = mixColor(sFog, st.fog, k); sSky = mixColor(sSky, st.skyTop, k)
}

internal fun mixColor(a: Long, b: Long, t: Float): Long {
    fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toLong().coerceIn(0, 255)
    return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

internal fun AdventureGame.revealMap() {
    val cx = (px / AdvWorld.CELL / 2f).toInt(); val cz = (pz / AdvWorld.CELL / 2f).toInt()
    for (j in cz - 3..cz + 3) for (i in cx - 3..cx + 3) if (i in 0..32 && j in 0..32 && (i - cx) * (i - cx) + (j - cz) * (j - cz) <= 11) mapSeen[j * 33 + i] = true
}

internal fun AdventureGame.updateParticles(dt: Float) {
    val it = parts.iterator()
    while (it.hasNext()) {
        val q = it.next()
        q.life -= dt; q.x += q.vx * dt; q.y += q.vy * dt; q.z += q.vz * dt; q.vy -= 6f * dt
        if (q.life <= 0f) it.remove()
    }
    // fogueiras soltam fagulhas perto do jogador
    if (mode == Mode.PLAY && (time * 7f).toInt() % 3 == 0 && parts.size < 80) {
        for ((i, b) in AdvWorld.bonfires.withIndex()) if (flags.contains("bf$i") && dist(b[0], b[1], px, pz) < 26f && rnd.nextFloat() < 0.12f)
            parts.add(Part(b[0] + (rnd.nextFloat() - 0.5f) * 0.6f, AdvWorld.hAt(b[0], b[1]) + 1f, b[1] + (rnd.nextFloat() - 0.5f) * 0.6f, (rnd.nextFloat() - 0.5f) * 0.5f, 1.8f + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 0.5f, 0.9f, 0xFFFFB347L, 0.7f))
    }
}

internal fun AdventureGame.startRiposte() {
    val t = riposteTarget ?: return
    pyaw = angTo(t.x, t.z)
    pAct = PA.RIPOSTE; pT = 0f; pDur = 0.7f; pHitAt = 0.42f; pHitDone = false
    stam = max(0f, stam - 5f); parryT = 0f; guardHeld = false; snd(Sfx.SWING_HEAVY)
}

private val treeKinds = setOf(PK.PINE, PK.OAK, PK.BIRCH, PK.SNOWPINE, PK.DEAD)

internal fun camBlocked(sx: Float, sy: Float, sz: Float): Boolean {
    var hit = false
    AdvWorld.forNear(sx, sz, 3.5f) { p ->
        if (hit || p.kind == PK.MOUNTAIN || p.r <= 0f) return@forNear
        val dx = p.x - sx; val dz = p.z - sz
        val crown = p.kind in treeKinds && sy > p.y + 1.4f * p.sc
        val rr = (if (crown) 2.0f * p.sc else p.r * p.sc + 0.35f)
        if (dx * dx + dz * dz < rr * rr && sy < p.y + p.h + 0.6f && sy > p.y - 0.5f) hit = true
    }
    return hit
}
