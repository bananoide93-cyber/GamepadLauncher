package com.gamepadlayout.app.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Dados dos monstros: 54 espécies em 5 áreas, 5 raridades, 6 tipos. Sprites gerados por semente. */
object MonDex {
    class Def(val id: Int, val name: String, val type: Int, val rarity: Int, val area: Int) {
        val hpB = 20 + area * 4 + rarity * 5 + (id * 7) % 6
        val atkB = 8 + area * 2 + rarity * 3 + (id * 5) % 4
        val defB = 8 + area * 2 + rarity * 2 + (id * 3) % 4
        fun hpAt(lv: Int) = hpB + lv * 4
        fun atkAt(lv: Int) = atkB + lv * 1.5f
        fun defAt(lv: Int) = defB + lv * 1.3f
        val specialName get() = typeMove[type]
        val specialPower get() = 55 + rarity * 8
    }

    val typeNames = listOf("Planta", "Fogo", "Água", "Terra", "Sombra", "Raio")
    val typeMove = listOf("Folhada", "Brasa", "Jato", "Pedrada", "Sombria", "Faísca")
    val typeColor = longArrayOf(P.GREEN, P.ORANGE, P.SKY, 0xFFB08D57L, P.VIOLET, P.YELLOW)
    val rarityNames = listOf("Comum", "Incomum", "Raro", "Épico", "Lendário")
    val rarityColor = longArrayOf(P.SILVER, P.LIME, P.SKY, P.VIOLET, P.YELLOW)
    val rarityWeight = intArrayOf(50, 28, 14, 6, 2)
    val rarityCatch = floatArrayOf(0.55f, 0.42f, 0.30f, 0.18f, 0.08f)
    val areaNames = listOf("Campos Verdes", "Floresta Sombria", "Cavernas de Cristal", "Dunas Douradas", "Ilha do Vulcão")
    val areaNeed = intArrayOf(0, 4, 10, 18, 28)

    private const val P_ = 0; private const val F_ = 1; private const val A_ = 2
    private const val T_ = 3; private const val S_ = 4; private const val R_ = 5

    private val raw: List<List<Pair<String, Int>>> = listOf(
        listOf("Folhito" to P_, "Brasito" to F_, "Gotinha" to A_, "Pipino" to R_, "Joaninha" to P_, "Sapito" to A_, "Coelhudo" to T_, "Florarainha" to P_),
        listOf("Cogulim" to P_, "Mofado" to S_, "Corujin" to S_, "Raizão" to P_, "Vagalume" to R_, "Aranhel" to S_, "Lobrume" to S_, "Morcegão" to S_, "Cervelua" to P_, "Espectrel" to S_),
        listOf("Pedrinho" to T_, "Geodão" to T_, "Cristalim" to A_, "Fagulhita" to R_, "Lamparino" to F_, "Tatuferro" to T_, "Topazito" to R_, "Morcristal" to S_, "Ametisto" to S_, "Gemeon" to T_, "Estalactor" to A_, "Rochedrake" to F_),
        listOf("Escarabel" to T_, "Cactuso" to P_, "Escorpix" to T_, "Areião" to T_, "Solzito" to F_, "Camelume" to T_, "Miragem" to S_, "Fenixito" to F_, "Dunhorn" to T_, "Faraonix" to S_),
        listOf("Brasinha" to F_, "Magmito" to F_, "Cinzel" to F_, "Fumacinha" to S_, "Caranguelo" to A_, "Salamandro" to F_, "Lavador" to F_, "Raiolume" to R_, "Tsunamito" to A_, "Obsidiano" to T_, "Trovão" to R_, "Vulcanis" to F_, "Pirodrake" to F_, "Titanvulc" to T_)
    )

    val all: List<Def> = run {
        val out = ArrayList<Def>()
        var id = 0
        for (a in raw.indices) {
            val n = raw[a].size
            for (i in 0 until n) {
                val f = if (n > 1) i.toFloat() / (n - 1) else 0f
                val r = when {
                    i == n - 1 -> 4
                    f < 0.4f -> 0
                    f < 0.65f -> 1
                    f < 0.85f -> 2
                    else -> 3
                }
                out.add(Def(id++, raw[a][i].first, raw[a][i].second, r, a))
            }
        }
        out
    }

    fun inArea(a: Int) = all.filter { it.area == a }

    /** Eficácia do tipo atacante sobre o defensor (2x, 1x, 0,5x). */
    fun eff(att: Int, def: Int): Float {
        val strong = setOf(1 to 0, 0 to 2, 2 to 1, 2 to 3, 0 to 3, 3 to 5, 5 to 2, 3 to 1, 4 to 0, 1 to 4, 5 to 4)
        if (att to def in strong) return 2f
        if (def to att in strong) return 0.5f
        return 1f
    }

    // ---- sprites (20x16, desenhados em PixArt.kt)
    private val cache = HashMap<Int, Spr>()
    fun sprite(d: Def): Spr = cache.getOrPut(d.id) { MonArt.build(d.name, d.id, d.type, d.rarity) }

