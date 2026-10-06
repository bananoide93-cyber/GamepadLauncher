package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Sobrevivência em ondas (estilo "arena survivors"): escolha uma classe, aguente as ondas,
 * escolha upgrades ao subir de nível e gaste moedas na loja entre as ondas. Chefe a cada 5 ondas.
 */
class SurviveGame : MiniGame(
    "survive", "Arena de Sobrevivência",
    "Escolha uma classe, resista às ondas de monstros, compre itens e monte sua build.",
    "Analógico: mover (a mira é automática) · D-pad + A: escolher/comprar · toque: arraste e toque"
) {
    // ---------------------------------------------------------------- dados
    private enum class Ph { CLASS, WAVE, LEVEL, SHOP }
    private enum class WType(val label: String, val rate: Float, val dmg: Float, val range: Float, val color: Long) {
        PISTOL("Pistola", 0.45f, 9f, 230f, P.YELLOW),
        SMG("Rajada", 0.17f, 4f, 190f, P.ORANGE),
        SHOTGUN("Escopeta", 0.85f, 5f, 150f, P.RED),
        BOW("Arco", 0.75f, 16f, 320f, P.LIME),
        ORB("Orbe", 1.1f, 14f, 260f, P.CYAN),
        BLADES("Laminas", 0f, 7f, 60f, P.SILVER)
    }

    private class PClass(
        val name: String, val desc: String, val hp: Float, val speed: Float, val dmg: Float,
        val rate: Float, val armor: Float, val crit: Float, val weapon: WType, val color: Long
    )

    private val classes = listOf(
        PClass("Soldado", "Equilibrado", 40f, 125f, 1f, 1f, 1f, 0.05f, WType.PISTOL, P.SKY),
        PClass("Mago", "Dano alto, frágil", 28f, 120f, 1.35f, 0.9f, 0f, 0.05f, WType.ORB, P.VIOLET),
        PClass("Tanque", "Muita vida e defesa", 62f, 100f, 0.9f, 0.9f, 3f, 0f, WType.SHOTGUN, P.GREEN),
        PClass("Cacador", "Veloz e crítico", 30f, 150f, 1.0f, 1.1f, 0f, 0.2f, WType.BOW, P.ORANGE)
    )

    private class Weapon(val type: WType, var tier: Int = 1) {
        var cd = 0f
        val dmg get() = type.dmg * (1f + 0.45f * (tier - 1))
        val interval get() = if (type.rate <= 0f) 0f else type.rate * (1f - 0.08f * (tier - 1))
    }

    private class Stats {
        var maxHp = 40f; var speed = 125f; var dmg = 1f; var rate = 1f; var armor = 0f
        var crit = 0.05f; var pickup = 60f; var coinMul = 1f; var regen = 0f; var steal = 0f
    }

    private class Offer(val name: String, val desc: String, val rarity: Int, val price: Int, val weapon: WType?, val stat: Int) {
        var sold = false
    }

    private class Enemy(var x: Float, var y: Float, val kind: Int, var hp: Float, val speed: Float, val r: Float, val dmg: Float) {
        var alive = true
        var flash = 0f
        var shootT = 1.5f + Random.nextFloat() * 2f
        var spawnT = 0f
        val maxHp = hp
    }

    private class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, val dmg: Float, val color: Long, val r: Float, var pierce: Int, var life: Float, val hostile: Boolean = false) {
        var alive = true
        val hit = HashSet<Enemy>()
    }

    private class Drop(var x: Float, var y: Float, val coin: Boolean, val value: Int) { var alive = true }

    private val rarityName = listOf("Comum", "Raro", "Épico", "Lendário")
    private val rarityColor = listOf(P.SILVER, P.SKY, P.VIOLET, P.YELLOW)

    // ---------------------------------------------------------------- estado
    private val rnd = Random.Default
    private var ph = Ph.CLASS
    private var cursor = 0
    private var cls = classes[0]
    private val st = Stats()
    private val weapons = ArrayList<Weapon>()
    private val enemies = ArrayList<Enemy>()
    private val shots = ArrayList<Shot>()
    private val drops = ArrayList<Drop>()
    private var px = 400f
    private var py = 260f
    private var hp = 40f
    private var inv = 0f
    private var wave = 0
    private var waveT = 0f
    private var waveLen = 20f
    private var spawnAcc = 0f
    private var coins = 0
    private var xp = 0
    private var lv = 1
    private var pendingLv = 0
    private var kills = 0
    private var bladeAng = 0f
    private var time = 0f
    private var regenAcc = 0f
    private var choices = listOf<Offer>()
    private var shop = listOf<Offer>()
    private var rerolls = 0
    private var shopMsg = ""
    private var bossAlive = false
    private var waveClear = 0f
    private var faceLeft = false
    private var moving = false
    private class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Long)
    private class Pop(val text: String, var x: Float, var y: Float, var life: Float, val color: Long)
    private val parts = ArrayList<Part>()
    private val pops = ArrayList<Pop>()
    private val decor = ArrayList<FloatArray>()

    init { reset() }

    override fun reset() {
        score = 0; over = false
        ph = Ph.CLASS; cursor = 0
        enemies.clear(); shots.clear(); drops.clear(); weapons.clear()
        wave = 0; coins = 0; xp = 0; lv = 1; pendingLv = 0; kills = 0
        px = 400f; py = 260f; inv = 0f; time = 0f
        shopMsg = ""
        applyClass(classes[0])
    }

    private fun applyClass(c: PClass) {
        cls = c
        st.maxHp = c.hp; st.speed = c.speed; st.dmg = c.dmg; st.rate = c.rate; st.armor = c.armor
        st.crit = c.crit; st.pickup = 60f; st.coinMul = 1f; st.regen = 0f; st.steal = 0f
        hp = st.maxHp
        weapons.clear(); weapons.add(Weapon(c.weapon))
    }

    // ---------------------------------------------------------------- loja / upgrades
    private val statNames = listOf("Vida", "Velocidade", "Dano", "Cadência", "Defesa", "Crítico", "Imã", "Moedas", "Regeneração", "Roubo de vida")

    private fun statOffer(stat: Int, rarity: Int, priced: Boolean): Offer {
        val m = 1f + rarity * 0.8f
        val (name, desc) = when (stat) {
            0 -> "Coração" to "+${(8 * m).toInt()} vida máx."
            1 -> "Botas" to "+${(8 * m).toInt()}% velocidade"
            2 -> "Afiador" to "+${(10 * m).toInt()}% dano"
            3 -> "Gatilho" to "+${(8 * m).toInt()}% cadência"
            4 -> "Escudo" to "+${(1 * m).toInt().coerceAtLeast(1)} defesa"
            5 -> "Mira" to "+${(5 * m).toInt()}% crítico"
            6 -> "Imã" to "+${(25 * m).toInt()} alcance do imã"
            7 -> "Cofre" to "+${(10 * m).toInt()}% moedas"
            8 -> "Trevo de cura" to "+${(0.4f * m * 10).toInt() / 10f} vida/s"
            else -> "Presa" to "+${(2 * m).toInt()}% roubo de vida"
        }
        val price = if (priced) ((14 + wave * 3) * (1f + rarity * 0.9f) * (0.8f + rnd.nextFloat() * 0.4f)).toInt() else 0
        return Offer(name, desc, rarity, price, null, stat)
    }

    private fun applyStat(o: Offer) {
        val m = 1f + o.rarity * 0.8f
        when (o.stat) {
            0 -> { st.maxHp += (8 * m).toInt(); hp += (8 * m).toInt() }
            1 -> st.speed *= 1f + (8 * m) / 100f
            2 -> st.dmg += (10 * m) / 100f
            3 -> st.rate += (8 * m) / 100f
            4 -> st.armor += (1 * m).toInt().coerceAtLeast(1)
            5 -> st.crit += (5 * m) / 100f
            6 -> st.pickup += 25 * m
            7 -> st.coinMul += (10 * m) / 100f
            8 -> st.regen += (0.4f * m * 10).toInt() / 10f
            else -> st.steal += (2 * m) / 100f
        }
        hp = min(hp, st.maxHp)
    }

    private fun rollRarity(): Int {
        val luck = wave * 0.012f
        val r = rnd.nextFloat()
        return when {
            r < 0.03f + luck * 0.5f -> 3
            r < 0.14f + luck -> 2
            r < 0.40f + luck * 1.5f -> 1
            else -> 0
        }
    }

    private fun weaponOffer(): Offer {
        val t = WType.values()[rnd.nextInt(WType.values().size)]
        val rar = rollRarity()
        val price = ((24 + wave * 4) * (1f + rar * 0.7f)).toInt()
        return Offer(t.label, "Arma · ${rarityName[rar]} (nível ${rar + 1})", rar, price, t, 0)
    }

    private fun rollShop() {
        shop = List(4) { if (rnd.nextFloat() < 0.3f) weaponOffer() else statOffer(rnd.nextInt(10), rollRarity(), true) }
    }

    private fun rerollPrice() = 4 + wave * 2 + rerolls * 3

    private fun buy(o: Offer) {
        if (o.sold) return
        if (coins < o.price) { shopMsg = "Moedas insuficientes"; return }
        if (o.weapon != null) {
            val same = weapons.firstOrNull { it.type == o.weapon }
            if (same != null) {
                same.tier = min(5, max(same.tier + 1, o.rarity + 1))
            } else if (weapons.size >= 4) {
                shopMsg = "Sem vaga de arma (4 máx.)"; return
            } else weapons.add(Weapon(o.weapon, o.rarity + 1))
        } else applyStat(o)
        coins -= o.price
        o.sold = true
        shopMsg = "Comprou ${o.name}"
    }

    // ---------------------------------------------------------------- ondas
    private fun startWave() {
        wave++
        waveLen = min(45f, 18f + wave * 1.6f)
        waveT = 0f; spawnAcc = 0f
        bossAlive = false
        waveClear = 0f
        px = 400f; py = 260f
        genDecor(); parts.clear(); pops.clear()
        enemies.clear(); shots.clear()
        hp = min(st.maxHp, hp + st.maxHp * 0.15f)
        ph = Ph.WAVE
        if (wave % 5 == 0) spawnBoss()
    }

    private fun edgePoint(): FloatArray {
        val side = rnd.nextInt(4)
        return when (side) {
            0 -> floatArrayOf(rnd.nextFloat() * 800f, 34f)
            1 -> floatArrayOf(rnd.nextFloat() * 800f, 440f)
            2 -> floatArrayOf(10f, 34f + rnd.nextFloat() * 400f)
            else -> floatArrayOf(790f, 34f + rnd.nextFloat() * 400f)
        }
    }

    private fun scaleHp() = 1f + wave * 0.22f + (wave / 5) * 0.4f

    private fun spawnEnemy() {
        val e = edgePoint()
        val roll = rnd.nextFloat()
        val s = scaleHp()
        val en = when {
            wave >= 8 && roll < 0.14f -> Enemy(e[0], e[1], 3, 14f * s, 45f, 12f, 6f + wave * 0.3f) // atirador
            wave >= 4 && roll < 0.30f -> Enemy(e[0], e[1], 2, 38f * s, 48f, 17f, 7f + wave * 0.3f) // bruto
            wave >= 2 && roll < 0.55f -> Enemy(e[0], e[1], 1, 7f * s, 105f, 8f, 3f + wave * 0.2f) // morcego
            else -> Enemy(e[0], e[1], 0, 14f * s, 62f, 11f, 4f + wave * 0.25f) // gosma
        }
        enemies.add(en)
    }

    private fun spawnBoss() {
        val s = scaleHp()
        val b = Enemy(400f, 120f, 4, 420f * s * (wave / 5), 50f, 30f, 12f + wave * 0.4f)
        enemies.add(b)
        bossAlive = true
    }

    // ---------------------------------------------------------------- atualização
    override fun update(dt: Float, input: GameInput) {
        time += dt
        if (over) return
        when (ph) {
            Ph.CLASS -> updateClass(input)
            Ph.WAVE -> updateWave(dt, input)
            Ph.LEVEL -> updateLevel(input)
            Ph.SHOP -> updateShop(input)
        }
    }

    private fun updateClass(input: GameInput) {
        if (input.stepX != 0) cursor = (cursor + input.stepX + classes.size) % classes.size
        if (input.firePressed) {
            applyClass(classes[cursor])
            rollShop(); rerolls = 0
            startWave()
        }
    }

    private fun updateLevel(input: GameInput) {
        if (input.stepX != 0) cursor = (cursor + input.stepX + choices.size) % choices.size
        if (input.firePressed) {
            applyStat(choices[cursor])
            pendingLv--
            if (pendingLv > 0) offerLevel() else ph = Ph.WAVE
        }
    }

    private fun offerLevel() {
        choices = List(3) { statOffer(rnd.nextInt(10), rollRarity(), false) }
        cursor = 0
        ph = Ph.LEVEL
    }

    private fun updateShop(input: GameInput) {
        val n = 6
        if (input.stepX != 0) cursor = (cursor + input.stepX + n) % n
        if (input.stepY != 0) cursor = if (input.stepY > 0) min(n - 1, if (cursor < 4) 4 else 5) else (if (cursor >= 4) cursor - 4 else cursor)
        if (input.firePressed) {
            when {
                cursor < 4 -> buy(shop[cursor])
                cursor == 4 -> {
                    val p = rerollPrice()
                    if (coins >= p) { coins -= p; rerolls++; rollShop(); shopMsg = "Loja renovada" } else shopMsg = "Moedas insuficientes"
                }
                else -> { rollShop(); rerolls = 0; shopMsg = ""; startWave() }
            }
        }
    }

    private fun nearest(x: Float, y: Float, range: Float): Enemy? {
        var best: Enemy? = null
        var bd = range * range
        for (e in enemies) {
            if (!e.alive) continue
            val d = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y)
            if (d < bd) { bd = d; best = e }
        }
        return best
    }

    private fun addShot(x: Float, y: Float, ang: Float, sp: Float, dmg: Float, color: Long, r: Float, pierce: Int, life: Float) {
        shots.add(Shot(x, y, cos(ang) * sp, sin(ang) * sp, dmg, color, r, pierce, life))
    }

    private fun fireWeapon(w: Weapon, target: Enemy) {
        val a = atan2(target.y - py, target.x - px)
        val crit = rnd.nextFloat() < st.crit
        val d = w.dmg * st.dmg * (if (crit) 2f else 1f)
        val t = w.type
        val life = t.range / 360f
        when (t) {
            WType.PISTOL -> addShot(px, py, a, 360f, d, t.color, 3f, 1, life)
            WType.SMG -> addShot(px, py, a + (rnd.nextFloat() - 0.5f) * 0.25f, 400f, d, t.color, 2.5f, 1, life)
            WType.SHOTGUN -> {
                val n = 3 + w.tier
                for (i in 0 until n) addShot(px, py, a + (i - (n - 1) / 2f) * 0.16f, 330f, d, t.color, 3f, 1, life)
            }
            WType.BOW -> addShot(px, py, a, 460f, d, t.color, 3f, 2 + w.tier, life)
            WType.ORB -> addShot(px, py, a, 210f, d, t.color, 7f, 99, life)
            WType.BLADES -> {}
        }
    }

    private fun hitFx(x: Float, y: Float, dmg: Float, crit: Boolean, color: Long) {
        if (parts.size < 160) for (i in 0 until 3) parts.add(Part(x, y, (rnd.nextFloat() - 0.5f) * 140f, (rnd.nextFloat() - 0.5f) * 140f, 0.35f, color))
        if (pops.size < 24) pops.add(Pop(dmg.toInt().toString(), x + (rnd.nextFloat() - 0.5f) * 10f, y - 8f, 0.6f, if (crit) P.YELLOW else P.WHITE))
    }

    private fun genDecor() {
        decor.clear()
        val r = Random(wave * 31 + 7)
        for (i in 0 until 46) decor.add(floatArrayOf(r.nextFloat() * 800f, 40f + r.nextFloat() * 400f, r.nextInt(4).toFloat()))
    }

    private fun hurt(d: Float) {
        if (inv > 0f) return
        val dmg = max(1f, d - st.armor)
        hp -= dmg
        inv = 0.5f
        if (hp <= 0f) { score = wave * 100 + kills; finish() }
    }

    private fun killEnemy(e: Enemy) {
        if (!e.alive) return
        e.alive = false
        kills++
        val boss = e.kind == 4
        val cv = ((if (boss) 25 else 1 + wave / 6) * st.coinMul).toInt().coerceAtLeast(1)
        if (rnd.nextFloat() < (if (boss) 1f else 0.7f)) drops.add(Drop(e.x, e.y, true, cv))
        drops.add(Drop(e.x + 6f, e.y, false, if (boss) 20 else 1 + e.kind))
        if (boss) { bossAlive = false; for (i in 0 until 6) drops.add(Drop(e.x + rnd.nextFloat() * 50f - 25f, e.y + rnd.nextFloat() * 50f - 25f, true, 6)) }
        if (st.steal > 0f && rnd.nextFloat() < 0.5f) hp = min(st.maxHp, hp + st.maxHp * st.steal * 0.5f + 0.5f)
    }

    private fun xpNeed() = 5 + lv * 4

    private fun updateWave(dt: Float, input: GameInput) {
        waveT += dt
        inv = max(0f, inv - dt)
        // movimento
        val mx = input.dx; val my = input.dy
        val len = sqrt(mx * mx + my * my)
        moving = len > 0.05f
        if (mx > 0.15f) faceLeft = false else if (mx < -0.15f) faceLeft = true
        if (len > 0.05f) {
            val k = min(1f, len) / len
            px += mx * k * st.speed * dt; py += my * k * st.speed * dt
        }
        px = px.coerceIn(14f, 786f); py = py.coerceIn(44f, 436f)
        if (st.regen > 0f) { regenAcc += dt * st.regen; if (regenAcc >= 1f) { regenAcc -= 1f; hp = min(st.maxHp, hp + 1f) } }

        // spawn
        if (waveT < waveLen) {
            spawnAcc += dt * (1.2f + wave * 0.35f)
            while (spawnAcc >= 1f) { spawnAcc -= 1f; if (enemies.count { it.alive } < 60) spawnEnemy() }
        }

        // armas
        bladeAng += dt * 3.2f
        for (w in weapons) {
            if (w.type == WType.BLADES) continue
            w.cd -= dt * st.rate
            if (w.cd <= 0f) {
                val t = nearest(px, py, w.type.range)
                if (t != null) { fireWeapon(w, t); w.cd = w.interval }
            }
        }

        // inimigos
        val newborn = ArrayList<Enemy>()
        for (e in enemies) {
            if (!e.alive) continue
            e.flash = max(0f, e.flash - dt)
            val dx = px - e.x; val dy = py - e.y
            val d = max(1f, sqrt(dx * dx + dy * dy))
            val keep = if (e.kind == 3) 200f else 0f
            val dir = if (e.kind == 3 && d < keep) -0.6f else 1f
            e.x += dx / d * e.speed * dir * dt; e.y += dy / d * e.speed * dir * dt
            if (e.kind == 3 || e.kind == 4) {
                e.shootT -= dt
                if (e.shootT <= 0f) {
                    e.shootT = if (e.kind == 4) 1.6f else 2.4f
                    val a = atan2(dy, dx)
                    val n = if (e.kind == 4) 8 else 1
                    for (i in 0 until n) {
                        val ang = if (e.kind == 4) a + (i - 3.5f) * 0.28f else a
                        shots.add(Shot(e.x, e.y, cos(ang) * 150f, sin(ang) * 150f, e.dmg * 0.7f, P.PLUM, 5f, 1, 5f, true))
                    }
                }
            }
            if (e.kind == 4) {
                e.spawnT += dt
                if (e.spawnT > 4f) { e.spawnT = 0f; for (i in 0 until 3) newborn.add(Enemy(e.x + i * 10f, e.y, 0, 10f * scaleHp(), 70f, 10f, 4f)) }
            }
            if (d < e.r + 9f) hurt(e.dmg)
        }
        enemies.addAll(newborn)
        // lâminas
        for (w in weapons) {
            if (w.type != WType.BLADES) continue
            val n = 1 + w.tier
            for (i in 0 until n) {
                val a = bladeAng + i * 2f * PI.toFloat() / n
                val bx = px + cos(a) * 52f; val by = py + sin(a) * 52f
                for (e in enemies) {
                    if (!e.alive) continue
                    if ((e.x - bx) * (e.x - bx) + (e.y - by) * (e.y - by) < (e.r + 8f) * (e.r + 8f)) {
                        if (e.flash <= 0f) {
                            e.hp -= w.dmg * st.dmg * (1f + st.crit)
                            e.flash = 0.25f
                            if (e.hp <= 0f) killEnemy(e)
                        }
                    }
                }
            }
        }
        // tiros
        for (s in shots) {
            if (!s.alive) continue
            s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt
            if (s.life <= 0f || s.x < -20f || s.x > 820f || s.y < 20f || s.y > 470f) { s.alive = false; continue }
            if (s.hostile) {
                if ((s.x - px) * (s.x - px) + (s.y - py) * (s.y - py) < (s.r + 8f) * (s.r + 8f)) { s.alive = false; hurt(s.dmg) }
                continue
            }
            for (e in enemies) {
                if (!e.alive || s.hit.contains(e)) continue
                if ((e.x - s.x) * (e.x - s.x) + (e.y - s.y) * (e.y - s.y) < (e.r + s.r) * (e.r + s.r)) {
                    s.hit.add(e)
                    e.hp -= s.dmg; e.flash = 0.1f
                    hitFx(s.x, s.y, s.dmg, s.dmg > 20f, s.color)
                    if (e.hp <= 0f) killEnemy(e)
                    s.pierce--
                    if (s.pierce <= 0) { s.alive = false; break }
                }
            }
        }
        // drops
        val fin = waveT >= waveLen && enemies.none { it.alive }
        for (d in drops) {
            if (!d.alive) continue
            val dx = px - d.x; val dy = py - d.y
            val dist = max(1f, sqrt(dx * dx + dy * dy))
            if (dist < st.pickup || fin) {
                val sp = 260f
                d.x += dx / dist * sp * dt; d.y += dy / dist * sp * dt
            }
            if (dist < 14f) {
                d.alive = false
                if (d.coin) coins += d.value else { xp += d.value }
            }
        }
        for (q in parts) { q.x += q.vx * dt; q.y += q.vy * dt; q.life -= dt }
        for (q in pops) { q.y -= 28f * dt; q.life -= dt }
        parts.removeAll { it.life <= 0f }
        pops.removeAll { it.life <= 0f }
        enemies.removeAll { !it.alive }
        shots.removeAll { !it.alive }
        drops.removeAll { !it.alive }
        while (xp >= xpNeed()) { xp -= xpNeed(); lv++; pendingLv++ }
        if (pendingLv > 0 && !over) { offerLevel(); return }
        if (fin) {
            waveClear += dt
            if (drops.isEmpty() || waveClear > 1.5f) {
                drops.clear()
                score = wave * 100 + kills
                if (score > best) best = score
                ph = Ph.SHOP; cursor = 0; shopMsg = ""
                hp = min(st.maxHp, hp + st.maxHp * 0.25f)
            }
        }
    }

    // ---------------------------------------------------------------- desenho
    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(P.INK)
        // chão
        for (ty in 0 until 14) for (tx in 0 until 25) {
            val c = if ((tx + ty) % 2 == 0) 0xFF2E5E3EL else 0xFF2A5738L
            v.rect(tx * 32f, 30f + ty * 30f, 32.5f, 30.5f, c)
        }
        when (ph) {
            Ph.CLASS -> drawClass(v)
            Ph.WAVE, Ph.LEVEL -> { drawWorld(v); if (ph == Ph.LEVEL) drawLevel(v) }
            Ph.SHOP -> drawShop(v)
        }
        if (ph != Ph.CLASS) {
            v.hudBar("ONDA $wave   NV $lv", "MOEDAS $coins")
        }
    }

    private fun shadow(v: View, x: Float, y: Float, w: Float) {
        val c = C.alpha(C.BLACK, 0.28f)
        v.rect(x - w / 2f, y, w, 3f, c)
        v.rect(x - w / 2f + 3f, y - 2f, w - 6f, 2f, c)
        v.rect(x - w / 2f + 3f, y + 3f, w - 6f, 2f, c)
    }

    private fun drawEnemy(v: View, e: Enemy) {
        val flash = e.flash > 0f
        val fl = px < e.x
        val fr = ((time * 6f + e.x * 0.05f).toInt()) % 2 == 0
        val spr: Spr
        val k: Float
        when (e.kind) {
            0 -> { spr = if (fr) SArt.slimeA else SArt.slimeB; k = 2.3f }
            1 -> { spr = if (fr) SArt.batA else SArt.batB; k = 2.1f }
            2 -> { spr = SArt.bruteS; k = 2.5f }
            3 -> { spr = SArt.spitterS; k = 2.3f }
            else -> { spr = SArt.bossS; k = 2.9f }
        }
        val w = spr.w * k; val h = spr.h * k
        val bob = if (e.kind == 1) sin(time * 9f + e.x) * 3f - 8f else if (e.kind == 2 || e.kind == 4) sin(time * 5f + e.y) * 1.5f else 0f
        shadow(v, e.x, e.y + h / 2f - 3f, w * 0.8f)
        spr.draw(v, e.x - w / 2f, e.y - h / 2f + bob, k, flip = fl, flash = flash)
        if (e.kind == 4) {
            v.rect(250f, 58f, 300f, 8f, P.INK)
            v.rect(251f, 59f, 298f * (e.hp / e.maxHp).coerceIn(0f, 1f), 6f, P.RED)
        } else if (e.kind == 2 && e.hp < e.maxHp) {
            v.rect(e.x - 14f, e.y - h / 2f - 4f, 28f, 3f, P.INK)
            v.rect(e.x - 14f, e.y - h / 2f - 4f, 28f * (e.hp / e.maxHp).coerceIn(0f, 1f), 3f, P.RED)
        }
    }

    private fun drawDecor(v: View) {
        for (d in decor) {
            val x = d[0]; val y = d[1]
            when (d[2].toInt()) {
                0 -> { v.rect(x, y, 2f, 5f, 0xFF3F8A52L); v.rect(x + 3f, y + 1f, 2f, 4f, 0xFF3F8A52L); v.rect(x - 3f, y + 2f, 2f, 3f, 0xFF3F8A52L) }
                1 -> { v.rect(x + 2f, y + 3f, 2f, 5f, 0xFF3F8A52L); v.rect(x, y, 6f, 3f, P.light(P.RED, 0.3f)); v.rect(x + 2f, y + 1f, 2f, 1f, P.YELLOW) }
                2 -> { v.rect(x, y + 2f, 9f, 5f, 0xFF6F8F7AL); v.rect(x + 1f, y, 6f, 3f, 0xFF8FB09AL); v.rect(x, y + 6f, 9f, 1f, 0xFF4F6F5AL) }
                else -> { v.rect(x, y, 3f, 3f, 0xFF4C9A62L); v.rect(x + 4f, y + 2f, 2f, 2f, 0xFF4C9A62L) }
            }
        }
    }

    private fun drawShot(v: View, s: Shot) {
        if (s.hostile) {
            v.pixDisc(s.x, s.y, s.r + 3f, C.alpha(P.RED, 0.35f), 2f)
            v.pixDisc(s.x, s.y, s.r, P.PLUM, 2f); v.pixDisc(s.x, s.y, s.r - 2f, P.ORANGE, 2f)
            return
        }
        val tx = s.x - s.vx * 0.035f; val ty = s.y - s.vy * 0.035f
        when {
            s.r >= 6f -> { // orbe
                v.pixDisc(s.x, s.y, s.r + 5f, C.alpha(s.color, 0.25f), 3f)
                v.pixDisc(s.x, s.y, s.r, s.color, 2f)
                v.pixDisc(s.x - 1f, s.y - 1f, s.r - 3f, P.WHITE, 2f)
            }
            s.pierce >= 3 -> { // flecha
                v.line(tx - s.vx * 0.02f, ty - s.vy * 0.02f, s.x, s.y, 2.5f, P.light(P.SLATE, 0.2f))
                v.pixDisc(s.x, s.y, 3f, s.color, 2f)
            }
            else -> {
                v.line(tx, ty, s.x, s.y, s.r * 1.4f, C.alpha(s.color, 0.55f))
                v.pixDisc(s.x, s.y, s.r, s.color, 2f)
                v.pixDisc(s.x, s.y, s.r - 1.5f, P.WHITE, 1.5f)
            }
        }
    }

    private fun drawWorld(v: View) {
        drawDecor(v)
        for (d in drops) {
            if (d.coin) {
                val wv = max(1.5f, 5f * kotlin.math.abs(cos(time * 6f + d.x)))
                v.rect(d.x - wv, d.y - 5f, wv * 2f, 10f, 0xFFB8860BL)
                v.rect(d.x - wv + 1f, d.y - 4f, max(1f, wv * 2f - 2f), 8f, P.YELLOW)
                v.rect(d.x - 1f, d.y - 3f, 2f, 5f, P.light(P.YELLOW, 0.6f))
            } else {
                val bob = sin(time * 5f + d.y) * 1.5f
                v.rect(d.x - 1f, d.y - 5f + bob, 2f, 10f, P.LIME); v.rect(d.x - 3f, d.y - 3f + bob, 6f, 6f, P.LIME)
                v.rect(d.x - 4f, d.y - 1f + bob, 8f, 2f, P.GREEN); v.rect(d.x - 1f, d.y - 3f + bob, 2f, 2f, P.WHITE)
            }
        }
        for (e in enemies.sortedBy { it.y }) drawEnemy(v, e)
        for (w in weapons) {
            if (w.type != WType.BLADES) continue
            val n = 1 + w.tier
            for (i in 0 until n) {
                val a = bladeAng + i * 2f * PI.toFloat() / n
                val bx = px + cos(a) * 52f; val by = py + sin(a) * 52f
                v.pixDisc(bx, by, 11f, C.alpha(P.CYAN, 0.25f), 3f)
                v.line(bx - cos(a + 1.57f) * 9f, by - sin(a + 1.57f) * 9f, bx + cos(a + 1.57f) * 9f, by + sin(a + 1.57f) * 9f, 4f, P.SILVER)
                v.line(bx - cos(a + 1.57f) * 6f, by - sin(a + 1.57f) * 6f, bx + cos(a + 1.57f) * 6f, by + sin(a + 1.57f) * 6f, 1.5f, P.WHITE)
            }
        }
        for (s in shots) drawShot(v, s)
        for (q in parts) v.rect(q.x, q.y, 3f, 3f, C.alpha(q.color, (q.life * 3f).coerceIn(0f, 1f)))
        // jogador
        val blink = inv > 0f && ((time * 20f).toInt() % 2 == 0)
        shadow(v, px, py + 22f, 26f)
        if (!blink) {
            val spr = SArt.players[classes.indexOf(cls).coerceAtLeast(0)]
            val k = 2.6f
            val bob = if (moving) kotlin.math.abs(sin(time * 12f)) * -3f else sin(time * 3f) * -1f
            spr.draw(v, px - spr.w * k / 2f, py - spr.h * k / 2f + bob, k, flip = faceLeft)
        }
        for (q in pops) v.pixText(q.text, q.x, q.y, 1.6f, C.alpha(q.color, (q.life * 3f).coerceIn(0f, 1f)), center = true, shadow = false)
        // barras
        v.rect(10f, 36f, 160f, 10f, P.INK)
        v.rect(11f, 37f, 158f * (hp / st.maxHp).coerceIn(0f, 1f), 8f, P.RED)
        v.pixText("${hp.toInt()}/${st.maxHp.toInt()}", 14f, 38f, 1.2f, P.WHITE, shadow = false)
        v.rect(10f, 49f, 160f, 5f, P.INK)
        v.rect(11f, 50f, 158f * (xp.toFloat() / xpNeed()).coerceIn(0f, 1f), 3f, P.LIME)
        val left = max(0f, waveLen - waveT)
        v.pixText("${left.toInt()}S", 400f, 36f, 2.6f, P.YELLOW, center = true)
        // armas
        var x = 10f
        for (w in weapons) { v.pixText("${w.type.label} ${w.tier}", x, 428f, 1.6f, w.type.color); x += 110f }
        if (wave % 5 == 0 && bossAlive) v.pixText("CHEFE!", 400f, 70f, 2.4f, P.RED, center = true)
    }

    private fun drawClass(v: View) {
        v.pixText("ESCOLHA SUA CLASSE", 400f, 24f, 4f, P.YELLOW, center = true)
        for (i in classes.indices) {
            val c = classes[i]
            val x = 30f + i * 188f
            val sel = i == cursor
            v.bevel(x, 90f, 176f, 270f, if (sel) P.VIOLET else P.DUSK)
            v.rect(x + 4f, 94f, 168f, 262f, if (sel) 0xFF2B2670L else P.INK)
            val spr = SArt.players[i]
            val bob = if (sel) sin(time * 5f) * -3f else 0f
            spr.draw(v, x + 88f - spr.w * 2.1f, 100f + bob, 4.2f)
            v.pixText(c.name, x + 88f, 196f, 2.8f, P.WHITE, center = true)
            v.pixText(c.desc, x + 88f, 222f, 1.4f, P.LILAC, center = true)
            v.pixText("VIDA ${c.hp.toInt()}", x + 12f, 250f, 1.8f, P.WHITE)
            v.pixText("VELOC ${c.speed.toInt()}", x + 12f, 270f, 1.8f, P.WHITE)
            v.pixText("DANO ${(c.dmg * 100).toInt()}%", x + 12f, 290f, 1.8f, P.WHITE)
            v.pixText("DEFESA ${c.armor.toInt()}", x + 12f, 310f, 1.8f, P.WHITE)
            v.pixText(c.weapon.label, x + 88f, 336f, 2f, c.weapon.color, center = true)
        }
        v.pixText("D-PAD ESCOLHER   A CONFIRMAR", 400f, 392f, 2.2f, P.CYAN, center = true, shadow = false)
        v.pixText("CHEFE A CADA 5 ONDAS", 400f, 416f, 1.8f, P.SILVER, center = true, shadow = false)
    }

    private fun drawCard(v: View, o: Offer, x: Float, y: Float, w: Float, h: Float, sel: Boolean, showPrice: Boolean) {
        val rc = rarityColor[o.rarity]
        v.bevel(x, y, w, h, if (sel) P.VIOLET else P.DUSK)
        v.rect(x + 4f, y + 4f, w - 8f, h - 8f, if (o.sold) 0xFF222222L else P.INK)
        v.rect(x + 4f, y + 4f, w - 8f, 5f, rc)
        v.pixText(o.name, x + w / 2f, y + 18f, 2.2f, if (o.sold) P.SLATE else P.WHITE, center = true)
        v.pixText(rarityName[o.rarity], x + w / 2f, y + 40f, 1.5f, rc, center = true)
        wrapText(v, o.desc, x + 10f, y + 62f, w - 20f)
        if (showPrice) v.pixText(if (o.sold) "VENDIDO" else "${o.price} MOEDAS", x + w / 2f, y + h - 22f, 1.9f, if (o.sold) P.SLATE else if (coins >= o.price) P.YELLOW else P.RED, center = true)
    }

    private fun wrapText(v: View, t: String, x: Float, y: Float, maxW: Float) {
        val words = PixelFont.clean(t).split(" ")
        var line = ""
        var yy = y
        for (w in words) {
            val tryL = if (line.isEmpty()) w else "$line $w"
            if (PixelFont.width(tryL, 1.5f) > maxW && line.isNotEmpty()) {
                v.pixText(line, x, yy, 1.5f, P.LILAC, shadow = false); yy += 14f; line = w
            } else line = tryL
        }
        if (line.isNotEmpty()) v.pixText(line, x, yy, 1.5f, P.LILAC, shadow = false)
    }

    private fun drawLevel(v: View) {
        v.rect(0f, 0f, 800f, 450f, C.alpha(C.BLACK, 0.65f))
        v.pixText("SUBIU DE NIVEL! ESCOLHA UM UPGRADE", 400f, 70f, 3f, P.YELLOW, center = true)
        for (i in choices.indices) drawCard(v, choices[i], 70f + i * 230f, 130f, 210f, 170f, i == cursor, false)
        v.pixText("D-PAD ESCOLHER   A CONFIRMAR", 400f, 340f, 2f, P.CYAN, center = true, shadow = false)
    }

    private fun drawShop(v: View) {
        v.rect(0f, 30f, 800f, 420f, C.alpha(C.BLACK, 0.5f))
        v.pixText("LOJA - ONDA $wave CONCLUIDA", 400f, 42f, 3.2f, P.YELLOW, center = true)
        for (i in 0 until 4) drawCard(v, shop[i], 20f + i * 196f, 80f, 186f, 160f, cursor == i, true)
        val rp = rerollPrice()
        v.bevel(200f, 256f, 190f, 40f, if (cursor == 4) P.VIOLET else P.DUSK)
        v.pixText("RENOVAR $rp", 295f, 268f, 2.2f, if (coins >= rp) P.YELLOW else P.RED, center = true)
        v.bevel(410f, 256f, 190f, 40f, if (cursor == 5) P.VIOLET else P.GREEN)
        v.pixText("PROXIMA ONDA", 505f, 268f, 2.2f, P.WHITE, center = true)
        if (shopMsg.isNotEmpty()) v.pixText(shopMsg, 400f, 306f, 2f, P.CYAN, center = true, shadow = false)
        // build
        v.pixText("SUA BUILD (${cls.name})", 20f, 332f, 2f, P.LILAC)
        var x = 20f
        for (w in weapons) { v.pixText("${w.type.label} N${w.tier}", x, 354f, 1.8f, w.type.color); x += 130f }
        v.pixText("VIDA ${hp.toInt()}/${st.maxHp.toInt()}  DANO ${(st.dmg * 100).toInt()}%  CAD ${(st.rate * 100).toInt()}%  VEL ${st.speed.toInt()}", 20f, 380f, 1.6f, P.WHITE, shadow = false)
        v.pixText("DEFESA ${st.armor.toInt()}  CRIT ${(st.crit * 100).toInt()}%  IMA ${st.pickup.toInt()}  MOEDAS +${((st.coinMul - 1f) * 100).toInt()}%", 20f, 400f, 1.6f, P.WHITE, shadow = false)
        v.pixText("REGEN ${"%.1f".format(st.regen)}  ROUBO ${(st.steal * 100).toInt()}%", 20f, 420f, 1.6f, P.WHITE, shadow = false)
    }
}

