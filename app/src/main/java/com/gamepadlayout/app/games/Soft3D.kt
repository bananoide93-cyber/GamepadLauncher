package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Malha de baixo polígono: vértices (x,y,z), faces (3 índices) e uma cor por face. */
class Mesh(val v: FloatArray, val f: IntArray, val col: LongArray, val cull: Boolean = true, val glow: BooleanArray? = null) {
    /** Raio da esfera que contém a malha, centrada na origem local (usado no descarte fora da tela). */
    val rad: Float = run {
        var m = 0f
        for (i in 0 until v.size / 3) {
            val d = v[i * 3] * v[i * 3] + v[i * 3 + 1] * v[i * 3 + 1] + v[i * 3 + 2] * v[i * 3 + 2]
            if (d > m) m = d
        }
        sqrt(m)
    }
    val tris: Int get() = f.size / 3
}

/** Construtor de malhas com normais garantidas para fora (a ordem dos vértices é corrigida sozinha). */
class MeshBuilder {
    private val vs = ArrayList<Float>()
    private val fs = ArrayList<Int>()
    private val cs = ArrayList<Long>()
    private val gs = ArrayList<Boolean>()
    /** Enquanto true, as faces criadas brilham (ignoram a luz): chamas, cristais, janelas acesas. */
    var glow = false

    fun vert(x: Float, y: Float, z: Float): Int { vs.add(x); vs.add(y); vs.add(z); return vs.size / 3 - 1 }
    private fun vx(i: Int) = vs[i * 3]
    private fun vy(i: Int) = vs[i * 3 + 1]
    private fun vz(i: Int) = vs[i * 3 + 2]

    /** Triângulo com normal desejada (nx,ny,nz): troca a ordem se necessário. */
    fun triN(a: Int, b: Int, c: Int, nx: Float, ny: Float, nz: Float, color: Long) {
        val ux = vx(b) - vx(a); val uy = vy(b) - vy(a); val uz = vz(b) - vz(a)
        val wx = vx(c) - vx(a); val wy = vy(c) - vy(a); val wz = vz(c) - vz(a)
        val cx = uy * wz - uz * wy; val cy = uz * wx - ux * wz; val cz = ux * wy - uy * wx
        if (cx * cx + cy * cy + cz * cz < 1e-10f) return
        val d = cx * nx + cy * ny + cz * nz
        if (d >= 0f) { fs.add(a); fs.add(b); fs.add(c) } else { fs.add(a); fs.add(c); fs.add(b) }
        cs.add(color); gs.add(glow)
    }

    fun quadN(a: Int, b: Int, c: Int, d: Int, nx: Float, ny: Float, nz: Float, color: Long) {
        triN(a, b, c, nx, ny, nz, color); triN(a, c, d, nx, ny, nz, color)
    }

    fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, color: Long, top: Long = color) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2
        val y0 = cy - sy / 2; val y1 = cy + sy / 2
        val z0 = cz - sz / 2; val z1 = cz + sz / 2
        val a = vert(x0, y0, z0); val b = vert(x1, y0, z0); val c = vert(x1, y1, z0); val d = vert(x0, y1, z0)
        val e = vert(x0, y0, z1); val f = vert(x1, y0, z1); val g = vert(x1, y1, z1); val h = vert(x0, y1, z1)
        quadN(a, b, c, d, 0f, 0f, -1f, color)
        quadN(e, f, g, h, 0f, 0f, 1f, color)
        quadN(a, e, h, d, -1f, 0f, 0f, color)
        quadN(b, f, g, c, 1f, 0f, 0f, color)
        quadN(d, c, g, h, 0f, 1f, 0f, top)
        quadN(a, b, f, e, 0f, -1f, 0f, color)
    }

    /** Caixa sem a face de baixo (para peças que ficam apoiadas no chão): menos triângulos. */
    fun boxNoBottom(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, color: Long, top: Long = color) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2
        val y0 = cy - sy / 2; val y1 = cy + sy / 2
        val z0 = cz - sz / 2; val z1 = cz + sz / 2
        val a = vert(x0, y0, z0); val b = vert(x1, y0, z0); val c = vert(x1, y1, z0); val d = vert(x0, y1, z0)
        val e = vert(x0, y0, z1); val f = vert(x1, y0, z1); val g = vert(x1, y1, z1); val h = vert(x0, y1, z1)
        quadN(a, b, c, d, 0f, 0f, -1f, color)
        quadN(e, f, g, h, 0f, 0f, 1f, color)
        quadN(a, e, h, d, -1f, 0f, 0f, color)
        quadN(b, f, g, c, 1f, 0f, 0f, color)
        quadN(d, c, g, h, 0f, 1f, 0f, top)
    }

    /** Tronco de cone vertical: raio r0 em cy, raio r1 em cy+h (r1 = 0 vira cone). */
    fun frustum(cx: Float, cy: Float, cz: Float, r0: Float, r1: Float, h: Float, sides: Int, color: Long, cap: Long = color) {
        val b = IntArray(sides); val t = IntArray(sides)
        for (i in 0 until sides) {
            val a = (i.toFloat() / sides) * 2f * PI.toFloat()
            b[i] = vert(cx + cos(a) * r0, cy, cz + sin(a) * r0)
            t[i] = vert(cx + cos(a) * r1, cy + h, cz + sin(a) * r1)
        }
        val slope = (r0 - r1) / max(0.001f, h)
        for (i in 0 until sides) {
            val j = (i + 1) % sides
            val am = ((i + 0.5f) / sides) * 2f * PI.toFloat()
            quadN(b[i], b[j], t[j], t[i], cos(am), slope, sin(am), color)
        }
        if (r1 > 0.001f) {
            val c = vert(cx, cy + h, cz)
            for (i in 0 until sides) triN(c, t[i], t[(i + 1) % sides], 0f, 1f, 0f, cap)
        }
    }

    /** Tronco de cone com a cor alternando por faixa (tijolos, listras). */
    fun frustumStripes(cx: Float, cy: Float, cz: Float, r0: Float, r1: Float, h: Float, sides: Int, c1: Long, c2: Long, cap: Long = c1) {
        val b = IntArray(sides); val t = IntArray(sides)
        for (i in 0 until sides) {
            val a = (i.toFloat() / sides) * 2f * PI.toFloat()
            b[i] = vert(cx + cos(a) * r0, cy, cz + sin(a) * r0)
            t[i] = vert(cx + cos(a) * r1, cy + h, cz + sin(a) * r1)
        }
        val slope = (r0 - r1) / max(0.001f, h)
        for (i in 0 until sides) {
            val j = (i + 1) % sides
            val am = ((i + 0.5f) / sides) * 2f * PI.toFloat()
            quadN(b[i], b[j], t[j], t[i], cos(am), slope, sin(am), if (i % 2 == 0) c1 else c2)
        }
        if (r1 > 0.001f) {
            val c = vert(cx, cy + h, cz)
            for (i in 0 until sides) triN(c, t[i], t[(i + 1) % sides], 0f, 1f, 0f, cap)
        }
    }

    fun sphere(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, color: Long, seg: Int = 6, ring: Int = 4, color2: Long = color) {
        val idx = Array(ring + 1) { IntArray(seg) }
        for (r in 0..ring) {
            val lat = -PI.toFloat() / 2f + PI.toFloat() * r / ring
            for (s in 0 until seg) {
                val lon = 2f * PI.toFloat() * s / seg
                idx[r][s] = vert(cx + cos(lat) * cos(lon) * rx, cy + sin(lat) * ry, cz + cos(lat) * sin(lon) * rz)
            }
        }
        for (r in 0 until ring) for (s in 0 until seg) {
            val s2 = (s + 1) % seg
            val lat = -PI.toFloat() / 2f + PI.toFloat() * (r + 0.5f) / ring
            val lon = 2f * PI.toFloat() * (s + 0.5f) / seg
            val nx = cos(lat) * cos(lon); val ny = sin(lat); val nz = cos(lat) * sin(lon)
            quadN(idx[r][s], idx[r][s2], idx[r + 1][s2], idx[r + 1][s], nx, ny, nz, if (color2 != color && (s + r) % 2 == 1) color2 else color)
        }
    }

    /** Pirâmide/octaedro: pontas em cima e embaixo (cristais). */
    fun gem(cx: Float, cy: Float, cz: Float, r: Float, hUp: Float, hDown: Float, sides: Int, color: Long, color2: Long = color) {
        val top = vert(cx, cy + hUp, cz); val bot = vert(cx, cy - hDown, cz)
        val m = IntArray(sides)
        for (i in 0 until sides) { val a = i.toFloat() / sides * 2f * PI.toFloat(); m[i] = vert(cx + cos(a) * r, cy, cz + sin(a) * r) }
        for (i in 0 until sides) {
            val j = (i + 1) % sides
            val am = (i + 0.5f) / sides * 2f * PI.toFloat()
            triN(top, m[i], m[j], cos(am), 0.6f, sin(am), if (i % 2 == 0) color else C.shade(color, 0.86f))
            triN(bot, m[i], m[j], cos(am), -0.6f, sin(am), color2)
        }
    }

    /** Cunha (prisma triangular) deitada: base retangular sx*sz e aresta superior ao longo de z. */
    fun roof(cx: Float, cy: Float, cz: Float, sx: Float, h: Float, sz: Float, color: Long, color2: Long = color) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2; val z0 = cz - sz / 2; val z1 = cz + sz / 2
        val a = vert(x0, cy, z0); val b = vert(x1, cy, z0); val c = vert(cx, cy + h, z0)
        val d = vert(x0, cy, z1); val e = vert(x1, cy, z1); val f = vert(cx, cy + h, z1)
        val sl = (sx / 2f) / max(0.01f, h)
        quadN(a, d, f, c, -1f, sl, 0f, color)
        quadN(b, e, f, c, 1f, sl, 0f, color2)
        triN(a, b, c, 0f, 0f, -1f, color2)
        triN(d, e, f, 0f, 0f, 1f, color2)
    }

    /** Copia outra malha para dentro desta (com giro em Y, escala e deslocamento). */
    fun add(m: Mesh, ox: Float, oy: Float, oz: Float, yaw: Float = 0f, scale: Float = 1f) {
        val base = vs.size / 3
        val cy = cos(yaw); val sy = sin(yaw)
        for (i in 0 until m.v.size / 3) {
            val x = m.v[i * 3] * scale; val y = m.v[i * 3 + 1] * scale; val z = m.v[i * 3 + 2] * scale
            vert(ox + x * cy + z * sy, oy + y, oz - x * sy + z * cy)
        }
        for (k in 0 until m.f.size / 3) {
            fs.add(base + m.f[k * 3]); fs.add(base + m.f[k * 3 + 1]); fs.add(base + m.f[k * 3 + 2]); cs.add(m.col[k]); gs.add(m.glow?.get(k) ?: false)
        }
    }


    /** Viga de seção quadrada entre dois pontos (galhos, trilhos de cerca, cabos). t = meia espessura. */
    fun beam(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, t: Float, color: Long, capColor: Long = color) {
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-4f) return
        dx /= len; dy /= len; dz /= len
        var ux = 0f; var uy = 1f; var uz = 0f
        if (abs(dy) > 0.95f) { ux = 1f; uy = 0f }
        // s = d x u ; w = s x d
        var sx = dy * uz - dz * uy; var sy = dz * ux - dx * uz; var sz = dx * uy - dy * ux
        val sl = sqrt(sx * sx + sy * sy + sz * sz); sx /= sl; sy /= sl; sz /= sl
        val wx = sy * dz - sz * dy; val wy = sz * dx - sx * dz; val wz = sx * dy - sy * dx
        val a0 = vert(ax - sx * t - wx * t, ay - sy * t - wy * t, az - sz * t - wz * t)
        val a1 = vert(ax + sx * t - wx * t, ay + sy * t - wy * t, az + sz * t - wz * t)
        val a2 = vert(ax + sx * t + wx * t, ay + sy * t + wy * t, az + sz * t + wz * t)
        val a3 = vert(ax - sx * t + wx * t, ay - sy * t + wy * t, az - sz * t + wz * t)
        val b0 = vert(bx - sx * t - wx * t, by - sy * t - wy * t, bz - sz * t - wz * t)
        val b1 = vert(bx + sx * t - wx * t, by + sy * t - wy * t, bz + sz * t - wz * t)
        val b2 = vert(bx + sx * t + wx * t, by + sy * t + wy * t, bz + sz * t + wz * t)
        val b3 = vert(bx - sx * t + wx * t, by - sy * t + wy * t, bz - sz * t + wz * t)
        quadN(a0, a1, b1, b0, -wx, -wy, -wz, color)
        quadN(a1, a2, b2, b1, sx, sy, sz, C.shade(color, 0.92f))
        quadN(a2, a3, b3, b2, wx, wy, wz, C.shade(color, 1.06f))
        quadN(a3, a0, b0, b3, -sx, -sy, -sz, C.shade(color, 0.86f))
        quadN(a0, a1, a2, a3, -dx, -dy, -dz, capColor)
        quadN(b0, b1, b2, b3, dx, dy, dz, capColor)
    }

    /** Disco plano horizontal (lagoas, folhas de vitória-régia, tapetes). */
    fun disc(cx: Float, cy: Float, cz: Float, r: Float, sides: Int, color: Long, color2: Long = color) {
        val c = vert(cx, cy, cz)
        val ring = IntArray(sides) { val a = it.toFloat() / sides * 2f * PI.toFloat(); vert(cx + cos(a) * r, cy, cz + sin(a) * r) }
        for (i in 0 until sides) triN(c, ring[i], ring[(i + 1) % sides], 0f, 1f, 0f, if (i % 2 == 0) color else color2)
    }

    /** Folha dupla-face (grama, folhas): quadrilátero sem culling. */
    fun blade(x0: Float, z0: Float, h: Float, w: Float, lean: Float, color: Long, tip: Long = color) {
        val a = vert(x0 - w, 0f, z0); val b = vert(x0 + w, 0f, z0); val c = vert(x0 + lean, h, z0)
        triN(a, b, c, 0f, 0f, 1f, color)
        triN(a, b, c, 0f, 0f, -1f, tip)
    }

    fun build(cull: Boolean = true): Mesh = Mesh(vs.toFloatArray(), fs.toIntArray(), cs.toLongArray(), cull, if (gs.any { it }) gs.toBooleanArray() else null)
}

