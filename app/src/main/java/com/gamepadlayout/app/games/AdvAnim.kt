package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Canais de uma pose. Convenção: ombro/quadril/cotovelo positivos = para a frente; joelho positivo = dobra. */
object PC {
    const val ROOT_Y = 0; const val ROOT_PITCH = 1; const val ROOT_ROLL = 2; const val ROOT_YAW = 3; const val ROOT_FWD = 4
    const val TORSO_YAW = 5; const val TORSO_PITCH = 6; const val TORSO_ROLL = 7; const val HEAD_PITCH = 8; const val HEAD_YAW = 9
    const val ARM_R_SH = 10; const val ARM_R_ROLL = 11; const val ARM_R_EL = 12
    const val ARM_L_SH = 13; const val ARM_L_ROLL = 14; const val ARM_L_EL = 15
    const val LEG_R_HIP = 16; const val LEG_R_KNEE = 17; const val LEG_L_HIP = 18; const val LEG_L_KNEE = 19
    const val WRIST = 20; const val SHIELD_G = 21; const val SHIELD_P = 22; const val CAPE = 23; const val HEAD_ROLL = 24
    const val N = 25
}

class Pose {
    val v = FloatArray(PC.N)
    fun clear() { java.util.Arrays.fill(v, 0f); v[PC.WRIST] = 1.2f }
    fun copyFrom(o: Pose) { System.arraycopy(o.v, 0, v, 0, PC.N) }
    init { v[PC.WRIST] = 1.2f }
}

/** Estado de animação de um personagem: pose atual (suavizada) e pose-alvo. */
class AnimState {
    val cur = Pose()
    val tgt = Pose()
    var phase = 0f
    var amt = 0f
    var inited = false
}

/** Identificadores das animações. */
object AN {
    const val IDLE = 0; const val GUARD = 1; const val PARRY = 2; const val ROLL = 3
    const val ATK1 = 4; const val ATK2 = 5; const val ATK3 = 6; const val HEAVY_CHARGE = 7; const val HEAVY_HIT = 8; const val SPIN = 9
    const val SHOOT = 10; const val CAST = 11; const val HURT_L = 12; const val HURT_H = 13; const val STUN = 14; const val FALL = 15
    const val DRINK = 16; const val PICKUP = 17; const val OPEN = 18; const val TALK = 19; const val WAVE = 20; const val WORK = 21
    const val CHEER = 22; const val WORRY = 23; const val DEAD = 24; const val RIPOSTE = 25
    const val E_OVER = 26; const val E_THRUST = 27; const val E_SLAM = 28; const val E_WIND = 29; const val E_SPIN = 30; const val E_LEAP = 31
    const val ROAR = 32; const val FLEE = 33; const val HANDS_HIP = 34; const val LOOK = 35; const val SWEEP = 36; const val KNEEL = 37
}

/** Estilos de arma (mudam as animações de ataque). */
object WS {
    const val SWORD = 0; const val AXE = 1; const val SPEAR = 2; const val BOW = 3; const val STAFF = 4; const val CLUB = 5; const val HAMMER = 6; const val NONE = 7
}

/** Cálculo das poses e esqueleto (cinemática direta) dos personagens articulados. */
object AdvAnim {
    private const val PI_F = PI.toFloat()

    /** Interpolação suave por trechos: pares (tempo, valor) crescentes em tempo. */
    private fun tr(t: Float, vararg kv: Float): Float {
        if (t <= kv[0]) return kv[1]
        var i = 0
        while (i + 3 < kv.size) {
            val t0 = kv[i]; val t1 = kv[i + 2]
            if (t <= t1) {
                val u = ((t - t0) / max(0.0001f, t1 - t0)).coerceIn(0f, 1f)
                val e = u * u * (3f - 2f * u)
                return kv[i + 1] + (kv[i + 3] - kv[i + 1]) * e
            }
            i += 2
        }
        return kv[kv.size - 1]
    }

    /** Locomoção: ciclo de passos proporcional ao movimento (amt 0 = parado, 1 = andando, 1.6 = correndo). */
    private fun locomotion(o: FloatArray, ph: Float, amt: Float, t: Float, look: Float) {
        val run = (amt - 1f).coerceIn(0f, 0.8f) / 0.8f
        val a = min(amt, 1f)
        val sw = sin(ph)
        val hipAmp = 0.65f * a + 0.45f * run
        o[PC.LEG_R_HIP] = sw * hipAmp
        o[PC.LEG_L_HIP] = -sw * hipAmp
        o[PC.LEG_R_KNEE] = max(0f, cos(ph)) * (0.8f * a + 0.7f * run) + 0.05f
        o[PC.LEG_L_KNEE] = max(0f, -cos(ph)) * (0.8f * a + 0.7f * run) + 0.05f
        val armAmp = 0.55f * a + 0.5f * run
        o[PC.ARM_R_SH] = -sw * armAmp + 0.05f
        o[PC.ARM_L_SH] = sw * armAmp + 0.05f
        o[PC.ARM_R_EL] = 0.25f + 0.35f * a + 0.5f * run
        o[PC.ARM_L_EL] = 0.25f + 0.35f * a + 0.5f * run
        o[PC.ARM_R_ROLL] = 0.08f; o[PC.ARM_L_ROLL] = 0.08f
        o[PC.ROOT_Y] = 0.04f * a * cos(ph * 2f) - 0.06f * run
        o[PC.ROOT_PITCH] = 0.04f * a + 0.2f * run
        o[PC.TORSO_YAW] = sw * (0.14f * a + 0.1f * run)
        o[PC.TORSO_PITCH] = 0.03f * a
        val br = sin(t * 2.2f) * (1f - a)
        o[PC.TORSO_PITCH] += br * 0.025f
        o[PC.ARM_R_SH] += br * 0.03f; o[PC.ARM_L_SH] -= br * 0.03f
        o[PC.HEAD_YAW] = look * 0.8f
        o[PC.HEAD_PITCH] = 0.04f
        o[PC.CAPE] = 0.12f + 0.45f * a + 0.35f * run + 0.05f * sin(t * 3f)
    }

