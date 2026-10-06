package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class Mode { TITLE, STORY, PLAY, DIALOG, MENU, INV, MAP, SETTINGS, DEAD, ENDING }

internal class Opt(val label: String, val desc: String = "", val ok: Boolean = true, val act: () -> Unit)

internal class Proj(var x: Float, var y: Float, var z: Float, var vx: Float, var vz: Float, var dmg: Float, var life: Float, val kind: Int, val parry: Boolean) {
    var alive = true
    var mine = false   // disparado/devolvido pelo jogador
    var src: En? = null
    var reflected = false
}

internal class Part(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, var life: Float, val color: Long, val size: Float = 1f)
internal class Spike(val x: Float, val z: Float, var t: Float, val dmg: Float) { var hit = false }
internal class Ring(val x: Float, val z: Float, var r: Float, val maxR: Float, val dmg: Float) { var hit = false }
internal class Bomb(var x: Float, var y: Float, var z: Float, var vx: Float, var vz: Float, var t: Float)
internal class Drop(val x: Float, val z: Float, val kind: Int, val amount: Int, val item: String = "") { var life = 40f; var t = 0f }
internal class Inter(val type: Int, val idx: Int, val x: Float, val z: Float)

internal class NpcRt(val d: NpcDef) {
    var x = d.x; var z = d.z; var yaw = 0f
    val anim = AnimState()
    var an = d.anim
    var phi = 0f; var amt = 0f
    var tx = d.x; var tz = d.z; var wait = 1f
    var look = 0f
    var talkT = 0f
    var emoAn = -1
}

/** Ações configuráveis. */
internal object Act {
    const val ATK = 0; const val HEAVY = 1; const val SPECIAL = 2; const val ITEM = 3; const val ROLL = 4; const val GUARD = 5
    const val LOCK = 6; const val MAP = 7; const val BAG = 8; const val INTERACT = 9; const val CAMRESET = 10
    const val COUNT = 11
    val names = arrayOf("ATAQUE", "GOLPE FORTE", "ESPECIAL", "USAR ITEM", "ROLAR", "DEFENDER/APARAR", "TRAVAR MIRA", "MAPA", "MOCHILA", "INTERAGIR", "CENTRAR CAMERA")
    val presets = listOf("PADRAO", "GATILHOS")
    fun preset(i: Int): IntArray = when (i) {
        1 -> intArrayOf(Btn.R1, Btn.R2, Btn.Y, Btn.X, Btn.A, Btn.L2, Btn.L1, Btn.SELECT, Btn.DUP, Btn.A, Btn.L3)
        else -> intArrayOf(Btn.A, Btn.Y, Btn.R2, Btn.X, Btn.R1, Btn.L2, Btn.L1, Btn.SELECT, Btn.DUP, Btn.A, Btn.L3)
    }
}

/** Estados do jogador. */
internal object PA {
    const val FREE = 0; const val ATK = 1; const val CHARGE = 2; const val HEAVY = 3; const val SPIN = 4; const val ROLL = 5; const val BACK = 6
    const val PARRY = 7; const val RIPOSTE = 8; const val HURT = 9; const val STUN = 10; const val DRINK = 11; const val PICK = 12; const val OPEN = 13
    const val SHOOT = 14; const val CAST = 15; const val DEAD = 16; const val SPECIAL = 17; const val THROW = 18
}

/**
 * "Vale de Aurora": aventura 3D de mundo aberto pequeno, no espírito dos jogos de 1998 (mira travada, espada e escudo),
 * com combate completo (combo, golpe forte, rolar, aparar), chefes de várias fases, missões, inventário e história própria.
 */