/** Sprites do Survival (desenhados com o mini-estúdio de pixel art). */
private object SArt {
    private fun pal(body: Long, accent: Long = P.YELLOW) = longArrayOf(
        0, C.shade(body, 0.28f), body, C.shade(body, 0.68f), P.light(body, 0.3f), 0xFFF1C27DL, P.WHITE, P.INK,
        0xFF8A97A8L, 0xFFD6DEE8L, P.YELLOW, 0xFF7A4A21L, accent
    )

    private fun human(kind: Int, color: Long): Spr {
        val c = Cv(16, 18)
        c.ell(8f, 6f, 3.2f, 3f, 5)
        c.rect(5, 9, 10, 13, 2)
        c.rect(5, 14, 6, 16, 3); c.rect(9, 14, 10, 16, 3)
        c.rect(4, 16, 6, 16, 7); c.rect(9, 16, 11, 16, 7)
        c.rect(4, 10, 5, 12, 2)
        c.p(10, 6, 7); c.p(9, 6, 6)
        when (kind) {
            0 -> { // soldado
                c.ell(8f, 4f, 3.8f, 2.6f, 2); c.rect(5, 5, 11, 5, 3); c.p(11, 6, 3)
                c.rect(10, 11, 14, 12, 8); c.p(15, 11, 9); c.rect(10, 13, 11, 13, 8)
                c.rect(5, 12, 10, 12, 11)
            }
            1 -> { // mago
                c.p(8, 0, 2); c.rect(7, 1, 9, 1, 2); c.rect(6, 2, 10, 2, 2); c.rect(4, 3, 12, 3, 3); c.rect(6, 4, 10, 4, 2)
                c.p(8, 2, 10)
                c.rect(5, 9, 10, 15, 2); c.rect(5, 14, 6, 17, 3); c.rect(9, 14, 10, 17, 3)
                c.line(13, 5, 13, 16, 11); c.ell(13f, 4f, 1.6f, 1.6f, 10); c.p(13, 4, 6)
                c.rect(8, 9, 8, 15, 4)
            }
            2 -> { // tanque
                c.ell(8f, 4f, 3.8f, 2.6f, 8); c.rect(5, 5, 11, 5, 3); c.p(4, 2, 6); c.p(3, 1, 6); c.p(12, 2, 6); c.p(13, 1, 6)
                c.rect(4, 9, 11, 13, 2); c.rect(3, 9, 4, 10, 8)
                c.rect(10, 9, 14, 15, 8); c.rect(11, 10, 13, 14, 9); c.p(12, 12, 10)
            }
            else -> { // caçador
                c.ell(8f, 5f, 4f, 3.6f, 2); c.rect(7, 5, 10, 7, 5); c.p(10, 6, 7); c.p(9, 6, 6)
                c.rect(4, 9, 11, 14, 2); c.rect(3, 10, 4, 15, 3)
                c.line(13, 5, 14, 9, 11); c.line(14, 9, 13, 13, 11); c.line(13, 5, 13, 13, 6)
                c.rect(11, 9, 14, 9, 6)
            }
        }
        c.finish()
        return c.toSpr(pal(color))
    }