    /** Escreve a pose-alvo de uma ação. t = progresso 0..1 da ação; h = momento do impacto (0..1); st = estilo de arma. */
    fun target(out: Pose, an: Int, t: Float, h: Float, st: Int, ph: Float, amt: Float, time: Float, look: Float) {
        val o = out.v
        java.util.Arrays.fill(o, 0f); o[PC.WRIST] = 1.2f
        locomotion(o, ph, amt, time, look)
        val hh = h.coerceIn(0.15f, 0.7f)
        when (an) {
            AN.IDLE -> {}
            AN.HANDS_HIP -> { o[PC.ARM_R_SH] = 0.1f; o[PC.ARM_R_ROLL] = 0.7f; o[PC.ARM_R_EL] = 1.6f; o[PC.ARM_L_SH] = 0.1f; o[PC.ARM_L_ROLL] = 0.7f; o[PC.ARM_L_EL] = 1.6f; o[PC.HEAD_YAW] = look * 0.9f + sin(time * 0.6f) * 0.25f }
            AN.LOOK -> { o[PC.HEAD_YAW] = sin(time * 0.8f) * 0.7f; o[PC.HEAD_PITCH] = sin(time * 0.5f) * 0.12f; o[PC.TORSO_YAW] = sin(time * 0.8f) * 0.2f; o[PC.ARM_R_SH] = 0.3f; o[PC.ARM_R_EL] = 1.1f }
            AN.GUARD -> {
                o[PC.SHIELD_G] = 1f
                o[PC.ARM_L_SH] = 1.15f; o[PC.ARM_L_EL] = 1.5f; o[PC.ARM_L_ROLL] = -0.25f
                o[PC.ARM_R_SH] = 0.55f; o[PC.ARM_R_EL] = 1.3f; o[PC.ARM_R_ROLL] = 0.2f
                o[PC.TORSO_YAW] = 0.35f + o[PC.TORSO_YAW] * 0.4f; o[PC.ROOT_Y] -= 0.07f
                o[PC.LEG_R_KNEE] += 0.25f; o[PC.LEG_L_KNEE] += 0.25f; o[PC.LEG_R_HIP] -= 0.12f; o[PC.LEG_L_HIP] += 0.2f
                o[PC.HEAD_YAW] = -0.3f; o[PC.ROOT_PITCH] += 0.05f
            }
            AN.PARRY -> {
                val k = tr(t, 0f, 0f, 0.2f, 1f, 0.55f, 0.8f, 1f, 0f)
                o[PC.SHIELD_G] = 1f; o[PC.SHIELD_P] = k
                o[PC.ARM_L_SH] = 1.2f + 0.4f * k; o[PC.ARM_L_EL] = 1.5f - 1.1f * k; o[PC.ARM_L_ROLL] = -0.25f
                o[PC.ARM_R_SH] = 0.55f - 0.4f * k; o[PC.ARM_R_EL] = 1.3f + 0.3f * k
                o[PC.TORSO_YAW] = 0.35f - 0.8f * k; o[PC.ROOT_FWD] = 0.3f * k; o[PC.ROOT_Y] = -0.08f
                o[PC.LEG_R_KNEE] += 0.3f; o[PC.LEG_L_KNEE] += 0.3f; o[PC.LEG_L_HIP] = 0.5f * k; o[PC.LEG_R_HIP] = -0.3f * k
                o[PC.HEAD_PITCH] = -0.1f
            }
            AN.ROLL -> {
                val crouch = tr(t, 0f, 0f, 0.18f, 1f, 0.8f, 1f, 1f, 0.35f)
                val flip = tr(t, 0.12f, 0f, 0.82f, 1f)
                o[PC.ROOT_PITCH] = flip * 2f * PI_F
                o[PC.ROOT_Y] = -0.3f * crouch
                o[PC.LEG_R_HIP] = 1.35f * crouch; o[PC.LEG_L_HIP] = 1.35f * crouch
                o[PC.LEG_R_KNEE] = 1.9f * crouch; o[PC.LEG_L_KNEE] = 1.9f * crouch
                o[PC.TORSO_PITCH] = 0.7f * crouch
                o[PC.ARM_R_SH] = 1.0f * crouch; o[PC.ARM_L_SH] = 1.0f * crouch; o[PC.ARM_R_EL] = 1.8f * crouch; o[PC.ARM_L_EL] = 1.8f * crouch
                o[PC.HEAD_PITCH] = 0.6f * crouch; o[PC.CAPE] = 0.9f
            }
            AN.ATK1, AN.ATK2, AN.ATK3 -> attack(o, an, t, hh, st)
            AN.HEAVY_CHARGE -> {
                val k = tr(t, 0f, 0f, 0.35f, 1f)
                o[PC.ARM_R_SH] = 2.2f * k + 0.2f; o[PC.ARM_R_EL] = 0.6f; o[PC.ARM_L_SH] = 1.6f * k; o[PC.ARM_L_EL] = 0.7f
                o[PC.TORSO_PITCH] = -0.28f * k; o[PC.TORSO_YAW] = 0.3f * k; o[PC.ROOT_Y] = -0.08f * k
                o[PC.LEG_R_KNEE] += 0.3f * k; o[PC.LEG_L_KNEE] += 0.3f * k; o[PC.LEG_L_HIP] = 0.4f * k; o[PC.LEG_R_HIP] = -0.3f * k
                o[PC.WRIST] = 2.4f; o[PC.HEAD_PITCH] = -0.1f
            }
            AN.HEAVY_HIT -> {
                val k = tr(t, 0f, 0f, hh, 1f, hh + 0.2f, 1f, 1f, 0f)
                val up = 1f - tr(t, 0f, 0f, hh, 1f)
                o[PC.ARM_R_SH] = 2.2f * up + 0.7f * (1f - up) - 0.2f * (1f - k); o[PC.ARM_R_EL] = 0.6f * up + 0.2f; o[PC.ARM_L_SH] = 1.6f * up + 0.6f * (1f - up); o[PC.ARM_L_EL] = 0.6f
                o[PC.TORSO_PITCH] = -0.28f * up + 0.55f * k; o[PC.ROOT_PITCH] = 0.3f * k; o[PC.ROOT_FWD] = 0.45f * k; o[PC.ROOT_Y] = -0.18f * k
                o[PC.LEG_R_KNEE] += 0.5f * k; o[PC.LEG_L_KNEE] += 0.5f * k; o[PC.LEG_L_HIP] = 0.7f * k; o[PC.LEG_R_HIP] = -0.4f * k
                o[PC.WRIST] = 2.4f - 1.0f * k; o[PC.HEAD_PITCH] = 0.2f * k
            }
            AN.SPIN -> {
                o[PC.ROOT_YAW] = t * 2f * PI_F
                o[PC.ARM_R_SH] = 1.45f; o[PC.ARM_R_ROLL] = 1.3f; o[PC.ARM_R_EL] = 0.1f; o[PC.ARM_L_SH] = 1.2f; o[PC.ARM_L_ROLL] = 1.2f
                o[PC.TORSO_PITCH] = 0.15f; o[PC.ROOT_Y] = -0.1f; o[PC.LEG_R_KNEE] += 0.4f; o[PC.LEG_L_KNEE] += 0.4f
                o[PC.LEG_R_HIP] = 0.3f; o[PC.LEG_L_HIP] = -0.3f; o[PC.WRIST] = 1.5f
            }
            AN.SHOOT -> {
                val draw = tr(t, 0f, 0f, hh, 1f, hh + 0.08f, 0f, 1f, 0f)
                o[PC.ARM_L_SH] = 1.5f; o[PC.ARM_L_EL] = 0.05f; o[PC.ARM_L_ROLL] = 0.1f
                o[PC.ARM_R_SH] = 1.45f + 0.1f * draw; o[PC.ARM_R_EL] = 0.3f + 1.7f * draw; o[PC.ARM_R_ROLL] = 0.5f * draw
                o[PC.TORSO_YAW] = 0.7f; o[PC.HEAD_YAW] = -0.7f; o[PC.ROOT_Y] = -0.04f
                o[PC.LEG_R_HIP] = -0.25f; o[PC.LEG_L_HIP] = 0.25f; o[PC.ROOT_FWD] = -0.1f * (1f - draw)
            }
            AN.CAST -> {
                val k = tr(t, 0f, 0f, hh, 1f, 1f, 0.2f)
                val raise = tr(t, 0f, 0f, hh * 0.9f, 1f)
                o[PC.ARM_R_SH] = 2.5f * raise - 0.9f * (k - raise).coerceAtLeast(0f); o[PC.ARM_R_EL] = 0.5f; o[PC.ARM_L_SH] = 1.4f * raise; o[PC.ARM_L_EL] = 0.6f; o[PC.ARM_L_ROLL] = 0.6f * raise
                o[PC.TORSO_PITCH] = -0.2f * raise + 0.3f * (k * (1f - raise)); o[PC.HEAD_PITCH] = -0.3f * raise; o[PC.ROOT_Y] = -0.04f; o[PC.WRIST] = 1.6f
            }
            AN.RIPOSTE -> {
                val k = tr(t, 0f, 0f, 0.3f, 1f, 0.6f, 1f, 1f, 0f)
                o[PC.ARM_R_SH] = 1.55f * k; o[PC.ARM_R_EL] = 0.1f; o[PC.ARM_R_ROLL] = 0.1f; o[PC.WRIST] = 1.55f
                o[PC.TORSO_YAW] = -0.5f * k; o[PC.ROOT_FWD] = 0.65f * k; o[PC.ROOT_PITCH] = 0.15f * k
                o[PC.LEG_L_HIP] = 0.9f * k; o[PC.LEG_R_HIP] = -0.5f * k; o[PC.LEG_L_KNEE] += 0.4f * k; o[PC.LEG_R_KNEE] += 0.2f * k
                o[PC.ARM_L_SH] = 0.6f * k; o[PC.SHIELD_G] = 0.4f
            }
            AN.HURT_L -> {
                val k = tr(t, 0f, 1f, 0.5f, 0.35f, 1f, 0f)
                o[PC.TORSO_PITCH] = -0.4f * k; o[PC.ROOT_PITCH] = -0.15f * k; o[PC.HEAD_PITCH] = -0.4f * k; o[PC.ROOT_FWD] = -0.15f * k
                o[PC.ARM_R_SH] = -0.4f * k; o[PC.ARM_L_SH] = -0.3f * k; o[PC.ARM_R_ROLL] = 0.6f * k; o[PC.ARM_L_ROLL] = 0.6f * k
                o[PC.LEG_R_KNEE] += 0.25f * k; o[PC.LEG_L_KNEE] += 0.25f * k; o[PC.ROOT_Y] = -0.04f * k
            }
            AN.HURT_H -> {
                val k = tr(t, 0f, 1f, 0.6f, 0.6f, 1f, 0f)
                o[PC.TORSO_PITCH] = -0.5f * k; o[PC.ROOT_PITCH] = -0.35f * k; o[PC.HEAD_PITCH] = -0.6f * k; o[PC.ROOT_FWD] = -0.3f * k; o[PC.ROOT_Y] = -0.1f * k
                o[PC.ARM_R_SH] = -0.7f * k; o[PC.ARM_L_SH] = -0.6f * k; o[PC.ARM_R_ROLL] = 1.0f * k; o[PC.ARM_L_ROLL] = 1.0f * k
                o[PC.LEG_R_KNEE] += 0.5f * k; o[PC.LEG_L_KNEE] += 0.5f * k; o[PC.LEG_R_HIP] = 0.4f * k; o[PC.LEG_L_HIP] = -0.3f * k
            }
            AN.STUN -> {
                o[PC.TORSO_ROLL] = sin(time * 7f) * 0.18f; o[PC.HEAD_ROLL] = sin(time * 7f + 1f) * 0.3f; o[PC.HEAD_YAW] = sin(time * 5f) * 0.5f
                o[PC.HEAD_PITCH] = 0.3f; o[PC.TORSO_PITCH] = 0.25f; o[PC.ARM_R_SH] = 0.2f; o[PC.ARM_L_SH] = 0.2f; o[PC.ARM_R_ROLL] = 0.4f; o[PC.ARM_L_ROLL] = 0.4f
                o[PC.ROOT_Y] = -0.08f; o[PC.LEG_R_KNEE] += 0.3f; o[PC.LEG_L_KNEE] += 0.3f
            }
            AN.FALL -> {
                val down = tr(t, 0f, 0f, 0.25f, 1f, 0.7f, 1f, 0.95f, 0f)
                o[PC.ROOT_PITCH] = -1.5f * down; o[PC.ROOT_Y] = -0.72f * down; o[PC.ROOT_FWD] = -0.5f * down
                o[PC.ARM_R_SH] = -0.5f * down; o[PC.ARM_L_SH] = -0.5f * down; o[PC.ARM_R_ROLL] = 0.9f * down; o[PC.ARM_L_ROLL] = 0.9f * down
                o[PC.LEG_R_KNEE] += 0.4f * down; o[PC.LEG_L_KNEE] += 0.2f * down; o[PC.HEAD_PITCH] = -0.4f * down
            }
            AN.DRINK -> {
                val k = tr(t, 0f, 0f, 0.3f, 1f, 0.75f, 1f, 1f, 0f)
                o[PC.ARM_R_SH] = 1.9f * k; o[PC.ARM_R_EL] = 2.3f * k; o[PC.ARM_R_ROLL] = 0.15f; o[PC.WRIST] = 2.6f * k
                o[PC.HEAD_PITCH] = -0.45f * k; o[PC.TORSO_PITCH] = -0.12f * k; o[PC.ARM_L_SH] = 0.2f; o[PC.ROOT_Y] = -0.03f
            }
            AN.PICKUP, AN.OPEN -> {
                val dn = tr(t, 0f, 0f, 0.3f, 1f, 0.55f, 1f, 0.8f, 0f)
                val up = tr(t, 0.5f, 0f, 0.8f, 1f, 1f, 0.3f)
                o[PC.ROOT_PITCH] = 0.55f * dn; o[PC.ROOT_Y] = -0.28f * dn; o[PC.LEG_R_HIP] = 1.0f * dn; o[PC.LEG_L_HIP] = 1.0f * dn
                o[PC.LEG_R_KNEE] = 1.6f * dn; o[PC.LEG_L_KNEE] = 1.6f * dn
                if (an == AN.PICKUP) { o[PC.ARM_R_SH] = 0.7f * dn + 1.5f * up; o[PC.ARM_R_EL] = 0.3f + 0.9f * up; o[PC.ARM_L_SH] = 0.4f * dn }
                else { o[PC.ARM_R_SH] = 1.1f * dn - 0.6f * up; o[PC.ARM_L_SH] = 1.1f * dn - 0.6f * up; o[PC.ARM_R_EL] = 0.4f; o[PC.ARM_L_EL] = 0.4f }
                o[PC.HEAD_PITCH] = 0.2f * dn
            }
            AN.TALK -> {
                val g = sin(time * 4.3f); val g2 = sin(time * 3.1f + 1f)
                o[PC.ARM_R_SH] = 0.9f + 0.35f * g; o[PC.ARM_R_EL] = 1.1f + 0.3f * g2; o[PC.ARM_R_ROLL] = 0.35f
                o[PC.ARM_L_SH] = 0.5f + 0.2f * g2; o[PC.ARM_L_EL] = 1.2f; o[PC.ARM_L_ROLL] = 0.35f
                o[PC.HEAD_PITCH] = 0.05f + 0.12f * max(0f, sin(time * 2.4f)); o[PC.HEAD_YAW] = look * 0.9f; o[PC.TORSO_YAW] = look * 0.3f
            }
            AN.WAVE -> {
                o[PC.ARM_R_SH] = 2.5f; o[PC.ARM_R_ROLL] = 0.55f; o[PC.ARM_R_EL] = 0.8f + 0.5f * sin(time * 11f)
                o[PC.HEAD_YAW] = look * 0.9f; o[PC.TORSO_ROLL] = 0.05f
            }
            AN.WORK -> {
                val c = (time * 2.4f) % 1f
                val k = tr(c, 0f, 0f, 0.55f, 1f, 0.68f, 0f, 1f, 0f)
                o[PC.ARM_R_SH] = 0.6f + 1.9f * k; o[PC.ARM_R_EL] = 0.6f + 0.2f * k; o[PC.ARM_L_SH] = 0.9f; o[PC.ARM_L_EL] = 1.2f
                o[PC.TORSO_PITCH] = 0.1f + 0.2f * (1f - k); o[PC.ROOT_Y] = -0.04f; o[PC.WRIST] = 1.9f
            }
            AN.SWEEP -> {
                val c = sin(time * 3f)
                o[PC.ARM_R_SH] = 1.0f; o[PC.ARM_R_EL] = 0.9f; o[PC.ARM_L_SH] = 1.0f; o[PC.ARM_L_EL] = 0.9f
                o[PC.TORSO_YAW] = c * 0.35f; o[PC.TORSO_PITCH] = 0.22f; o[PC.HEAD_PITCH] = 0.2f; o[PC.ROOT_Y] = -0.03f
            }
            AN.KNEEL -> {
                o[PC.ROOT_Y] = -0.3f; o[PC.LEG_R_HIP] = 1.3f; o[PC.LEG_R_KNEE] = 1.5f; o[PC.LEG_L_HIP] = -0.2f; o[PC.LEG_L_KNEE] = 1.7f
                o[PC.TORSO_PITCH] = 0.3f; o[PC.HEAD_PITCH] = 0.3f; o[PC.ARM_R_SH] = 0.6f; o[PC.ARM_L_SH] = 0.6f
            }
            AN.CHEER -> {
                val b = abs(sin(time * 7f))
                o[PC.ARM_R_SH] = 2.7f; o[PC.ARM_L_SH] = 2.7f; o[PC.ARM_R_ROLL] = 0.45f; o[PC.ARM_L_ROLL] = 0.45f; o[PC.ARM_R_EL] = 0.3f; o[PC.ARM_L_EL] = 0.3f
                o[PC.ROOT_Y] = 0.2f * b; o[PC.LEG_R_KNEE] += 0.5f * (1f - b); o[PC.LEG_L_KNEE] += 0.5f * (1f - b); o[PC.HEAD_PITCH] = -0.2f
            }
            AN.WORRY -> {
                o[PC.ARM_R_SH] = 2.2f; o[PC.ARM_R_EL] = 2.3f; o[PC.ARM_L_SH] = 2.2f; o[PC.ARM_L_EL] = 2.3f; o[PC.ARM_R_ROLL] = 0.2f; o[PC.ARM_L_ROLL] = 0.2f
                o[PC.TORSO_PITCH] = 0.2f; o[PC.HEAD_PITCH] = 0.25f; o[PC.TORSO_ROLL] = sin(time * 9f) * 0.03f; o[PC.ROOT_Y] = -0.03f
            }
            AN.DEAD -> {
                val k = tr(t, 0f, 0f, 0.5f, 1f)
                o[PC.ROOT_PITCH] = -1.52f * k; o[PC.ROOT_Y] = -0.74f * k; o[PC.ROOT_FWD] = -0.6f * k
                o[PC.ARM_R_SH] = -0.6f * k; o[PC.ARM_L_SH] = -0.4f * k; o[PC.ARM_R_ROLL] = 1.0f * k; o[PC.ARM_L_ROLL] = 1.2f * k
                o[PC.LEG_R_KNEE] += 0.5f * k; o[PC.LEG_L_KNEE] += 0.2f * k; o[PC.LEG_L_HIP] = 0.3f * k; o[PC.HEAD_PITCH] = -0.5f * k
            }
            AN.E_WIND -> {
                val k = tr(t, 0f, 0f, 1f, 1f)
                o[PC.ARM_R_SH] = 2.3f * k; o[PC.ARM_R_EL] = 0.5f; o[PC.ARM_L_SH] = 0.8f * k; o[PC.TORSO_PITCH] = -0.25f * k; o[PC.WRIST] = 2.3f
                o[PC.LEG_R_KNEE] += 0.2f * k; o[PC.LEG_L_KNEE] += 0.2f * k; o[PC.ROOT_Y] = -0.05f * k
            }
            AN.E_OVER -> {
                val up = 1f - tr(t, 0f, 0f, hh, 1f)
                val k = tr(t, hh, 0f, hh + 0.15f, 1f, 1f, 0.2f)
                o[PC.ARM_R_SH] = 2.3f * up + 0.5f * (1f - up); o[PC.ARM_R_EL] = 0.5f; o[PC.ARM_L_SH] = 0.6f; o[PC.WRIST] = 2.2f - 0.9f * (1f - up)
                o[PC.TORSO_PITCH] = -0.25f * up + 0.45f * k; o[PC.ROOT_FWD] = 0.35f * k; o[PC.ROOT_Y] = -0.1f * k
                o[PC.LEG_L_HIP] = 0.5f * k; o[PC.LEG_R_HIP] = -0.3f * k; o[PC.LEG_R_KNEE] += 0.3f * k
            }
            AN.E_THRUST -> {
                val pull = tr(t, 0f, 0f, hh, 1f)
                val k = tr(t, hh, 0f, hh + 0.12f, 1f, 1f, 0.15f)
                o[PC.ARM_R_SH] = -0.1f * pull + 1.5f * k; o[PC.ARM_R_EL] = 1.3f * pull * (1f - k) + 0.1f; o[PC.ARM_L_SH] = 0.9f * pull; o[PC.WRIST] = 1.55f
                o[PC.TORSO_YAW] = 0.6f * pull - 1.0f * k; o[PC.ROOT_FWD] = 0.55f * k; o[PC.LEG_L_HIP] = 0.8f * k; o[PC.LEG_R_HIP] = -0.5f * k
                o[PC.LEG_R_KNEE] += 0.3f * pull
            }
            AN.E_SLAM -> {
                val up = 1f - tr(t, 0f, 0f, hh, 1f)
                val k = tr(t, hh, 0f, hh + 0.12f, 1f, 1f, 0.5f)
                o[PC.ARM_R_SH] = 2.8f * up + 0.4f * (1f - up); o[PC.ARM_L_SH] = 2.8f * up + 0.4f * (1f - up); o[PC.ARM_R_EL] = 0.4f; o[PC.ARM_L_EL] = 0.4f
                o[PC.ARM_R_ROLL] = 0.15f; o[PC.ARM_L_ROLL] = 0.15f
                o[PC.TORSO_PITCH] = -0.4f * up + 0.7f * k; o[PC.ROOT_PITCH] = 0.25f * k; o[PC.ROOT_Y] = -0.2f * k + 0.1f * up
                o[PC.LEG_R_KNEE] += 0.5f * k; o[PC.LEG_L_KNEE] += 0.5f * k; o[PC.WRIST] = 2.3f - 1.0f * (1f - up)
            }
            AN.E_SPIN -> {
                o[PC.ROOT_YAW] = t * 6f * PI_F
                o[PC.ARM_R_SH] = 1.5f; o[PC.ARM_R_ROLL] = 1.3f; o[PC.ARM_L_SH] = 1.4f; o[PC.ARM_L_ROLL] = 1.3f
                o[PC.TORSO_PITCH] = 0.2f; o[PC.ROOT_Y] = -0.1f; o[PC.LEG_R_KNEE] += 0.4f; o[PC.LEG_L_KNEE] += 0.4f; o[PC.WRIST] = 1.55f
            }
            AN.E_LEAP -> {
                val crouch = tr(t, 0f, 0f, hh, 1f, hh + 0.05f, 0f)
                val air = tr(t, hh, 0f, hh + 0.1f, 1f, 0.9f, 1f, 1f, 0f)
                o[PC.ROOT_Y] = -0.22f * crouch + 0.1f * air; o[PC.LEG_R_HIP] = 1.1f * crouch - 0.5f * air; o[PC.LEG_L_HIP] = 1.1f * crouch + 0.3f * air
                o[PC.LEG_R_KNEE] = 1.6f * crouch + 0.6f * air; o[PC.LEG_L_KNEE] = 1.6f * crouch + 0.3f * air
                o[PC.ARM_R_SH] = 2.4f * air + 0.8f * crouch; o[PC.ARM_L_SH] = 2.4f * air + 0.8f * crouch; o[PC.ROOT_PITCH] = 0.3f * crouch - 0.2f * air
            }
            AN.ROAR -> {
                val k = tr(t, 0f, 0f, 0.3f, 1f, 0.8f, 1f, 1f, 0f)
                o[PC.ARM_R_SH] = 1.2f * k; o[PC.ARM_L_SH] = 1.2f * k; o[PC.ARM_R_ROLL] = 1.2f * k; o[PC.ARM_L_ROLL] = 1.2f * k
                o[PC.TORSO_PITCH] = -0.4f * k; o[PC.HEAD_PITCH] = -0.5f * k; o[PC.ROOT_Y] = 0.04f * k + sin(time * 30f) * 0.01f * k
            }
            AN.FLEE -> {
                o[PC.ARM_R_SH] = 2.0f + sin(ph * 2f) * 0.4f; o[PC.ARM_L_SH] = 2.0f - sin(ph * 2f) * 0.4f; o[PC.ARM_R_ROLL] = 0.4f; o[PC.ARM_L_ROLL] = 0.4f
                o[PC.ROOT_PITCH] = 0.25f; o[PC.HEAD_PITCH] = 0.25f
            }
        }
    }

