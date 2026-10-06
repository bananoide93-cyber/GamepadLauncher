package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Regiões do Vale. */
object Z {
    const val VILA = 0; const val CAMPINA = 1; const val FLORESTA = 2; const val LAGO = 3; const val RUINAS = 4
    const val PICO = 5; const val CAVERNA = 6; const val SANTUARIO = 7; const val JARDIM = 8
    const val COUNT = 9
}

/** Aparência e clima de uma região. */
class ZoneStyle(
    val name: String, val ambient: Float, val lr: Float, val lg: Float, val lb: Float, val fog: Long, val fogStart: Float, val fogEnd: Float,
    val skyTop: Long, val music: Int, val ground1: Long, val ground2: Long
)

class Prop(val kind: Int, val x: Float, val z: Float, val rot: Float, val sc: Float, val r: Float) {
    var y = 0f
    val h get() = PK.height(kind) * sc
    var sway = 0f
}

/** Requisito para abrir uma porta. type: 0 chave (id do item), 1 flag, 2 explosão (bomba ou golpe forte de machado), 3 estilhaços, 4 missão concluída (id). */
class Req(val type: Int, val id: String = "")

class DoorDef(val id: String, val x: Float, val z: Float, val yaw: Float, val style: Int, val req: Req, val label: String)

class ChestDef(val id: String, val x: Float, val z: Float, val rare: Boolean = false)

class NpcDef(val id: Int, val name: String, val x: Float, val z: Float, val rig: Int, val anim: Int, val roam: Float = 0f)

class SignDef(val x: Float, val z: Float, val yaw: Float, val text: String)

class SpawnDef(val kind: Int, val x: Float, val z: Float, val zone: Int)

/** Mundo do "Vale de Aurora": terreno procedural, regiões, cenário, portas, baús, NPCs e pontos de geração. */
object AdvWorld {
    const val CELL = 3f
    const val N = 66
    const val SIZE = N * CELL
    const val WATER = -1.45f

    val village = floatArrayOf(100f, 150f)
    val arenas = arrayOf(
        floatArrayOf(24f, 104f), floatArrayOf(56f, 42f), floatArrayOf(168f, 64f), floatArrayOf(152f, 114f), floatArrayOf(100f, 14f)
    )
    val arenaR = floatArrayOf(14f, 14f, 14f, 13f, 15f)
    val bonfires = arrayOf(
        floatArrayOf(100f, 141f), floatArrayOf(100f, 110f), floatArrayOf(62f, 132f), floatArrayOf(38f, 114f), floatArrayOf(74f, 80f),
        floatArrayOf(138f, 82f), floatArrayOf(126f, 118f), floatArrayOf(138f, 152f), floatArrayOf(100f, 42f), floatArrayOf(100f, 24f)
    )
    val bonfireNames = listOf(
        "Aldeia de Aurora", "Encruzilhada", "Borda da Floresta", "Clareira Musgosa", "Portal das Ruínas",
        "Sopé do Pico", "Boca da Caverna", "Cais do Lago", "Portão do Coração", "Santuário"
    )