    private fun slime(squash: Boolean): Spr {
        val c = Cv(16, 12)
        if (!squash) { c.ell(8f, 7f, 6.5f, 4.3f, 2); c.ell(8f, 4.5f, 4f, 2.6f, 2) }
        else { c.ell(8f, 8f, 7.4f, 3.4f, 2) }
        val y = if (squash) 8 else 7
        c.p(5, y - 3, 6); c.p(6, y - 4, 6)
        c.eye(7, y); c.eye(11, y)
        c.p(8, y + 2, 7); c.p(9, y + 2, 7)
        c.finish()
        return c.toSpr(pal(0xFF6BCB5BL))
    }

    private fun bat(up: Boolean): Spr {
        val c = Cv(16, 10)
        c.ell(8f, 5f, 2f, 2.6f, 2)
        c.p(6, 2, 2); c.p(10, 2, 2)
        for (x in 1..5) {
            val t = (x - 1) / 4f
            val y0 = if (up) 1 + (t * 3).toInt() else 4 + (t * 2).toInt()
            val y1 = if (up) 6 else 9 - (t * 2).toInt()
            for (y in y0..y1) { c.p(x, y, 3); c.p(15 - x, y, 3) }
        }
        c.p(7, 4, 9); c.p(9, 4, 9); c.p(8, 7, 6)
        c.finish()
        return c.toSpr(pal(0xFF8E44ADL, 0xFFFF5252L))
    }

