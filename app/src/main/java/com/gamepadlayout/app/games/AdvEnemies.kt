package com.gamepadlayout.app.games

/** Comportamentos básicos. */
object Beh {
    const val AGGR = 0      // agressivo: salta sobre o jogador
    const val CHASER = 1    // perseguidor: persegue e golpeia, desvia de lado
    const val RANGED = 2    // a distância: mantém espaço e atira
    const val GUARD = 3     // defensivo: guarda a frente, só abre depois de golpe forte/aparo/costas
    const val FLEE = 4      // fugitivo: rouba e foge
    const val SPECIAL = 5   // especial: investidas, combos longos
    const val BOSS = 6
}

/** Tipos de ataque. */
object AK {
    const val ARC = 0; const val THRUST = 1; const val SLAM = 2; const val SPIN = 3; const val LEAP = 4; const val SHOT = 5
    const val WAVE = 6; const val CHARGE = 7; const val SUMMON = 8; const val SPIKES = 9; const val ORBS = 10
}

/**
 * Um ataque: wind = preparo (telegrafado), act = janela de dano, rec = recuperação (vulnerável).
 * parry = pode ser aparado (brilho amarelo); senão brilho vermelho (role!). chain = índice do próximo ataque encadeado (-1 nenhum).
 */
class AtkDef(
    val an: Int, val wind: Float, val act: Float, val rec: Float, val reach: Float, val dmg: Float, val parry: Boolean, val kind: Int,
    val minD: Float, val maxD: Float, val phase: Int = 0, val knock: Float = 4f, val arc: Float = 1.7f, val rad: Float = 2.5f,
    val chain: Int = -1, val chainP: Float = 0f, val w: Int = 1
) {
    val total get() = wind + act + rec
}

class EDef(
    val kind: Int, val name: String, val hp: Float, val speed: Float, val sc: Float, val poise: Float, val beh: Int, val gems: Int,
    val atks: List<AtkDef>, val weapon: String = "", val shield: Boolean = false, val sight: Float = 14f, val rad: Float = 0.7f,
    val boss: Boolean = false, val phases: FloatArray = floatArrayOf(), val mat: String = "", val matCh: Float = 0f, val title: String = "",
    val flying: Boolean = false, val training: Boolean = false, val boss0: Int = -1
)

object AdvEnemies {
    private fun a(an: Int, wind: Float, act: Float, rec: Float, reach: Float, dmg: Float, parry: Boolean, kind: Int, minD: Float, maxD: Float,
                  phase: Int = 0, knock: Float = 4f, arc: Float = 1.7f, rad: Float = 2.5f, chain: Int = -1, chainP: Float = 0f, w: Int = 1) =
        AtkDef(an, wind, act, rec, reach, dmg, parry, kind, minD, maxD, phase, knock, arc, rad, chain, chainP, w)

    const val SLIME = 0; const val COGU = 1; const val CASC = 2; const val FERR = 3; const val ARCHER = 4; const val HARE = 5
    const val FROG = 6; const val BAT = 7; const val STATUE = 8; const val ELITE = 9; const val TRAINER = 10; const val SLIMEB = 11
    const val MUSGRIM = 20; const val SENTINEL = 21; const val CAPTAIN = 22; const val CRYSTAL = 23; const val AUREL = 24