    /** Golpes de arma branca. */
    private fun attack(o: FloatArray, an: Int, t: Float, h: Float, st: Int) {
        val wind = tr(t, 0f, 0f, h, 1f)
        val k = tr(t, h, 0f, h + 0.13f, 1f, h + 0.45f, 0.6f, 1f, 0f)
        when (st) {
            WS.SPEAR -> {
                val pull = wind * (1f - k)
                o[PC.ARM_R_SH] = -0.2f * pull + 1.5f * k; o[PC.ARM_R_EL] = 1.4f * pull + 0.1f; o[PC.ARM_L_SH] = 0.7f * (wind.coerceAtLeast(k)); o[PC.ARM_L_EL] = 1.0f
                o[PC.TORSO_YAW] = (if (an == AN.ATK2) -0.4f else 0.5f) * pull - (if (an == AN.ATK2) -0.5f else 0.8f) * k
                o[PC.ROOT_FWD] = 0.55f * k; o[PC.LEG_L_HIP] = 0.8f * k - 0.2f * pull; o[PC.LEG_R_HIP] = -0.5f * k + 0.2f * pull
                o[PC.LEG_R_KNEE] += 0.4f * pull + 0.2f * k; o[PC.ROOT_PITCH] = 0.12f * k; o[PC.WRIST] = 1.55f
            }
            WS.AXE, WS.HAMMER, WS.CLUB -> {
                val up = 1f - wind
                o[PC.ARM_R_SH] = 2.4f * wind * (1f - k) + 0.6f * k; o[PC.ARM_R_EL] = 0.5f; o[PC.ARM_L_SH] = 1.2f * wind * (1f - k) + 0.4f * k; o[PC.ARM_L_EL] = 0.7f
                o[PC.TORSO_PITCH] = -0.3f * wind * (1f - k) + 0.5f * k; o[PC.ROOT_FWD] = 0.4f * k; o[PC.ROOT_Y] = -0.08f * wind - 0.1f * k
                o[PC.LEG_L_HIP] = 0.6f * k; o[PC.LEG_R_HIP] = -0.35f * k; o[PC.LEG_R_KNEE] += 0.35f * (wind + k) * 0.5f; o[PC.LEG_L_KNEE] += 0.3f * k
                o[PC.WRIST] = 2.3f - 1.1f * k; o[PC.TORSO_YAW] = 0.25f * wind * (1f - k) - 0.2f * k + up * 0f
            }
            WS.STAFF -> {
                o[PC.ARM_R_SH] = 1.8f * wind * (1f - k) + 1.1f * k; o[PC.ARM_R_EL] = 0.7f; o[PC.ARM_L_SH] = 1.0f * wind; o[PC.ARM_L_EL] = 1.0f
                o[PC.TORSO_YAW] = 0.6f * wind * (1f - k) - 0.7f * k; o[PC.ROOT_FWD] = 0.25f * k; o[PC.WRIST] = 1.9f - 0.5f * k
            }
            else -> {
                when (an) {
                    AN.ATK1 -> {
                        o[PC.TORSO_YAW] = 0.95f * wind * (1f - k) - 0.95f * k
                        o[PC.ARM_R_SH] = 1.0f * wind * (1f - k) + 1.45f * k; o[PC.ARM_R_ROLL] = 0.9f * wind * (1f - k) + 0.2f * k; o[PC.ARM_R_EL] = 0.7f * wind * (1f - k) + 0.15f * k
                        o[PC.ARM_L_SH] = -0.3f * wind
                    }
                    AN.ATK2 -> {
                        o[PC.TORSO_YAW] = -0.9f * wind * (1f - k) + 0.9f * k
                        o[PC.ARM_R_SH] = 1.3f * wind * (1f - k) + 1.45f * k; o[PC.ARM_R_ROLL] = -0.2f * wind * (1f - k) + 1.0f * k; o[PC.ARM_R_EL] = 0.6f * wind * (1f - k) + 0.15f * k
                        o[PC.ARM_L_SH] = 0.4f * wind
                    }
                    else -> {
                        o[PC.ARM_R_SH] = 2.6f * wind * (1f - k) + 0.9f * k; o[PC.ARM_R_EL] = 0.5f; o[PC.ARM_L_SH] = 1.0f * wind * (1f - k) + 0.3f * k
                        o[PC.TORSO_PITCH] = -0.3f * wind * (1f - k) + 0.5f * k; o[PC.ROOT_PITCH] = 0.25f * k; o[PC.ROOT_Y] = -0.12f * k
                        o[PC.WRIST] = 2.4f - 1.1f * k
                    }
                }
                o[PC.ROOT_FWD] = 0.3f * k; o[PC.LEG_L_HIP] = 0.55f * k; o[PC.LEG_R_HIP] = -0.3f * k; o[PC.LEG_L_KNEE] += 0.3f * k; o[PC.LEG_R_KNEE] += 0.2f * (wind + k) * 0.5f
                if (an != AN.ATK3) o[PC.ROOT_PITCH] = 0.1f * k
            }
        }
        o[PC.SHIELD_G] = if (st == WS.SWORD) 0f else 0f
    }