/**
 * Renderizador 3D por software.
 *
 * Todos os objetos (terreno, cenário, personagens) entram na mesma lista e são ordenados do mais longe ao mais perto
 * por "grupo" (cada instância é um grupo, ordenado pela distância horizontal até a câmera); dentro do grupo os
 * triângulos são ordenados pela profundidade. Isso evita a mistura de triângulos de objetos diferentes (o que
 * causava as texturas piscando e os modelos atravessando uns aos outros). A lista inteira vai para a tela em um
 * único lote.
 */
class R3 {
    var focal = 430f
    var cxs = 400f
    var cys = 225f
    private val near = 0.45f
    var camX = 0f; var camY = 0f; var camZ = 0f
    var yaw = 0f; var pitch = 0f
    private var cy = 1f; private var sy = 0f; private var cp = 1f; private var sp = 0f

    var fogColor = 0xFFBFE3F5L
    var fogStart = 40f
    var fogEnd = 120f

    /** Cor da luz (multiplicador por canal) e brilho ambiente da região. */
    var lightR = 1f; var lightG = 1f; var lightB = 1f
    var ambient = 0.40f

    /** Luz principal (sol) e luz de preenchimento. */
    private val lx = 0.46f; private val ly = 0.78f; private val lz = -0.42f
    private val mx = -0.55f; private val my = 0.25f; private val mz = 0.80f