    val defs: Map<Int, EDef> = listOf(
        EDef(SLIME, "Gosma Verde", 28f, 2.4f, 1f, 8f, Beh.AGGR, 6, listOf(a(AN.E_LEAP, 0.5f, 0.35f, 0.7f, 2.2f, 8f, true, AK.LEAP, 0f, 5f, rad = 1.4f)), sight = 10f, rad = 0.8f, mat = "gosma", matCh = 0.5f),
        EDef(SLIMEB, "Gosma Azul", 40f, 3.0f, 1.1f, 10f, Beh.AGGR, 10, listOf(a(AN.E_LEAP, 0.42f, 0.35f, 0.6f, 2.4f, 11f, true, AK.LEAP, 0f, 5.5f, rad = 1.5f)), sight = 11f, rad = 0.9f, mat = "gosma", matCh = 0.7f),
        EDef(COGU, "Cogubruto", 60f, 2.6f, 1.15f, 25f, Beh.CHASER, 14, listOf(
            a(AN.E_OVER, 0.7f, 0.2f, 0.8f, 2.7f, 15f, true, AK.ARC, 0f, 3.1f, arc = 1.6f, w = 2),
            a(AN.E_SLAM, 0.85f, 0.25f, 1.0f, 2.8f, 12f, false, AK.SPIN, 0f, 2.8f, rad = 2.8f)
        ), weapon = "club", sight = 14f),
        EDef(CASC, "Cascarao", 75f, 2.0f, 1.2f, 40f, Beh.GUARD, 20, listOf(
            a(AN.E_THRUST, 0.65f, 0.2f, 0.9f, 3.2f, 17f, true, AK.THRUST, 0f, 3.5f, w = 2),
            a(AN.E_OVER, 0.85f, 0.2f, 1.1f, 2.6f, 14f, false, AK.ARC, 0f, 2.8f, arc = 1.6f)
        ), weapon = "ferrugem", shield = true, sight = 13f),
        EDef(FERR, "Ferrugento", 62f, 3.4f, 1.2f, 22f, Beh.CHASER, 18, listOf(
            a(AN.E_OVER, 0.45f, 0.18f, 0.55f, 2.8f, 12f, true, AK.ARC, 0f, 3.2f, arc = 1.7f, chain = 1, chainP = 0.45f, w = 2),
            a(AN.E_THRUST, 0.32f, 0.16f, 0.7f, 3.2f, 14f, true, AK.THRUST, 0f, 3.5f)
        ), weapon = "ferrugem", sight = 15f, mat = "minerio", matCh = 0.25f),
        EDef(ARCHER, "Arqueiro Musgo", 40f, 2.6f, 1.05f, 10f, Beh.RANGED, 16, listOf(
            a(AN.SHOOT, 0.95f, 0.1f, 0.8f, 20f, 10f, true, AK.SHOT, 5f, 18f, w = 3),
            a(AN.E_THRUST, 0.5f, 0.15f, 0.7f, 2.4f, 8f, true, AK.THRUST, 0f, 2.6f)
        ), weapon = "arco", sight = 18f),
        EDef(HARE, "Lebre Ladra", 14f, 4.6f, 1f, 5f, Beh.FLEE, 5, listOf(a(AN.E_LEAP, 0.3f, 0.25f, 0.5f, 1.8f, 0f, false, AK.LEAP, 0f, 2f, rad = 1.2f)), sight = 11f, rad = 0.6f),
        EDef(FROG, "Ra Ferrugem", 26f, 2.7f, 1f, 8f, Beh.AGGR, 9, listOf(a(AN.E_LEAP, 0.5f, 0.35f, 0.7f, 2.3f, 9f, true, AK.LEAP, 0f, 5f, rad = 1.4f)), sight = 11f, rad = 0.8f),
        EDef(BAT, "Morcego de Cristal", 16f, 4.2f, 1f, 6f, Beh.SPECIAL, 8, listOf(a(AN.E_LEAP, 0.5f, 0.4f, 0.8f, 1.6f, 7f, false, AK.CHARGE, 0f, 6f, rad = 1.2f)), sight = 14f, rad = 0.6f, flying = true),
        EDef(STATUE, "Guardiao de Pedra", 120f, 1.6f, 1.35f, 70f, Beh.GUARD, 40, listOf(
            a(AN.E_OVER, 0.9f, 0.2f, 1.2f, 3.1f, 22f, true, AK.ARC, 0f, 3.6f, arc = 1.7f, w = 2),
            a(AN.E_SLAM, 1.1f, 0.25f, 1.4f, 3.0f, 26f, false, AK.SLAM, 0f, 4f, rad = 2.6f)
        ), weapon = "machado", shield = true, sight = 12f, rad = 0.9f),
        EDef(ELITE, "Ferrugento Veterano", 150f, 3.2f, 1.4f, 60f, Beh.SPECIAL, 60, listOf(
            a(AN.E_OVER, 0.5f, 0.18f, 0.35f, 3.2f, 16f, true, AK.ARC, 0f, 3.6f, arc = 1.8f, chain = 1, chainP = 0.8f, w = 2),
            a(AN.E_THRUST, 0.3f, 0.16f, 0.35f, 3.4f, 16f, true, AK.THRUST, 0f, 3.8f, chain = 2, chainP = 0.6f),
            a(AN.E_SLAM, 0.6f, 0.22f, 1.1f, 3.4f, 24f, false, AK.SLAM, 0f, 4f, rad = 2.6f),
            a(AN.E_LEAP, 0.8f, 0.55f, 0.9f, 3.0f, 22f, false, AK.CHARGE, 5f, 14f, rad = 1.5f)
        ), weapon = "ferrugem", sight = 16f, mat = "minerio", matCh = 0.6f),
        EDef(TRAINER, "Instrutor", 9999f, 1.8f, 1.2f, 99f, Beh.CHASER, 0, listOf(
            a(AN.E_OVER, 1.0f, 0.25f, 1.4f, 2.8f, 4f, true, AK.ARC, 0f, 3.2f, arc = 1.7f, knock = 2f)
        ), weapon = "galho", shield = true, sight = 12f, training = true),
        // chefes
        EDef(MUSGRIM, "Musgrim, o Troll", 420f, 2.6f, 1.75f, 120f, Beh.BOSS, 250, listOf(
            a(AN.E_OVER, 0.85f, 0.22f, 1.0f, 3.8f, 22f, true, AK.ARC, 0f, 4.4f, arc = 1.9f, w = 3),
            a(AN.E_SLAM, 1.0f, 0.3f, 1.2f, 3.0f, 20f, false, AK.SPIN, 0f, 4.6f, rad = 4.4f, w = 2),
            a(AN.E_SLAM, 1.1f, 0.25f, 1.3f, 3.4f, 28f, false, AK.SLAM, 0f, 5.2f, rad = 3.2f, w = 2),
            a(AN.E_LEAP, 0.9f, 0.5f, 1.2f, 3.0f, 24f, false, AK.LEAP, 5f, 14f, rad = 3.2f, w = 2),
            a(AN.ROAR, 0.9f, 0.4f, 1.0f, 3f, 0f, false, AK.SUMMON, 0f, 30f, phase = 1, w = 1),
            a(AN.E_OVER, 0.55f, 0.2f, 0.4f, 3.8f, 18f, true, AK.ARC, 0f, 4.4f, phase = 1, arc = 1.9f, chain = 0, chainP = 0.5f, w = 2)
        ), sight = 22f, rad = 1.2f, boss = true, phases = floatArrayOf(0.5f), title = "Guardiao da Floresta", boss0 = 0),
        EDef(SENTINEL, "Sentinela Rachada", 520f, 2.4f, 1.9f, 140f, Beh.BOSS, 300, listOf(
            a(AN.E_THRUST, 0.8f, 0.2f, 1.0f, 4.6f, 24f, true, AK.THRUST, 0f, 5.2f, w = 3),
            a(AN.E_LEAP, 1.0f, 0.6f, 1.4f, 3f, 28f, false, AK.CHARGE, 6f, 22f, w = 2),
            a(AN.E_OVER, 0.85f, 0.2f, 1.1f, 4.0f, 24f, true, AK.ARC, 0f, 4.6f, arc = 1.8f, w = 3),
            a(AN.CAST, 1.0f, 0.3f, 1.0f, 3f, 14f, true, AK.ORBS, 4f, 22f, phase = 1, w = 2),
            a(AN.E_SLAM, 1.0f, 0.25f, 1.4f, 3.4f, 28f, false, AK.SLAM, 0f, 5f, phase = 2, rad = 3.6f, w = 2),
            a(AN.E_LEAP, 0.8f, 0.5f, 0.5f, 3f, 28f, false, AK.CHARGE, 6f, 22f, phase = 2, chain = 1, chainP = 0.7f)
        ), sight = 24f, rad = 1.3f, boss = true, phases = floatArrayOf(0.66f, 0.33f), title = "Memoria das Ruinas", boss0 = 1),
        EDef(CAPTAIN, "Capitao Ferrugem", 640f, 3.2f, 1.75f, 130f, Beh.BOSS, 350, listOf(
            a(AN.E_OVER, 0.55f, 0.18f, 0.3f, 3.9f, 20f, true, AK.ARC, 0f, 4.4f, arc = 1.8f, chain = 1, chainP = 1f, w = 3),
            a(AN.E_THRUST, 0.35f, 0.18f, 0.3f, 4.2f, 20f, true, AK.THRUST, 0f, 4.6f, chain = 2, chainP = 0.55f),
            a(AN.E_SLAM, 0.7f, 0.25f, 1.1f, 3.2f, 30f, false, AK.SLAM, 0f, 4.4f, rad = 2.8f),
            a(AN.E_SPIN, 0.9f, 0.9f, 0.9f, 4.6f, 18f, false, AK.SPIN, 0f, 5.2f, rad = 4.6f, w = 2),
            a(AN.E_LEAP, 0.9f, 0.5f, 1.0f, 3f, 28f, false, AK.LEAP, 5f, 16f, rad = 3.4f, w = 2),
            a(AN.E_SPIN, 0.5f, 1.2f, 0.7f, 4.8f, 20f, false, AK.SPIN, 0f, 5.4f, phase = 1, rad = 4.8f, w = 2)
        ), weapon = "ferrugem", sight = 24f, rad = 1.2f, boss = true, phases = floatArrayOf(0.5f), title = "Guardiao do Pico", boss0 = 2),
        EDef(CRYSTAL, "Guarda-Cristal", 760f, 2.2f, 2.0f, 160f, Beh.BOSS, 400, listOf(
            a(AN.E_OVER, 0.9f, 0.2f, 1.1f, 4.0f, 26f, true, AK.ARC, 0f, 4.6f, arc = 1.8f, w = 3),
            a(AN.CAST, 1.0f, 1.2f, 1.2f, 14f, 20f, false, AK.SPIKES, 3f, 18f, w = 2),
            a(AN.E_SLAM, 1.0f, 0.25f, 1.3f, 3.6f, 30f, false, AK.SLAM, 0f, 5f, rad = 3.4f, w = 2),
            a(AN.CAST, 1.0f, 0.3f, 1.0f, 3f, 14f, true, AK.ORBS, 4f, 22f, phase = 1, w = 2),
            a(AN.E_SPIN, 0.8f, 0.3f, 1.3f, 4.4f, 22f, false, AK.SPIN, 0f, 5f, phase = 1, rad = 4.4f)
        ), sight = 22f, rad = 1.5f, boss = true, phases = floatArrayOf(0.5f), title = "Guardiao Opcional", boss0 = 3),
        EDef(AUREL, "Aurel, o Rei Ferrugem", 900f, 2.8f, 1.7f, 200f, Beh.BOSS, 600, listOf(
            a(AN.E_SPIN, 0.8f, 0.3f, 1.0f, 4.6f, 26f, true, AK.ARC, 0f, 5.0f, arc = 3.0f, w = 3),
            a(AN.E_OVER, 0.75f, 0.2f, 0.9f, 4.2f, 28f, true, AK.ARC, 0f, 4.8f, arc = 1.9f, chain = 0, chainP = 0.4f, w = 3),
            a(AN.E_SLAM, 1.0f, 0.25f, 1.3f, 3.4f, 30f, false, AK.SLAM, 0f, 5.2f, rad = 3.6f, w = 2),
            a(AN.CAST, 1.0f, 0.3f, 1.0f, 3f, 16f, true, AK.ORBS, 4f, 22f, phase = 1, w = 2),
            a(AN.CAST, 1.0f, 1.2f, 1.2f, 14f, 22f, false, AK.SPIKES, 3f, 18f, phase = 1, w = 2),
            a(AN.ROAR, 1.3f, 0.5f, 1.4f, 3f, 24f, false, AK.WAVE, 0f, 30f, phase = 2, rad = 13f, w = 2),
            a(AN.E_LEAP, 0.7f, 0.5f, 0.6f, 3f, 28f, false, AK.CHARGE, 6f, 16f, phase = 2, chain = 1, chainP = 0.6f, w = 2)
        ), sight = 26f, rad = 1.5f, boss = true, phases = floatArrayOf(0.66f, 0.33f), title = "O Ultimo Guardiao", boss0 = 4)
    ).associateBy { it.kind }