    fun drawMon(v: View, d: Def, x: Float, y: Float, px: Float, flip: Boolean = false, silhouette: Boolean = false) {
        sprite(d).draw(v, x, y, px, flip, silhouette = if (silhouette) P.DUSK else 0L)
    }
}

/**
 * Explore 5 áreas, enfrente monstros selvagens, capture-os com cristais e complete a Dex.
 * O progresso é salvo automaticamente.
 */
class MonstersGame : MiniGame(
    "monsters", "Caçadores de Monstros",
    "Explore 5 áreas, capture 54 monstros de 5 raridades e complete a Dex.",
    "D-pad/analógico: mover e escolher · A: confirmar · X/Y: Dex ou voltar · toque: arraste e toque"
) {
    override val persistent = true

    private enum class Mode { STARTER, EXPLORE, BATTLE, DEX, CAMP }
    private enum class Bp { TEXT, MENU, MOVES, ITEMS, SWAP, CAPT }
    private class Own(val id: Int, var lv: Int, var exp: Int, var n: Int) { var hp = 0 }
    private class Foe(val def: MonDex.Def, val lv: Int) { var hp = def.hpAt(lv) }

    private val rnd = Random.Default
    private var mode = Mode.STARTER
    private var time = 0f
    private val owned = LinkedHashMap<Int, Own>()
    private val seen = HashSet<Int>()
    private val party = ArrayList<Int>()
    private var coins = 30
    private var inv = intArrayOf(6, 1, 0, 3) // cristal, super, ultra, poção
    private var area = 0
    private var tx = 1
    private var ty = 6
    private var fx = 1
    private var fy = 6
    private var mt = 1f
    private var cursor = 0
    private var note = ""
    private var noteT = 0f
    private var grassCool = 0
    private var map = Array(12) { CharArray(25) { '.' } }
    private var steps = 0

    // batalha
    private var foe: Foe? = null
    private var bp = Bp.TEXT
    private var bText = ""
    private var bT = 0f
    private var bNext: (() -> Unit)? = null
    private var active = 0
    private var bCursor = 0
    private var forced = false
    private var capT = 0f
    private var capShakes = 0
    private var capOk = false
    private var capTier = 0
    private var flashFoe = 0f
    private var flashMe = 0f

    // loja
    private val shopItems = listOf("Cristal" to 10, "Cristal Super" to 25, "Cristal Ultra" to 60, "Poção" to 15)

    init { reset() }

    override fun reset() {
        owned.clear(); seen.clear(); party.clear()
        coins = 30; inv = intArrayOf(6, 1, 0, 3)
        area = 0; tx = 1; ty = 6; fx = 1; fy = 6; mt = 1f
        mode = Mode.STARTER; cursor = 0
        score = 0; over = false
        genMap()
    }

    // ---------------------------------------------------------------- persistência
    override fun save(): String {
        val o = owned.values.joinToString(",") { "${it.id}:${it.lv}:${it.exp}:${it.n}" }
        return listOf(
            "1", coins.toString(), inv.joinToString(","), area.toString(), party.joinToString(","),
            o, seen.joinToString(",")
        ).joinToString(";")
    }

    override fun load(data: String) {
        val p = data.split(";")
        if (p.size < 7 || p[0] != "1") return
        owned.clear(); seen.clear(); party.clear()
        coins = p[1].toInt()
        val iv = p[2].split(",").map { it.toInt() }
        if (iv.size == 4) inv = iv.toIntArray()
        area = p[3].toInt().coerceIn(0, 4)
        if (p[5].isNotEmpty()) for (e in p[5].split(",")) {
            val q = e.split(":")
            val o = Own(q[0].toInt(), q[1].toInt(), q[2].toInt(), q[3].toInt())
            if (o.id in MonDex.all.indices) { o.hp = MonDex.all[o.id].hpAt(o.lv); owned[o.id] = o }
        }
        if (p[4].isNotEmpty()) for (s in p[4].split(",")) { val i = s.toInt(); if (owned.containsKey(i)) party.add(i) }
        if (p[6].isNotEmpty()) for (s in p[6].split(",")) seen.add(s.toInt())
        seen.addAll(owned.keys)
        genMap()
        tx = 1; ty = 6; fx = 1; fy = 6; mt = 1f
        mode = if (party.isEmpty()) Mode.STARTER else Mode.EXPLORE
        updateScore()
    }

    private fun updateScore() {
        score = owned.size * 10
        if (score > best) best = score
    }

    // ---------------------------------------------------------------- mapa
    private fun genMap() {
        val r = Random(area * 977 + 13)
        val m = Array(12) { CharArray(25) { '.' } }
        for (x in 0 until 25) { m[0][x] = '#'; m[11][x] = '#' }
        for (y in 0 until 12) { m[y][0] = '#'; m[y][24] = '#' }
        for (i in 0 until 60) {
            val x = 1 + r.nextInt(23); val y = 1 + r.nextInt(10)
            if (y in 5..7) continue
            m[y][x] = '#'
        }
        for (k in 0 until 2) {
            val x = 3 + r.nextInt(17)
            val y = if (k == 0) 1 + r.nextInt(2) else 9
            for (yy in y..min(10, y + 1)) for (xx in x..x + 2) if (yy !in 5..7) m[yy][xx] = 'W'
        }
        for (k in 0 until 7) {
            val x = 1 + r.nextInt(20); val y = 1 + r.nextInt(8)
            for (yy in y..y + 2) for (xx in x..x + 3) if (yy in 1..10 && xx in 1..23 && m[yy][xx] == '.') m[yy][xx] = ','
        }
        for (x in 1..5) for (y in 5..7) m[y][x] = '.'
        m[6][4] = 'C'
        m[6][24] = if (area < 4) 'E' else '#'
        m[6][0] = if (area > 0) 'B' else '#'
        map = m
    }

    private val palGround = longArrayOf(0xFF3E7C45L, 0xFF28464AL, 0xFF3A4A66L, 0xFFC8A55AL, 0xFF4B2A2EL)
    private val palGrass = longArrayOf(0xFF2E9650L, 0xFF1F6E5AL, 0xFF4E6E9BL, 0xFFD9B66CL, 0xFF6D2E26L)
    private val palWall = longArrayOf(0xFF1F4D2EL, 0xFF16282CL, 0xFF222B44L, 0xFF8B6B35L, 0xFF2A1518L)
    private val palWater = longArrayOf(0xFF3B6FD0L, 0xFF304F8AL, 0xFF73EFF7L, 0xFF4FA8C8L, 0xFFFF6A2BL)

    // ---------------------------------------------------------------- loop
    override fun update(dt: Float, input: GameInput) {
        time += dt
        if (noteT > 0f) noteT -= dt
        flashFoe = max(0f, flashFoe - dt); flashMe = max(0f, flashMe - dt)
        when (mode) {
            Mode.STARTER -> updateStarter(input)
            Mode.EXPLORE -> updateExplore(dt, input)
            Mode.BATTLE -> updateBattle(dt, input)
            Mode.DEX -> updateDex(input)
            Mode.CAMP -> updateCamp(input)
        }
    }

    private fun popNote(t: String) { note = t; noteT = 2.6f }

    private fun updateStarter(input: GameInput) {
        if (input.stepX != 0) cursor = (cursor + input.stepX + 3) % 3
        if (input.firePressed) {
            val id = cursor
            val o = Own(id, 5, 0, 1); o.hp = MonDex.all[id].hpAt(5)
            owned[id] = o; seen.add(id); party.add(id)
            mode = Mode.EXPLORE
            updateScore(); saveDirty = true
            popNote("Boa! ${MonDex.all[id].name} é seu parceiro")
        }
    }

    private fun passable(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x > 24 || y > 11) return false
        val c = map[y][x]
        return c != '#' && c != 'W'
    }

    private fun healAll() { for (i in party) owned[i]?.let { it.hp = MonDex.all[it.id].hpAt(it.lv) } }

    private fun updateExplore(dt: Float, input: GameInput) {
        if (input.altPressed) { mode = Mode.DEX; cursor = party.firstOrNull() ?: 0; return }
        if (mt < 1f) {
            mt = min(1f, mt + dt * 7f)
            if (mt >= 1f) arrive()
            return
        }
        val dx = input.dx; val dy = input.dy
        var nx = tx; var ny = ty
        if (abs(dx) > 0.5f && abs(dx) >= abs(dy)) nx += if (dx > 0) 1 else -1
        else if (abs(dy) > 0.5f) ny += if (dy > 0) 1 else -1
        else return
        if (!passable(nx, ny)) return
        val c = map[ny][nx]
        if (c == 'E' && MonDex.all.count { owned.containsKey(it.id) } < MonDex.areaNeed[area + 1]) {
            popNote("Precisa de ${MonDex.areaNeed[area + 1]} monstros (você tem ${owned.size})"); return
        }
        fx = tx; fy = ty; tx = nx; ty = ny; mt = 0f
    }

    private fun arrive() {
        val c = map[ty][tx]
        steps++
        when (c) {
            'E' -> { area++; genMap(); tx = 1; ty = 6; fx = 1; fy = 6; healAll(); saveDirty = true; popNote("${MonDex.areaNames[area]}!") }
            'B' -> { area--; genMap(); tx = 23; ty = 6; fx = 23; fy = 6; healAll(); saveDirty = true; popNote("${MonDex.areaNames[area]}!") }
            'C' -> { healAll(); mode = Mode.CAMP; cursor = 0; popNote("Equipe curada!") }
            ',' -> {
                if (grassCool > 0) grassCool-- else if (rnd.nextFloat() < 0.2f) { grassCool = 3; startWild() }
            }
        }
    }

    // ---------------------------------------------------------------- acampamento
    private fun updateCamp(input: GameInput) {
        val n = shopItems.size + 1
        if (input.stepY != 0) cursor = (cursor + input.stepY + n) % n
        if (input.altPressed) { mode = Mode.EXPLORE; return }
        if (input.firePressed) {
            if (cursor == shopItems.size) { mode = Mode.EXPLORE; return }
            val p = shopItems[cursor].second
            if (coins >= p) { coins -= p; inv[cursor]++; saveDirty = true; popNote("Comprou ${shopItems[cursor].first}") }
            else popNote("Moedas insuficientes")
        }
    }

    // ---------------------------------------------------------------- dex
    private fun updateDex(input: GameInput) {
        val n = MonDex.all.size
        if (input.stepX != 0) cursor = (cursor + input.stepX + n) % n
        if (input.stepY != 0) cursor = (cursor + input.stepY * 9 + n) % n
        if (input.altPressed) { mode = Mode.EXPLORE; return }
        if (input.firePressed && owned.containsKey(cursor)) {
            if (party.contains(cursor)) { if (party.size > 1) party.remove(cursor) }
            else if (party.size < 4) party.add(cursor)
            else { party.removeAt(3); party.add(cursor) }
            saveDirty = true
        }
    }

    // ---------------------------------------------------------------- batalha
    private fun startWild() {
        val defs = MonDex.inArea(area)
        val total = defs.sumOf { MonDex.rarityWeight[it.rarity] }
        var r = rnd.nextInt(total)
        var pick = defs[0]
        for (d in defs) { r -= MonDex.rarityWeight[d.rarity]; if (r < 0) { pick = d; break } }
        val lv = (area * 5 + 3 + rnd.nextInt(4) + pick.rarity * 2).coerceIn(2, 50)
        foe = Foe(pick, lv)
        seen.add(pick.id)
        active = party.indexOfFirst { (owned[it]?.hp ?: 0) > 0 }
        if (active < 0) { healAll(); active = 0 }
        mode = Mode.BATTLE
        bCursor = 0
        say("Um ${pick.name} selvagem apareceu!") { bp = Bp.MENU; bCursor = 0 }
    }

    private fun say(t: String, next: () -> Unit) { bText = t; bT = 0f; bNext = next; bp = Bp.TEXT }

    private fun me() = owned[party[active]]!!
    private fun meDef() = MonDex.all[me().id]

    private fun damage(lv: Int, power: Int, atk: Float, def: Float, eff: Float): Int =
        (((2f * lv / 5f + 2f) * power * atk / def / 30f + 2f) * eff * (0.85f + rnd.nextFloat() * 0.15f)).toInt().coerceAtLeast(1)

    private fun effText(e: Float) = if (e > 1.5f) " Foi super eficaz!" else if (e < 0.9f) " Pouco eficaz..." else ""

    private fun playerAttack(special: Boolean) {
        val f = foe ?: return
        val m = me(); val d = meDef()
        val power = if (special) d.specialPower else 40
        val e = if (special) MonDex.eff(d.type, f.def.type) else 1f
        val dmg = damage(m.lv, power, d.atkAt(m.lv), f.def.defAt(f.lv), e)
        f.hp = max(0, f.hp - dmg)
        flashFoe = 0.3f
        val mv = if (special) d.specialName else "Investida"
        say("${d.name} usou $mv! -$dmg.${effText(e)}") { if (f.hp <= 0) win() else enemyTurn() }
    }

    private fun enemyTurn() {
        val f = foe ?: return
        val m = me(); val d = meDef()
        val special = rnd.nextFloat() < 0.6f
        val power = if (special) f.def.specialPower else 40
        val e = if (special) MonDex.eff(f.def.type, d.type) else 1f
        val dmg = damage(f.lv, power, f.def.atkAt(f.lv), d.defAt(m.lv), e)
        m.hp = max(0, m.hp - dmg)
        flashMe = 0.3f
        val mv = if (special) f.def.specialName else "Investida"
        say("${f.def.name} usou $mv! -$dmg.${effText(e)}") {
            if (m.hp <= 0) {
                if (party.any { (owned[it]?.hp ?: 0) > 0 }) say("${d.name} desmaiou!") { bp = Bp.SWAP; forced = true; bCursor = 0 }
                else lose()
            } else bp = Bp.MENU
        }
    }

    private fun win() {
        val f = foe ?: return
        val m = me()
        val gain = f.lv * 6 + f.def.rarity * 10
        val coin = 4 + f.lv + f.def.rarity * 5
        coins += coin
        m.exp += gain
        var lvUp = false
        while (m.exp >= m.lv * 12 && m.lv < 50) { m.exp -= m.lv * 12; m.lv++; lvUp = true }
        saveDirty = true
        val extra = if (lvUp) " ${meDef().name} subiu para o nível ${m.lv}!" else ""
        say("Venceu! +$gain EXP, +$coin moedas.$extra") { endBattle() }
    }

    private fun lose() {
        say("Todos os seus monstros desmaiaram...") {
            coins = coins / 2
            healAll()
            tx = 4; ty = 6; fx = 4; fy = 6; mt = 1f
            saveDirty = true
            endBattle()
            popNote("Você acordou no acampamento")
        }
    }

    private fun endBattle() { foe = null; mode = Mode.EXPLORE; updateScore() }

    private fun throwCrystal(tier: Int) {
        val f = foe ?: return
        inv[tier]--
        val mul = floatArrayOf(1f, 1.6f, 2.6f)[tier]
        val hpF = f.hp.toFloat() / f.def.hpAt(f.lv)
        val chance = (MonDex.rarityCatch[f.def.rarity] * mul * (1f + (1f - hpF) * 1.6f)).coerceIn(0.03f, 0.95f)
        capOk = rnd.nextFloat() < chance
        capShakes = if (capOk) 3 else 1 + rnd.nextInt(2)
        capT = 0f; capTier = tier
        bText = "Lançou o ${shopItems[tier].first}!"
        bp = Bp.CAPT
    }

    private fun resolveCapture() {
        val f = foe ?: return
        if (capOk) {
            val dup = owned[f.def.id]
            if (dup != null) {
                dup.n++
                coins += 20 + f.def.rarity * 10
                say("Capturou ${f.def.name}! (repetido: +${20 + f.def.rarity * 10} moedas)") { endBattle() }
            } else {
                val o = Own(f.def.id, f.lv, 0, 1); o.hp = f.def.hpAt(f.lv)
                owned[f.def.id] = o
                if (party.size < 4) party.add(f.def.id)
                say("Capturou ${f.def.name}! Registrado na Dex.") { endBattle() }
            }
            saveDirty = true
        } else {
            say("${f.def.name} escapou do cristal!") { enemyTurn() }
        }
    }

    private fun updateBattle(dt: Float, input: GameInput) {
        when (bp) {
            Bp.TEXT -> {
                bT += dt
                if (bT > 1.5f || (input.firePressed && bT > 0.25f)) { val n = bNext; bNext = null; n?.invoke() }
            }
            Bp.CAPT -> {
                capT += dt
                if (capT > 0.8f + capShakes * 0.6f) resolveCapture()
            }
            Bp.MENU -> {
                if (input.stepX != 0) bCursor = bCursor xor 1
                if (input.stepY != 0) bCursor = bCursor xor 2
                if (input.firePressed) when (bCursor) {
                    0 -> { bp = Bp.MOVES; bCursor = 0 }
                    1 -> { bp = Bp.ITEMS; bCursor = 0 }
                    2 -> { bp = Bp.SWAP; forced = false; bCursor = 0 }
                    else -> {
                        val f = foe!!
                        if (rnd.nextFloat() < 0.65f - f.def.rarity * 0.08f) say("Fugiu em segurança!") { endBattle() }
                        else say("Não conseguiu fugir!") { enemyTurn() }
                    }
                }
            }
            Bp.MOVES -> {
                if (input.stepY != 0 || input.stepX != 0) bCursor = bCursor xor 1
                if (input.altPressed) { bp = Bp.MENU; bCursor = 0; return }
                if (input.firePressed) playerAttack(bCursor == 1)
            }
            Bp.ITEMS -> {
                if (input.stepY != 0) bCursor = (bCursor + input.stepY + 4) % 4
                if (input.altPressed) { bp = Bp.MENU; bCursor = 1; return }
                if (input.firePressed) {
                    if (inv[bCursor] <= 0) return
                    if (bCursor < 3) throwCrystal(bCursor)
                    else {
                        val m = me(); val mx = meDef().hpAt(m.lv)
                        if (m.hp >= mx) return
                        inv[3]--; m.hp = min(mx, m.hp + 35)
                        say("${meDef().name} recuperou vida!") { enemyTurn() }
                    }
                }
            }
            Bp.SWAP -> {
                if (input.stepY != 0) bCursor = (bCursor + input.stepY + party.size) % party.size
                if (input.altPressed && !forced) { bp = Bp.MENU; bCursor = 2; return }
                if (input.firePressed) {
                    val o = owned[party[bCursor]]!!
                    if (o.hp <= 0 || bCursor == active) return
                    active = bCursor
                    val wasForced = forced
                    forced = false
                    say("Vai, ${MonDex.all[o.id].name}!") { if (wasForced) bp = Bp.MENU else enemyTurn() }
                }
            }
        }
    }

    // ---------------------------------------------------------------- desenho
    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(P.INK)
        when (mode) {
            Mode.STARTER -> drawStarter(v)
            Mode.EXPLORE -> drawExplore(v)
            Mode.BATTLE -> drawBattle(v)
            Mode.DEX -> drawDex(v)
            Mode.CAMP -> { drawExplore(v); drawCamp(v) }
        }
    }

    private fun typeTag(v: View, type: Int, x: Float, y: Float) {
        v.rect(x, y, 66f, 14f, MonDex.typeColor[type])
        v.pixText(MonDex.typeNames[type], x + 33f, y + 3f, 1.4f, P.INK, center = true, shadow = false)
    }

    private fun drawStarter(v: View) {
        v.pixText("ESCOLHA SEU PARCEIRO", 400f, 24f, 4.2f, P.YELLOW, center = true)
        for (i in 0 until 3) {
            val d = MonDex.all[i]
            val x = 50f + i * 245f
            v.bevel(x, 90f, 220f, 250f, if (i == cursor) P.VIOLET else P.DUSK)
            v.rect(x + 4f, 94f, 212f, 242f, P.INK)
            MonDex.drawMon(v, d, x + 30f, 112f, 8f)
            v.pixText(d.name, x + 110f, 258f, 2.8f, P.WHITE, center = true)
            typeTag(v, d.type, x + 77f, 288f)
            v.pixText("Nível 5", x + 110f, 312f, 1.8f, P.LILAC, center = true)
        }
        v.pixText("D-PAD ESCOLHER   A CONFIRMAR", 400f, 372f, 2.2f, P.CYAN, center = true, shadow = false)
        v.pixText("54 MONSTROS - 5 ÁREAS - 5 RARIDADES", 400f, 400f, 1.8f, P.SILVER, center = true, shadow = false)
    }

    private fun drawExplore(v: View) {
        val a = area
        for (y in 0 until 12) for (x in 0 until 25) {
            val c = map[y][x]
            val px = x * 32f; val py = 38f + y * 32f
            val base = if ((x + y) % 2 == 0) palGround[a] else C.shade(palGround[a], 0.94f)
            when (c) {
                '#' -> {
                    v.rect(px, py, 32.5f, 32.5f, palGround[a])
                    v.pixDisc(px + 16f, py + 14f, 15f, palWall[a], 4f)
                    v.pixDisc(px + 16f, py + 11f, 11f, C.shade(palGrass[a], 0.8f), 4f)
                    v.rect(px + 14f, py + 24f, 5f, 8f, 0xFF5B3A1FL)
                }
                'W' -> { v.rect(px, py, 32.5f, 32.5f, palWater[a]); v.rect(px + 4f, py + 10f + (time * 6f + x).toInt() % 3 * 4f, 12f, 3f, P.light(palWater[a], 0.4f)) }
                ',' -> {
                    v.rect(px, py, 32.5f, 32.5f, palGrass[a])
                    for (k in 0 until 3) v.rect(px + 4f + k * 10f, py + 6f + (k % 2) * 8f, 3f, 14f, C.shade(palGrass[a], 0.7f))
                    v.rect(px + 5f, py + 20f, 22f, 3f, P.light(palGrass[a], 0.25f))
                }
                'C' -> { v.rect(px, py, 32.5f, 32.5f, base); v.rect(px + 4f, py + 8f, 24f, 20f, P.RED); v.rect(px + 8f, py + 4f, 16f, 6f, P.WHITE); v.rect(px + 13f, py + 16f, 6f, 12f, P.YELLOW) }
                'E', 'B' -> {
                    v.rect(px, py, 32.5f, 32.5f, base)
                    v.rect(px + 2f, py + 2f, 28f, 28f, C.alpha(P.VIOLET, 0.6f + 0.3f * kotlin.math.sin(time * 4f)))
                    v.pixText(if (c == 'E') ">" else "<", px + 16f, py + 9f, 2.4f, P.WHITE, center = true)
                }
                else -> v.rect(px, py, 32.5f, 32.5f, base)
            }
        }
        val t = mt.coerceIn(0f, 1f)
        val ex = (fx + (tx - fx) * t) * 32f
        val ey = 38f + (fy + (ty - fy) * t) * 32f
        v.pixDisc(ex + 16f, ey + 28f, 10f, C.alpha(P.INK, 0.4f), 3f)
        v.rect(ex + 8f, ey + 14f, 16f, 14f, P.RED)
        v.rect(ex + 9f, ey + 2f, 14f, 13f, 0xFFF1C27DL)
        v.rect(ex + 8f, ey, 16f, 5f, P.NAVY)
        v.rect(ex + 12f, ey + 8f, 3f, 3f, P.INK); v.rect(ex + 18f, ey + 8f, 3f, 3f, P.INK)
        v.rect(ex + 9f, ey + 28f, 5f, 4f, P.INK); v.rect(ex + 18f, ey + 28f, 5f, 4f, P.INK)
        v.hudBar(MonDex.areaNames[area], "DEX ${owned.size}/54   MOEDAS $coins")
        if (noteT > 0f) {
            v.rect(150f, 392f, 500f, 34f, C.alpha(P.INK, 0.9f))
            v.rect(150f, 392f, 500f, 2f, P.VIOLET)
            v.pixText(note, 400f, 402f, 2f, P.WHITE, center = true, shadow = false)
        } else v.pixText("X/Y: DEX E EQUIPE", 400f, 432f, 1.6f, P.LILAC, center = true, shadow = false)
    }

    private fun drawCamp(v: View) {
        v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.55f))
        v.bevel(220f, 60f, 360f, 300f, P.DUSK)
        v.rect(224f, 64f, 352f, 292f, P.INK)
        v.pixText("ACAMPAMENTO", 400f, 76f, 3f, P.YELLOW, center = true)
        v.pixText("MOEDAS $coins", 400f, 104f, 2f, P.CYAN, center = true)
        for (i in shopItems.indices) {
            val y = 134f + i * 38f
            if (cursor == i) v.rect(236f, y - 6f, 328f, 30f, P.VIOLET)
            v.pixText(shopItems[i].first, 250f, y, 2.2f, P.WHITE)
            v.pixText("${shopItems[i].second}  (x${inv[i]})", 550f - PixelFont.width("${shopItems[i].second}  (x${inv[i]})", 2.2f), y, 2.2f, P.YELLOW)
        }
        val y = 134f + shopItems.size * 38f
        if (cursor == shopItems.size) v.rect(236f, y - 6f, 328f, 30f, P.VIOLET)
        v.pixText("SAIR", 400f, y, 2.2f, P.WHITE, center = true)
        if (noteT > 0f) v.pixText(note, 400f, 335f, 1.8f, P.LIME, center = true, shadow = false)
    }

    private fun bar(v: View, x: Float, y: Float, w: Float, f: Float) {
        v.rect(x, y, w, 8f, P.INK)
        val c = if (f > 0.5f) P.GREEN else if (f > 0.2f) P.YELLOW else P.RED
        v.rect(x + 1f, y + 1f, (w - 2f) * f.coerceIn(0f, 1f), 6f, c)
    }

    private fun drawBattle(v: View) {
        val f = foe ?: return
        // fundo
        v.rect(0f, 30f, 800f, 190f, palGrass[area])
        v.rect(0f, 220f, 800f, 130f, palGround[area])
        v.hudBar(MonDex.areaNames[area], "MOEDAS $coins")
        // inimigo
        val fy = 60f + if (flashFoe > 0f) 4f else 0f
        if (!(bp == Bp.CAPT && capT > 0.5f)) MonDex.drawMon(v, f.def, 480f, fy, 8f)
        else { v.pixDisc(560f, 130f, 18f, P.RED, 3f); v.rect(542f, 128f, 36f, 4f, P.INK) }
        v.pixDisc(558f, 168f, 46f, C.alpha(P.INK, 0.25f), 6f)
        v.bevel(60f, 46f, 280f, 62f, P.DUSK)
        v.rect(64f, 50f, 272f, 54f, P.INK)
        v.pixText(f.def.name, 72f, 56f, 2.2f, P.WHITE)
        v.pixText("NV ${f.lv}", 330f - PixelFont.width("NV ${f.lv}", 2f), 56f, 2f, P.LILAC)
        bar(v, 72f, 78f, 256f, f.hp.toFloat() / f.def.hpAt(f.lv))
        v.pixText(MonDex.rarityNames[f.def.rarity], 72f, 90f, 1.3f, MonDex.rarityColor[f.def.rarity], shadow = false)
        typeTag(v, f.def.type, 262f, 90f)
        // meu monstro
        val m = me(); val d = meDef()
        val my = 170f + if (flashMe > 0f) 4f else 0f
        MonDex.drawMon(v, d, 70f, my - 10f, 8.5f, flip = true)
        v.bevel(440f, 232f, 330f, 66f, P.DUSK)
        v.rect(444f, 236f, 322f, 58f, P.INK)
        v.pixText(d.name, 452f, 242f, 2.2f, P.WHITE)
        v.pixText("NV ${m.lv}", 760f - PixelFont.width("NV ${m.lv}", 2f), 242f, 2f, P.LILAC)
        bar(v, 452f, 266f, 306f, m.hp.toFloat() / d.hpAt(m.lv))
        v.pixText("${m.hp}/${d.hpAt(m.lv)}", 452f, 278f, 1.4f, P.WHITE, shadow = false)
        // painel
        v.bevel(10f, 350f, 780f, 92f, P.DUSK)
        v.rect(14f, 354f, 772f, 84f, P.INK)
        when (bp) {
            Bp.TEXT -> wrap(v, bText)
            Bp.CAPT -> {
                wrap(v, bText)
                val sh = (capT / 0.6f).toInt().coerceAtMost(capShakes)
                v.pixText("*".repeat(sh), 700f, 400f, 3f, P.YELLOW)
            }
            Bp.MENU -> {
                wrap(v, "O que ${d.name} vai fazer?")
                val labels = listOf("LUTAR", "MOCHILA", "TROCAR", "FUGIR")
                for (i in 0 until 4) {
                    val x = 480f + (i % 2) * 150f; val y = 366f + (i / 2) * 32f
                    if (bCursor == i) v.rect(x - 8f, y - 6f, 140f, 28f, P.VIOLET)
                    v.pixText(labels[i], x, y, 2.2f, P.WHITE)
                }
            }
            Bp.MOVES -> {
                for (i in 0 until 2) {
                    val y = 368f + i * 32f
                    if (bCursor == i) v.rect(30f, y - 6f, 740f, 28f, P.VIOLET)
                    val nm = if (i == 0) "Investida" else d.specialName
                    val tp = if (i == 0) "NORMAL" else MonDex.typeNames[d.type]
                    val pw = if (i == 0) 40 else d.specialPower
                    v.pixText("$nm   $tp   PODER $pw", 44f, y, 2.2f, if (i == 0) P.WHITE else MonDex.typeColor[d.type])
                }
            }
            Bp.ITEMS -> {
                for (i in 0 until 4) {
                    val y = 356f + i * 20f
                    if (bCursor == i) v.rect(30f, y - 3f, 740f, 18f, P.VIOLET)
                    val col = if (inv[i] > 0) P.WHITE else P.SLATE
                    v.pixText("${shopItems[i].first}  x${inv[i]}", 44f, y, 1.8f, col)
                    if (i < 3) v.pixText("CAPTURA", 400f, y, 1.6f, P.LILAC, shadow = false) else v.pixText("CURA 35", 400f, y, 1.6f, P.LILAC, shadow = false)
                }
            }
            Bp.SWAP -> {
                for (i in party.indices) {
                    val o = owned[party[i]]!!; val dd = MonDex.all[o.id]
                    val y = 356f + i * 20f
                    if (bCursor == i) v.rect(30f, y - 3f, 740f, 18f, P.VIOLET)
                    v.pixText("${dd.name}  NV ${o.lv}  HP ${o.hp}/${dd.hpAt(o.lv)}${if (i == active) "  (EM CAMPO)" else ""}", 44f, y, 1.8f, if (o.hp > 0) P.WHITE else P.RED)
                }
            }
        }
    }

    private fun wrap(v: View, t: String) {
        val words = t.split(" ")
        var line = ""
        var y = 366f
        for (w in words) {
            val tryL = if (line.isEmpty()) w else "$line $w"
            if (PixelFont.width(PixelFont.clean(tryL), 2.4f) > 740f && line.isNotEmpty()) { v.pixText(line, 30f, y, 2.4f, P.WHITE); y += 28f; line = w }
            else line = tryL
        }
        if (line.isNotEmpty()) v.pixText(line, 30f, y, 2.4f, P.WHITE)
    }

    private fun drawDex(v: View) {
        v.rect(0f, 0f, 800f, 450f, P.INK)
        v.hudBar("DEX ${owned.size}/54   VISTOS ${seen.size}", "X/Y VOLTAR   A EQUIPE")
        val cw = 84f; val ch = 38f
        for (i in MonDex.all.indices) {
            val d = MonDex.all[i]
            val cx = 22f + (i % 9) * (cw + 2f); val cy = 38f + (i / 9) * (ch + 2f)
            val isOwned = owned.containsKey(i); val isSeen = seen.contains(i)
            v.rect(cx, cy, cw, ch, if (i == cursor) P.VIOLET else P.DUSK)
            v.rect(cx + 2f, cy + 2f, cw - 4f, ch - 4f, if (isOwned) C.shade(MonDex.rarityColor[d.rarity], 0.28f) else 0xFF14152AL)
            if (isSeen) MonDex.drawMon(v, d, cx + cw / 2f - 17f, cy + 2f, 1.7f, silhouette = !isOwned)
            else v.pixText("?", cx + cw / 2f, cy + 10f, 3f, P.SLATE, center = true)
            v.rect(cx + 2f, cy + ch - 5f, cw - 4f, 3f, MonDex.areaColor(d.area))
            if (party.contains(i)) v.rect(cx + 4f, cy + 4f, 7f, 7f, P.YELLOW)
        }
        // detalhes
        val d = MonDex.all[cursor]
        val y0 = 292f
        v.bevel(10f, y0, 780f, 150f, P.DUSK)
        v.rect(14f, y0 + 4f, 772f, 142f, P.INK)
        if (seen.contains(cursor)) {
            MonDex.drawMon(v, d, 24f, y0 + 14f, 6.6f, silhouette = !owned.containsKey(cursor))
            v.pixText("N${cursor + 1}  ${if (owned.containsKey(cursor)) d.name else "???"}", 160f, y0 + 14f, 3f, P.WHITE)
            v.pixText(MonDex.areaNames[d.area], 160f, y0 + 46f, 2f, MonDex.areaColor(d.area))
            v.pixText(MonDex.rarityNames[d.rarity], 160f, y0 + 70f, 2.2f, MonDex.rarityColor[d.rarity])
            typeTag(v, d.type, 330f, y0 + 72f)
            val o = owned[cursor]
            if (o != null) {
                v.pixText("NV ${o.lv}  HP ${d.hpAt(o.lv)}  ATQ ${d.atkAt(o.lv).toInt()}  DEF ${d.defAt(o.lv).toInt()}", 160f, y0 + 98f, 2f, P.LILAC)
                v.pixText("CAPTURADOS ${o.n}   ${if (party.contains(cursor)) "NA EQUIPE (A TIRA)" else "A: ENTRAR NA EQUIPE"}", 160f, y0 + 122f, 1.8f, P.CYAN, shadow = false)
            } else v.pixText("AINDA NÃO CAPTURADO", 160f, y0 + 98f, 2f, P.SLATE)
        } else {
            v.pixText("N${cursor + 1}  ???", 160f, y0 + 14f, 3f, P.SLATE)
            v.pixText("ÁREA: ${MonDex.areaNames[d.area]}", 160f, y0 + 56f, 2f, P.SLATE)
            v.pixText("ENCONTRE-O EXPLORANDO", 160f, y0 + 84f, 2f, P.SLATE)
        }
    }
}

private fun MonDex.areaColor(a: Int): Long = longArrayOf(P.GREEN, P.TEAL, P.SKY, P.ORANGE, P.RED)[a]
