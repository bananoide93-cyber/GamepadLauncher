package com.gamepadlayout.app.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A galinha atravessando a rua (e o rio): avance o máximo que conseguir. */
class ChickenGame : MiniGame(
    "chicken", "Galinha na Estrada", "Atravesse ruas e rios. Cuidado com os carros e fique sobre os troncos!",
    "D-pad: pular · A: avançar · toque: arraste ou toque para avançar"
) {
    private class Obj(var x: Float, val w: Float, val color: Long)

    private class Row(val kind: Int, val speed: Float) {
        val objs = ArrayList<Obj>()
        val trees = BooleanArray(16)
        var timer = 0f
        var minW = 60f
        var maxW = 100f
        var minGap = 150f
        var maxGap = 300f
    }

    private val grass = 0
    private val road = 1
    private val river = 2
    private val cell = 50f
    private val ncols = 16

    private val rnd = Random.Default
    private val rows = ArrayList<Row>()
    private var blockLeft = 0
    private var blockKind = 0
    private var lastKind = 0

    private var cr = 0
    private var maxRow = 0
    private var x = 8 * 50f + 25f
    private var vr = 0f
    private var vx = 8 * 50f + 25f
    private var time = 0f

    private val carColors = longArrayOf(0xFFE53935L, 0xFF42A5F5L, 0xFFFFD54FL, 0xFFAB47BCL, 0xFF26A69AL, 0xFFFF7043L)

    init { reset() }

    override fun reset() {
        rows.clear()
        blockLeft = 0
        blockKind = 0
        lastKind = 0
        cr = 0
        maxRow = 0
        x = 8 * cell + cell / 2f
        vx = x
        vr = 0f
        time = 0f
        score = 0
        over = false
        ensure(16)
    }

    private fun ensure(upTo: Int) {
        while (rows.size <= upTo) rows.add(genRow(rows.size))
    }

    private fun genRow(i: Int): Row {
        if (i < 2) { lastKind = grass; return Row(grass, 0f) }
        if (blockLeft == 0) {
            val p = rnd.nextFloat()
            blockKind = when {
                lastKind != grass -> grass
                p < 0.55f -> road
                p < 0.80f -> river
                else -> grass
            }
            blockLeft = when (blockKind) {
                road -> 1 + rnd.nextInt(3)
                river -> 1 + rnd.nextInt(2)
                else -> 1
            }
        }
        blockLeft--
        lastKind = blockKind
        val diff = 1f + min(0.8f, maxRow / 80f)
        val dir = if (rnd.nextBoolean()) 1f else -1f
        return when (blockKind) {
            road -> {
                val r = Row(road, dir * (90f + rnd.nextFloat() * 130f) * diff)
                r.minW = 60f; r.maxW = 105f; r.minGap = 130f + abs(r.speed) * 0.6f; r.maxGap = r.minGap + 160f
                fill(r)
                r
            }
            river -> {
                val r = Row(river, dir * (45f + rnd.nextFloat() * 55f) * min(diff, 1.4f))
                r.minW = 110f; r.maxW = 190f; r.minGap = 60f; r.maxGap = 120f
                fill(r)
                r
            }
            else -> {
                val r = Row(grass, 0f)
                for (c in 0 until ncols) r.trees[c] = rnd.nextFloat() < 0.12f
                r
            }
        }
    }

    private fun pick(a: Float, b: Float): Float = a + rnd.nextFloat() * (b - a)

    private fun newObj(r: Row, x: Float, w: Float): Obj {
        val color = if (r.kind == river) 0xFF8D6E63L else carColors[rnd.nextInt(carColors.size)]
        return Obj(x, w, color)
    }

    private fun fill(r: Row) {
        var pos = -rnd.nextFloat() * 150f
        while (pos < 850f) {
            val w = pick(r.minW, r.maxW)
            r.objs.add(newObj(r, pos, w))
            pos += w + pick(r.minGap, r.maxGap)
        }
        if (r.objs.isEmpty()) { r.timer = 0f; return }
        r.timer = if (r.speed > 0f) {
            val x0 = r.objs.minOf { it.x }
            max(0.05f, (r.minGap - x0 - 10f) / abs(r.speed))
        } else {
            val r0 = r.objs.maxOf { it.x + it.w }
            max(0.05f, (r.minGap - (810f - r0)) / abs(r.speed))
        }
    }

    private fun updateRow(r: Row, dt: Float) {
        if (r.kind == grass) return
        for (o in r.objs) o.x += r.speed * dt
        r.objs.removeAll { if (r.speed > 0f) it.x > 870f else it.x + it.w < -70f }
        r.timer -= dt
        if (r.timer <= 0f) {
            val w = pick(r.minW, r.maxW)
            val gap = pick(r.minGap, r.maxGap)
            val sx = if (r.speed > 0f) -w - 10f else 810f
            r.objs.add(newObj(r, sx, w))
            r.timer = (w + gap) / abs(r.speed)
        }
    }

    private fun cellOf(px: Float): Int = (px / cell).toInt().coerceIn(0, ncols - 1)

    private fun tryMove(dc: Int, dr: Int) {
        if (dr != 0) {
            val nr = cr + dr
            if (nr < 0 || nr < maxRow - 3) return
            ensure(nr + 14)
            val target = rows[nr]
            if (target.kind == grass) {
                val c = cellOf(x)
                if (target.trees[c]) return
                x = c * cell + cell / 2f
            }
            cr = nr
            if (cr > maxRow) maxRow = cr
        } else if (dc != 0) {
            val row = rows[cr]
            if (row.kind == grass) {
                val nc = cellOf(x) + dc
                if (nc < 0 || nc >= ncols || row.trees[nc]) return
                x = nc * cell + cell / 2f
            } else {
                x = (x + dc * cell).coerceIn(15f, 785f)
            }
        }
    }

    override fun update(dt: Float, input: GameInput) {
        if (over) return
        time += dt
        ensure(cr + 16)
        val lo = max(0, cr - 3)
        val hi = min(rows.size - 1, cr + 14)
        for (i in lo..hi) updateRow(rows[i], dt)

        if (input.stepY < 0 || input.firePressed) tryMove(0, 1)
        else if (input.stepY > 0) tryMove(0, -1)
        else if (input.stepX != 0) tryMove(input.stepX, 0)

        val row = rows[cr]
        if (row.kind == river) {
            var onLog = false
            for (o in row.objs) if (x >= o.x && x <= o.x + o.w) onLog = true
            if (!onLog) { finish(); return }
            x += row.speed * dt
            if (x < 5f || x > 795f) { finish(); return }
        } else if (row.kind == road) {
            for (o in row.objs) {
                if (abs(x - (o.x + o.w / 2f)) < o.w / 2f + 12f) { finish(); return }
            }
        }
        score = maxRow
        vr += (cr - vr) * min(1f, dt * 16f)
        vx += (x - vx) * min(1f, dt * 22f)
    }

    override fun draw(g: Gfx) {
        val v = View(g, 800f, 450f)
        v.clear(0xFF2E7D32L)
        val camRow = vr - 2f
        val first = camRow.toInt() - 1
        for (r in max(0, first)..min(rows.size - 1, first + 11)) {
            val y = 400f - (r - camRow) * cell
            val row = rows[r]
            when (row.kind) {
                grass -> {
                    v.rect(0f, y, 800f, cell, if (r % 2 == 0) 0xFF4CAF50L else 0xFF43A047L)
                    for (c in 0 until ncols) if (row.trees[c]) {
                        v.rect(c * cell + 21f, y + 26f, 8f, 18f, 0xFF5D4037L)
                        v.circle(c * cell + 25f, y + 20f, 18f, 0xFF1B5E20L)
                        v.circle(c * cell + 20f, y + 15f, 8f, 0xFF2E7D32L)
                    }
                }
                road -> {
                    v.rect(0f, y, 800f, cell, 0xFF37474FL)
                    val below = rows.getOrNull(r - 1)
                    if (below != null && below.kind == road) {
                        var dx = 0f
                        while (dx < 800f) { v.rect(dx, y + cell - 2f, 26f, 4f, 0x88FFFFFFL); dx += 52f }
                    }
                    for (o in row.objs) drawCar(v, o, y, row.speed > 0f)
                }
                else -> {
                    v.rect(0f, y, 800f, cell, 0xFF1E88E5L)
                    var wx = ((time * 20f) % 60f) - 60f
                    while (wx < 800f) { v.rect(wx, y + 12f + (r % 3) * 10f, 24f, 3f, 0x55FFFFFFL); wx += 60f }
                    for (o in row.objs) {
                        v.rect(o.x + 8f, y + 8f, o.w - 16f, cell - 16f, o.color)
                        v.circle(o.x + 8f, y + cell / 2f, cell / 2f - 8f, o.color)
                        v.circle(o.x + o.w - 8f, y + cell / 2f, cell / 2f - 8f, C.shade(o.color, 0.8f))
                        v.rect(o.x + o.w / 2f - 1f, y + 12f, 2f, cell - 24f, C.shade(o.color, 0.7f))
                    }
                }
            }
        }
        drawChicken(v)
        v.text("Pontos: $score", 12f, 24f, 20f, C.WHITE, false)
        v.text("Recorde: $best", 790f - 120f, 24f, 16f, C.WHITE, false)
    }

    private fun drawCar(v: View, o: Obj, y: Float, right: Boolean) {
        v.rect(o.x, y + 9f, o.w, cell - 18f, o.color)
        v.rect(o.x + o.w * 0.28f, y + 13f, o.w * 0.44f, cell - 26f, C.shade(o.color, 0.7f))
        v.rect(o.x + o.w * 0.32f, y + 16f, o.w * 0.14f, cell - 32f, 0xFFB3E5FCL)
        v.rect(o.x + o.w * 0.54f, y + 16f, o.w * 0.14f, cell - 32f, 0xFFB3E5FCL)
        val hx = if (right) o.x + o.w - 5f else o.x
        v.rect(hx, y + 12f, 5f, 6f, C.YELLOW)
        v.rect(hx, y + cell - 18f, 5f, 6f, C.YELLOW)
        v.rect(o.x + 8f, y + 6f, 14f, 5f, C.BLACK)
        v.rect(o.x + o.w - 22f, y + 6f, 14f, 5f, C.BLACK)
        v.rect(o.x + 8f, y + cell - 11f, 14f, 5f, C.BLACK)
        v.rect(o.x + o.w - 22f, y + cell - 11f, 14f, 5f, C.BLACK)
    }

    private fun drawChicken(v: View) {
        val d = min(1f, abs(cr - vr))
        val lift = sin(d * 3.14159f) * 10f
        val cx = vx
        val cy = 325f - lift
        if (over) {
            v.rect(cx - 20f, cy + 4f, 40f, 10f, C.WHITE)
            v.rect(cx - 12f, cy + 8f, 24f, 8f, C.RED)
            return
        }
        v.circle(cx + 3f, cy + 18f, 15f, 0x33000000L)
        v.line(cx - 6f, cy + 14f, cx - 9f, cy + 24f, 3f, C.ORANGE)
        v.line(cx + 6f, cy + 14f, cx + 9f, cy + 24f, 3f, C.ORANGE)
        v.circle(cx, cy, 17f, C.WHITE)
        v.circle(cx - 8f, cy + 3f, 8f, 0xFFE0E0E0L)
        v.circle(cx + 8f, cy + 3f, 8f, 0xFFE0E0E0L)
        v.circle(cx, cy - 15f, 10f, C.WHITE)
        v.circle(cx, cy - 25f, 4f, C.RED)
        v.circle(cx - 4f, cy - 24f, 3f, C.RED)
        v.circle(cx + 4f, cy - 24f, 3f, C.RED)
        v.rect(cx - 3f, cy - 10f, 6f, 6f, C.ORANGE)
        v.circle(cx - 4f, cy - 17f, 1.8f, C.BLACK)
        v.circle(cx + 4f, cy - 17f, 1.8f, C.BLACK)
    }
}
