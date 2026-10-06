package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Identificadores dos efeitos sonoros do Vale de Aurora.
 * Para trocar um som por um arquivo seu: coloque "audio/sfx_<nome>.wav" (ou "audio/music_<nome>.wav") em
 * app/src/main/assets e ele passa a valer no lugar do som sintetizado (veja [Sfx.NAMES] e [Mus.NAMES]).
 */
object Sfx {
    const val UI_MOVE = 0; const val UI_OK = 1; const val UI_BACK = 2; const val SWING = 3; const val SWING_HEAVY = 4
    const val HIT = 5; const val HIT_HEAVY = 6; const val HURT = 7; const val BLOCK = 8; const val PARRY = 9
    const val GUARD_BREAK = 10; const val ROLL = 11; const val HEAL = 12; const val PICKUP = 13; const val CHEST = 14
    const val DOOR = 15; const val QUEST = 16; const val LEVEL = 17; const val ENEMY_DIE = 18; const val BOSS_ROAR = 19
    const val SHOOT = 20; const val MAGIC = 21; const val STEP = 22; const val BONFIRE = 23; const val DEATH = 24
    const val SLAM = 25; const val SPIT = 26; const val LOCK = 27; const val COIN = 28; const val STUN = 29
    const val WARN = 30; const val EXPLODE = 31; const val SWITCH = 32; const val EQUIP = 33; const val MENU_OPEN = 34
    const val DENIED = 35; const val BOSS_INTRO = 36; const val SPECIAL = 37; const val LEAP = 38; const val ARROW_HIT = 39
    const val COUNT = 40
    val NAMES = arrayOf(
        "ui_move", "ui_ok", "ui_back", "swing", "swing_heavy", "hit", "hit_heavy", "hurt", "block", "parry",
        "guard_break", "roll", "heal", "pickup", "chest", "door", "quest", "level", "enemy_die", "boss_roar",
        "shoot", "magic", "step", "bonfire", "death", "slam", "spit", "lock", "coin", "stun",
        "warn", "explode", "switch", "equip", "menu_open", "denied", "boss_intro", "special", "leap", "arrow_hit"
    )
}

/** Faixas de música (uma por região, uma para chefes). */
object Mus {
    const val TITLE = 0; const val VILLAGE = 1; const val CAMPINA = 2; const val FOREST = 3; const val LAKE = 4
    const val RUINS = 5; const val CAVE = 6; const val PEAK = 7; const val TEMPLE = 8; const val BOSS = 9
    const val FINAL = 10; const val ENDING = 11
    const val COUNT = 12
    val NAMES = arrayOf("title", "village", "campina", "forest", "lake", "ruins", "cave", "peak", "temple", "boss", "final", "ending")
}

/** Sintetizador puro em Kotlin: gera efeitos e música como PCM de 16 bits, mono, 22050 Hz. */
object Synth {
    const val RATE = 22050

    private fun buf(sec: Float) = FloatArray(max(1, (sec * RATE).toInt()))

    /** Forma de onda a partir da fase (em ciclos). 0 seno, 1 quadrada, 2 serra, 3 triângulo, 4 pulso 25%. */
    private fun wave(kind: Int, ph: Float): Float {
        val p = ph - floor(ph)
        return when (kind) {
            0 -> sin(p * 2f * PI.toFloat())
            1 -> if (p < 0.5f) 0.7f else -0.7f
            2 -> (p * 2f - 1f) * 0.8f
            3 -> (if (p < 0.5f) p * 4f - 1f else 3f - p * 4f)
            else -> if (p < 0.25f) 0.7f else -0.7f
        }
    }

    private fun midi(m: Int) = 440f * 2f.pow((m - 69) / 12f)