    /** Aproxima a pose atual da pose-alvo (rate = quão rápido). Ângulos de giro completo usam a diferença mais curta. */
    fun blend(st: AnimState, dt: Float, rate: Float) {
        val k = 1f - exp(-rate * dt)
        val c = st.cur.v; val t = st.tgt.v
        if (!st.inited) { System.arraycopy(t, 0, c, 0, PC.N); st.inited = true; return }
        for (i in 0 until PC.N) {
            var d = t[i] - c[i]
            if (i == PC.ROOT_PITCH || i == PC.ROOT_YAW) {
                val tp = 2f * PI_F
                d -= Math.round(d / tp) * tp
            }
            c[i] += d * k
        }
        if (c[PC.ROOT_PITCH] > 3f * PI_F || c[PC.ROOT_PITCH] < -3f * PI_F) c[PC.ROOT_PITCH] %= 2f * PI_F
    }
}

/** Esqueleto: monta as matrizes de cada junta e desenha as peças de um [Rig2] no renderizador. */
object AdvSkel {
    private val M = FloatArray(12); private val T = FloatArray(12); private val H = FloatArray(12)
    private val A = FloatArray(12); private val F = FloatArray(12); private val L = FloatArray(12); private val K = FloatArray(12)
    private val W = FloatArray(12); private val S = FloatArray(12); private val B = FloatArray(12)