    private fun brute(): Spr {
        val c = Cv(22, 20)
        c.ell(10f, 12f, 7f, 6f, 2)
        c.ell(10f, 13.5f, 4.5f, 3.5f, 5, 2)
        c.ell(10f, 5f, 4f, 3.5f, 2)
        c.p(6, 1, 6); c.p(5, 0, 6); c.p(14, 1, 6); c.p(15, 0, 6)
        c.rect(1, 9, 4, 15, 2); c.rect(16, 9, 19, 15, 2)
        c.rect(6, 17, 8, 19, 3); c.rect(12, 17, 14, 19, 3)
        c.line(20, 2, 18, 13, 11); c.ell(20f, 2f, 2f, 2f, 3)
        c.p(8, 5, 9); c.p(7, 5, 9); c.p(12, 5, 9); c.p(11, 5, 9)
        c.rect(8, 7, 12, 7, 7); c.p(8, 8, 6); c.p(12, 8, 6)
        c.finish()
        return c.toSpr(pal(0xFFC0392BL))
    }

    private fun spitter(): Spr {
        val c = Cv(14, 16)
        c.ell(7f, 5f, 6f, 4f, 2)
        c.p(4, 4, 6); c.p(5, 4, 6); c.p(9, 3, 6); c.p(10, 3, 6); c.p(7, 2, 6)
        c.rect(5, 8, 9, 13, 5); c.rect(4, 14, 6, 15, 3); c.rect(8, 14, 10, 15, 3)
        c.eye(6, 10); c.eye(9, 10); c.rect(6, 12, 8, 12, 7)
        c.p(3, 8, 8); c.p(10, 8, 8)
        c.finish()
        return c.toSpr(pal(0xFFE67E22L))
    }