    /** Nota com varredura exponencial de frequência, ataque curto e queda exponencial. */
    private fun tone(b: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, kind: Int, vol: Float, att: Float = 0.004f, decay: Float = 4f, vib: Float = 0f) {
        val s0 = (start * RATE).toInt(); val n = (dur * RATE).toInt()
        var ph = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx < 0 || idx >= b.size) continue
            val t = i.toFloat() / n
            var f = f0 * (f1 / f0).pow(t)
            if (vib != 0f) f *= 1f + vib * sin(i.toFloat() / RATE * 2f * PI.toFloat() * 7f)
            ph += f / RATE
            val a = min(1f, i.toFloat() / (att * RATE))
            val tail = min(1f, (n - i).toFloat() / (0.006f * RATE))
            b[idx] += wave(kind, ph) * vol * a * exp(-decay * t) * tail
        }
    }

    /** Ruído com filtro passa-baixas de um polo (coef 0..1; maior = mais agudo), com varredura. */
    private fun noise(b: FloatArray, start: Float, dur: Float, vol: Float, c0: Float, c1: Float, seed: Int = 1, att: Float = 0.004f, decay: Float = 3f, hp: Boolean = false, bell: Boolean = false) {
        val r = Random(seed)
        val s0 = (start * RATE).toInt(); val n = (dur * RATE).toInt()
        var y = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx < 0 || idx >= b.size) continue
            val t = i.toFloat() / n
            val c = c0 + (c1 - c0) * t
            val x = r.nextFloat() * 2f - 1f
            y += c * (x - y)
            val out = if (hp) x - y else y
            val a = min(1f, i.toFloat() / (att * RATE))
            val env = if (bell) sin(PI.toFloat() * t) else exp(-decay * t)
            val tail = min(1f, (n - i).toFloat() / (0.006f * RATE))
            b[idx] += out * vol * a * env * tail
        }
    }

    private fun pcm(b: FloatArray, gain: Float = 1f): ShortArray {
        var peak = 0f
        for (v in b) peak = max(peak, abs(v))
        val k = if (peak > 0.9f) 0.9f / peak else 1f
        return ShortArray(b.size) { (b[it] * k * gain * 32767f).toInt().coerceIn(-32767, 32767).toShort() }
    }

    fun sfx(id: Int): ShortArray {
        val b: FloatArray
        when (id) {
            Sfx.UI_MOVE -> { b = buf(0.05f); tone(b, 0f, 0.045f, 880f, 880f, 0, 0.3f, decay = 3f) }
            Sfx.UI_OK -> { b = buf(0.16f); tone(b, 0f, 0.07f, 660f, 660f, 3, 0.35f); tone(b, 0.07f, 0.09f, 990f, 990f, 3, 0.35f) }
            Sfx.UI_BACK -> { b = buf(0.14f); tone(b, 0f, 0.07f, 520f, 520f, 3, 0.3f); tone(b, 0.06f, 0.08f, 340f, 340f, 3, 0.3f) }
            Sfx.SWING -> { b = buf(0.2f); noise(b, 0f, 0.18f, 0.55f, 0.12f, 0.7f, 3, att = 0.03f, bell = true) }
            Sfx.SWING_HEAVY -> { b = buf(0.4f); noise(b, 0f, 0.36f, 0.75f, 0.06f, 0.5f, 4, att = 0.1f, bell = true); tone(b, 0.05f, 0.3f, 140f, 70f, 2, 0.12f, decay = 3f) }
            Sfx.HIT -> { b = buf(0.18f); noise(b, 0f, 0.07f, 0.6f, 0.55f, 0.25f, 5, decay = 5f); tone(b, 0f, 0.12f, 180f, 60f, 0, 0.7f, decay = 5f) }
            Sfx.HIT_HEAVY -> { b = buf(0.4f); noise(b, 0f, 0.14f, 0.8f, 0.5f, 0.15f, 6, decay = 4f); tone(b, 0f, 0.3f, 110f, 34f, 0, 0.9f, decay = 4f); tone(b, 0f, 0.05f, 900f, 300f, 1, 0.25f, decay = 6f) }
            Sfx.HURT -> { b = buf(0.3f); tone(b, 0f, 0.26f, 360f, 120f, 2, 0.45f, decay = 3f); noise(b, 0f, 0.1f, 0.4f, 0.4f, 0.2f, 7) }
            Sfx.BLOCK -> { b = buf(0.3f); for (f in floatArrayOf(520f, 1317f, 2150f)) tone(b, 0f, 0.22f, f, f, 0, 0.3f, decay = 7f); noise(b, 0f, 0.03f, 0.5f, 0.8f, 0.8f, 8, hp = true, decay = 6f); tone(b, 0f, 0.08f, 140f, 80f, 0, 0.4f) }
            Sfx.PARRY -> {
                b = buf(0.7f)
                for (f in floatArrayOf(1568f, 2093f, 2637f)) tone(b, 0f, 0.55f, f, f, 0, 0.28f, decay = 4f)
                for (f in floatArrayOf(830f, 1990f, 3100f)) tone(b, 0f, 0.25f, f, f, 0, 0.22f, decay = 7f)
                noise(b, 0f, 0.05f, 0.7f, 0.9f, 0.9f, 9, hp = true, decay = 5f)
                tone(b, 0.06f, 0.1f, 2093f, 3136f, 3, 0.2f); tone(b, 0.12f, 0.12f, 2637f, 3951f, 3, 0.18f)
            }
            Sfx.GUARD_BREAK -> { b = buf(0.5f); noise(b, 0f, 0.3f, 0.7f, 0.5f, 0.1f, 10, decay = 3f); tone(b, 0f, 0.4f, 130f, 45f, 2, 0.5f, decay = 4f) }
            Sfx.ROLL -> { b = buf(0.3f); noise(b, 0f, 0.26f, 0.32f, 0.1f, 0.4f, 11, att = 0.05f, bell = true) }
            Sfx.HEAL -> { b = buf(0.6f); val n = intArrayOf(72, 76, 79, 84, 88); for ((i, m) in n.withIndex()) tone(b, i * 0.07f, 0.28f, midi(m), midi(m), 3, 0.28f, decay = 3.5f) }
            Sfx.PICKUP -> { b = buf(0.2f); tone(b, 0f, 0.06f, 784f, 784f, 1, 0.25f, decay = 2f); tone(b, 0.06f, 0.12f, 1175f, 1175f, 1, 0.25f, decay = 3f) }
            Sfx.CHEST -> {
                b = buf(0.9f); tone(b, 0f, 0.25f, 110f, 190f, 2, 0.25f, decay = 1.5f, vib = 0.05f); noise(b, 0f, 0.25f, 0.15f, 0.12f, 0.2f, 12, att = 0.05f, bell = true)
                for ((i, m) in intArrayOf(76, 79, 84, 88).withIndex()) tone(b, 0.28f + i * 0.08f, 0.35f, midi(m), midi(m), 3, 0.25f, decay = 3f)
            }
            Sfx.DOOR -> { b = buf(1.0f); noise(b, 0f, 0.95f, 0.5f, 0.06f, 0.08f, 13, att = 0.1f, bell = true); tone(b, 0f, 0.9f, 55f, 48f, 2, 0.3f, att = 0.1f, decay = 0.8f) }
            Sfx.QUEST -> { b = buf(1.1f); val n = intArrayOf(72, 76, 79, 84); for ((i, m) in n.withIndex()) { tone(b, i * 0.14f, 0.5f, midi(m), midi(m), 1, 0.18f, decay = 2f); tone(b, i * 0.14f, 0.5f, midi(m), midi(m), 3, 0.25f, decay = 2f) }; tone(b, 0.56f, 0.5f, midi(88), midi(88), 3, 0.25f, decay = 2f) }
            Sfx.LEVEL -> { b = buf(0.9f); for ((i, m) in intArrayOf(60, 64, 67, 72, 76, 79, 84).withIndex()) tone(b, i * 0.07f, 0.3f, midi(m), midi(m), 3, 0.25f, decay = 3f) }
            Sfx.ENEMY_DIE -> { b = buf(0.4f); noise(b, 0f, 0.14f, 0.5f, 0.6f, 0.2f, 14, decay = 4f); tone(b, 0f, 0.3f, 420f, 70f, 1, 0.3f, decay = 3f) }
            Sfx.BOSS_ROAR -> { b = buf(1.4f); tone(b, 0f, 1.3f, 95f, 45f, 2, 0.5f, att = 0.08f, decay = 1.5f, vib = 0.12f); noise(b, 0f, 1.2f, 0.4f, 0.1f, 0.05f, 15, att = 0.1f, bell = true) }
            Sfx.SHOOT -> { b = buf(0.25f); tone(b, 0f, 0.2f, 640f, 180f, 3, 0.4f, decay = 4f); noise(b, 0f, 0.03f, 0.4f, 0.8f, 0.8f, 16, hp = true) }
            Sfx.MAGIC -> { b = buf(0.6f); tone(b, 0f, 0.5f, 700f, 1500f, 0, 0.3f, decay = 2f, vib = 0.04f); tone(b, 0.05f, 0.45f, 1050f, 2250f, 0, 0.18f, decay = 2f) }
            Sfx.STEP -> { b = buf(0.06f); noise(b, 0f, 0.04f, 0.2f, 0.25f, 0.15f, 17, decay = 6f) }
            Sfx.BONFIRE -> { b = buf(0.9f); for (i in 0 until 12) noise(b, i * 0.05f, 0.04f, 0.3f, 0.7f, 0.5f, 100 + i, hp = true, decay = 6f); tone(b, 0f, 0.8f, 220f, 330f, 0, 0.22f, att = 0.05f, decay = 2f); tone(b, 0.2f, 0.6f, 330f, 440f, 0, 0.15f, decay = 2f) }
            Sfx.DEATH -> { b = buf(1.5f); for ((i, m) in intArrayOf(69, 65, 62, 57).withIndex()) tone(b, i * 0.28f, 0.7f, midi(m), midi(m) * 0.97f, 3, 0.3f, decay = 2f) }
            Sfx.SLAM -> { b = buf(0.8f); noise(b, 0f, 0.5f, 0.8f, 0.4f, 0.04f, 18, decay = 2.5f); tone(b, 0f, 0.7f, 75f, 28f, 0, 0.95f, decay = 3f) }
            Sfx.SPIT -> { b = buf(0.2f); tone(b, 0f, 0.14f, 300f, 800f, 0, 0.35f, decay = 3f); noise(b, 0f, 0.06f, 0.3f, 0.5f, 0.3f, 19) }
            Sfx.LOCK -> { b = buf(0.1f); tone(b, 0f, 0.03f, 1200f, 1200f, 3, 0.3f); tone(b, 0.035f, 0.04f, 1800f, 1800f, 3, 0.3f) }
            Sfx.COIN -> { b = buf(0.3f); tone(b, 0f, 0.06f, 988f, 988f, 1, 0.22f, decay = 2f); tone(b, 0.06f, 0.2f, 1319f, 1319f, 1, 0.22f, decay = 4f) }
            Sfx.STUN -> { b = buf(0.5f); for (i in 0 until 6) tone(b, i * 0.07f, 0.07f, if (i % 2 == 0) 700f else 950f, if (i % 2 == 0) 700f else 950f, 3, 0.25f, decay = 1f) }
            Sfx.WARN -> { b = buf(0.3f); tone(b, 0f, 0.08f, 440f, 440f, 1, 0.3f, decay = 1f); tone(b, 0.12f, 0.08f, 440f, 440f, 1, 0.3f, decay = 1f) }
            Sfx.EXPLODE -> { b = buf(0.8f); noise(b, 0f, 0.6f, 0.9f, 0.5f, 0.05f, 20, decay = 3f); tone(b, 0f, 0.5f, 90f, 30f, 0, 0.7f, decay = 3f) }
            Sfx.SWITCH -> { b = buf(0.25f); noise(b, 0f, 0.02f, 0.5f, 0.9f, 0.9f, 21, hp = true, decay = 3f); tone(b, 0.02f, 0.18f, 523f, 784f, 3, 0.3f, decay = 3f) }
            Sfx.EQUIP -> { b = buf(0.2f); noise(b, 0f, 0.03f, 0.4f, 0.8f, 0.8f, 22, hp = true, decay = 5f); noise(b, 0.07f, 0.03f, 0.4f, 0.8f, 0.8f, 23, hp = true, decay = 5f); tone(b, 0.07f, 0.1f, 700f, 700f, 0, 0.12f, decay = 5f) }
            Sfx.MENU_OPEN -> { b = buf(0.2f); tone(b, 0f, 0.15f, 400f, 800f, 3, 0.28f, decay = 2f) }
            Sfx.DENIED -> { b = buf(0.3f); tone(b, 0f, 0.1f, 150f, 150f, 1, 0.3f, decay = 1f); tone(b, 0.12f, 0.12f, 120f, 120f, 1, 0.3f, decay = 1.5f) }
            Sfx.BOSS_INTRO -> { b = buf(1.6f); for (i in 0 until 3) { tone(b, i * 0.4f, 0.4f, 80f, 35f, 0, 0.8f, decay = 4f); noise(b, i * 0.4f, 0.1f, 0.4f, 0.4f, 0.1f, 30 + i, decay = 5f) }; tone(b, 1.0f, 0.55f, 100f, 50f, 2, 0.5f, att = 0.05f, decay = 1.5f, vib = 0.1f) }
            Sfx.SPECIAL -> { b = buf(0.7f); tone(b, 0f, 0.5f, 200f, 1200f, 2, 0.3f, decay = 1.5f); noise(b, 0f, 0.5f, 0.5f, 0.1f, 0.9f, 24, att = 0.2f, bell = true) }
            Sfx.LEAP -> { b = buf(0.4f); noise(b, 0f, 0.35f, 0.45f, 0.1f, 0.5f, 25, att = 0.08f, bell = true); tone(b, 0f, 0.3f, 160f, 400f, 3, 0.2f, decay = 2f) }
            Sfx.ARROW_HIT -> { b = buf(0.2f); noise(b, 0f, 0.04f, 0.5f, 0.6f, 0.3f, 26, decay = 4f); tone(b, 0f, 0.1f, 260f, 120f, 0, 0.4f, decay = 5f) }
            else -> b = buf(0.05f)
        }
        return pcm(b)
    }

    // ------------------------------------------------------------------ música

    private class MDef(
        val bpm: Int, val bars: Int, val root: Int, val scale: IntArray, val prog: IntArray, val seed: Int,
        val lead: Int = 3, val leadDensity: Float = 0.6f, val arp: Boolean = true, val drums: Int = 0, val pad: Float = 0.12f,
        val bass: Float = 0.2f, val leadVol: Float = 0.16f, val octave: Int = 12, val pings: Boolean = false
    )

    private val MAJ = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MIN = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val DOR = intArrayOf(0, 2, 3, 5, 7, 9, 10)
    private val PHR = intArrayOf(0, 1, 3, 5, 7, 8, 10)
    private val LYD = intArrayOf(0, 2, 4, 6, 7, 9, 11)

    private fun def(id: Int): MDef = when (id) {
        Mus.TITLE -> MDef(84, 8, 57, MIN, intArrayOf(0, 5, 3, 4, 0, 5, 3, 4), 11, lead = 3, leadDensity = 0.5f, drums = 1, leadVol = 0.2f)
        Mus.VILLAGE -> MDef(96, 8, 60, MAJ, intArrayOf(0, 3, 4, 0, 5, 3, 4, 0), 21, lead = 3, leadDensity = 0.65f, drums = 1, pad = 0.1f)
        Mus.CAMPINA -> MDef(104, 8, 62, LYD, intArrayOf(0, 4, 5, 3, 0, 4, 1, 3), 31, lead = 0, leadDensity = 0.7f, drums = 1, leadVol = 0.2f)
        Mus.FOREST -> MDef(78, 8, 57, DOR, intArrayOf(0, 6, 3, 6, 0, 6, 4, 3), 41, lead = 3, leadDensity = 0.45f, drums = 0, pad = 0.16f, pings = true)
        Mus.LAKE -> MDef(70, 8, 64, MAJ, intArrayOf(0, 5, 3, 4, 0, 5, 1, 4), 51, lead = 0, leadDensity = 0.4f, drums = 0, pad = 0.15f, arp = true, leadVol = 0.2f)
        Mus.RUINS -> MDef(76, 8, 55, PHR, intArrayOf(0, 1, 0, 6, 0, 1, 4, 0), 61, lead = 4, leadDensity = 0.4f, drums = 2, pad = 0.15f, leadVol = 0.13f)
        Mus.CAVE -> MDef(60, 8, 50, MIN, intArrayOf(0, 0, 5, 5, 0, 0, 6, 4), 71, lead = 3, leadDensity = 0.25f, arp = false, drums = 0, pad = 0.2f, bass = 0.26f, leadVol = 0.12f, pings = true)
        Mus.PEAK -> MDef(108, 8, 52, MIN, intArrayOf(0, 5, 6, 4, 0, 5, 6, 4), 81, lead = 1, leadDensity = 0.6f, drums = 2, leadVol = 0.13f)
        Mus.TEMPLE -> MDef(64, 8, 53, PHR, intArrayOf(0, 0, 1, 1, 0, 5, 1, 0), 91, lead = 4, leadDensity = 0.3f, arp = false, drums = 0, pad = 0.22f, bass = 0.26f, leadVol = 0.12f, pings = true)
        Mus.BOSS -> MDef(142, 8, 50, MIN, intArrayOf(0, 5, 3, 4, 0, 5, 6, 4), 101, lead = 1, leadDensity = 0.8f, drums = 3, leadVol = 0.14f, bass = 0.26f)
        Mus.FINAL -> MDef(150, 8, 48, PHR, intArrayOf(0, 1, 0, 6, 0, 1, 5, 4), 111, lead = 2, leadDensity = 0.85f, drums = 3, leadVol = 0.12f, bass = 0.28f, pad = 0.16f)
        else -> MDef(80, 8, 60, MAJ, intArrayOf(0, 4, 5, 3, 0, 4, 3, 0), 121, lead = 3, leadDensity = 0.5f, drums = 1, pad = 0.14f)
    }

    private fun degMidi(root: Int, scale: IntArray, deg: Int): Int {
        val oct = Math.floorDiv(deg, scale.size); val d = Math.floorMod(deg, scale.size)
        return root + 12 * oct + scale[d]
    }

    fun music(id: Int): ShortArray {
        val d = def(id)
        val beat = 60f / d.bpm
        val barLen = beat * 4f
        val total = barLen * d.bars
        val b = FloatArray((total * RATE).toInt())
        val rnd = Random(d.seed)
        // motivo de 2 compassos repetido com variações
        val motif = IntArray(16) { if (rnd.nextFloat() < d.leadDensity) rnd.nextInt(-2, 5) else Int.MIN_VALUE }
        var cur = 4
        for (bar in 0 until d.bars) {
            val t0 = bar * barLen
            val chordDeg = d.prog[bar % d.prog.size]
            val c0 = degMidi(d.root, d.scale, chordDeg)
            val c1 = degMidi(d.root, d.scale, chordDeg + 2)
            val c2 = degMidi(d.root, d.scale, chordDeg + 4)
            // pad
            for (m in intArrayOf(c0, c1, c2)) tone(b, t0, barLen * 0.98f, midi(m), midi(m), 3, d.pad, att = barLen * 0.25f, decay = 0.7f)
            // baixo
            val bassM = c0 - 12
            for (k in intArrayOf(0, 2)) tone(b, t0 + k * beat, beat * 1.8f, midi(bassM), midi(bassM), 3, d.bass, att = 0.01f, decay = 1.6f)
            if (d.drums >= 2) for (k in intArrayOf(1, 3)) tone(b, t0 + (k + 0.5f) * beat, beat * 0.4f, midi(bassM + 7), midi(bassM + 7), 3, d.bass * 0.5f, decay = 3f)
            // arpejo
            if (d.arp) {
                val notes = intArrayOf(c0 + 12, c1 + 12, c2 + 12, c1 + 12)
                for (i in 0 until 8) tone(b, t0 + i * beat * 0.5f, beat * 0.5f, midi(notes[i % 4]), midi(notes[i % 4]), 3, 0.05f, decay = 4f)
            }
            // melodia (motivo com variação a cada 4 compassos)
            val vary = (bar / 2) % 2 == 1
            val half = (bar % 2) * 8
            for (i in 0 until 8) {
                val mo = motif[half + i]
                if (mo == Int.MIN_VALUE) continue
                val step = if (vary && i % 3 == 0) mo + 1 else mo
                cur = (chordDeg + 7 + step).coerceIn(chordDeg + 2, chordDeg + 12)
                val m = degMidi(d.root, d.scale, cur)
                tone(b, t0 + i * beat * 0.5f, beat * (if (i % 4 == 0) 0.9f else 0.45f), midi(m), midi(m), d.lead, d.leadVol, att = 0.01f, decay = 2.6f, vib = if (d.lead == 3 || d.lead == 0) 0.004f else 0f)
            }
            if (d.pings && bar % 2 == 1) {
                val m = degMidi(d.root, d.scale, chordDeg + 9 + rnd.nextInt(0, 3))
                tone(b, t0 + beat * (1 + rnd.nextInt(0, 3)), beat * 2.5f, midi(m), midi(m), 0, 0.08f, decay = 3f)
            }
            // bateria
            when (d.drums) {
                1 -> for (i in 0 until 4) { if (i % 2 == 0) tone(b, t0 + i * beat, 0.12f, 110f, 45f, 0, 0.35f, decay = 5f) else noise(b, t0 + i * beat, 0.07f, 0.12f, 0.9f, 0.9f, 3 + bar, hp = true, decay = 6f) }
                2 -> for (i in 0 until 8) {
                    val tt = t0 + i * beat * 0.5f
                    if (i % 4 == 0) tone(b, tt, 0.14f, 120f, 42f, 0, 0.4f, decay = 5f)
                    if (i % 4 == 2) noise(b, tt, 0.09f, 0.22f, 0.8f, 0.8f, 5 + i, hp = true, decay = 5f)
                    if (i % 2 == 1) noise(b, tt, 0.03f, 0.07f, 0.95f, 0.95f, 9 + i, hp = true, decay = 6f)
                }
                3 -> for (i in 0 until 16) {
                    val tt = t0 + i * beat * 0.25f
                    if (i % 4 == 0) tone(b, tt, 0.12f, 125f, 40f, 0, 0.45f, decay = 5f)
                    if (i % 8 == 4) { noise(b, tt, 0.1f, 0.26f, 0.8f, 0.8f, 7 + i, hp = true, decay = 5f); tone(b, tt, 0.08f, 190f, 150f, 0, 0.12f, decay = 5f) }
                    if (i % 2 == 0) noise(b, tt, 0.03f, 0.08f, 0.95f, 0.95f, 11 + i, hp = true, decay = 6f)
                }
            }
        }
        return pcm(b, 0.85f)
    }
}