    fun draw(
        r3: R3, rig: Rig2, p: Pose, x: Float, y: Float, z: Float, yaw: Float, sc: Float,
        flash: Boolean = false, fx: Long = 0L, fxAmt: Float = 0f,
        weapon: Mesh? = null, weaponLeft: Boolean = false, shield: Mesh? = null, handItem: Mesh? = null,
        lod: Boolean = false, bias: Float = 0f, shadow: Float = 0.8f
    ) {
        if (!r3.visible(x, y + 1.3f * sc, z, 2.6f * sc)) return
        val v = p.v
        r3.group(x, z, bias)
        if (shadow > 0f) r3.shadow(x, y + 0.06f, z, shadow * sc)
        if (lod) {
            val bob = v[PC.ROOT_Y]
            r3.meshKeep(rig.lod, x, y + bob * sc, z, yaw + v[PC.ROOT_YAW], v[PC.ROOT_PITCH], sc, flash, fx, fxAmt)
            if (weapon != null) r3.meshKeep(weapon, x + sinF(yaw) * 0.5f * sc, y + 1.1f * sc, z + cosF(yaw) * 0.5f * sc, yaw, -1.0f, sc, flash, fx, fxAmt)
            return
        }
        val bk = rig.bulk
        Xf.ident(M); M[3] = x; M[7] = y; M[11] = z
        Xf.rotY(M, yaw + v[PC.ROOT_YAW])
        Xf.move(M, 0f, v[PC.ROOT_Y] * sc, v[PC.ROOT_FWD] * sc)
        Xf.move(M, 0f, 0.9f * sc, 0f)
        Xf.rotX(M, v[PC.ROOT_PITCH]); Xf.rotZ(M, v[PC.ROOT_ROLL])
        Xf.move(M, 0f, -0.9f * sc, 0f)
        r3.meshXf(rig.pelvis, M, sc, flash, fx, fxAmt)
        // tronco
        Xf.copy(M, T); Xf.move(T, 0f, RG.WAIST * sc, 0f)
        Xf.rotY(T, v[PC.TORSO_YAW]); Xf.rotX(T, v[PC.TORSO_PITCH]); Xf.rotZ(T, v[PC.TORSO_ROLL])
        r3.meshXf(rig.torso, T, sc, flash, fx, fxAmt)
        // cabeça
        Xf.copy(T, H); Xf.move(H, 0f, RG.NECK_Y * sc, 0f)
        Xf.rotY(H, v[PC.HEAD_YAW]); Xf.rotX(H, v[PC.HEAD_PITCH]); Xf.rotZ(H, v[PC.HEAD_ROLL])
        r3.meshXf(rig.head, H, sc, flash, fx, fxAmt)
        // capa / mochila
        val back = rig.back
        if (back != null) {
            Xf.copy(T, B); Xf.move(B, 0f, (RG.NECK_Y - 0.04f) * sc, -0.3f * bk * sc)
            Xf.rotX(B, v[PC.CAPE])
            r3.meshXf(back, B, sc, flash, fx, fxAmt)
        }
        // braços (direito = lado +x, segura a arma)
        for (side in 0..1) {
            val sg = if (side == 0) 1f else -1f
            val sh = if (side == 0) v[PC.ARM_R_SH] else v[PC.ARM_L_SH]
            val rl = if (side == 0) v[PC.ARM_R_ROLL] else v[PC.ARM_L_ROLL]
            val el = if (side == 0) v[PC.ARM_R_EL] else v[PC.ARM_L_EL]
            Xf.copy(T, A); Xf.move(A, sg * RG.SHOULDER_X * bk * sc, RG.SHOULDER_Y * sc, 0f)
            Xf.rotX(A, -sh); Xf.rotZ(A, sg * rl)
            r3.meshXf(rig.uArm, A, sc, flash, fx, fxAmt)
            Xf.copy(A, F); Xf.move(F, 0f, -RG.UARM * sc, 0f); Xf.rotX(F, -el)
            r3.meshXf(rig.fArm, F, sc, flash, fx, fxAmt)
            val holds = (side == 0 && !weaponLeft) || (side == 1 && weaponLeft)
            if (holds && (weapon != null || handItem != null)) {
                Xf.copy(F, W); Xf.move(W, 0f, -(RG.FARM + 0.04f) * sc, 0f); Xf.rotX(W, -v[PC.WRIST])
                if (handItem != null) r3.meshXf(handItem, W, sc, flash, fx, fxAmt)
                else if (weapon != null) r3.meshXf(weapon, W, sc, flash, fx, fxAmt)
            }
        }
        // escudo (segue o tronco: carregado ao lado, na frente quando em guarda)
        if (shield != null) {
            val g = v[PC.SHIELD_G].coerceIn(0f, 1f); val pr = v[PC.SHIELD_P].coerceIn(0f, 1f)
            val cx = -0.9f * bk * (1f - g) + (-0.3f * bk) * g + 0.15f * pr
            val cy = 0.2f * (1f - g) + 0.32f * g + 0.06f * pr
            val cz = 0.05f * (1f - g) + 0.6f * g + 0.3f * pr
            Xf.copy(T, S); Xf.move(S, cx * sc, cy * sc, cz * sc)
            Xf.rotY(S, -1.5708f * (1f - g)); Xf.rotX(S, -0.12f * g)
            r3.meshXf(shield, S, sc, flash, fx, fxAmt)
        }
        // pernas
        for (side in 0..1) {
            val sg = if (side == 0) 1f else -1f
            val hip = if (side == 0) v[PC.LEG_R_HIP] else v[PC.LEG_L_HIP]
            val kn = if (side == 0) v[PC.LEG_R_KNEE] else v[PC.LEG_L_KNEE]
            Xf.copy(M, L); Xf.move(L, sg * RG.HIP_X * bk * sc, RG.HIP_Y * sc, 0f); Xf.rotX(L, -hip)
            r3.meshXf(rig.thigh, L, sc, flash, fx, fxAmt)
            Xf.copy(L, K); Xf.move(K, 0f, -RG.THIGH * sc, 0f); Xf.rotX(K, kn)
            r3.meshXf(rig.shin, K, sc, flash, fx, fxAmt)
        }
    }

    private fun sinF(a: Float) = sin(a)
    private fun cosF(a: Float) = cos(a)

    /** Posição no mundo da ponta da arma (para rastros e impactos): usa a última matriz da mão calculada em [draw]. */
    fun handWorld(out: FloatArray) { out[0] = W[3]; out[1] = W[7]; out[2] = W[11] }
}