    private fun boss(): Spr {
        val c = Cv(34, 32)
        for (side in 0..1) {
            for (x in 0..8) {
                val xx = if (side == 0) 2 + x else 31 - x
                val top = 6 + x / 2 - (if (x in 3..6) 5 else 0)
                for (y in top..(22 - x)) c.p(xx, y, 3)
            }
        }
        c.ell(17f, 20f, 9f, 8f, 2)
        c.ell(17f, 22f, 5.5f, 5f, 5, 2)
        c.ell(17f, 9f, 7f, 6f, 2)
        c.line(11, 5, 7, 0, 6); c.line(12, 5, 8, 0, 6); c.line(23, 5, 27, 0, 6); c.line(22, 5, 26, 0, 6)
        c.rect(7, 18, 9, 27, 2); c.rect(25, 18, 27, 27, 2)
        c.rect(12, 27, 15, 31, 3); c.rect(19, 27, 22, 31, 3)
        c.rect(6, 28, 9, 28, 6); c.rect(25, 28, 28, 28, 6)
        c.rect(12, 8, 15, 10, 9); c.rect(19, 8, 22, 10, 9); c.rect(13, 9, 14, 9, 10); c.rect(20, 9, 21, 9, 10)
        c.rect(13, 13, 21, 14, 7)
        for (x in 13..21 step 2) c.p(x, 13, 6)
        c.finish()
        return c.toSpr(pal(0xFF6C3BD1L))
    }

    val players: List<Spr> by lazy { listOf(human(0, P.SKY), human(1, P.VIOLET), human(2, P.GREEN), human(3, P.ORANGE)) }
    val slimeA by lazy { slime(false) }
    val slimeB by lazy { slime(true) }
    val batA by lazy { bat(true) }
    val batB by lazy { bat(false) }
    val bruteS by lazy { brute() }
    val spitterS by lazy { spitter() }
    val bossS by lazy { boss() }
}