class AdventureGame : MiniGame(
    "vale", "Vale de Aurora",
    "Aventura 3D: explore o vale, ajude a aldeia, aprenda a aparar golpes, enfrente os guardiões e restaure o Coração de Cristal.",
    "Analógicos: mover/câmera · A: atacar/falar · Y: golpe forte (segure) · R2: especial · L2: defender (aperte na hora: aparar) · R1: rolar · L1: travar mira · X: item · D-pad cima: mochila · baixo: seiva · esquerda/direita: trocar arma · R3: trocar item · SELECT: mapa"
), AdvCtx {
    override val persistent = true
    override val usesAudio = true
    override val crtEffect = false
    override val dpadButtons = true

    internal val r3 = R3()
    internal val rnd = Random(System.nanoTime())
    internal var mode = Mode.TITLE
    internal var time = 0f

    // ---- progresso / inventário
    internal val own = HashMap<String, Int>()
    internal val wlv = HashMap<String, Int>()
    internal val flags = HashSet<String>()
    internal val counters = HashMap<String, Int>()
    internal var gems = 0; internal var lvVig = 0; internal var lvFor = 0; internal var fruits = 0
    internal var flaskMax = 3; internal var flask = 3
    internal var eqW = "galho"; internal var eqS = "tabua"; internal var eqC = ""
    internal var quickIdx = 0
    internal var lastBf = 0
    internal var playSec = 0f
    internal var deathGems = 0; internal var deathX = 0f; internal var deathZ = 0f
    internal val mapSeen = BooleanArray(33 * 33)
    internal var hasSave = false

    // ---- configurações (salvas junto)
    internal var sens = 1.0f; internal var invertY = false; internal var lockHold = false; internal var parryDiff = 1
    internal var shakeOn = true; internal var autoSwitch = true; internal var showHints = true; internal var lodBias = 1f
    internal val bind = Act.preset(0)
    internal var presetIdx = 0

    // ---- jogador
    internal var px = 100f; internal var py = 0f; internal var pz = 146f; internal var pyaw = PI.toFloat()
    internal var hp = 100f; internal var stam = 100f; internal var stamDelay = 0f
    internal var pAct = PA.FREE; internal var pT = 0f; internal var pDur = 0.4f; internal var pHitAt = 0.4f; internal var pHitDone = false
    internal var combo = 0; internal var comboQueued = false; internal var comboWin = 0f; internal var chargeT = 0f; internal var heavyMul = 1f
    internal var guardHeld = false; internal var parryT = 0f; internal var parryRec = 0f; internal var parryCd = 0f
    internal var iT = 0f; internal var hurtT = 0f
    internal var riposteT = 0f; internal var riposteTarget: En? = null
    internal var buffT = 0f
    internal var pvx = 0f; internal var pvz = 0f
    internal var rollX = 0f; internal var rollZ = 0f; internal var rollSpd = 0f
    internal var specialKind = 0
    internal var pwalk = 0f; internal var pamt = 0f
    internal val pAnim = AnimState()
    internal var lockT: En? = null
    internal var lockPrev = false
    internal var flickReady = true
    internal var atkHeld = 0f; internal var heavyHeld = 0f
    internal var camYaw = PI.toFloat(); internal var camPitch = 0.30f; internal var camDist = 8.5f
    internal var camX = 0f; internal var camY = 0f; internal var camZ = 0f
    internal var camYawSmooth = 0f
    internal var wasMoving = false
    internal var swingFlip = 1f
    internal var trailT = 0f
    internal var heldAttackKind = 0
    internal var parryFx = 0f
    internal var curInter: Inter? = null
    internal var pendingAct: (() -> Unit)? = null
    internal var pendingQuick = ""
    internal var lastCombatTip = 0f

    // ---- mundo vivo
    internal val ens = ArrayList<En>()
    internal val npcs = ArrayList<NpcRt>()
    internal val projs = ArrayList<Proj>()
    internal val parts = ArrayList<Part>()
    internal val spikes = ArrayList<Spike>()
    internal val rings = ArrayList<Ring>()
    internal val bombs = ArrayList<Bomb>()
    internal val drops = ArrayList<Drop>()
    internal val critters = ArrayList<AdvWorld.Critter>()
    internal val herbs = AdvWorld.collectHerbSpots()
    internal var engaged: En? = null
    internal val arenaBoss = arrayOfNulls<En>(5)
    internal var zone = Z.VILA
    internal var zoneBanner = 0f; internal var zoneName = ""

    // ---- interface
    internal var toast = ""; internal var toastT = 0f
    internal var toastQ = ArrayList<String>()
    internal var banner = ""; internal var bannerSub = ""; internal var bannerT = 0f
    internal var hitStop = 0f; internal var shake = 0f; internal var shakeAmp = 0f
    internal var flashT = 0f; internal var flashCol = 0L
    internal var prompt = ""
    internal var popup = ""; internal var popupT = 0f; internal var popupCol = P.YELLOW
    internal var cursor = 0
    internal var menuTitle = ""; internal var menuOpts: List<Opt> = emptyList(); internal var menuBuild: (() -> List<Opt>)? = null
    internal var titleCur = 0; internal var confirmNew = false
    internal var pageIdx = 0; internal var pageT = 0f
    internal var convo: Convo? = null; internal var dlgIdx = 0; internal var dlgT = 0f; internal var dlgChoice = false; internal var dlgEnded = false
    internal var dlgNpc: NpcRt? = null
    internal var invTab = 0; internal var invCur = 0
    internal var remapPage = false; internal var remapCur = 0
    internal var setCur = 0; internal var setRemap = -1; internal var setFrom = Mode.TITLE
    internal var mapCursorX = 33f; internal var mapCursorZ = 50f
    internal var deadT = 0f
    internal var endCountdown = 0f
    internal var fade = 0f; internal var fadeDir = 0
    internal var fadeAction: (() -> Unit)? = null
    internal var tipT = 0f; internal var tipText = ""
    internal var invTipFlag = false
    internal var perf = 1f
    internal var lastTris = 0
    internal var sfxCd = FloatArray(Sfx.COUNT)
    internal var checkT = 0f
    internal var styleBlend = 0f

    // estilo atual (interpolado entre regiões)
    internal var sAmb = 0.44f; internal var sLr = 1f; internal var sLg = 1f; internal var sLb = 1f
    internal var sFog = 0xFFC4E4F4L; internal var sFogS = 70f; internal var sFogE = 150f; internal var sSky = 0xFF4F8BD0L

    init { reset() }

    // =================================================================== AdvCtx
    override fun has(id: String) = (own[id] ?: 0) > 0
    override fun count(id: String) = own[id] ?: 0
    override fun flag(f: String) = flags.contains(f)
    override fun setFlag(f: String) { flags.add(f); saveDirty = true }
    override fun give(id: String, n: Int) {
        own[id] = (own[id] ?: 0) + n
        if (id == "frasco") { own.remove("frasco"); flaskMax++; flask++; toast("Carga de seiva +1") }
        if (id == "flecha" && n > 0) { /* municao */ }
        saveDirty = true
        if (n > 0) snd(Sfx.PICKUP)
    }
    override fun take(id: String, n: Int) { val c = (own[id] ?: 0) - n; if (c <= 0) own.remove(id) else own[id] = c; saveDirty = true }
    override fun addGems(n: Int) { gems += n; saveDirty = true; if (n > 0) snd(Sfx.COIN) }
    override fun cnt(key: String) = counters[key] ?: 0
    override fun addCnt(key: String, n: Int) { counters[key] = (counters[key] ?: 0) + n; saveDirty = true }
    override fun toast(t: String) { say(t) }
    override fun heal() { restoreAll() }
    override fun shards() = listOf("frag_g", "frag_b", "frag_r").count { has(it) }
    override fun openShop(which: Int) { pendingMenu = { openShopMenu(which) } }
    override fun openForge() { pendingMenu = { openForgeMenu() } }
    internal var pendingMenu: (() -> Unit)? = null

    // =================================================================== utilidades
    internal fun dist(x: Float, z: Float, a: Float, b: Float) = sqrt((x - a) * (x - a) + (z - b) * (z - b))
    internal fun wrap(a0: Float): Float { var a = a0; while (a > PI) a -= 2f * PI.toFloat(); while (a < -PI) a += 2f * PI.toFloat(); return a }
    internal fun lerpAng(c: Float, t: Float, k: Float) = c + wrap(t - c) * k.coerceIn(0f, 1f)
    internal fun say(t: String, d: Float = 3f) { if (toastT > 0.1f && toast != t) { if (toastQ.size < 4 && !toastQ.contains(t)) toastQ.add(t) } else { toast = t; toastT = d } }
    internal fun snd(id: Int) {
        if (id < 0 || id >= Sfx.COUNT) return
        if (sfxCd[id] > 0f) return
        sfxCd[id] = if (id == Sfx.STEP) 0.18f else 0.04f
        if (sfxQueue.size < 24) sfxQueue.add(id)
    }
    internal fun popupMsg(t: String, c: Long = P.YELLOW) { popup = t; popupT = 0.9f; popupCol = c }
    internal fun showBanner(t: String, sub: String) { banner = t; bannerSub = sub; bannerT = 4.5f }
    internal fun giveTip(id: String) {
        if (!showHints || flags.contains(id)) return
        val t = AdvData.tips[id] ?: return
        flags.add(id); tipText = t; tipT = 6f
    }
    internal fun spark(x: Float, y: Float, z: Float, n: Int, color: Long, spd: Float = 3f, size: Float = 1f) {
        if (parts.size > 160) return
        for (i in 0 until n) parts.add(Part(x, y, z, (rnd.nextFloat() - 0.5f) * spd, rnd.nextFloat() * spd, (rnd.nextFloat() - 0.5f) * spd, 0.5f, color, size))
    }
    internal fun doShake(a: Float, t: Float) { if (shakeOn) { shake = max(shake, t); shakeAmp = max(shakeAmp, a) } }
    internal fun flash(c: Long, t: Float) { flashCol = c; flashT = t }

    internal fun maxHp() = 100f + lvVig * 12f + fruits * 10f + (if (eqC == "folha") 25f else 0f)
    internal fun wdef(): WDef = AdvData.weapons[eqW] ?: AdvData.weapons["galho"]!!
    internal fun sdef(): ShDef = AdvData.shields[eqS] ?: AdvData.shields["tabua"]!!
    internal fun wDmg() = wdef().dmg * (1f + 0.22f * (wlv[eqW] ?: 0)) * (1f + 0.07f * lvFor) * (if (eqC == "brasa") 1.15f else 1f) * (if (buffT > 0f) 1.3f else 1f)
    internal fun parryWindow(): Float {
        val base = when (parryDiff) { 0 -> 0.34f; 2 -> 0.14f; else -> 0.22f }
        return base + wdef().parryBonus + sdef().parry + (if (eqC == "vale") 0.05f else 0f)
    }
    internal fun stamRegen() = 34f * (if (eqC == "pena") 1.35f else 1f)
    internal fun hasBtn(a: Int, i: GameInput) = i.bHeld[bind[a]]
    internal fun pressBtn(a: Int, i: GameInput) = i.bPressed[bind[a]]
    internal fun relBtn(a: Int, i: GameInput) = i.bReleased[bind[a]]
    internal fun earn(n: Int) { val v = (n * (if (eqC == "trevo") 1.25f else 1f)).toInt(); gems += v; saveDirty = true }

    internal fun restoreAll() { hp = maxHp(); stam = 100f; flask = flaskMax }

    internal fun ownedWeapons() = AdvData.weaponOrder.filter { has(it) }
    internal fun quickList(): List<String> = AdvData.quickItems.filter { it == "seiva" || has(it) }
    internal fun quickItem(): String { val q = quickList(); if (q.isEmpty()) return "seiva"; return q[quickIdx.coerceIn(0, q.size - 1)] }

    internal fun isNew(id: String) = has(id) && !flags.contains("sn_$id") && id != "galho" && id != "tabua" && id != "seiva"

    // =================================================================== persistência
    internal var crash: String? = null
    private fun fail(w: String, e: Throwable) {
        val st = e.stackTrace.take(5).joinToString(" | ") { "${it.fileName}:${it.lineNumber}" }
        crash = "$w: ${e.javaClass.simpleName} ${e.message ?: ""} @ $st"
    }
    override fun reset() { try { crash = null; reset0() } catch (e: Throwable) { fail("reset", e) } }
    private fun reset0() {
        own.clear(); wlv.clear(); flags.clear(); counters.clear(); java.util.Arrays.fill(mapSeen, false)
        own["galho"] = 1; own["tabua"] = 1; own["seiva"] = 1
        eqW = "galho"; eqS = "tabua"; eqC = ""; quickIdx = 0
        gems = 0; lvVig = 0; lvFor = 0; fruits = 0; flaskMax = 3; flask = 3; lastBf = 0
        deathGems = 0; playSec = 0f; buffT = 0f
        flags.add("bf0")
        hasSave = false
        mode = Mode.TITLE; titleCur = 0; confirmNew = false
        score = 0; over = false
        spawnWorld(); placeAtBonfire()
        musicId = Mus.TITLE
    }

    override fun save(): String {
        fun m(h: Map<String, Int>) = h.entries.joinToString(",") { "${it.key}:${it.value}" }
        val seen = StringBuilder(); var acc = 0; var nb = 0
        for (b in mapSeen) { acc = acc shl 1 or (if (b) 1 else 0); nb++; if (nb == 4) { seen.append("0123456789abcdef"[acc]); acc = 0; nb = 0 } }
        if (nb > 0) { acc = acc shl (4 - nb); seen.append("0123456789abcdef"[acc]) }
        val kv = listOf(
            "g" to gems, "vig" to lvVig, "for" to lvFor, "fr" to fruits, "fm" to flaskMax, "w" to eqW, "s" to eqS, "c" to eqC.ifEmpty { "-" },
            "bf" to lastBf, "ps" to playSec.toInt(), "dg" to deathGems, "dx" to deathX.toInt(), "dz" to deathZ.toInt(), "qi" to quickIdx,
            "own" to m(own), "wl" to m(wlv), "fl" to flags.joinToString(","), "cn" to m(counters), "ms" to seen,
            "set" to listOf(sens, if (invertY) 1 else 0, if (lockHold) 1 else 0, parryDiff, if (shakeOn) 1 else 0, if (autoSwitch) 1 else 0, if (showHints) 1 else 0, presetIdx).joinToString(","),
            "bd" to bind.joinToString(",")
        )
        return "2;" + kv.joinToString(";") { "${it.first}=${it.second}" }
    }

    override fun load(data: String) {
        try {
            if (data.startsWith("2;")) loadV2(data) else loadV1(data)
        } catch (e: Exception) { return }
        try {
            hasSave = flags.contains("met") || playSec > 30f
            spawnWorld(); restoreAll(); placeAtBonfire()
            mode = Mode.TITLE; titleCur = 0; musicId = Mus.TITLE
        } catch (e: Throwable) { fail("load", e) }
    }

    private fun parseMap(s: String, into: HashMap<String, Int>) {
        into.clear()
        for (e in s.split(",")) if (e.isNotEmpty()) { val q = e.split(":"); if (q.size == 2) into[q[0]] = q[1].toIntOrNull() ?: 0 }
    }

    private fun loadV2(data: String) {
        val kv = HashMap<String, String>()
        for (p in data.substring(2).split(";")) { val i = p.indexOf('='); if (i > 0) kv[p.substring(0, i)] = p.substring(i + 1) }
        gems = kv["g"]?.toIntOrNull() ?: 0; lvVig = kv["vig"]?.toIntOrNull() ?: 0; lvFor = kv["for"]?.toIntOrNull() ?: 0
        fruits = kv["fr"]?.toIntOrNull() ?: 0; flaskMax = kv["fm"]?.toIntOrNull() ?: 3
        eqW = kv["w"] ?: "galho"; eqS = kv["s"] ?: "tabua"; eqC = (kv["c"] ?: "-").let { if (it == "-") "" else it }
        lastBf = (kv["bf"]?.toIntOrNull() ?: 0).coerceIn(0, AdvWorld.bonfires.size - 1)
        playSec = kv["ps"]?.toFloatOrNull() ?: 0f; deathGems = kv["dg"]?.toIntOrNull() ?: 0
        deathX = kv["dx"]?.toFloatOrNull() ?: 0f; deathZ = kv["dz"]?.toFloatOrNull() ?: 0f; quickIdx = kv["qi"]?.toIntOrNull() ?: 0
        parseMap(kv["own"] ?: "", own); parseMap(kv["wl"] ?: "", wlv); parseMap(kv["cn"] ?: "", counters)
        flags.clear(); for (f in (kv["fl"] ?: "").split(",")) if (f.isNotEmpty()) flags.add(f)
        val ms = kv["ms"] ?: ""
        java.util.Arrays.fill(mapSeen, false)
        for (i in mapSeen.indices) { val c = ms.getOrNull(i / 4) ?: break; val v = "0123456789abcdef".indexOf(c); if (v >= 0) mapSeen[i] = (v shr (3 - i % 4)) and 1 == 1 }
        val st = (kv["set"] ?: "").split(",")
        if (st.size >= 8) {
            sens = st[0].toFloatOrNull()?.coerceIn(0.4f, 2.2f) ?: 1f; invertY = st[1] == "1"; lockHold = st[2] == "1"
            parryDiff = st[3].toIntOrNull()?.coerceIn(0, 2) ?: 1; shakeOn = st[4] == "1"; autoSwitch = st[5] == "1"; showHints = st[6] == "1"; presetIdx = st[7].toIntOrNull() ?: 0
        }
        val bd = (kv["bd"] ?: "").split(",").mapNotNull { it.toIntOrNull() }
        if (bd.size == Act.COUNT) for (i in 0 until Act.COUNT) bind[i] = bd[i].coerceIn(0, Btn.COUNT - 1)
        if (bind[Act.BAG] == Btn.R3) bind[Act.BAG] = Btn.DUP
        if (!own.containsKey("galho")) own["galho"] = 1
        if (!has(eqW)) eqW = "galho"
        if (!has(eqS)) eqS = "tabua"
    }

    /** Salvamento do jogo anterior (v10): aproveita cacos, níveis e itens principais. */
    private fun loadV1(data: String) {
        val p = data.split(";")
        if (p.size < 16 || p[0] != "1") throw IllegalStateException()
        own.clear(); wlv.clear(); flags.clear(); counters.clear()
        gems = p[1].toInt(); lvVig = p[2].toInt(); lvFor = p[3].toInt(); flaskMax = p[4].toInt()
        eqW = p[5]; eqS = p[6]; eqC = if (p[7] == "-") "" else p[7]
        playSec = p[9].toFloat()
        for (e in p[13].split(",")) if (e.isNotEmpty()) { val q = e.split(":"); own[q[0]] = q[1].toInt() }
        for (e in p[14].split(",")) if (e.isNotEmpty()) { val q = e.split(":"); wlv[q[0]] = q[1].toInt() }
        flags.add("bf0"); flags.add("met")
        own["galho"] = 1
        if (!has(eqW) || AdvData.weapons[eqW] == null) eqW = "galho"
        if (!has(eqS) || AdvData.shields[eqS] == null) eqS = "tabua"
        lastBf = 0
    }

    // =================================================================== mundo
    internal fun spawnWorld() {
        ens.clear(); projs.clear(); spikes.clear(); rings.clear(); bombs.clear(); drops.clear(); parts.clear()
        lockT = null; engaged = null
        var idx = 0
        for (s in AdvWorld.spawns()) {
            val d = AdvEnemies.defs[s.kind] ?: continue
            val e = En(d, s.x, s.z, idx++)
            e.y = AdvWorld.hAt(s.x, s.z); e.yaw = rnd.nextFloat() * 6.28f
            if (d.training && flags.contains("tr_done")) { /* o instrutor continua lá para treinar */ }
            ens.add(e)
        }
        for (i in 0 until 5) spawnBoss(i)
        if (npcs.isEmpty()) for (n in AdvWorld.npcs) { val r = NpcRt(n); r.yaw = atan2(100f - n.x, 140f - n.z); npcs.add(r) }
        if (critters.isEmpty()) critters.addAll(AdvWorld.critters())
    }

    internal fun spawnBoss(i: Int) {
        arenaBoss[i] = null
        if (flags.contains(AdvEnemies.rewards[i].flag)) return
        val d = AdvEnemies.defs[20 + i] ?: return
        val a = AdvWorld.arenas[i]
        val e = En(d, a[0], a[1], 9000 + i)
        e.y = AdvWorld.hAt(a[0], a[1]); e.yaw = 0f
        ens.add(e); arenaBoss[i] = e
    }

    internal fun placeAtBonfire() {
        val b = AdvWorld.bonfires[lastBf.coerceIn(0, AdvWorld.bonfires.size - 1)]
        px = b[0] + 1.5f; pz = b[1] + 2.4f; py = AdvWorld.hAt(px, pz)
        pyaw = PI.toFloat(); camYaw = PI.toFloat()
        if (!walkable(px, pz, px, pz, 0.4f)) { px = b[0]; pz = b[1] + 3.2f }
    }

    internal fun doorOpen(d: DoorDef) = flags.contains("dr_${d.id}")

    internal fun walkable(x: Float, z: Float, fromX: Float, fromZ: Float, rad: Float, ignoreProps: Boolean = false): Boolean {
        if (x < 2f || z < 2f || x > AdvWorld.SIZE - 2f || z > AdvWorld.SIZE - 2f) return false
        val h = AdvWorld.hAt(x, z)
        if (h < AdvWorld.WATER - 0.1f) return false
        val step = max(0.05f, dist(x, z, fromX, fromZ))
        if ((h - AdvWorld.hAt(fromX, fromZ)) / step > 1.7f) return false
        for (d in AdvWorld.doors) if (!doorOpen(d)) {
            // porta como parede de 5 x 1,4 orientada por yaw
            val dx = x - d.x; val dz = z - d.z
            val c = cos(d.yaw); val s = sin(d.yaw)
            val lx = dx * c - dz * s; val lz = dx * s + dz * c
            if (abs(lx) < 2.6f + rad && abs(lz) < 0.8f + rad) return false
        }
        if (!ignoreProps) {
            if (AdvWorld.blockedByProps(x, z, rad)) return false
            for (b in AdvWorld.bonfires) if (dist(x, z, b[0], b[1]) < 1.0f + rad) return false
            for (n in npcs) if (n.d.rig < 100 && dist(x, z, n.x, n.z) < 0.8f + rad) return false
        }
        val eb = engaged
        if (eb != null && eb.alive && eb.spawnIdx >= 9000) {
            val a = AdvWorld.arenas[eb.spawnIdx - 9000]
            if (dist(x, z, a[0], a[1]) > AdvWorld.arenaR[eb.spawnIdx - 9000] + 1.5f) return false
        }
        return true
    }

    internal fun moveBy(x: Float, z: Float, dx: Float, dz: Float, rad: Float): FloatArray {
        var nx = x; var nz = z
        if (walkable(x + dx, z, x, z, rad)) nx = x + dx
        if (walkable(nx, z + dz, nx, z, rad)) nz = z + dz
        return floatArrayOf(nx, nz)
    }

    // =================================================================== menus genéricos
    internal fun openMenu(title: String, build: () -> List<Opt>) {
        menuTitle = title; menuBuild = build; menuOpts = build(); cursor = 0; mode = Mode.MENU; snd(Sfx.MENU_OPEN)
    }
    internal fun closeMenu() { mode = Mode.PLAY; menuBuild = null; saveDirty = true; snd(Sfx.UI_BACK) }

    private fun costLv(lv: Int) = 40 + 30 * lv

    internal fun bonfireMenu(idx: Int): List<Opt> = listOf(
        Opt("Descansar", "Recupera tudo e acende a Brasa. Os monstros voltam.") { rest(idx); say("Voce descansou na Brasa: ${AdvWorld.bonfireNames[idx]}"); closeMenu() },
        Opt("Fortalecer", "Troque gemas por vida ou forca.") { openMenu("FORTALECER") { levelMenu() } },
        Opt("Viajar", "Va para outra Brasa acesa.", ok = litBonfires().size > 1) { openMenu("VIAJAR") { travelMenu(idx) } },
        Opt("Sair") { closeMenu() }
    )

    private fun levelMenu(): List<Opt> {
        val cv = costLv(lvVig); val cf = costLv(lvFor)
        return listOf(
            Opt("Vigor ${lvVig + 1}   (${cv} gemas)", "+12 de vida maxima.", gems >= cv) { gems -= cv; lvVig++; hp = maxHp(); snd(Sfx.LEVEL); popupMsg("VIGOR!") },
            Opt("Forca ${lvFor + 1}   (${cf} gemas)", "+7% de dano.", gems >= cf) { gems -= cf; lvFor++; snd(Sfx.LEVEL); popupMsg("FORCA!") },
            Opt("Voltar") { openMenu("BRASA") { bonfireMenu(lastBf) } }
        )
    }

    internal fun litBonfires() = AdvWorld.bonfires.indices.filter { flags.contains("bf$it") }

    private fun travelMenu(cur: Int): List<Opt> {
        val l = litBonfires().filter { it != cur }.map { i ->
            Opt(AdvWorld.bonfireNames[i], "") { closeMenu(); startFade { lastBf = i; placeAtBonfire(); lockT = null; snd(Sfx.BONFIRE) } }
        }
        return l + Opt("Voltar") { openMenu("BRASA") { bonfireMenu(cur) } }
    }

    internal fun rest(idx: Int) {
        lastBf = idx; flags.add("bf$idx"); restoreAll()
        // os monstros voltam; chefes derrotados continuam derrotados
        val boss = ens.filter { it.spawnIdx >= 9000 && !it.alive }
        ens.clear(); projs.clear(); spikes.clear(); rings.clear(); bombs.clear()
        engaged = null; lockT = null
        var i = 0
        for (s in AdvWorld.spawns()) {
            val d = AdvEnemies.defs[s.kind] ?: continue
            val e = En(d, s.x, s.z, i++); e.y = AdvWorld.hAt(s.x, s.z); e.yaw = rnd.nextFloat() * 6.28f; ens.add(e)
        }
        for (k in 0 until 5) spawnBoss(k)
        boss.size
        snd(Sfx.BONFIRE); saveDirty = true
    }

    internal fun startFade(act: () -> Unit) { fadeDir = 1; fade = 0f; fadeAction = act }

    private fun openShopMenu(which: Int) {
        val list = if (which == 0) AdvData.shopTonho else AdvData.shopLia
        openMenu(if (which == 0) "LOJA DO TONHO" else "LOJA DA LIA") {
            list.map { s ->
                if (s.id == "seiva") Opt("Recarregar seiva   (20 gemas)", "Enche suas cargas de seiva.", gems >= 20 && flask < flaskMax) { gems -= 20; flask = flaskMax; snd(Sfx.HEAL) }
                else {
                    val it = AdvData.items[s.id]
                    val n = if (s.qty > 1) " x${s.qty}" else ""
                    val owned = (it?.cat == Cat.ARMA || it?.cat == Cat.EQUIP) && has(s.id)
                    Opt("${it?.name ?: s.id}$n   (${s.price} gemas)${if (owned) "  TEM" else ""}", it?.stat ?: "", gems >= s.price && !owned) {
                        gems -= s.price; give(s.id, max(1, s.qty)); snd(Sfx.COIN); say("Comprou ${it?.name}")
                    }
                }
            } + Opt("Sair") { closeMenu() }
        }
    }

    private fun openForgeMenu() {
        openMenu("FORJA DO TONHO") {
            val ws = ownedWeapons()
            ws.map { id ->
                val lv = wlv[id] ?: 0
                val cg = 60 + 50 * lv; val cm = 1 + lv
                Opt("${AdvData.items[id]?.name}  +$lv", "Custa $cg gemas e $cm minerio rubro. +22% de dano por nivel (maximo +5).",
                    lv < 5 && gems >= cg && count("minerio") >= cm) {
                    gems -= cg; take("minerio", cm); wlv[id] = lv + 1; snd(Sfx.LEVEL); popupMsg("AFIADA!")
                }
            } + Opt("Sair") { closeMenu() }
        }
    }

    // =================================================================== diálogo
    internal fun startConvo(c: Convo, npc: NpcRt? = null) {
        convo = c; dlgIdx = 0; dlgT = 0f; dlgChoice = false; dlgEnded = false; dlgNpc = npc; cursor = 0
        mode = Mode.DIALOG; snd(Sfx.UI_OK)
        if (npc != null) { npc.talkT = 0f; npc.look = 0f }
    }

    internal fun endConvo() {
        convo = null; dlgNpc?.let { it.emoAn = -1 }; dlgNpc = null
        mode = Mode.PLAY; saveDirty = true
        val pm = pendingMenu
        if (pm != null) { pendingMenu = null; pm() }
        checkQuests()
    }

    internal fun updateDialog(dt: Float, input: GameInput) {
        val c = convo ?: run { mode = Mode.PLAY; return }
        val n = dlgNpc
        if (n != null) { n.yaw = lerpAng(n.yaw, atan2(px - n.x, pz - n.z), dt * 6f); n.talkT += dt }
        if (dlgChoice) {
            val k = c.choices.size
            if (input.stepY != 0) { cursor = (cursor + input.stepY + k) % k; snd(Sfx.UI_MOVE) }
            if (input.firePressed || input.bPressed[Btn.A]) {
                val next = c.choices[cursor].second
                snd(Sfx.UI_OK)
                if (next == null) endConvo() else startConvo(next, n)
            }
            return
        }
        val line = c.lines[dlgIdx]
        n?.emoAn = when (line.emo) { 1 -> AN.CHEER; 2 -> AN.WORRY; 3 -> AN.HANDS_HIP; 4 -> AN.WORRY; else -> AN.TALK }
        dlgT += dt * 55f
        if (input.firePressed || input.bPressed[Btn.A]) {
            if (dlgT < line.text.length) dlgT = line.text.length.toFloat()
            else {
                snd(Sfx.UI_MOVE)
                if (dlgIdx < c.lines.size - 1) { dlgIdx++; dlgT = 0f }
                else {
                    if (!dlgEnded) { dlgEnded = true; c.onEnd?.invoke(this) }
                    if (pendingMenu != null) endConvo()
                    else if (c.choices.isNotEmpty()) { dlgChoice = true; cursor = 0 }
                    else endConvo()
                }
            }
        }
    }

    // =================================================================== missões
    internal fun checkQuests() {
        for (q in AdvQuests.all) {
            if (flags.contains("qd_${q.id}")) continue
            if (!q.main && !flags.contains("q_${q.id}")) continue
            if (!q.steps.all { it.done(this) }) continue
            if (!q.auto) { if (!flags.contains("qr_${q.id}")) { flags.add("qr_${q.id}"); say("Missao pronta: volte a ${q.giver}"); snd(Sfx.QUEST) }; continue }
            flags.add("qd_${q.id}")
            if (q.gems > 0) earn(q.gems)
            for (it in q.items) if (it.n > 0) give(it.id, it.n)
            showBanner("MISSAO CONCLUIDA", q.title)
            snd(Sfx.QUEST)
            saveDirty = true
        }
        if (flags.contains("dr_celeiro") && !flags.contains("q_start_tr")) flags.add("q_start_tr")
    }

    internal fun onKill(e: En) {
        for (q in AdvQuests.all) if (q.killKind == e.kind && flags.contains("q_${q.id}") && !flags.contains("qd_${q.id}")) addCnt("kq_${q.id}")
    }

    // =================================================================== ciclo principal
    override fun update(dt0: Float, input: GameInput) { if (crash != null) return; try { update0(dt0, input) } catch (e: Throwable) { fail("update", e) } }
    private fun update0(dt0: Float, input: GameInput) {
        val dt = min(dt0, 0.05f)
        time += dt
        for (i in sfxCd.indices) if (sfxCd[i] > 0f) sfxCd[i] -= dt
        if (toastT > 0f) { toastT -= dt; if (toastT <= 0f && toastQ.isNotEmpty()) { toast = toastQ.removeAt(0); toastT = 3f } }
        if (bannerT > 0f) bannerT -= dt
        if (popupT > 0f) popupT -= dt
        if (tipT > 0f) tipT -= dt
        if (flashT > 0f) flashT -= dt
        if (shake > 0f) { shake -= dt; if (shake <= 0f) shakeAmp = 0f }
        if (fadeDir != 0) {
            fade += dt * 2.6f * fadeDir
            if (fadeDir > 0 && fade >= 1f) { fade = 1f; fadeDir = -1; fadeAction?.invoke(); fadeAction = null }
            else if (fadeDir < 0 && fade <= 0f) { fade = 0f; fadeDir = 0 }
        }
        when (mode) {
            Mode.TITLE -> updateTitle(dt, input)
            Mode.STORY -> updateStory(dt, input)
            Mode.PLAY -> updatePlay(dt, input)
            Mode.DIALOG -> { updateDialog(dt, input); updateWorldLite(dt) }
            Mode.MENU -> updateMenu(input)
            Mode.INV -> updateInv(input)
            Mode.MAP -> updateMap(dt, input)
            Mode.SETTINGS -> updateSettings(input)
            Mode.DEAD -> updateDead(dt, input)
            Mode.ENDING -> updateEnding(dt, input)
        }
        updateMusic()
    }

    private fun updateMusic() {
        musicId = when (mode) {
            Mode.TITLE -> Mus.TITLE
            Mode.STORY -> Mus.TITLE
            Mode.ENDING -> Mus.ENDING
            Mode.DEAD -> -1
            else -> {
                val eb = engaged
                if (eb != null && eb.alive) (if (eb.kind == AdvEnemies.AUREL) Mus.FINAL else Mus.BOSS)
                else AdvWorld.styles[zone].music
            }
        }
    }

    // ---------------------------------------------------------------- título e história
    private fun updateTitle(dt: Float, input: GameInput) {
        camYaw += dt * 0.12f
        val opts = titleOpts()
        if (input.stepY != 0) { titleCur = (titleCur + input.stepY + opts.size) % opts.size; confirmNew = false; snd(Sfx.UI_MOVE) }
        if (input.firePressed || input.bPressed[Btn.A]) {
            snd(Sfx.UI_OK)
            when (opts[titleCur]) {
                "CONTINUAR" -> { mode = Mode.PLAY; fade = 1f; fadeDir = -1; saveDirty = true; zone = AdvWorld.zoneAt(px, pz); sAmb = AdvWorld.styles[zone].ambient }
                "AJUSTES" -> { setFrom = Mode.TITLE; setCur = 0; setRemap = -1; mode = Mode.SETTINGS }
                else -> {
                    if (hasSave && !confirmNew) { confirmNew = true }
                    else {
                        val keep = save(); val st = keep.substringAfter("set=", "").substringBefore(";"); val bd = keep.substringAfter("bd=", "").substringBefore(";")
                        reset()
                        if (st.isNotEmpty()) loadSettingsOnly(st, bd)
                        mode = Mode.STORY; pageIdx = 0; pageT = 0f; saveDirty = true
                    }
                }
            }
        }
    }

    private fun loadSettingsOnly(st: String, bd: String) {
        val s = st.split(",")
        if (s.size >= 8) {
            sens = s[0].toFloatOrNull()?.coerceIn(0.4f, 2.2f) ?: 1f; invertY = s[1] == "1"; lockHold = s[2] == "1"
            parryDiff = s[3].toIntOrNull()?.coerceIn(0, 2) ?: 1; shakeOn = s[4] == "1"; autoSwitch = s[5] == "1"; showHints = s[6] == "1"; presetIdx = s[7].toIntOrNull() ?: 0
        }
        val b = bd.split(",").mapNotNull { it.toIntOrNull() }
        if (b.size == Act.COUNT) for (i in 0 until Act.COUNT) bind[i] = b[i].coerceIn(0, Btn.COUNT - 1)
        if (bind[Act.BAG] == Btn.R3) bind[Act.BAG] = Btn.DUP
    }

    internal fun titleOpts(): List<String> = if (hasSave) listOf("CONTINUAR", "NOVO JOGO", "AJUSTES") else listOf("NOVO JOGO", "AJUSTES")

    private fun updateStory(dt: Float, input: GameInput) {
        pageT += dt; camYaw += dt * 0.08f
        if ((input.firePressed || input.bPressed[Btn.A]) && pageT > 0.4f) {
            pageIdx++; pageT = 0f; snd(Sfx.UI_MOVE)
            if (pageIdx >= AdvData.prologue.size) {
                mode = Mode.PLAY; placeAtBonfire(); zone = Z.VILA; sAmb = AdvWorld.styles[zone].ambient; fade = 1f; fadeDir = -1
                showBanner("VALE DE AURORA", "Fale com a Vovo Na"); zoneName = AdvWorld.styles[Z.VILA].name; zoneBanner = 3f
            }
        }
    }

    private fun updateDead(dt: Float, input: GameInput) {
        deadT += dt
        if (deadT > 2.2f && (input.firePressed || input.bPressed[Btn.A])) {
            mode = Mode.PLAY
            val g = gems; if (g > 0) { deathGems = g; deathX = px; deathZ = pz; gems = 0 }
            val old = lastBf
            restoreAll()
            for (b in boss0()) { b.hp = b.def.hp }
            val keepDeath = deathGems > 0
            val dx = deathX; val dz = deathZ; val dg = deathGems
            rest(old); deathGems = dg; deathX = dx; deathZ = dz
            if (!keepDeath) deathGems = 0
            placeAtBonfire(); pAct = PA.FREE; pT = 0f; iT = 1.2f; fade = 1f; fadeDir = -1
            saveDirty = true
        }
    }

    private fun boss0() = ens.filter { it.spawnIdx >= 9000 && it.alive }

    private fun updateEnding(dt: Float, input: GameInput) {
        pageT += dt; camYaw += dt * 0.1f
        if ((input.firePressed || input.bPressed[Btn.A]) && pageT > 0.4f) {
            if (pageIdx < AdvData.epilogue.size - 1) { pageIdx++; pageT = 0f; snd(Sfx.UI_MOVE) }
            else { flags.add("ended"); saveDirty = true; hasSave = true; mode = Mode.TITLE; titleCur = 0; placeAtBonfire(); lastBf = 0 }
        }
    }

    private fun updateMenu(input: GameInput) {
        val n = menuOpts.size
        if (n == 0) { closeMenu(); return }
        if (input.stepY != 0) { cursor = (cursor + input.stepY + n) % n; snd(Sfx.UI_MOVE) }
        if (input.altPressed || input.bPressed[Btn.X] || input.bPressed[Btn.Y]) { closeMenu(); return }
        if (input.firePressed || input.bPressed[Btn.A]) {
            val o = menuOpts[cursor.coerceIn(0, n - 1)]
            if (!o.ok) { say("Indisponivel"); snd(Sfx.DENIED); return }
            val before = menuBuild
            snd(Sfx.UI_OK)
            o.act()
            if (mode == Mode.MENU && menuBuild === before) { menuOpts = menuBuild?.invoke() ?: emptyList(); cursor = cursor.coerceIn(0, max(0, menuOpts.size - 1)) }
        }
    }

    // ---------------------------------------------------------------- mochila
    internal fun invItems(): List<ItemDef> = AdvData.items.values.filter { it.cat == invTab && has(it.id) }

    private fun updateInv(input: GameInput) {
        val tabs = Cat.names.size + 2 // + missões, + ajustes
        if (input.stepX != 0) { invTab = (invTab + input.stepX + tabs) % tabs; invCur = 0; snd(Sfx.UI_MOVE) }
        if (input.bPressed[Btn.SELECT]) { mode = Mode.MAP; snd(Sfx.MENU_OPEN); return }
        if (input.altPressed || input.bPressed[Btn.X] || input.bPressed[Btn.Y] || (bind[Act.BAG] < Btn.DUP && pressBtn(Act.BAG, input))) { mode = Mode.PLAY; snd(Sfx.UI_BACK); saveDirty = true; return }
        val qTab = Cat.names.size; val sTab = Cat.names.size + 1
        if (invTab == sTab) { setFrom = Mode.INV; setCur = 0; setRemap = -1; mode = Mode.SETTINGS; return }
        if (invTab == qTab) {
            val n = AdvQuests.all.count { val s = AdvQuests.state(it, this); s == 1 || s == 2 || s == 3 }
            if (input.stepY != 0 && n > 0) { invCur = (invCur + input.stepY + n) % n; snd(Sfx.UI_MOVE) }
            return
        }
        val list = invItems()
        if (input.stepY != 0 && list.isNotEmpty()) { invCur = (invCur + input.stepY + list.size) % list.size; snd(Sfx.UI_MOVE) }
        val cur = list.getOrNull(invCur.coerceIn(0, max(0, list.size - 1)))
        if (cur != null) flags.add("sn_${cur.id}")
        if ((input.firePressed || input.bPressed[Btn.A]) && cur != null) {
            when (cur.cat) {
                Cat.ARMA -> { eqW = cur.id; snd(Sfx.EQUIP) }
                Cat.EQUIP -> {
                    if (cur.slot == 1) eqS = cur.id else eqC = if (eqC == cur.id) "" else cur.id
                    hp = min(hp, maxHp()); snd(Sfx.EQUIP)
                }
                Cat.CONSUMO -> {
                    when (cur.id) {
                        "pedra" -> { take("pedra"); buffT = 90f; say("A lamina brilha por 90 s"); snd(Sfx.SPECIAL) }
                        "fruta" -> { take("fruta"); fruits++; hp = maxHp(); say("Vida maxima +10"); snd(Sfx.HEAL) }
                        "seiva" -> if (flask > 0 && hp < maxHp()) { flask--; hp = min(maxHp(), hp + maxHp() * 0.45f); snd(Sfx.HEAL) } else snd(Sfx.DENIED)
                        else -> snd(Sfx.DENIED)
                    }
                }
                else -> {}
            }
            saveDirty = true
        }
    }

    // ---------------------------------------------------------------- mapa
    private fun updateMap(dt: Float, input: GameInput) {
        if (input.altPressed || input.bPressed[Btn.X] || input.bPressed[Btn.Y] || input.bPressed[Btn.SELECT]) { mode = Mode.PLAY; snd(Sfx.UI_BACK); return }
        if (input.bPressed[Btn.R3]) { mode = Mode.INV; snd(Sfx.MENU_OPEN) }
    }

    // ---------------------------------------------------------------- ajustes
    internal val setItems = listOf("PRESET DE CONTROLES", "SENSIBILIDADE DA CAMERA", "CAMERA INVERTIDA", "MIRA: MODO", "JANELA DO APARAR", "TROCA AUTOMATICA DE ALVO", "TREMOR DE TELA", "DICAS", "MUSICA", "EFEITOS", "RENDERIZACAO", "REMAPEAR BOTOES", "AVISO DE ERRO", "VOLTAR")

    private fun updateSettings(input: GameInput) {
        if (remapPage) {
            if (setRemap >= 0) {
                for (b in 0 until Btn.COUNT) if (input.bPressed[b] && b != Btn.SELECT || (input.bPressed[b] && b == Btn.SELECT)) {
                    val old = bind[setRemap]
                    for (a in 0 until Act.COUNT) if (a != setRemap && bind[a] == b && !((a == Act.INTERACT && setRemap == Act.ATK) || (a == Act.ATK && setRemap == Act.INTERACT))) bind[a] = old
                    bind[setRemap] = b; setRemap = -1; snd(Sfx.UI_OK); presetIdx = 99; saveDirty = true
                    return
                }
                return
            }
            if (input.stepY != 0) { remapCur = (remapCur + input.stepY + Act.COUNT) % Act.COUNT; snd(Sfx.UI_MOVE) }
            if (input.firePressed || input.bPressed[Btn.A]) { setRemap = remapCur; snd(Sfx.UI_OK); input.bPressed[Btn.A] = false; input.firePressed = false }
            else if (input.altPressed || input.bPressed[Btn.X] || input.bPressed[Btn.Y]) { remapPage = false; snd(Sfx.UI_BACK) }
            return
        }
        if (setCur >= setItems.size) setCur = 0
        val remapView = setCur == 11
        if (input.stepY != 0) { setCur = (setCur + input.stepY + setItems.size) % setItems.size; snd(Sfx.UI_MOVE) }
        if (input.altPressed || input.bPressed[Btn.X] || input.bPressed[Btn.Y]) { leaveSettings(); return }
        val dx = input.stepX
        val ok = input.firePressed || input.bPressed[Btn.A]
        when (setCur) {
            0 -> if (dx != 0 || ok) { val d = if (dx != 0) dx else 1; presetIdx = if (presetIdx > 90) 0 else (presetIdx + d + Act.presets.size) % Act.presets.size; Act.preset(presetIdx).copyInto(bind); snd(Sfx.UI_MOVE); saveDirty = true }
            1 -> if (dx != 0) { sens = (sens + dx * 0.1f).coerceIn(0.4f, 2.2f); snd(Sfx.UI_MOVE); saveDirty = true }
            2 -> if (dx != 0 || ok) { invertY = !invertY; snd(Sfx.UI_MOVE); saveDirty = true }
            3 -> if (dx != 0 || ok) { lockHold = !lockHold; snd(Sfx.UI_MOVE); saveDirty = true }
            4 -> if (dx != 0 || ok) { parryDiff = (parryDiff + (if (dx != 0) dx else 1) + 3) % 3; snd(Sfx.UI_MOVE); saveDirty = true }
            5 -> if (dx != 0 || ok) { autoSwitch = !autoSwitch; snd(Sfx.UI_MOVE); saveDirty = true }
            6 -> if (dx != 0 || ok) { shakeOn = !shakeOn; snd(Sfx.UI_MOVE); saveDirty = true }
            7 -> if (dx != 0 || ok) { showHints = !showHints; snd(Sfx.UI_MOVE); saveDirty = true }
            8 -> if (dx != 0) { musicVolume = (musicVolume + dx * 0.1f).coerceIn(0f, 1f); saveDirty = true }
            9 -> if (dx != 0) { sfxVolume = (sfxVolume + dx * 0.1f).coerceIn(0f, 1f); snd(Sfx.HIT); saveDirty = true }
            10 -> if (dx != 0 || ok) { GfxFlags.compat3d = !GfxFlags.compat3d; snd(Sfx.UI_MOVE) }
            11 -> if (ok) { remapPage = true; remapCur = 0; setRemap = -1; snd(Sfx.UI_OK) }
            12 -> if (dx != 0 || ok) { Crumb.setDiag(!Crumb.diagOn()); snd(Sfx.UI_MOVE) }
            13 -> if (ok) leaveSettings()
        }
        if (remapView && dx != 0) { /* sem efeito */ }
    }

    private fun leaveSettings() { snd(Sfx.UI_BACK); mode = if (setFrom == Mode.INV) Mode.INV else Mode.TITLE; saveDirty = true }

    override fun draw(g: Gfx) {
        val c = crash
        if (c == null) { try { drawAll(g) } catch (e: Throwable) { fail("draw", e) }; if (crash == null) return }
        g.rect(0f, 0f, g.width, g.height, 0xFF000000L)
        val msg = crash ?: return
        g.text("ERRO NO VALE DE AURORA - TIRE UMA FOTO E ENVIE", g.width / 2f, g.height * 0.2f, g.height * 0.05f, 0xFFFFFF00L, true)
        var i = 0; var y = g.height * 0.32f
        while (i < msg.length) { val e = minOf(msg.length, i + 70); g.text(msg.substring(i, e), g.width / 2f, y, g.height * 0.04f, 0xFFFFFFFFL, true); y += g.height * 0.06f; i = e }
    }

    fun beginEndingIfNeeded() {}
}
