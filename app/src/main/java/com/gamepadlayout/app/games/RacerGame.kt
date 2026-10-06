package com.gamepadlayout.app.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Corrida retrô em pseudo-3D (estrada em faixas, como nos arcades dos anos 80/90).
 * Corra contra o relógio, ultrapasse o trânsito e passe pelos checkpoints para ganhar tempo.
 */
class RacerGame : MiniGame(
    "racer", "Estrada Neon", "Corrida contra o relógio. Ultrapasse o trânsito e cruze os checkpoints!",
    "Analógico / D-pad: dirigir (cima acelera, baixo freia) · A: turbo · toque: arraste"
) {
    private class Car(var z: Float, val lane: Float, val speed: Float, val tint: Long) {
        var hit = false
        var passed = false
    }

    private val rnd = Random.Default
    private val cars = ArrayList<Car>()
    private var pos = 0f
    private var speed = 0f
    private var playerX = 0f
    private var heading = 0f
    private var timeLeft = 40f
    private var nextCheck = 4000f
    private var checkBanner = 0f
    private var crashT = 0f
    private var turbo = 1f
    private var boosting = false
    private var overtakes = 0
    private var clock = 0f
    private var spawnT = 0f

    private companion object {
        const val H = 450f
        const val HY = 175f
        const val K = 45f
        const val MAXSPEED = 230f
        const val STRIP = 3f
        const val N = 91
        val TINTS = longArrayOf(P.RED, P.SKY, P.ORANGE, P.LIME, P.LILAC, P.CYAN)
    }

    private val cxArr = FloatArray(N)
    private val hwArr = FloatArray(N)
    private val rzArr = FloatArray(N)

    init { reset() }

    override fun reset() {
        cars.clear()
        pos = 0f; speed = 0f; playerX = 0f; heading = 0f
        timeLeft = 40f; nextCheck = 4000f; checkBanner = 0f; crashT = 0f
        turbo = 1f; boosting = false; overtakes = 0; clock = 0f; spawnT = 0f
        score = 0
        over = false
        for (i in 0 until 4) spawn(900f + i * 650f)
    }

    private fun curve(s: Float): Float {
        val a = 1.1f * sin(s / 900f) + 0.7f * sin(s / 410f + 1.3f)
        return if (s < 700f) a * (s / 700f) else a
    }

    private fun spawn(ahead: Float) {
        val lane = (rnd.nextInt(3) - 1) * 0.55f + (rnd.nextFloat() - 0.5f) * 0.1f
        cars.add(Car(pos + ahead, lane, MAXSPEED * (0.35f + rnd.nextFloat() * 0.35f), TINTS[rnd.nextInt(TINTS.size)]))
    }

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        clock += dt
        timeLeft -= dt
        if (checkBanner > 0f) checkBanner -= dt
        if (crashT > 0f) crashT -= dt

        boosting = input.fire && turbo > 0.02f
        turbo = if (boosting) max(0f, turbo - 0.45f * dt) else min(1f, turbo + 0.12f * dt)
        val offRoad = abs(playerX) > 1.08f
        var cap = MAXSPEED * (1f + min(0.25f, pos / 60000f))
        if (boosting) cap *= 1.3f
        if (offRoad) cap = 70f
        if (crashT > 0f) cap = min(cap, 90f)
        val brake = input.dy > 0.5f
        val target = if (brake) 0f else cap
        val rate = if (brake) 260f else if (speed > cap) 220f else 110f + (if (boosting) 120f else 0f)
        speed += (target - speed).coerceIn(-rate * dt, rate * dt)
        speed = max(0f, speed)

        val sp = speed / MAXSPEED
        playerX += input.dx * 1.9f * dt * (0.35f + 0.65f * min(1f, sp * 1.4f))
        val c = curve(pos)
        playerX -= c * sp * sp * 0.75f * dt
        playerX = playerX.coerceIn(-1.6f, 1.6f)
        heading += c * sp * dt * 18f
        pos += speed * dt

        if (pos >= nextCheck) {
            nextCheck += 4000f
            timeLeft += 22f
            checkBanner = 1.8f
        }

        spawnT -= dt
        if (spawnT <= 0f && cars.size < 7) {
            spawnT = 1.1f + rnd.nextFloat() * 1.4f
            spawn(2600f + rnd.nextFloat() * 900f)
        }
        for (car in cars) {
            car.z += car.speed * dt
            val rz = car.z - pos
            if (!car.passed && rz < -20f) {
                car.passed = true
                if (!car.hit) overtakes++
            }
            if (!car.hit && rz > -12f && rz < 14f && abs(car.lane - playerX) < 0.3f) {
                car.hit = true
                crashT = 0.8f
                speed *= 0.3f
            }
        }
        cars.removeAll { it.z - pos < -400f || it.z - pos > 5200f }

        score = (pos / 10f).toInt() + overtakes * 25
        if (timeLeft <= 0f) {
            timeLeft = 0f
            finish()
        }
    }

    private val pal = mapOf('W' to 0xFF9FD8F5L, 'R' to 0xFFFF3B3BL, 'K' to P.INK, 'B' to 0xFF7A4F30L, 'G' to 0xFF2E9B57L, 'L' to 0xFF63D27AL, 'Y' to P.YELLOW)
    private val carSpr = Sprite(
        listOf(
            "....SSSSSS....",
            "...STWWWWTS...",
            "..STTWWWWTTS..",
            ".STTTTTTTTTTS.",
            "STTTTTTTTTTTTS",
            "SRRTTTTTTTTRRS",
            "SKKSSSSSSSSKKS",
            ".KK........KK."
        ), pal
    )
    private val palm = Sprite(
        listOf(
            "G.GGGGG.G",
            ".GGGLGGG.",
            "..GGGGG..",
            "G..GBG..G",
            "....B....",
            "....B....",
            "....B....",
            "....B....",
            "...BBB..."
        ), pal
    )

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(P.INK)
        // céu em faixas
        val sky = longArrayOf(0xFF1A0B4BL, 0xFF3A1670L, 0xFF6A1F8AL, 0xFFA02D8AL, 0xFFD9507AL, 0xFFFF8A5AL, 0xFFFFC070L)
        val bh = HY / sky.size
        for (i in sky.indices) v.rect(0f, i * bh, 800f, bh + 1f, sky[i])
        // sol listrado
        val sunY = HY - 42f
        var sy = sunY - 58f
        var k = 0
        while (sy < sunY + 58f) {
            val dy = sy + 2f - sunY
            val half = kotlin.math.sqrt((58f * 58f - dy * dy).coerceAtLeast(0f))
            if (!(k > 6 && k % 3 == 0)) v.rect(560f - half, sy, half * 2f, 5f, P.YELLOW)
            sy += 6f; k++
        }
        // montanhas (parallax pelo "heading")
        for (layer in 0 until 2) {
            val col = if (layer == 0) 0xFF3B2478L else 0xFF2A1858L
            val off = heading * (0.06f + layer * 0.05f)
            var x = -16f
            while (x < 816f) {
                val hh = 22f + 26f * (0.5f + 0.5f * sin((x + off) / (46f + layer * 20f) + layer * 2f)) +
                    14f * sin((x + off) / 19f)
                v.rect(x, HY - hh - layer * 6f, 17f, hh + layer * 6f, col)
                x += 16f
            }
        }

        // faixas da estrada (de perto para longe)
        var dxs = 0f
        var lat = 0f
        var prevRz = 0f
        for (i in 0 until N) {
            val y = H - STRIP * (i + 1)
            val t = (y + STRIP / 2f - HY) / (H - HY)
            val rz = K * (1f / t - 1f)
            val dRz = rz - prevRz
            prevRz = rz
            dxs += curve(pos + rz) * dRz
            lat += dxs * dRz
            val hw = 330f * t
            val cx = 400f + lat * t * 0.0007f - playerX * hw
            rzArr[i] = rz; hwArr[i] = hw; cxArr[i] = cx
            val band = ((pos + rz) / 36f).toInt() % 2 == 0
            val grass = if (band) 0xFF2F8F4FL else 0xFF37A05AL
            val road = if (band) 0xFF4B4F63L else 0xFF454960L
            val rumble = if (((pos + rz) / 18f).toInt() % 2 == 0) P.RED else P.WHITE
            v.rect(0f, y, 800f, STRIP + 0.5f, grass)
            v.rect(cx - hw * 1.14f, y, hw * 2.28f, STRIP + 0.5f, rumble)
            v.rect(cx - hw, y, hw * 2f, STRIP + 0.5f, road)
            if (band) {
                for (l in intArrayOf(-1, 1)) v.rect(cx + l * hw * 0.34f - hw * 0.012f, y, hw * 0.024f, STRIP + 0.5f, P.WHITE)
            }
            if (((pos + rz) / 60f).toInt() % 2 == 0) {
                v.rect(cx - hw * 0.012f, y, hw * 0.024f, STRIP + 0.5f, P.YELLOW)
            }
        }

        // cenário e trânsito, do mais longe para o mais perto
        class Item(val rz: Float, val kind: Int, val side: Float, val car: Car?)
        val items = ArrayList<Item>()
        val s0 = (pos / 90f).toInt() * 90f
        for (n in 0..44) {
            val s = s0 + n * 90f
            val rz = s - pos
            if (rz < 0f) continue
            val side = if ((s / 90f).toInt() % 2 == 0) -1f else 1f
            items.add(Item(rz, 0, side, null))
        }
        for (car in cars) {
            val rz = car.z - pos
            if (rz > -10f && rz < 3800f) items.add(Item(rz, 1, 0f, car))
        }
        items.sortByDescending { it.rz }
        for (it in items) {
            val t = K / (it.rz + K)
            val y = HY + t * (H - HY)
            val idx = (((H - y) / STRIP).toInt() - 1).coerceIn(0, N - 1)
            val cx = cxArr[idx]
            val hw = hwArr[idx]
            if (it.kind == 0) {
                val px = 9f * t
                if (px < 0.7f) continue
                val sx = cx + it.side * hw * 1.55f
                palm.draw(v, sx - 4.5f * px, y - 9f * px, px)
            } else {
                val car = it.car!!
                val px = 8f * t
                if (px < 0.6f) continue
                val x = cx + car.lane * hw
                if (car.hit) {
                    if ((clock * 14f).toInt() % 2 == 0) continue
                }
                v.rect(x - 7f * px, y - 1f * px, 14f * px, 1.2f * px, C.alpha(C.BLACK, 0.3f))
                carSpr.draw(v, x - 7f * px, y - 8f * px, px, tint = car.tint)
            }
        }

        // carro do jogador
        val shake = if (crashT > 0f) sin(clock * 70f) * 4f else 0f
        val ppx = 7.4f
        val py = H - 10f - 8f * ppx
        val px0 = 400f - 7f * ppx + shake
        if (boosting) {
            v.rect(px0 + 2f * ppx, py + 7.5f * ppx, 3f * ppx, 2.5f * ppx + (clock * 40f).toInt() % 3 * 3f, P.ORANGE)
            v.rect(px0 + 9f * ppx, py + 7.5f * ppx, 3f * ppx, 2.5f * ppx + (clock * 40f).toInt() % 3 * 3f, P.ORANGE)
        }
        v.rect(px0 - 2f, py + 8f * ppx, 14f * ppx + 4f, 6f, C.alpha(C.BLACK, 0.35f))
        carSpr.draw(v, px0, py, ppx, tint = P.YELLOW)

        v.hudBar("PONTOS $score", "TEMPO ${timeLeft.toInt()}  ${(speed * 1.2f).toInt()} KM/H")
        // turbo
        v.rect(10f, 424f, 124f, 14f, P.INK)
        v.rect(12f, 426f, 120f * turbo, 10f, if (boosting) P.ORANGE else P.CYAN)
        v.pixText("TURBO", 142f, 426f, 1.8f, P.WHITE, shadow = false)
        if (checkBanner > 0f) v.pixText("CHECKPOINT! +TEMPO", 400f, 90f, 4.5f, P.YELLOW, center = true)
        if (pos < 40f && !over) v.pixText("ACELERA!", 400f, 90f, 5f, P.YELLOW, center = true)
    }
}