    private val MAX = 15000
    private val px = FloatArray(MAX * 6)
    private val col = IntArray(MAX)
    private val keys = LongArray(MAX)
    private var n = 0
    private var gKey = 0L

    // buffers de recorte
    private val cv = FloatArray(12)
    private val ov = FloatArray(15)

    // saída ordenada
    private var outPos = FloatArray(MAX * 6)
    private var outCol = IntArray(MAX)

    var dropped = 0
        private set

    fun begin(x: Float, y: Float, z: Float, yawA: Float, pitchA: Float) {
        camX = x; camY = y; camZ = z; yaw = yawA; pitch = pitchA
        cy = cos(yaw); sy = sin(yaw); cp = cos(pitch); sp = sin(pitch)
        n = 0; dropped = 0; gKey = 0L
    }

    /** Converte um ponto do mundo para a tela (false se estiver atrás da câmera). */
    fun project(wx: Float, wy: Float, wz: Float, out: FloatArray): Boolean {
        val dx = wx - camX; val dy = wy - camY; val dz = wz - camZ
        val xc = dx * cy - dz * sy
        val z0 = dx * sy + dz * cy
        val zc = z0 * cp - dy * sp
        val yc = z0 * sp + dy * cp
        if (zc < near) return false
        out[0] = cxs + xc / zc * focal
        out[1] = cys - yc / zc * focal
        out[2] = zc
        return true
    }