    private fun sm(t: Float): Float = if (t <= 0f) 0f else if (t >= 1f) 1f else t * t * (3f - 2f * t)
    private fun dist(x: Float, z: Float, cx: Float, cz: Float) = sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz))

    // ------------------------------------------------------------------ terreno
    fun hgt(x: Float, z: Float): Float {
        var h = 1.0f * sin(x * 0.11f) * cos(z * 0.09f) + 0.6f * sin(x * 0.23f + 1.3f) * sin(z * 0.19f + 0.4f)
        // Pico Ferrugem (nordeste)
        h += 17f * sm(1f - dist(x, z, 172f, 64f) / 46f)
        h += 4f * sm(1f - dist(x, z, 140f, 40f) / 24f)
        // colinas das ruínas
        h += 2.2f * sm(1f - dist(x, z, 62f, 56f) / 30f)
        // lago e ilha
        h -= 6.2f * sm(1f - dist(x, z, 156f, 160f) / 26f)
        h += 5.4f * sm(1f - dist(x, z, 156f, 160f) / 6f)
        val bar = sm(1f - abs(x - 156f) / 2.4f) * sm((z - 160f) / 4f) * sm((182f - z) / 4f)
        h += 4.4f * bar
        // lagoa pequena na floresta
        h -= 3.5f * sm(1f - dist(x, z, 24f, 150f) / 8f)
        // crista que protege o Santuário (só há passagem pelo portão)
        val gap = 1f - sm(1f - abs(x - 100f) / 5f)
        h += 8f * sm(1f - abs(z - 34f) / 5f) * gap
        // anel de pedra da caverna (entrada a oeste)
        val dc = dist(x, z, 152f, 114f)
        val west = if (x < 150f) sm(1f - abs(z - 114f) / 5f) else 0f
        h += 10f * sm(1f - abs(dc - 21f) / 5f) * (1f - west)
        // jardim secreto (anel de pedra, entrada a leste)
        val dj = dist(x, z, 22f, 30f)
        val east = if (x > 22f) sm(1f - abs(z - 30f) / 4f) else 0f
        h += 9f * sm(1f - abs(dj - 15f) / 4f) * (1f - east)
        fun flat(cx: Float, cz: Float, r: Float, base: Float) { h += (base - h) * sm(1f - dist(x, z, cx, cz) / r) }
        flat(100f, 150f, 21f, 0.4f)
        flat(100f, 125f, 8f, 0.45f)
        flat(24f, 104f, 14f, 0.6f)
        flat(56f, 42f, 14f, 3.4f)
        flat(168f, 64f, 15f, 13.5f)
        flat(152f, 114f, 17f, 1.0f)
        flat(100f, 15f, 20f, 2.6f)
        flat(22f, 30f, 11f, 0.5f)
        flat(100f, 42f, 7f, 0.5f)
        flat(88f, 66f, 11f, 1.6f)
        flat(140f, 82f, 6f, 5.0f)
        val edge = min(min(x, z), min(SIZE - x, SIZE - z))
        if (edge < 9f) h += (9f - edge) * 2.6f
        return h
    }

    val hv = FloatArray((N + 1) * (N + 1)) { val i = it % (N + 1); val j = it / (N + 1); hgt(i * CELL, j * CELL) }

    fun hAt(x: Float, z: Float): Float {
        val gx = (x / CELL).coerceIn(0f, N - 0.001f); val gz = (z / CELL).coerceIn(0f, N - 0.001f)
        val i = gx.toInt(); val j = gz.toInt()
        val fx = gx - i; val fz = gz - j
        val a = hv[j * (N + 1) + i]; val b = hv[j * (N + 1) + i + 1]
        val c = hv[(j + 1) * (N + 1) + i]; val d = hv[(j + 1) * (N + 1) + i + 1]
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz
    }

    fun isWater(x: Float, z: Float) = hAt(x, z) < WATER + 0.05f

    // ------------------------------------------------------------------ caminhos
    private val pathNodes = arrayOf(
        floatArrayOf(100f, 150f), floatArrayOf(100f, 126f), floatArrayOf(100f, 108f), floatArrayOf(100f, 80f), floatArrayOf(100f, 44f),
        floatArrayOf(72f, 138f), floatArrayOf(46f, 124f), floatArrayOf(36f, 112f), floatArrayOf(24f, 104f),
        floatArrayOf(88f, 92f), floatArrayOf(74f, 80f), floatArrayOf(62f, 62f), floatArrayOf(56f, 42f),
        floatArrayOf(126f, 100f), floatArrayOf(140f, 84f), floatArrayOf(156f, 72f), floatArrayOf(168f, 64f),
        floatArrayOf(126f, 118f),
        floatArrayOf(124f, 148f), floatArrayOf(138f, 152f),
        floatArrayOf(100f, 24f), floatArrayOf(100f, 14f)
    )
    private val pathEdges = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8, 2 to 9, 9 to 10, 10 to 11, 11 to 12,
        2 to 13, 13 to 14, 14 to 15, 15 to 16, 13 to 17, 0 to 18, 18 to 19, 4 to 20, 20 to 21
    )

    private fun segDist(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float): Float {
        val dx = bx - ax; val dz = bz - az
        val t = (((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz)).coerceIn(0f, 1f)
        return dist(px, pz, ax + dx * t, az + dz * t)
    }

    fun pathDist(x: Float, z: Float): Float {
        var m = 99f
        for (e in pathEdges) { val a = pathNodes[e.first]; val b = pathNodes[e.second]; val d = segDist(x, z, a[0], a[1], b[0], b[1]); if (d < m) m = d }
        return m
    }

    fun onPath(x: Float, z: Float) = pathDist(x, z) < 2.4f

    // ------------------------------------------------------------------ regiões
    fun zoneAt(x: Float, z: Float): Int = when {
        dist(x, z, 22f, 30f) < 17f -> Z.JARDIM
        dist(x, z, 152f, 114f) < 21f -> Z.CAVERNA
        z < 33f && x > 74f && x < 126f -> Z.SANTUARIO
        dist(x, z, 100f, 150f) < 27f -> Z.VILA
        dist(x, z, 156f, 160f) < 31f -> Z.LAGO
        x > 124f && z < 106f -> Z.PICO
        x < 90f && z < 98f && dist(x, z, 62f, 58f) < 38f -> Z.RUINAS
        x < 66f && z > 74f -> Z.FLORESTA
        else -> Z.CAMPINA
    }

    val styles: Array<ZoneStyle> = arrayOf(
        ZoneStyle("Aldeia de Aurora", 0.46f, 1.0f, 0.97f, 0.9f, 0xFFC9E6F5L, 70f, 150f, 0xFF4A86CCL, Mus.VILLAGE, 0xFF6DBB55L, 0xFF68B250L),
        ZoneStyle("Campina dos Ventos", 0.44f, 1.0f, 1.0f, 0.92f, 0xFFC4E4F4L, 70f, 150f, 0xFF4F8BD0L, Mus.CAMPINA, 0xFF72C05AL, 0xFF6BB655L),
        ZoneStyle("Floresta Musgosa", 0.38f, 0.82f, 1.0f, 0.82f, 0xFFA8D0B4L, 45f, 115f, 0xFF3F7AA8L, Mus.FOREST, 0xFF3F8F4AL, 0xFF3A8545L),
        ZoneStyle("Lago Espelho", 0.46f, 0.92f, 1.0f, 1.04f, 0xFFC2E8F6L, 70f, 150f, 0xFF4A90D8L, Mus.LAKE, 0xFF7CC668L, 0xFF74BE60L),
        ZoneStyle("Ruínas Cinzentas", 0.40f, 0.9f, 0.9f, 1.0f, 0xFFB5BCCCL, 52f, 125f, 0xFF5A6E94L, Mus.RUINS, 0xFF9AA39AL, 0xFF8F988FL),
        ZoneStyle("Pico Ferrugem", 0.42f, 1.1f, 0.9f, 0.78f, 0xFFE6C5A8L, 52f, 130f, 0xFF7A6A8CL, Mus.PEAK, 0xFF8C8279L, 0xFF857B72L),
        ZoneStyle("Caverna Cristalina", 0.30f, 0.62f, 0.7f, 1.0f, 0xFF1C1A3AL, 18f, 62f, 0xFF14122EL, Mus.CAVE, 0xFF5A5470L, 0xFF524C68L),
        ZoneStyle("Santuário do Coração", 0.40f, 0.9f, 0.82f, 1.1f, 0xFF9C86C8L, 40f, 105f, 0xFF4B3A86L, Mus.TEMPLE, 0xFF5A4F63L, 0xFF534859L),
        ZoneStyle("Jardim Secreto", 0.52f, 1.05f, 1.0f, 0.9f, 0xFFD8F0D0L, 40f, 100f, 0xFF5AA0D8L, Mus.VILLAGE, 0xFF86D06AL, 0xFF7CC862L)
    )

    private fun hash(i: Int, j: Int): Int { var h = i * 374761393 + j * 668265263; h = (h xor (h shr 13)) * 1274126177; return h xor (h shr 16) }

    fun cellColor(i: Int, j: Int): Long {
        val cx = (i + 0.5f) * CELL; val cz = (j + 0.5f) * CELL
        val h = (hv[j * (N + 1) + i] + hv[(j + 1) * (N + 1) + i + 1] + hv[j * (N + 1) + i + 1] + hv[(j + 1) * (N + 1) + i]) / 4f
        val n = hash(i, j)
        val alt = (n and 1) == 0
        val nz = ((n shr 3) and 7) / 7f
        fun v(c: Long, amt: Float) = C.shade(c, 1f + (nz - 0.5f) * amt)
        if (h < WATER + 0.05f) return if (alt) 0xFF3E7FD0L else 0xFF4A8BDCL
        if (h < -0.6f) return v(0xFFE0CF8CL, 0.12f)
        val zone = zoneAt(cx, cz)
        val st = styles[zone]
        if (zone == Z.CAVERNA) return v(if (alt) st.ground1 else st.ground2, 0.2f)
        if (pathDist(cx, cz) < 2.3f && h < 12f) return v(if (alt) 0xFFC9A66BL else 0xFFC09D62L, 0.1f)
        if (zone == Z.VILA && dist(cx, cz, 100f, 150f) < 6f) return v(0xFFC9A66BL, 0.08f)
        if (h > 17f) return v(0xFFEFF6FAL, 0.06f)
        if (h > 12f && zone == Z.PICO) return v(if (alt) 0xFF9A9088L else 0xFF8E847CL, 0.14f)
        if (zone == Z.PICO && h > 6f) return v(if (alt) 0xFFA65F3AL else 0xFF9C5835L, 0.14f)
        return v(if (alt) st.ground1 else st.ground2, 0.16f)
    }

    val cellCols = LongArray(N * N) { cellColor(it % N, it / N) }

    // ------------------------------------------------------------------ objetos de mundo
    val doors: List<DoorDef> = listOf(
        DoorDef("celeiro", 100f, 134f, 0f, 0, Req(0, "chave_celeiro"), "Portão do Campo de Treino"),
        DoorDef("cripta", 92f, 60f, 0f, 1, Req(0, "chave_ferro"), "Portão da Cripta"),
        DoorDef("caverna", 133f, 114f, PI.toFloat() / 2f, 1, Req(0, "chave_cristal"), "Portão da Caverna"),
        DoorDef("jardim", 38f, 30f, PI.toFloat() / 2f, 2, Req(2), "Parede Rachada"),
        DoorDef("coracao", 100f, 34f, 0f, 3, Req(3), "Portão do Coração")
    )

    val chests: List<ChestDef> = listOf(
        ChestDef("c_casa", 109f, 158f), ChestDef("c_campina1", 84f, 118f), ChestDef("c_campina2", 122f, 128f),
        ChestDef("c_flor1", 50f, 150f), ChestDef("c_flor2", 18f, 126f), ChestDef("c_flor3", 40f, 96f), ChestDef("c_martelo", 28f, 140f),
        ChestDef("c_ruina1", 78f, 56f), ChestDef("c_ruina2", 46f, 70f), ChestDef("c_ruina3", 64f, 34f),
        ChestDef("c_cripta", 92f, 72f, true), ChestDef("c_cripta2", 84f, 76f),
        ChestDef("c_pico1", 150f, 74f), ChestDef("c_pico2", 188f, 80f), ChestDef("c_pico3", 176f, 48f),
        ChestDef("c_lago1", 124f, 172f), ChestDef("c_colar", 150f, 140f), ChestDef("c_ilha", 156f, 160f, true),
        ChestDef("c_caverna1", 160f, 122f), ChestDef("c_caverna2", 144f, 104f), ChestDef("c_cajado", 164f, 108f, true),
        ChestDef("c_jardim1", 20f, 26f, true), ChestDef("c_jardim2", 26f, 36f),
        ChestDef("c_santuario1", 84f, 18f), ChestDef("c_santuario2", 116f, 18f)
    )

    val npcs: List<NpcDef> = listOf(
        NpcDef(0, "Vovó Ná", 94f, 156f, 0, AN.HANDS_HIP),
        NpcDef(1, "Tonho Bigorna", 110f, 150f, 1, AN.WORK),
        NpcDef(2, "Lia", 104f, 160f, 2, AN.TALK),
        NpcDef(3, "Piu", 92f, 144f, 100, AN.IDLE),
        NpcDef(4, "Mestre Beto", 82f, 162f, 3, AN.SWEEP),
        NpcDef(5, "Guarda Dória", 100f, 132f, 5, AN.IDLE, 0f),
        NpcDef(6, "Kiki", 98f, 154f, 6, AN.IDLE, 7f),
        NpcDef(7, "Barnabé", 138f, 156f, 4, AN.IDLE),
        NpcDef(8, "Mira", 20f, 138f, 7, AN.LOOK),
        NpcDef(9, "Eco", 100f, 100f, 101, AN.IDLE),
        NpcDef(10, "Eco", 66f, 48f, 101, AN.IDLE),
        NpcDef(11, "Eco", 156f, 78f, 101, AN.IDLE),
        NpcDef(12, "Eco", 100f, 40f, 101, AN.IDLE),
        NpcDef(13, "Nôa", 112f, 146f, 8, AN.WAVE, 5f)
    )

    val signs: List<SignDef> = listOf(
        SignDef(104f, 124f, 0f, "Campo de Treino. Portão trancado: procure a chave."),
        SignDef(96f, 112f, 0.3f, "Encruzilhada: Floresta a oeste, Ruínas ao norte, Pico a leste, Aldeia ao sul."),
        SignDef(72f, 136f, 0.2f, "Floresta Musgosa. Cuidado com o troll."),
        SignDef(130f, 150f, 0f, "Lago Espelho. Pesca proibida às rãs ferrugentas."),
        SignDef(104f, 78f, 0f, "Ruínas Cinzentas."),
        SignDef(132f, 94f, 0.4f, "Pico Ferrugem. Só entra quem não teme o calor."),
        SignDef(104f, 46f, 0f, "Portão do Coração. Três estilhaços abrem o caminho.")
    )

    /** Pontos de tutorial: a primeira vez que o jogador passa por perto, mostra a dica. */
    class TutSpot(val id: String, val x: Float, val z: Float, val r: Float, val text: String)
    val tutSpots: List<TutSpot> = listOf(
        TutSpot("t_move", 100f, 146f, 7f, "Analógico esquerdo: andar. Analógico direito: câmera."),
        TutSpot("t_npc", 96f, 154f, 4f, "Chegue perto de alguém e aperte A para conversar."),
        TutSpot("t_door", 100f, 130f, 4f, "Portas trancadas pedem chaves. Veja a mochila (segure X)."),
        TutSpot("t_yard", 100f, 124f, 6f, "Ataque com A. Segure L1 para travar a mira em um inimigo."),
        TutSpot("t_guard", 100f, 118f, 5f, "Segure L2 para defender. Aperte L2 NA HORA do golpe para aparar (parry)."),
        TutSpot("t_roll", 94f, 114f, 6f, "R1 rola e dá um instante de invencibilidade."),
        TutSpot("t_bonfire", 100f, 138f, 4f, "Brasas salvam o jogo, curam e permitem viajar."),
        TutSpot("t_map", 100f, 108f, 5f, "SELECT abre o mapa. D-pad troca de arma e de item.")
    )

    // ------------------------------------------------------------------ cenário
    val props: Array<Prop> by lazy { genProps() }
    private const val BUCKET = 12f
    private val BN = (SIZE / BUCKET).toInt() + 1
    private val grid: Array<ArrayList<Prop>> by lazy {
        val g = Array(BN * BN) { ArrayList<Prop>() }
        for (p in props) { val bx = (p.x / BUCKET).toInt().coerceIn(0, BN - 1); val bz = (p.z / BUCKET).toInt().coerceIn(0, BN - 1); g[bz * BN + bx].add(p) }
        g
    }

    /** Chama cb para cada objeto dentro de um quadrado de lado 2*rad em torno de (x,z). */
    inline fun forNear(x: Float, z: Float, rad: Float, cb: (Prop) -> Unit) {
        val b0x = ((x - rad) / bucketSize()).toInt().coerceIn(0, bucketN() - 1); val b1x = ((x + rad) / bucketSize()).toInt().coerceIn(0, bucketN() - 1)
        val b0z = ((z - rad) / bucketSize()).toInt().coerceIn(0, bucketN() - 1); val b1z = ((z + rad) / bucketSize()).toInt().coerceIn(0, bucketN() - 1)
        for (bz in b0z..b1z) for (bx in b0x..b1x) { val l = bucketAt(bx, bz); for (i in 0 until l.size) cb(l[i]) }
    }
    fun bucketSize() = BUCKET
    fun bucketN() = BN
    fun bucketAt(bx: Int, bz: Int): ArrayList<Prop> = grid[bz * BN + bx]

    fun blockedByProps(x: Float, z: Float, rad: Float): Boolean {
        forNear(x, z, rad + 4f) { p ->
            if (p.r > 0f) { val dx = p.x - x; val dz = p.z - z; val rr = p.r * p.sc.coerceAtLeast(0.8f) * (if (p.kind == PK.CLIFF) 1f else 1f) + rad; if (dx * dx + dz * dz < rr * rr) return true }
        }
        return false
    }

    private fun genProps(): Array<Prop> {
        val r = Random(2026)
        val out = ArrayList<Prop>()
        val keepOut = ArrayList<FloatArray>()
        fun ko(x: Float, z: Float, rad: Float) { keepOut.add(floatArrayOf(x, z, rad)) }
        for (a in arenas.indices) ko(arenas[a][0], arenas[a][1], arenaR[a] + 2f)
        for (b in bonfires) ko(b[0], b[1], 3.5f)
        for (c in chests) ko(c.x, c.z, 2f)
        for (n in npcs) ko(n.x, n.z, 2f)
        for (d in doors) ko(d.x, d.z, 3.5f)
        ko(100f, 150f, 20f); ko(100f, 125f, 9f); ko(100f, 18f, 10f)
        fun free(x: Float, z: Float, pad: Float): Boolean {
            for (k in keepOut) { val dx = x - k[0]; val dz = z - k[1]; val rr = k[2] + pad; if (dx * dx + dz * dz < rr * rr) return false }
            if (pathDist(x, z) < 2.8f + pad) return false
            if (hAt(x, z) < -0.7f) return false
            if (x < 5f || z < 5f || x > SIZE - 5f || z > SIZE - 5f) return false
            return true
        }
        fun add(kind: Int, x: Float, z: Float, sc: Float, rad: Float = PK.radius(kind), rot: Float = r.nextFloat() * 6.28f): Prop {
            val p = Prop(kind, x, z, rot, sc, rad); p.y = hAt(x, z); p.sway = if (kind <= PK.BUSH || kind == PK.GRASS || kind == PK.SNOWPINE || kind == PK.REED) 1f else 0f; out.add(p); return p
        }
        fun scatter(count: Int, zone: Int, pad: Float, tries: Int, f: (Float, Float) -> Boolean = { _, _ -> true }, make: (Float, Float) -> Unit) {
            var c = 0; var t = 0
            while (c < count && t < tries) {
                t++
                val x = 5f + r.nextFloat() * (SIZE - 10f); val z = 5f + r.nextFloat() * (SIZE - 10f)
                if (zone >= 0 && zoneAt(x, z) != zone) continue
                if (!free(x, z, pad) || !f(x, z)) continue
                make(x, z); c++
            }
        }
        fun line(kind: Int, x0: Float, z0: Float, x1: Float, z1: Float, step: Float, sc: Float = 1f) {
            val d = dist(x0, z0, x1, z1); val n = max(1, (d / step).toInt())
            val rot = atan2(x1 - x0, z1 - z0)
            for (i in 0..n) { val t = i.toFloat() / n; val x = x0 + (x1 - x0) * t; val z = z0 + (z1 - z0) * t; add(kind, x, z, sc, PK.radius(kind), rot + PI.toFloat() / 2f) }
        }

        // --- aldeia
        val houses = listOf(
            Triple(PK.COTTAGE, 84f, 144f), Triple(PK.COTTAGE, 116f, 140f), Triple(PK.COTTAGE, 90f, 168f), Triple(PK.COTTAGE, 112f, 166f),
            Triple(PK.COTTAGE, 78f, 154f), Triple(PK.COTTAGE, 122f, 154f), Triple(PK.ELDER_HOME, 92f, 160f), Triple(PK.SMITHY, 112f, 156f),
            Triple(PK.BARN, 70f, 170f)
        )
        for ((k, x, z) in houses) { val p = add(k, x, z, 1f, PK.radius(k), 0f); p.y = hAt(x, z) }
        add(PK.WINDMILL, 128f, 168f, 1f); add(PK.WELL, 100f, 156f, 1f)
        add(PK.STALL, 104f, 163f, 1f, 1.0f, 0f)
        for (i in 0 until 6) { val a = i / 6f * 2f * PI.toFloat(); add(PK.LANTERN, 100f + cos(a) * 9f, 150f + sin(a) * 9f, 1f) }
        add(PK.SCARECROW, 82f, 178f, 1f); add(PK.HAY, 66f, 176f, 1f); add(PK.HAY, 74f, 178f, 1f); add(PK.HAY, 62f, 168f, 1f)
        for (i in 0 until 6) add(PK.BARREL, 118f + (i % 3) * 1.2f, 160f + (i / 3) * 1.2f, 1f)
        for (i in 0 until 4) add(PK.CRATE, 106f + i * 1.1f, 168f, 1f)
        line(PK.FENCE, 70f, 182f, 90f, 184f, 2.2f); line(PK.FENCE, 112f, 178f, 134f, 178f, 2.2f)
        // --- campo de treino (cerca com portão em x=100,z=134)
        val tx0 = 90f; val tx1 = 110f; val tz0 = 118f; val tz1 = 134f
        line(PK.FENCE, tx0, tz0, tx1, tz0, 2.1f); line(PK.FENCE, tx0, tz0, tx0, tz1, 2.1f); line(PK.FENCE, tx1, tz0, tx1, tz1, 2.1f)
        line(PK.FENCE, tx0, tz1, 97.5f, tz1, 2.1f); line(PK.FENCE, 102.5f, tz1, tx1, tz1, 2.1f)
        add(PK.SCARECROW, 95f, 124f, 1f); add(PK.SCARECROW, 105f, 124f, 1f); add(PK.BARREL, 92f, 131f, 1f); add(PK.BARREL, 108f, 131f, 1f)
        add(PK.BANNER, 94f, 120f, 1f, 0.3f); add(PK.BANNER, 106f, 120f, 1f, 0.3f)
        // --- placas
        for (s in signs) add(PK.SIGN, s.x, s.z, 1f, 0.35f, s.yaw)

        // --- campina
        scatter(46, Z.CAMPINA, 1.5f, 3000) { x, z -> add(if (r.nextInt(3) == 0) PK.BIRCH else PK.OAK, x, z, 0.85f + r.nextFloat() * 0.5f) }
        scatter(36, Z.CAMPINA, 1f, 2000) { x, z -> add(PK.BUSH, x, z, 0.8f + r.nextFloat() * 0.5f) }
        scatter(14, Z.CAMPINA, 1.5f, 2000) { x, z -> add(if (r.nextBoolean()) PK.ROCK else PK.LOG, x, z, 0.7f + r.nextFloat() * 0.6f) }
        // --- floresta
        scatter(190, Z.FLORESTA, 1.5f, 6000) { x, z -> add(when (r.nextInt(5)) { 0 -> PK.OAK; 1 -> PK.BIRCH; else -> PK.PINE }, x, z, 0.85f + r.nextFloat() * 0.6f) }
        scatter(40, Z.FLORESTA, 1f, 3000) { x, z -> add(PK.BUSH, x, z, 0.8f + r.nextFloat() * 0.5f) }
        scatter(22, Z.FLORESTA, 1f, 2000) { x, z -> add(if (r.nextBoolean()) PK.STUMP else PK.LOG, x, z, 0.8f + r.nextFloat() * 0.5f) }
        scatter(12, Z.FLORESTA, 1.5f, 2000) { x, z -> add(PK.ROCK, x, z, 0.8f + r.nextFloat() * 0.8f) }
        // cabana da Mira
        add(PK.COTTAGE, 16f, 142f, 1f, PK.radius(PK.COTTAGE), 0.6f)
        // --- lago
        scatter(30, Z.LAGO, 0.5f, 3000, { x, z -> hAt(x, z) < 0.4f && hAt(x, z) > -1.2f }) { x, z -> add(PK.REED, x, z, 0.9f + r.nextFloat() * 0.5f) }
        scatter(24, Z.LAGO, 0f, 3000, { x, z -> isWater(x, z) && hAt(x, z) > WATER - 2.5f }) { x, z -> val p = add(PK.LILY, x, z, 0.8f + r.nextFloat() * 0.6f); p.y = WATER + 0.02f }
        scatter(14, Z.LAGO, 1.5f, 2000) { x, z -> add(if (r.nextBoolean()) PK.OAK else PK.BIRCH, x, z, 0.9f + r.nextFloat() * 0.4f) }
        scatter(8, Z.LAGO, 1.5f, 2000) { x, z -> add(PK.ROCK, x, z, 0.8f + r.nextFloat() * 0.6f) }
        add(PK.LANTERN, 134f, 154f, 1f); add(PK.CRATE, 142f, 158f, 1f); add(PK.BARREL, 143.5f, 157f, 1f)
        // --- ruínas
        scatter(26, Z.RUINAS, 1.5f, 3000) { x, z -> add(when (r.nextInt(3)) { 0 -> PK.PILLAR; 1 -> PK.PILLAR_BROKEN; else -> PK.WALL }, x, z, 1f) }
        scatter(6, Z.RUINAS, 2f, 2000) { x, z -> add(PK.ARCH, x, z, 1f) }
        scatter(5, Z.RUINAS, 2f, 2000) { x, z -> add(PK.STATUE, x, z, 1f) }
        scatter(3, Z.RUINAS, 3f, 2000) { x, z -> add(PK.TOWER, x, z, 1f) }
        scatter(14, Z.RUINAS, 1.5f, 2000) { x, z -> add(PK.DEAD, x, z, 0.9f + r.nextFloat() * 0.4f) }
        scatter(16, Z.RUINAS, 1.5f, 2000) { x, z -> add(PK.ROCK, x, z, 0.8f + r.nextFloat() * 0.8f) }
        scatter(5, Z.RUINAS, 1.5f, 2000) { x, z -> add(PK.RUNE, x, z, 1f) }
        // cripta: pátio murado ao redor de (88,72) com portão de ferro em (92,60)
        run {
            val cx0 = 78f; val cx1 = 102f; val cz0 = 60f; val cz1 = 84f
            line(PK.WALL, cx0, cz0, 89.5f, cz0, 3.0f); line(PK.WALL, 94.5f, cz0, cx1, cz0, 3.0f)
            line(PK.WALL, cx0, cz1, cx1, cz1, 3.0f); line(PK.WALL, cx0, cz0, cx0, cz1, 3.0f); line(PK.WALL, cx1, cz0, cx1, cz1, 3.0f)
            add(PK.STATUE, 82f, 66f, 1f); add(PK.STATUE, 98f, 66f, 1f); add(PK.PILLAR, 86f, 78f, 1f); add(PK.PILLAR, 98f, 78f, 1f)
            add(PK.TORCH, 90f, 62f, 1f); add(PK.TORCH, 94f, 62f, 1f)
        }
        // --- pico
        scatter(46, Z.PICO, 1.5f, 5000, { x, z -> hAt(x, z) < 11f }) { x, z -> add(if (r.nextInt(4) == 0) PK.ROCK_RUST else PK.ROCK, x, z, 0.8f + r.nextFloat() * 1.0f) }
        scatter(18, Z.PICO, 3f, 3000) { x, z -> add(PK.CLIFF, x, z, 0.9f + r.nextFloat() * 0.6f) }
        scatter(40, Z.PICO, 1.5f, 4000, { x, z -> hAt(x, z) > 4f }) { x, z -> add(PK.SNOWPINE, x, z, 0.8f + r.nextFloat() * 0.5f) }
        scatter(14, Z.PICO, 1.5f, 3000) { x, z -> add(PK.DEAD, x, z, 0.9f + r.nextFloat() * 0.4f) }
        for (i in 0 until 6) { val a = i / 6f * 2f * PI.toFloat(); add(PK.BANNER, 168f + cos(a) * 12.5f, 64f + sin(a) * 12.5f, 1.2f, 0.3f, a) }
        // --- caverna
        scatter(42, Z.CAVERNA, 1f, 5000, { x, z -> hAt(x, z) < 3f }) { x, z -> add(if (r.nextBoolean()) PK.CRYSTAL_B else PK.CRYSTAL_P, x, z, 0.7f + r.nextFloat() * 0.9f) }
        scatter(26, Z.CAVERNA, 1f, 3000, { x, z -> hAt(x, z) < 3f }) { x, z -> add(PK.STALAG, x, z, 0.7f + r.nextFloat() * 0.8f) }
        scatter(16, Z.CAVERNA, 1.5f, 3000, { x, z -> hAt(x, z) < 3f }) { x, z -> add(PK.ROCK, x, z, 0.8f + r.nextFloat() * 0.7f) }
        // --- santuário
        for (i in 0 until 12) { val a = i / 12f * 2f * PI.toFloat(); add(PK.TEMPLE_COL, 100f + cos(a) * 17f, 14f + sin(a) * 17f * 0.55f, 1f) }
        scatter(10, Z.SANTUARIO, 2f, 2000) { x, z -> add(PK.CRYSTAL_P, x, z, 0.8f + r.nextFloat() * 0.8f) }
        add(PK.STATUE, 88f, 30f, 1f); add(PK.STATUE, 112f, 30f, 1f); add(PK.TORCH, 94f, 36f, 1f); add(PK.TORCH, 106f, 36f, 1f)
        // --- jardim secreto
        scatter(18, Z.JARDIM, 1.2f, 2000) { x, z -> add(if (r.nextBoolean()) PK.OAK else PK.BIRCH, x, z, 0.8f + r.nextFloat() * 0.4f) }
        scatter(26, Z.JARDIM, 0.5f, 2000) { x, z -> add(if (r.nextBoolean()) PK.FLOWER_P else PK.FLOWER_B, x, z, 1.2f) }
        // --- muralha rachada do jardim
        add(PK.CLIFF, 40f, 25f, 1.0f); add(PK.CLIFF, 40f, 35f, 1.0f)

        // --- cobertura baixa
        scatter(300, -1, 0.3f, 8000, { x, z -> val zn = zoneAt(x, z); (zn == Z.CAMPINA || zn == Z.FLORESTA || zn == Z.VILA || zn == Z.LAGO || zn == Z.JARDIM) && hAt(x, z) > -0.3f && hAt(x, z) < 6f }) { x, z ->
            val zn = zoneAt(x, z)
            when {
                zn == Z.FLORESTA && r.nextInt(3) == 0 -> add(PK.MUSHROOM, x, z, 1f, 0f)
                r.nextInt(3) == 0 -> add(when (r.nextInt(3)) { 0 -> PK.FLOWER_P; 1 -> PK.FLOWER_Y; else -> PK.FLOWER_B }, x, z, 1f, 0f)
                else -> add(PK.GRASS, x, z, 0.9f + r.nextFloat() * 0.5f, 0f)
            }
        }
        // ervas coletáveis (missão da Vovó)
        val herbSpots = listOf(floatArrayOf(112f, 100f), floatArrayOf(86f, 106f), floatArrayOf(118f, 132f), floatArrayOf(78f, 124f))
        for (h in herbSpots) add(PK.HERB, h[0], h[1], 1.2f, 0f)
        // --- montanhas de fundo (fora do mapa)
        for (i in 0 until 30) {
            val t = i / 30f * 2f * PI.toFloat()
            val x = SIZE / 2f + cos(t) * (SIZE * 0.68f + r.nextFloat() * 20f); val z = SIZE / 2f + sin(t) * (SIZE * 0.68f + r.nextFloat() * 20f)
            val p = Prop(PK.MOUNTAIN, x, z, r.nextFloat() * 6f, 2.2f + r.nextFloat() * 1.6f, 0f); p.y = -4f; out.add(p)
        }
        return out.toTypedArray()
    }

    // ------------------------------------------------------------------ inimigos
    /** Tabela de geração: tipos em [AdvEnemies]. */
    fun spawns(): List<SpawnDef> {
        val r = Random(77)
        val out = ArrayList<SpawnDef>()
        fun place(kind: Int, n: Int, zone: Int, minPlayerDist: Float = 12f, f: (Float, Float) -> Boolean = { _, _ -> true }) {
            var c = 0; var t = 0
            while (c < n && t < 3000) {
                t++
                val x = 6f + r.nextFloat() * (SIZE - 12f); val z = 6f + r.nextFloat() * (SIZE - 12f)
                if (zoneAt(x, z) != zone) continue
                if (hAt(x, z) < -0.5f) continue
                if (!f(x, z)) continue
                var bad = false
                for (a in arenas.indices) if (dist(x, z, arenas[a][0], arenas[a][1]) < arenaR[a] + 2f) bad = true
                for (b in bonfires) if (dist(x, z, b[0], b[1]) < 8f) bad = true
                for (d in doors) if (dist(x, z, d.x, d.z) < 4f) bad = true
                if (dist(x, z, 100f, 150f) < 20f) bad = true
                if (dist(x, z, 100f, 126f) < 10f) bad = true
                if (blockedByProps(x, z, 1.2f)) continue
                if (bad) continue
                out.add(SpawnDef(kind, x, z, zone)); c++
            }
        }
        // campina: tutorial leve
        place(0, 9, Z.CAMPINA) { _, z -> z > 92f }
        place(5, 3, Z.CAMPINA)
        place(0, 3, Z.CAMPINA) { _, z -> z <= 92f }
        // floresta
        place(0, 5, Z.FLORESTA); place(1, 9, Z.FLORESTA); place(4, 5, Z.FLORESTA); place(5, 2, Z.FLORESTA)
        // lago
        place(6, 9, Z.LAGO) { x, z -> hAt(x, z) < 1.5f }; place(0, 3, Z.LAGO); place(1, 3, Z.LAGO)
        // ruínas
        place(2, 8, Z.RUINAS) { x, z -> x < 74f || z > 86f || x > 104f }; place(8, 5, Z.RUINAS); place(4, 4, Z.RUINAS); place(1, 3, Z.RUINAS)
        // pico
        place(3, 8, Z.PICO); place(2, 4, Z.PICO); place(9, 2, Z.PICO); place(4, 3, Z.PICO)
        // caverna
        place(7, 8, Z.CAVERNA); place(3, 3, Z.CAVERNA); place(11, 4, Z.CAVERNA)
        // santuário
        place(3, 4, Z.SANTUARIO); place(9, 2, Z.SANTUARIO); place(8, 3, Z.SANTUARIO)
        // jardim (pequeno)
        place(5, 2, Z.JARDIM)
        // cripta: guardiões fixos
        out.add(SpawnDef(8, 84f, 70f, Z.RUINAS)); out.add(SpawnDef(8, 96f, 70f, Z.RUINAS)); out.add(SpawnDef(9, 90f, 76f, Z.RUINAS)); out.add(SpawnDef(2, 84f, 80f, Z.RUINAS))
        // campo de treino
        out.add(SpawnDef(10, 100f, 123f, Z.VILA)); out.add(SpawnDef(0, 94f, 128f, Z.VILA)); out.add(SpawnDef(0, 106f, 128f, Z.VILA))
        return out
    }

    // ------------------------------------------------------------------ fauna decorativa
    class Critter(val kind: Int, var x: Float, var z: Float, val hx: Float, val hz: Float, val ph: Float) { var vx = 0f; var vz = 0f; var t = 0f }

    fun critters(): List<Critter> {
        val r = Random(5)
        val out = ArrayList<Critter>()
        var n = 0; var t = 0
        while (n < 26 && t < 2000) {
            t++
            val x = 8f + r.nextFloat() * (SIZE - 16f); val z = 8f + r.nextFloat() * (SIZE - 16f)
            val zn = zoneAt(x, z)
            if (zn != Z.CAMPINA && zn != Z.VILA && zn != Z.FLORESTA && zn != Z.JARDIM) continue
            if (hAt(x, z) < 0f || blockedByProps(x, z, 0.6f)) continue
            out.add(Critter(0, x, z, x, z, r.nextFloat() * 6f)); n++
        }
        for (i in 0 until 14) { val x = 40f + r.nextFloat() * 130f; val z = 90f + r.nextFloat() * 90f; out.add(Critter(1, x, z, x, z, r.nextFloat() * 6f)) }
        for (i in 0 until 16) { val x = 40f + r.nextFloat() * 130f; val z = 60f + r.nextFloat() * 120f; if (hAt(x, z) > 0f) out.add(Critter(2, x, z, x, z, r.nextFloat() * 6f)) }
        for (i in 0 until 7) { val x = 136f + r.nextFloat() * 40f; val z = 142f + r.nextFloat() * 36f; if (isWater(x, z)) out.add(Critter(3, x, z, x, z, r.nextFloat() * 6f)) }
        return out
    }

    fun collectHerbSpots(): List<Prop> = props.filter { it.kind == PK.HERB }
}