    /** Recompensa fixa do chefe (indice de arena 0..4). */
    class BossReward(val flag: String, val item: String, val gems: Int, val extra: String)
    val rewards = listOf(
        BossReward("bossG", "frag_g", 250, ""), BossReward("bossB", "frag_b", 300, "espelho"),
        BossReward("bossR", "frag_r", 350, "ferrugem"), BossReward("bossC", "vale", 400, "fruta"), BossReward("bossA", "aurora", 600, "")
    )
}

/** Estados da IA. */
object ES {
    const val IDLE = 0; const val CHASE = 1; const val WIND = 2; const val ACT = 3; const val REC = 4; const val HURT = 5
    const val STUN = 6; const val DEAD = 7; const val FLEE = 8; const val INTRO = 9; const val STRAFE = 10; const val RETREAT = 11
}

/** Inimigo em campo. */
class En(val def: EDef, val hx: Float, val hz: Float, val spawnIdx: Int) {
    var x = hx; var z = hz; var y = 0f
    var yaw = 0f
    var hp = def.hp
    var poise = def.poise
    var st = ES.IDLE
    var t = 0f            // tempo no estado atual
    var atk = -1          // ataque em curso
    var lastAtk = -1
    var hitDone = false   // este golpe já acertou/foi tratado
    var phase = 0
    var alive = true
    var flash = 0f        // brilho de dano
    var stunT = 0f
    var hurtT = 0f
    var poiseLock = 0f    // tempo sem regenerar poise
    var aggro = false
    var wander = 0f
    var wx = hx; var wz = hz
    var vx = 0f; var vz = 0f
    var tx = 0f; var tz = 0f // destino do salto/investida
    var guardUp = false
    var strafeDir = 1f
    var cd = 0f           // tempo de espera até o próximo ataque
    var bodyHit = 0f
    var stolen = 0
    var introDone = false
    var chainNext = -1
    var deadT = 0f
    var hits = 0          // golpes recebidos (treino)
    var hopT = 0f
    var parried = false
    var actTimer = 0f
    var spikesDone = 0
    var seen = false
    var parryImmune = 0f
    var introDur = 2.4f
    var phi2 = 0f
    val anim = AnimState()
    var animId = AN.IDLE
    var animT = 0f
    var phi = 0f
    var amt = 0f
    var look = 0f
    val boss get() = def.boss
    val kind get() = def.kind
}