    /** Profundidade da câmera de um ponto (negativo/zero = atrás). */
    fun depthOf(wx: Float, wy: Float, wz: Float): Float {
        val dx = wx - camX; val dy = wy - camY; val dz = wz - camZ
        val z0 = dx * sy + dz * cy
        return z0 * cp - dy * sp
    }

    private fun camT(wx: Float, wy: Float, wz: Float, o: FloatArray, i: Int) {
        val dx = wx - camX; val dy = wy - camY; val dz = wz - camZ
        val xc = dx * cy - dz * sy
        val z0 = dx * sy + dz * cy
        o[i] = xc
        o[i + 1] = z0 * sp + dy * cp
        o[i + 2] = z0 * cp - dy * sp
    }

    // ------------------------------------------------------------------ grupos e descarte

    private fun qd(d: Float): Long = ((260f - d) * 50f).toLong().coerceIn(0L, 65535L)

    /** Inicia um grupo ordenado pela distância horizontal (+ viés) até (x,z). layer 0 = cena, 1 = por cima de tudo. */
    fun group(x: Float, z: Float, bias: Float = 0f, layer: Int = 0) {
        val dx = x - camX; val dz = z - camZ
        gKey = (layer.toLong() shl 54) or (qd(sqrt(dx * dx + dz * dz) + bias) shl 38)
    }

    /** Teste rápido: a esfera (centro no mundo, raio) pode aparecer na tela? */
    fun visible(wx: Float, wy: Float, wz: Float, rad: Float): Boolean {
        val dx = wx - camX; val dy = wy - camY; val dz = wz - camZ
        val xc = dx * cy - dz * sy
        val z0 = dx * sy + dz * cy
        val zc = z0 * cp - dy * sp
        val yc = z0 * sp + dy * cp
        if (zc + rad < near) return false
        val limX = (zc + rad) * (440f / focal) + rad
        if (abs(xc) > limX) return false
        val limY = (zc + rad) * (max(cys, 450f - cys) + 50f) / focal + rad
        if (abs(yc) > limY) return false
        return true
    }

    // ------------------------------------------------------------------ triângulos

    private fun mixI(a: Int, b: Int, f: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * f).toInt(); val g = (ag + (bg - ag) * f).toInt(); val bl = (ab + (bb - ab) * f).toInt()
        return (a and (0xFF shl 24)) or (r shl 16) or (g shl 8) or bl
    }

    /**
     * Um triângulo do mundo. bright >= 0 ignora a luz. fx/fxAmt misturam uma cor de efeito (piscar de dano etc.).
     * decal = true desenha antes dos outros triângulos do mesmo grupo (sombras e marcas no chão).
     */
    fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, ccx: Float, ccy: Float, ccz: Float,
            color: Long, cull: Boolean, fx: Long = 0L, fxAmt: Float = 0f, bright: Float = -1f, decal: Boolean = false, noFog: Boolean = false) {
        var nx = (by - ay) * (ccz - az) - (bz - az) * (ccy - ay)
        var ny = (bz - az) * (ccx - ax) - (bx - ax) * (ccz - az)
        var nz = (bx - ax) * (ccy - ay) - (by - ay) * (ccx - ax)
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len < 1e-7f) return
        nx /= len; ny /= len; nz /= len
        val facing = nx * (camX - ax) + ny * (camY - ay) + nz * (camZ - az)
        if (facing < 0f) {
            if (cull) return
            nx = -nx; ny = -ny; nz = -nz
        }
        camT(ax, ay, az, cv, 0); camT(bx, by, bz, cv, 3); camT(ccx, ccy, ccz, cv, 6)
        var inside = 0
        if (cv[2] >= near) inside++
        if (cv[5] >= near) inside++
        if (cv[8] >= near) inside++
        if (inside == 0) return
        val zAvg = (cv[2] + cv[5] + cv[8]) / 3f
        var c = color.toInt()
        if (bright < 0f) {
            val d1 = max(0f, nx * lx + ny * ly + nz * lz)
            val d2 = max(0f, nx * mx + ny * my + nz * mz)
            val b = ambient + 0.50f * d1 + 0.14f * d2 + 0.10f * ny
            c = shade(c, b * lightR, b * lightG, b * lightB)
        } else c = shade(c, bright, bright, bright)
        if (fxAmt > 0f) c = mixI(c, fx.toInt(), fxAmt)
        if (!noFog && zAvg > fogStart) {
            val f = ((zAvg - fogStart) / (fogEnd - fogStart)).coerceIn(0f, 1f)
            c = mixI(c, fogColor.toInt(), f * 0.96f)
        }
        if (inside == 3) {
            push(cv[0], cv[1], cv[2], cv[3], cv[4], cv[5], cv[6], cv[7], cv[8], c, zAvg, decal)
        } else {
            // Sutherland-Hodgman contra o plano próximo
            var m = 0
            for (i in 0 until 3) {
                val j = (i + 1) % 3
                val zi = cv[i * 3 + 2]; val zj = cv[j * 3 + 2]
                val ii = zi >= near; val jj = zj >= near
                if (ii) { ov[m * 3] = cv[i * 3]; ov[m * 3 + 1] = cv[i * 3 + 1]; ov[m * 3 + 2] = zi; m++ }
                if (ii != jj) {
                    val t = (near - zi) / (zj - zi)
                    ov[m * 3] = cv[i * 3] + (cv[j * 3] - cv[i * 3]) * t
                    ov[m * 3 + 1] = cv[i * 3 + 1] + (cv[j * 3 + 1] - cv[i * 3 + 1]) * t
                    ov[m * 3 + 2] = near
                    m++
                }
            }
            if (m >= 3) {
                push(ov[0], ov[1], ov[2], ov[3], ov[4], ov[5], ov[6], ov[7], ov[8], c, zAvg, decal)
                if (m == 4) push(ov[0], ov[1], ov[2], ov[6], ov[7], ov[8], ov[9], ov[10], ov[11], c, zAvg, decal)
            }
        }
    }

    private fun shade(c: Int, fr: Float, fg: Float, fb: Float): Int {
        val r = (((c shr 16) and 0xFF) * fr).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 0xFF) * fg).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * fb).toInt().coerceIn(0, 255)
        return (c and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
    }

    private fun push(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, c: Int, zAvg: Float, decal: Boolean) {
        if (n >= MAX) { dropped++; return }
        val s1x = cxs + x1 / z1 * focal; val s1y = cys - y1 / z1 * focal
        val s2x = cxs + x2 / z2 * focal; val s2y = cys - y2 / z2 * focal
        val s3x = cxs + x3 / z3 * focal; val s3y = cys - y3 / z3 * focal
        if (max(s1x, max(s2x, s3x)) < -20f || min(s1x, min(s2x, s3x)) > 820f) return
        if (max(s1y, max(s2y, s3y)) < -20f || min(s1y, min(s2y, s3y)) > 470f) return
        val area = abs((s2x - s1x) * (s3y - s1y) - (s3x - s1x) * (s2y - s1y))
        if (area < 0.2f) return
        // expande 0,5 px a partir do centro para fechar frestas entre triângulos
        val mx = (s1x + s2x + s3x) / 3f; val my = (s1y + s2y + s3y) / 3f
        val o = n * 6
        px[o] = grow(s1x, mx); px[o + 1] = grow(s1y, my)
        px[o + 2] = grow(s2x, mx); px[o + 3] = grow(s2y, my)
        px[o + 4] = grow(s3x, mx); px[o + 5] = grow(s3y, my)
        col[n] = c
        val minor = if (decal) 0L else qd(zAvg)
        keys[n] = gKey or (minor shl 20) or n.toLong()
        n++
    }

    private fun grow(p: Float, m: Float): Float = if (p > m) p + 0.5f else p - 0.5f

    // ------------------------------------------------------------------ malhas

    private var tvBuf = FloatArray(3 * 1024)
    private fun tv(nv: Int): FloatArray { if (tvBuf.size < nv * 3) tvBuf = FloatArray(nv * 3); return tvBuf }

    /**
     * Desenha uma malha (escala → rolagem Z → inclinação X → giro Y → posição). Cria um grupo próprio, ordenado pela
     * distância da origem da malha. Devolve false se foi descartada por estar fora da tela.
     */
    fun mesh(m: Mesh, ox: Float, oy: Float, oz: Float, yawM: Float, pitchM: Float, scale: Float,
             flash: Boolean = false, rollM: Float = 0f, stretchY: Float = 1f, fx: Long = 0L, fxAmt: Float = 0f,
             bias: Float = 0f, sortX: Float = Float.NaN, sortZ: Float = Float.NaN, keepGroup: Boolean = false,
             swayX: Float = 0f, swayZ: Float = 0f): Boolean {
        if (!visible(ox, oy + m.rad * scale * 0.4f, oz, m.rad * scale * max(1f, stretchY))) return false
        if (!keepGroup) group(if (sortX.isNaN()) ox else sortX, if (sortZ.isNaN()) oz else sortZ, bias)
        val nv = m.v.size / 3
        val tmp = tv(nv)
        val cyw = cos(yawM); val syw = sin(yawM); val cpm = cos(pitchM); val spm = sin(pitchM)
        val crl = cos(rollM); val srl = sin(rollM)
        val rolled = rollM != 0f
        for (i in 0 until nv) {
            var x = m.v[i * 3] * scale; var y = m.v[i * 3 + 1] * scale * stretchY; var z = m.v[i * 3 + 2] * scale
            if (swayX != 0f || swayZ != 0f) { val hy = max(0f, y); x += swayX * hy * hy; z += swayZ * hy * hy }
            if (rolled) { val x2 = x * crl - y * srl; val y2 = x * srl + y * crl; x = x2; y = y2 }
            val y1 = y * cpm - z * spm; val z1 = y * spm + z * cpm
            val x2 = x * cyw + z1 * syw; val z2 = -x * syw + z1 * cyw
            tmp[i * 3] = ox + x2; tmp[i * 3 + 1] = oy + y1; tmp[i * 3 + 2] = oz + z2
        }
        emit(m, tmp, flash, fx, fxAmt)
        return true
    }

    /** Como [mesh], mas dentro do grupo atual (peças de um mesmo personagem). */
    fun meshKeep(m: Mesh, ox: Float, oy: Float, oz: Float, yawM: Float, pitchM: Float, scale: Float, flash: Boolean = false, fx: Long = 0L, fxAmt: Float = 0f) {
        mesh(m, ox, oy, oz, yawM, pitchM, scale, flash, 0f, 1f, fx, fxAmt, 0f, Float.NaN, Float.NaN, true)
    }

    private fun emit(m: Mesh, tmp: FloatArray, flash: Boolean, fx: Long, fxAmt: Float) {
        val fc = m.f.size / 3
        val fxc = if (flash) 0xFFFFFFFFL else fx
        val amt = if (flash) 0.85f else fxAmt
        val gl = m.glow
        for (k in 0 until fc) {
            val a = m.f[k * 3] * 3; val b = m.f[k * 3 + 1] * 3; val c = m.f[k * 3 + 2] * 3
            tri(tmp[a], tmp[a + 1], tmp[a + 2], tmp[b], tmp[b + 1], tmp[b + 2], tmp[c], tmp[c + 1], tmp[c + 2], m.col[k], m.cull, fxc, amt,
                if (gl != null && gl[k]) 1.08f else -1f)
        }
    }

    /**
     * Desenha uma malha com uma matriz 3x4 (linhas: r00 r01 r02 tx / r10 r11 r12 ty / r20 r21 r22 tz), usada pelos
     * esqueletos. Não cria grupo: use [group] antes (todas as peças de um personagem ficam no mesmo grupo).
     */
    fun meshXf(m: Mesh, xf: FloatArray, scale: Float = 1f, flash: Boolean = false, fx: Long = 0L, fxAmt: Float = 0f) {
        val nv = m.v.size / 3
        val tmp = tv(nv)
        for (i in 0 until nv) {
            val x = m.v[i * 3] * scale; val y = m.v[i * 3 + 1] * scale; val z = m.v[i * 3 + 2] * scale
            tmp[i * 3] = xf[0] * x + xf[1] * y + xf[2] * z + xf[3]
            tmp[i * 3 + 1] = xf[4] * x + xf[5] * y + xf[6] * z + xf[7]
            tmp[i * 3 + 2] = xf[8] * x + xf[9] * y + xf[10] * z + xf[11]
        }
        emit(m, tmp, flash, fx, fxAmt)
    }

    /** Mancha de sombra elíptica no chão (decalque do grupo atual). */
    fun shadow(x: Float, y: Float, z: Float, r: Float, alpha: Float = 0.34f, sx: Float = 1f, sz: Float = 1f) {
        val c = C.alpha(C.BLACK, alpha)
        val sides = 8
        var ox = 0f; var oz = 0f
        for (i in 0 until sides) {
            val a0 = i.toFloat() / sides * 2f * PI.toFloat(); val a1 = (i + 1f) / sides * 2f * PI.toFloat()
            tri(x + ox, y, z + oz, x + ox + cos(a0) * r * sx, y, z + oz + sin(a0) * r * sz, x + ox + cos(a1) * r * sx, y, z + oz + sin(a1) * r * sz,
                c, false, 0L, 0f, 1f, decal = true, noFog = false)
        }
    }

    /** Triângulo solto já com cor final (efeitos: arcos de golpe, marcas de alcance). decal = por baixo do grupo. */
    fun fxTri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, color: Long, decal: Boolean = false) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, color, false, 0L, 0f, 1f, decal, noFog = false)
    }

    /** Envia tudo o que foi acumulado para a tela, do mais longe ao mais perto. */
    fun flush(v: View) {
        if (n == 0) return
        java.util.Arrays.sort(keys, 0, n)
        val s = v.s; val ox = v.ox; val oy = v.oy
        for (i in 0 until n) {
            val id = (keys[i] and 0xFFFFFL).toInt()
            val o = id * 6; val q = i * 6
            outPos[q] = ox + px[o] * s; outPos[q + 1] = oy + px[o + 1] * s
            outPos[q + 2] = ox + px[o + 2] * s; outPos[q + 3] = oy + px[o + 3] * s
            outPos[q + 4] = ox + px[o + 4] * s; outPos[q + 5] = oy + px[o + 5] * s
            outCol[i] = col[id]
        }
        v.g.triBatch(outPos, outCol, n)
    }

    val count get() = n
}

/** Matrizes 3x4 simples (linha por linha) para os esqueletos. */
object Xf {
    fun ident(m: FloatArray) {
        m[0] = 1f; m[1] = 0f; m[2] = 0f; m[3] = 0f
        m[4] = 0f; m[5] = 1f; m[6] = 0f; m[7] = 0f
        m[8] = 0f; m[9] = 0f; m[10] = 1f; m[11] = 0f
    }

    fun copy(src: FloatArray, dst: FloatArray) { System.arraycopy(src, 0, dst, 0, 12) }

    /** m = m * T(x,y,z) */
    fun move(m: FloatArray, x: Float, y: Float, z: Float) {
        m[3] += m[0] * x + m[1] * y + m[2] * z
        m[7] += m[4] * x + m[5] * y + m[6] * z
        m[11] += m[8] * x + m[9] * y + m[10] * z
    }

    /** m = m * Rx(a): positivo leva +y para +z. */
    fun rotX(m: FloatArray, a: Float) {
        if (a == 0f) return
        val c = cos(a); val s = sin(a)
        for (r in 0 until 3) {
            val b = r * 4
            val y = m[b + 1]; val z = m[b + 2]
            m[b + 1] = y * c + z * s
            m[b + 2] = -y * s + z * c
        }
    }

    /** m = m * Ry(a): positivo leva +z para +x (giro do corpo, igual ao yaw do jogo). */
    fun rotY(m: FloatArray, a: Float) {
        if (a == 0f) return
        val c = cos(a); val s = sin(a)
        for (r in 0 until 3) {
            val b = r * 4
            val x = m[b]; val z = m[b + 2]
            m[b] = x * c - z * s
            m[b + 2] = x * s + z * c
        }
    }

    /** m = m * Rz(a) */
    fun rotZ(m: FloatArray, a: Float) {
        if (a == 0f) return
        val c = cos(a); val s = sin(a)
        for (r in 0 until 3) {
            val b = r * 4
            val x = m[b]; val y = m[b + 1]
            m[b] = x * c + y * s
            m[b + 1] = -x * s + y * c
        }
    }

    /** Aplica a matriz a um ponto local. */
    fun apply(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[1] * y + m[2] * z + m[3]
        out[1] = m[4] * x + m[5] * y + m[6] * z + m[7]
        out[2] = m[8] * x + m[9] * y + m[10] * z + m[11]
    }
}
