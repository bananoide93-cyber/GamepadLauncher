package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Tipos de objeto de cenário. */
object PK {
    const val PINE = 0; const val OAK = 1; const val BIRCH = 2; const val DEAD = 3; const val BUSH = 4
    const val ROCK = 5; const val ROCK_RUST = 6; const val CLIFF = 7
    const val COTTAGE = 8; const val SMITHY = 9; const val BARN = 10; const val ELDER_HOME = 11; const val WINDMILL = 12
    const val WELL = 13; const val FENCE = 14; const val SIGN = 15; const val LANTERN = 16; const val TORCH = 17
    const val PILLAR = 18; const val PILLAR_BROKEN = 19; const val WALL = 20; const val ARCH = 21; const val STATUE = 22
    const val TOWER = 23; const val STUMP = 24; const val FLOWER_P = 25; const val FLOWER_Y = 26; const val FLOWER_B = 27
    const val MUSHROOM = 28; const val GRASS = 29; const val REED = 30; const val LILY = 31; const val BARREL = 32
    const val CRATE = 33; const val HAY = 34; const val CRYSTAL_B = 35; const val CRYSTAL_P = 36; const val STALAG = 37
    const val MOUNTAIN = 38; const val RUNE = 39; const val SCARECROW = 40; const val STALL = 41; const val TEMPLE_COL = 42
    const val HERB = 43; const val LOG = 44; const val BANNER = 45; const val SNOWPINE = 46; const val COUNT = 47

    /** Raio de colisão padrão (0 = atravessável) e altura aproximada (para a câmera). */
    val RADIUS = floatArrayOf(
        0.7f, 0.8f, 0.6f, 0.6f, 0.7f, 1.1f, 1.1f, 2.4f,
        2.1f, 2.5f, 3.2f, 2.3f, 2.0f,
        0.9f, 0.5f, 0.35f, 0.3f, 0.3f,
        0.65f, 0.8f, 1.7f, 0.9f, 0.7f, 1.7f, 0.6f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0.55f,
        0.6f, 1.3f, 0.7f, 0.8f, 0.55f, 0f, 1.0f, 0.6f,
        0f, 0.5f, 0.3f, 0.7f, 0f
    )
    val HEIGHT = floatArrayOf(
        4.5f, 4.5f, 4.2f, 3.2f, 1.2f, 1.4f, 1.6f, 6f,
        4f, 4f, 4.5f, 4.2f, 7f,
        1.6f, 1.0f, 2f, 2.4f, 1.9f,
        4f, 2f, 1.8f, 4f, 2.6f, 7f, 0.8f, 0.5f, 0.5f, 0.5f,
        0.6f, 0.4f, 1.0f, 0.1f, 0.9f,
        0.9f, 0.9f, 1.6f, 0.9f, 1.0f, 0.5f, 2.0f, 2.0f,
        0f, 0.7f, 0.4f, 3f, 0f
    )
    fun radius(k: Int) = RADIUS.getOrElse(k) { 0f }
    fun height(k: Int) = HEIGHT.getOrElse(k) { 1f }
}

/** Malhas de baixo polígono do cenário, itens e efeitos do Vale de Aurora. */
object AdvModels {
    private fun mb(f: MeshBuilder.() -> Unit): Mesh { val b = MeshBuilder(); b.f(); return b.build() }
    private fun mbc(cull: Boolean, f: MeshBuilder.() -> Unit): Mesh { val b = MeshBuilder(); b.f(); return b.build(cull) }

    // paleta
    const val BARK = 0xFF7A4A21L; const val BARK2 = 0xFF5E3718L
    const val LEAF1 = 0xFF3F9A47L; const val LEAF2 = 0xFF55B04EL; const val LEAF3 = 0xFF2F7D3AL
    const val STONE = 0xFF9A9DA2L; const val STONE2 = 0xFF7D8086L; const val STONE_L = 0xFFB9BCC0L
    const val WALLC = 0xFFEBD9B2L; const val WOOD = 0xFF8A5A2BL; const val WOOD_L = 0xFFA86F36L
    const val ROOF_R = 0xFFC0533CL; const val ROOF_B = 0xFF4F7FC8L; const val ROOF_G = 0xFF4E9A52L; const val ROOF_P = 0xFF8160C4L
    const val GOLD = 0xFFFFC93CL; const val IRON = 0xFF8D97A3L; const val IRON_L = 0xFFDDE6EEL

    // ------------------------------------------------------------------ árvores
    val pine: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 0.3f, 0.22f, 1.3f, 5, BARK)
        frustum(0f, 0.9f, 0f, 1.55f, 0.15f, 1.9f, 6, LEAF3, LEAF1)
        frustum(0f, 1.9f, 0f, 1.25f, 0.1f, 1.8f, 6, LEAF1, LEAF2)
        frustum(0f, 2.9f, 0f, 0.9f, 0f, 1.7f, 6, LEAF2, 0xFF6CC45AL)
    } }
    val pineFar: Mesh by lazy { mb { frustum(0f, 0.6f, 0f, 1.45f, 0f, 4.2f, 5, LEAF1) } }
    val snowPine: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 0.3f, 0.22f, 1.3f, 5, BARK)
        frustum(0f, 0.9f, 0f, 1.5f, 0.15f, 1.9f, 6, 0xFF3C6E4AL, 0xFFEFF6FAL)
        frustum(0f, 1.9f, 0f, 1.2f, 0.1f, 1.8f, 6, 0xFF4A7E58L, 0xFFF4FAFCL)
        frustum(0f, 2.9f, 0f, 0.85f, 0f, 1.6f, 6, 0xFFF2F8FAL)
    } }
    val oak: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 0.36f, 0.26f, 1.7f, 5, BARK)
        beam(0f, 1.5f, 0f, 0.9f, 2.4f, 0.2f, 0.12f, BARK2)
        sphere(0f, 2.7f, 0f, 1.6f, 1.25f, 1.6f, LEAF2, 6, 3, LEAF1)
        sphere(0.9f, 2.3f, 0.3f, 1.0f, 0.85f, 1.0f, LEAF1, 5, 3, LEAF2)
        sphere(-0.8f, 3.3f, -0.2f, 1.0f, 0.8f, 1.0f, 0xFF63BC58L, 5, 3, LEAF2)
    } }
    val oakFar: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.3f, 0.25f, 1.8f, 4, BARK); sphere(0f, 2.8f, 0f, 1.6f, 1.3f, 1.6f, LEAF2, 5, 2) } }
    val birch: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 0.22f, 0.16f, 2.8f, 5, 0xFFF1EDE0L)
        for (i in 0 until 4) box(0f, 0.5f + i * 0.6f, 0.19f, 0.24f, 0.07f, 0.06f, 0xFF3A3A3AL)
        sphere(0f, 3.5f, 0f, 1.25f, 1.1f, 1.25f, 0xFF8CC85AL, 6, 3, 0xFF73B04AL)
        sphere(0.6f, 2.9f, 0.2f, 0.8f, 0.7f, 0.8f, 0xFF9AD264L, 5, 3)
    } }
    val deadTree: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 0.34f, 0.2f, 2.4f, 5, 0xFF5B4636L)
        beam(0f, 1.6f, 0f, 1.2f, 2.8f, 0.3f, 0.1f, 0xFF4F3B2DL)
        beam(0f, 2.0f, 0f, -1.0f, 3.1f, -0.2f, 0.1f, 0xFF4F3B2DL)
        beam(0f, 2.3f, 0f, 0.1f, 3.6f, 0.9f, 0.08f, 0xFF4F3B2DL)
        beam(1.2f, 2.8f, 0.3f, 1.8f, 3.4f, 0.3f, 0.06f, 0xFF4F3B2DL)
    } }
    val bush: Mesh by lazy { mb {
        sphere(0f, 0.45f, 0f, 0.8f, 0.5f, 0.8f, LEAF1, 5, 3, LEAF2)
        sphere(0.55f, 0.35f, 0.25f, 0.5f, 0.35f, 0.5f, LEAF2, 5, 2)
        glow = false
        sphere(0.25f, 0.8f, 0.3f, 0.09f, 0.09f, 0.09f, 0xFFE6425AL, 3, 2)
        sphere(-0.3f, 0.7f, 0.35f, 0.09f, 0.09f, 0.09f, 0xFFE6425AL, 3, 2)
    } }
    val stump: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.65f, 0.55f, 0.8f, 6, WOOD, 0xFFD2A566L); box(0.5f, 0.1f, 0.4f, 0.3f, 0.2f, 0.3f, BARK2) } }
    val logM: Mesh by lazy { mb { beam(-1.0f, 0.35f, 0f, 1.0f, 0.35f, 0f, 0.33f, WOOD, 0xFFD2A566L) } }

    // ------------------------------------------------------------------ pedras
    val rock: Mesh by lazy { mb { sphere(0f, 0.4f, 0f, 1.05f, 0.75f, 0.9f, STONE, 5, 3, STONE2); sphere(0.8f, 0.25f, 0.3f, 0.5f, 0.4f, 0.45f, STONE2, 4, 2) } }
    val rockRust: Mesh by lazy { mb { sphere(0f, 0.5f, 0f, 1.1f, 0.9f, 1f, 0xFFA16C50L, 5, 3, 0xFF8C5A42L); sphere(-0.8f, 0.3f, 0.3f, 0.5f, 0.45f, 0.5f, 0xFF8C5A42L, 4, 2) } }
    val cliff: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 2.6f, 1.5f, 3.0f, 6, STONE2, STONE_L)
        frustum(1.2f, 0f, 0.6f, 1.5f, 0.8f, 4.5f, 5, STONE, STONE_L)
        frustum(-1.3f, 0f, -0.5f, 1.4f, 0.7f, 2.2f, 5, 0xFF8A8D93L, STONE_L)
    } }
    val cliffFar: Mesh by lazy { mb { frustum(0f, 0f, 0f, 2.6f, 1.0f, 4.5f, 5, STONE2) } }
    val mountain: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 10f, 5.6f, 8f, 7, 0xFF6F7E8EL, 0xFF8493A3L)
        frustum(0f, 8f, 0f, 5.6f, 2.2f, 6f, 7, 0xFF8493A3L, 0xFFA7B4C2L)
        frustum(0f, 14f, 0f, 2.2f, 0f, 3.2f, 7, 0xFFF2F8FCL)
    } }
    val crystalB: Mesh by lazy { mb { glow = true
        gem(0f, 0.9f, 0f, 0.4f, 1.2f, 0.4f, 5, 0xFF7FD6FFL, 0xFF3F9AD0L); gem(0.6f, 0.5f, 0.2f, 0.28f, 0.7f, 0.3f, 4, 0xFF9BE6FFL, 0xFF4BA8E0L); gem(-0.5f, 0.45f, -0.2f, 0.25f, 0.6f, 0.3f, 4, 0xFF6FC8F5L, 0xFF3F8AC0L) } }
    val crystalP: Mesh by lazy { mb { glow = true
        gem(0f, 1.0f, 0f, 0.45f, 1.5f, 0.4f, 5, 0xFFB38CFFL, 0xFF6B4CD0L); gem(0.65f, 0.5f, -0.1f, 0.3f, 0.8f, 0.3f, 4, 0xFFC9A8FFL, 0xFF7B5CE0L); gem(-0.55f, 0.55f, 0.25f, 0.28f, 0.7f, 0.3f, 4, 0xFFA77CFFL, 0xFF5B3CC0L) } }
    val stalag: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.7f, 0f, 2.6f, 5, 0xFF6A6470L); frustum(0.9f, 0f, 0.3f, 0.45f, 0f, 1.4f, 4, 0xFF5A5460L) } }
    val rune: Mesh by lazy { mb {
        box(0f, 1.0f, 0f, 0.9f, 2.0f, 0.4f, STONE2, STONE_L)
        glow = true; box(0f, 1.15f, 0.21f, 0.35f, 0.7f, 0.04f, 0xFF9BE6FFL); box(0f, 1.15f, 0.21f, 0.12f, 1.0f, 0.05f, 0xFFD6F5FFL)
    } }

    // ------------------------------------------------------------------ construções
    private fun MeshBuilder.cottageBody(roof: Long, wall: Long, w: Float = 3.4f, d: Float = 3f, chim: Boolean = true) {
        boxNoBottom(0f, 1.0f, 0f, w, 2.0f, d, wall)
        box(0f, 0.12f, 0f, w + 0.2f, 0.24f, d + 0.2f, STONE2)
        // vigas
        for (sx in floatArrayOf(-w / 2f + 0.05f, w / 2f - 0.05f)) box(sx, 1.1f, d / 2f + 0.02f, 0.14f, 1.9f, 0.05f, WOOD)
        box(0f, 1.95f, d / 2f + 0.02f, w, 0.14f, 0.05f, WOOD)
        roof(0f, 2.0f, 0f, w + 0.9f, 1.7f, d + 0.8f, roof, C.shade(roof, 0.82f))
        if (chim) { box(w * 0.28f, 3.0f, -0.3f, 0.5f, 1.3f, 0.5f, STONE2, STONE_L) }
        // porta e janelas
        box(0f, 0.75f, d / 2f + 0.03f, 0.8f, 1.5f, 0.06f, 0xFF6B4423L)
        box(0.28f, 0.75f, d / 2f + 0.07f, 0.1f, 0.1f, 0.04f, GOLD)
        glow = true
        box(w * 0.3f, 1.35f, d / 2f + 0.04f, 0.6f, 0.55f, 0.05f, 0xFFFFE9A0L)
        box(-w * 0.3f, 1.35f, d / 2f + 0.04f, 0.6f, 0.55f, 0.05f, 0xFFFFE9A0L)
        glow = false
        box(w * 0.3f, 1.35f, d / 2f + 0.08f, 0.7f, 0.06f, 0.05f, WOOD); box(-w * 0.3f, 1.35f, d / 2f + 0.08f, 0.7f, 0.06f, 0.05f, WOOD)
        box(w * 0.3f, 1.05f, d / 2f + 0.16f, 0.7f, 0.18f, 0.22f, WOOD_L); sphere(w * 0.3f, 1.18f, d / 2f + 0.17f, 0.22f, 0.1f, 0.08f, 0xFFFF7FA8L, 4, 2)
    }
    val cottage: Mesh by lazy { mb { cottageBody(ROOF_R, WALLC) } }
    val cottageB: Mesh by lazy { mb { cottageBody(ROOF_B, 0xFFE9E2C4L) } }
    val cottageG: Mesh by lazy { mb { cottageBody(ROOF_G, 0xFFEFDDB8L) } }
    val elderHome: Mesh by lazy { mb {
        cottageBody(ROOF_P, 0xFFE8DCC8L, 3.8f, 3.2f)
        frustum(1.9f, 0f, -1.0f, 0.7f, 0.6f, 2.4f, 6, 0xFFE8DCC8L); frustum(1.9f, 2.3f, -1.0f, 0.95f, 0f, 1.2f, 6, ROOF_P)
        sphere(0f, 3.7f, 0f, 0.0f + 0.01f, 0.01f, 0.01f, ROOF_P, 3, 2)
    } }
    val houseFar: Mesh by lazy { mb { boxNoBottom(0f, 1.0f, 0f, 3.4f, 2f, 3f, WALLC); roof(0f, 2.0f, 0f, 4.3f, 1.7f, 3.8f, ROOF_R, C.shade(ROOF_R, 0.82f)) } }
    val smithy: Mesh by lazy { mb {
        boxNoBottom(0f, 1.2f, 0f, 4.6f, 2.4f, 3.6f, 0xFFB9AC9BL)
        box(0f, 0.14f, 0f, 4.8f, 0.28f, 3.8f, STONE2)
        roof(0f, 2.4f, 0f, 5.4f, 1.5f, 4.4f, 0xFF6B5B55L, 0xFF554843L)
        box(1.3f, 3.4f, -0.6f, 0.9f, 2.4f, 0.9f, STONE2, STONE_L)
        box(-0.5f, 0.9f, 1.85f, 1.6f, 1.8f, 0.06f, 0xFF3B2B22L)
        glow = true; box(-0.5f, 0.9f, 1.88f, 1.2f, 1.2f, 0.05f, 0xFFFF9A3CL); glow = false
        // bigorna
        box(2.9f, 0.45f, 1.0f, 0.7f, 0.2f, 0.4f, 0xFF3C4048L); box(2.9f, 0.25f, 1.0f, 0.3f, 0.3f, 0.25f, 0xFF3C4048L); frustum(2.9f, 0f, 1.0f, 0.3f, 0.26f, 0.12f, 5, WOOD)
        // espada na parede
        box(-2.0f, 1.5f, 1.85f, 0.08f, 0.9f, 0.04f, IRON_L); box(-2.0f, 1.1f, 1.85f, 0.4f, 0.07f, 0.05f, GOLD)
    } }
    val barn: Mesh by lazy { mb {
        boxNoBottom(0f, 1.5f, 0f, 5.6f, 3.0f, 4.4f, 0xFFB8483AL)
        box(0f, 0.12f, 0f, 5.8f, 0.24f, 4.6f, STONE2)
        roof(0f, 3.0f, 0f, 6.4f, 2.4f, 5.2f, 0xFF7A4A21L, 0xFF5E3718L)
        box(0f, 1.2f, 2.22f, 2.2f, 2.4f, 0.08f, 0xFF6B3A24L)
        beam(-1.1f, 0.05f, 2.27f, 1.1f, 2.35f, 2.27f, 0.07f, 0xFFE9D9B8L); beam(1.1f, 0.05f, 2.27f, -1.1f, 2.35f, 2.27f, 0.07f, 0xFFE9D9B8L)
        box(0f, 3.9f, 2.1f, 1.0f, 0.7f, 0.06f, 0xFF3B2B22L)
    } }
    val windmill: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 1.8f, 1.05f, 5.0f, 8, 0xFFEADFC8L, 0xFFD8C9A8L)
        frustum(0f, 5.0f, 0f, 1.25f, 0f, 1.8f, 8, ROOF_R)
        box(0f, 0.7f, 1.62f, 0.8f, 1.4f, 0.1f, 0xFF6B4423L)
        glow = true; box(0f, 3.0f, 1.2f, 0.5f, 0.6f, 0.08f, 0xFFFFE9A0L); glow = false
    } }
    val windmillBlades: Mesh by lazy { mbc(false) {
        for (i in 0 until 4) {
            val a = i * PI.toFloat() / 2f
            val ex = -sin(a) * 3.6f; val ey = cos(a) * 3.6f
            beam(0f, 0f, 0f, ex, ey, 0f, 0.07f, WOOD)
            val px = -sin(a) * 2.0f; val py = cos(a) * 2.0f
            val qx = cos(a) * 0.9f; val qy = sin(a) * 0.9f
            box(0f, 0f, 0f, 0.01f, 0.01f, 0.01f, WOOD)
            val v0 = vert(px - qx * 0f, py - qy * 0f, 0.02f); val v1 = vert(ex, ey, 0.02f); val v2 = vert(ex + qx, ey + qy, 0.02f); val v3 = vert(px + qx, py + qy, 0.02f)
            quadN(v0, v1, v2, v3, 0f, 0f, 1f, 0xFFF4EEDCL); quadN(v0, v1, v2, v3, 0f, 0f, -1f, 0xFFE0D8C0L)
        }
    } }
    val well: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 0.95f, 0.9f, 0.9f, 8, STONE, STONE_L)
        disc(0f, 0.8f, 0f, 0.7f, 8, 0xFF2E6FB5L, 0xFF3A80C8L)
        box(-0.85f, 1.6f, 0f, 0.14f, 1.6f, 0.14f, WOOD); box(0.85f, 1.6f, 0f, 0.14f, 1.6f, 0.14f, WOOD)
        roof(0f, 2.3f, 0f, 2.2f, 0.9f, 1.5f, ROOF_R, C.shade(ROOF_R, 0.8f))
        beam(0f, 2.0f, 0f, 0f, 1.2f, 0f, 0.025f, 0xFFCDBB95L); box(0f, 1.1f, 0f, 0.3f, 0.25f, 0.3f, WOOD)
    } }
    val fence: Mesh by lazy { mb {
        for (i in -1..1) { box(i * 1.1f, 0.5f, 0f, 0.16f, 1.0f, 0.16f, WOOD); frustum(i * 1.1f, 1.0f, 0f, 0.1f, 0f, 0.12f, 4, WOOD_L) }
        beam(-1.1f, 0.78f, 0f, 1.1f, 0.78f, 0f, 0.05f, WOOD_L); beam(-1.1f, 0.4f, 0f, 1.1f, 0.4f, 0f, 0.05f, WOOD_L)
    } }
    val sign: Mesh by lazy { mb { box(0f, 0.8f, 0f, 0.12f, 1.6f, 0.12f, WOOD); box(0.2f, 1.45f, 0.08f, 0.9f, 0.4f, 0.07f, WOOD_L); box(-0.2f, 1.0f, 0.08f, 0.7f, 0.3f, 0.07f, WOOD_L); glow = true; box(0.2f, 1.45f, 0.125f, 0.6f, 0.06f, 0.02f, 0xFFFFE9A0L) } }
    val lantern: Mesh by lazy { mb { box(0f, 1.1f, 0f, 0.1f, 2.2f, 0.1f, 0xFF3C4048L); box(0f, 2.3f, 0f, 0.36f, 0.08f, 0.36f, 0xFF3C4048L); glow = true; box(0f, 2.05f, 0f, 0.24f, 0.4f, 0.24f, 0xFFFFE08AL); glow = false; frustum(0f, 2.35f, 0f, 0.3f, 0f, 0.25f, 4, 0xFF3C4048L) } }
    val torchPost: Mesh by lazy { mb { box(0f, 0.9f, 0f, 0.14f, 1.8f, 0.14f, BARK2); box(0f, 1.85f, 0f, 0.3f, 0.12f, 0.3f, 0xFF3C4048L) } }
    val barrel: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.5f, 0.5f, 0.9f, 7, WOOD, WOOD_L); frustum(0f, 0.28f, 0f, 0.53f, 0.53f, 0.08f, 7, 0xFF4A4A52L); frustum(0f, 0.6f, 0f, 0.53f, 0.53f, 0.08f, 7, 0xFF4A4A52L) } }
    val crate: Mesh by lazy { mb { box(0f, 0.45f, 0f, 0.9f, 0.9f, 0.9f, WOOD_L, 0xFFC08040L); beam(-0.45f, 0.02f, 0.46f, 0.45f, 0.88f, 0.46f, 0.04f, WOOD); beam(0.45f, 0.02f, 0.46f, -0.45f, 0.88f, 0.46f, 0.04f, WOOD) } }
    val hay: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.9f, 0.5f, 0.9f, 7, 0xFFE3C050L); frustum(0f, 0.9f, 0f, 0.5f, 0f, 0.45f, 7, 0xFFEBCB62L) } }
    val stall: Mesh by lazy { mb {
        for (sx in floatArrayOf(-1.1f, 1.1f)) for (sz in floatArrayOf(-0.7f, 0.7f)) box(sx, 1.0f, sz, 0.12f, 2.0f, 0.12f, WOOD)
        roof(0f, 2.0f, 0f, 3.0f, 0.7f, 2.0f, 0xFFE0A93CL, 0xFFC4882AL)
        box(0f, 0.85f, 0.7f, 2.2f, 0.12f, 0.6f, WOOD_L); box(0f, 0.4f, 0.7f, 2.0f, 0.8f, 0.5f, WOOD)
        sphere(-0.6f, 1.05f, 0.7f, 0.18f, 0.16f, 0.18f, 0xFFE6425AL, 4, 2); sphere(-0.2f, 1.05f, 0.7f, 0.18f, 0.16f, 0.18f, 0xFFF0C040L, 4, 2); sphere(0.3f, 1.05f, 0.7f, 0.18f, 0.16f, 0.18f, 0xFF6BCB5BL, 4, 2)
    } }
    val scarecrow: Mesh by lazy { mb {
        box(0f, 0.9f, 0f, 0.1f, 1.8f, 0.1f, WOOD); beam(-0.8f, 1.35f, 0f, 0.8f, 1.35f, 0f, 0.05f, WOOD)
        box(0f, 1.2f, 0f, 0.5f, 0.7f, 0.25f, 0xFF5F8FD0L); sphere(0f, 1.85f, 0f, 0.28f, 0.28f, 0.28f, 0xFFE8C98AL, 5, 3)
        frustum(0f, 2.0f, 0f, 0.4f, 0.12f, 0.12f, 6, 0xFFB5651DL); frustum(0f, 2.1f, 0f, 0.22f, 0f, 0.3f, 6, 0xFFB5651DL)
    } }
    val banner: Mesh by lazy { mbc(false) {
        box(0f, 1.6f, 0f, 0.1f, 3.2f, 0.1f, BARK2)
        val a = vert(0.05f, 3.0f, 0f); val b = vert(0.05f, 1.8f, 0f); val c = vert(0.9f, 1.8f, 0f); val d = vert(0.9f, 3.0f, 0f)
        quadN(a, b, c, d, 0f, 0f, 1f, 0xFFB0302AL); quadN(a, b, c, d, 0f, 0f, -1f, 0xFF8E1B1BL)
        glow = true; val e = vert(0.3f, 2.6f, 0.01f); val f = vert(0.65f, 2.6f, 0.01f); val g = vert(0.47f, 2.2f, 0.01f); triN(e, f, g, 0f, 0f, 1f, GOLD)
    } }

    // ------------------------------------------------------------------ ruínas e templo
    val pillar: Mesh by lazy { mb { box(0f, 0.15f, 0f, 1.5f, 0.3f, 1.5f, 0xFFD2D4C9L); frustum(0f, 0.3f, 0f, 0.62f, 0.5f, 3.3f, 7, 0xFFC4C7BCL, 0xFFD2D4C9L); box(0f, 3.75f, 0f, 1.5f, 0.35f, 1.5f, 0xFFD8DACFL); box(0f, 3.5f, 0f, 1.1f, 0.2f, 1.1f, 0xFFC4C7BCL) } }
    val pillarBroken: Mesh by lazy { mb { box(0f, 0.15f, 0f, 1.5f, 0.3f, 1.5f, 0xFFC0C3B8L); frustum(0f, 0.3f, 0f, 0.62f, 0.55f, 1.5f, 7, 0xFFB3B6AAL); box(1.3f, 0.25f, 0.4f, 0.9f, 0.5f, 0.7f, 0xFFA9ACA0L); box(-1.0f, 0.2f, -0.7f, 0.6f, 0.4f, 0.5f, 0xFFA0A397L); box(0.2f, 0.5f, 1.2f, 0.5f, 0.3f, 0.4f, 0xFF9CA093L) } }
    val wall: Mesh by lazy { mb { box(0f, 0.9f, 0f, 3.2f, 1.8f, 0.8f, 0xFFB5B8AEL, 0xFFC9CCC2L); box(0.7f, 2.05f, 0f, 1.2f, 0.5f, 0.8f, 0xFFAAADA3L); for (i in 0 until 3) box(-1.2f + i * 1.0f, 0.5f, 0.41f, 0.9f, 0.04f, 0.02f, 0xFF93968CL); box(-1.0f, 1.2f, 0.41f, 0.04f, 0.8f, 0.02f, 0xFF93968CL) } }
    val arch: Mesh by lazy { mb { box(-1.3f, 1.6f, 0f, 0.8f, 3.2f, 0.9f, 0xFFB9BCB0L, 0xFFD0D3C8L); box(1.3f, 1.6f, 0f, 0.8f, 3.2f, 0.9f, 0xFFB9BCB0L, 0xFFD0D3C8L); box(0f, 3.4f, 0f, 3.4f, 0.6f, 0.9f, 0xFFC4C7BCL, 0xFFD8DACFL); box(0f, 3.9f, 0f, 1.0f, 0.4f, 0.9f, 0xFFB0B3A8L) } }
    val statue: Mesh by lazy { mb {
        box(0f, 0.3f, 0f, 1.4f, 0.6f, 1.4f, 0xFFB0B3A8L, 0xFFC4C7BCL)
        box(0f, 1.5f, 0f, 0.8f, 1.7f, 0.5f, 0xFF9CA09AL); sphere(0f, 2.65f, 0f, 0.38f, 0.4f, 0.38f, 0xFFA8ACA6L, 5, 3)
        beam(-0.5f, 2.2f, 0f, -0.9f, 1.3f, 0.2f, 0.12f, 0xFF9CA09AL); beam(0.5f, 2.2f, 0f, 0.7f, 2.9f, 0.2f, 0.1f, 0xFF9CA09AL)
        beam(0.7f, 2.9f, 0.2f, 0.7f, 1.6f, 0.2f, 0.05f, 0xFFC9CCC2L)
        glow = true; box(0f, 2.68f, 0.34f, 0.3f, 0.06f, 0.04f, 0xFF9BE6FFL)
    } }
    val tower: Mesh by lazy { mb {
        frustum(0f, 0f, 0f, 1.7f, 1.4f, 7.0f, 8, 0xFFB5B8AEL, 0xFFC9CCC2L)
        for (i in 0 until 8) { val a = i / 8f * 2f * PI.toFloat(); box(cos(a) * 1.35f, 7.3f, sin(a) * 1.35f, 0.55f, 0.6f, 0.55f, 0xFFA9ACA2L) }
        box(0f, 3.6f, 1.4f, 0.4f, 0.8f, 0.1f, 0xFF2A2C34L); box(0f, 5.4f, 1.3f, 0.4f, 0.8f, 0.1f, 0xFF2A2C34L)
    } }
    val templeCol: Mesh by lazy { mb {
        box(0f, 0.2f, 0f, 2.0f, 0.4f, 2.0f, 0xFFE0D8F0L)
        frustumStripes(0f, 0.4f, 0f, 0.7f, 0.6f, 4.6f, 8, 0xFFD8D0EAL, 0xFFC8BEDEL, 0xFFEAE4F8L)
        box(0f, 5.2f, 0f, 1.8f, 0.4f, 1.8f, 0xFFE8E0F8L)
        glow = true; box(0f, 3.0f, 0.72f, 0.14f, 1.4f, 0.04f, 0xFFB08CFFL)
    } }

    // ------------------------------------------------------------------ vegetação baixa
    private fun flowerMesh(col: Long) = mbc(false) {
        blade(0f, 0f, 0.4f, 0.03f, 0f, 0xFF3F8F3AL)
        sphere(0f, 0.45f, 0f, 0.12f, 0.06f, 0.12f, col, 5, 2)
        sphere(0f, 0.5f, 0f, 0.05f, 0.04f, 0.05f, 0xFFFFE066L, 3, 2)
    }
    val flowerP: Mesh by lazy { flowerMesh(0xFFFF6FA5L) }
    val flowerY: Mesh by lazy { flowerMesh(0xFFFFE566L) }
    val flowerB: Mesh by lazy { flowerMesh(0xFF8FB4FFL) }
    val mushroom: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.16f, 0.12f, 0.45f, 5, 0xFFF4E9D0L); sphere(0f, 0.45f, 0f, 0.45f, 0.28f, 0.45f, 0xFFD94A3AL, 5, 3); sphere(0.2f, 0.62f, 0.1f, 0.08f, 0.04f, 0.08f, 0xFFF4F4F4L, 3, 2); sphere(-0.15f, 0.64f, -0.15f, 0.07f, 0.04f, 0.07f, 0xFFF4F4F4L, 3, 2) } }
    val grass: Mesh by lazy { mbc(false) {
        blade(-0.15f, 0f, 0.55f, 0.07f, 0.1f, 0xFF4FA84AL, 0xFF3F8F3AL); blade(0.05f, 0.1f, 0.7f, 0.07f, -0.08f, 0xFF5CBA52L, 0xFF4FA84AL); blade(0.2f, -0.05f, 0.5f, 0.07f, 0.06f, 0xFF45A040L, 0xFF3A8A38L)
    } }
    val herb: Mesh by lazy { mbc(false) {
        blade(-0.1f, 0f, 0.45f, 0.08f, 0.1f, 0xFF6BCB5BL, 0xFF4FA84AL); blade(0.1f, 0.05f, 0.5f, 0.08f, -0.1f, 0xFF7BDA6BL, 0xFF55B04EL)
        glow = true; sphere(0f, 0.55f, 0f, 0.1f, 0.1f, 0.1f, 0xFFB6F27AL, 4, 2)
    } }
    val reed: Mesh by lazy { mbc(false) { blade(-0.2f, 0f, 1.5f, 0.05f, 0.1f, 0xFF7DA84AL); blade(0f, 0.1f, 1.8f, 0.05f, -0.1f, 0xFF8DB85AL); blade(0.2f, 0f, 1.3f, 0.05f, 0.05f, 0xFF6F9A42L); box(0f, 1.6f, 0.1f, 0.12f, 0.35f, 0.12f, 0xFF7A4A21L) } }
    val lily: Mesh by lazy { mb { disc(0f, 0.02f, 0f, 0.55f, 7, 0xFF4FA84AL, 0xFF5CBA52L); sphere(0.1f, 0.12f, 0.05f, 0.14f, 0.1f, 0.14f, 0xFFFF9FC8L, 5, 2) } }

    // ------------------------------------------------------------------ baús, chaves, itens de mundo
    val chest: Mesh by lazy { mb {
        box(0f, 0.35f, 0f, 1.2f, 0.7f, 0.8f, WOOD); box(0f, 0.8f, 0f, 1.25f, 0.28f, 0.85f, WOOD_L)
        box(0f, 0.55f, 0.42f, 0.2f, 0.25f, 0.08f, GOLD)
        box(-0.5f, 0.45f, 0f, 0.1f, 0.9f, 0.86f, 0xFF4A4A52L); box(0.5f, 0.45f, 0f, 0.1f, 0.9f, 0.86f, 0xFF4A4A52L)
    } }
    val chestOpen: Mesh by lazy { mb {
        box(0f, 0.35f, 0f, 1.2f, 0.7f, 0.8f, WOOD)
        box(0f, 1.0f, -0.55f, 1.25f, 0.8f, 0.12f, WOOD_L); box(-0.5f, 0.95f, -0.55f, 0.1f, 0.9f, 0.14f, 0xFF4A4A52L); box(0.5f, 0.95f, -0.55f, 0.1f, 0.9f, 0.14f, 0xFF4A4A52L)
        glow = true; box(0f, 0.72f, 0f, 1.0f, 0.06f, 0.6f, GOLD)
    } }
    val chestRare: Mesh by lazy { mb {
        box(0f, 0.35f, 0f, 1.3f, 0.7f, 0.9f, 0xFF5B3A8CL); box(0f, 0.82f, 0f, 1.35f, 0.3f, 0.95f, 0xFF7B52B8L)
        box(0f, 0.55f, 0.47f, 0.24f, 0.28f, 0.08f, GOLD)
        box(-0.55f, 0.45f, 0f, 0.1f, 0.95f, 0.96f, GOLD); box(0.55f, 0.45f, 0f, 0.1f, 0.95f, 0.96f, GOLD)
        glow = true; gem(0f, 1.2f, 0f, 0.12f, 0.16f, 0.1f, 4, 0xFFE0C8FFL)
    } }
    val keyM: Mesh by lazy { mb { glow = true
        frustum(0f, 0.35f, 0f, 0.2f, 0.2f, 0.08f, 6, GOLD); box(0f, 0.1f, 0f, 0.07f, 0.55f, 0.07f, GOLD)
        box(0.1f, -0.1f, 0f, 0.2f, 0.07f, 0.07f, GOLD); box(0.08f, 0f, 0f, 0.16f, 0.07f, 0.07f, GOLD)
    } }
    val heartFruit: Mesh by lazy { mb { glow = true; sphere(-0.15f, 0.1f, 0f, 0.22f, 0.22f, 0.22f, 0xFFFF5A70L, 5, 3); sphere(0.15f, 0.1f, 0f, 0.22f, 0.22f, 0.22f, 0xFFFF5A70L, 5, 3); gem(0f, -0.08f, 0f, 0.3f, 0.0f, 0.3f, 4, 0xFFE63B58L); box(0f, 0.38f, 0f, 0.05f, 0.14f, 0.05f, 0xFF3F8F3AL) } }
    val potion: Mesh by lazy { mb { frustum(0f, 0f, 0f, 0.22f, 0.26f, 0.3f, 6, 0xFFB6E3F0L); frustum(0f, 0.3f, 0f, 0.1f, 0.1f, 0.2f, 6, 0xFFB6E3F0L); glow = true; frustum(0f, 0.04f, 0f, 0.2f, 0.22f, 0.24f, 6, 0xFF7BDA6BL); glow = false; box(0f, 0.52f, 0f, 0.14f, 0.1f, 0.14f, WOOD) } }
    val bombM: Mesh by lazy { mb { sphere(0f, 0.25f, 0f, 0.28f, 0.28f, 0.28f, 0xFF2A2C34L, 6, 3); beam(0f, 0.5f, 0f, 0.1f, 0.7f, 0f, 0.03f, 0xFFC9A66BL); glow = true; sphere(0.1f, 0.74f, 0f, 0.07f, 0.07f, 0.07f, 0xFFFF9F1CL, 3, 2) } }
    val arrowM: Mesh by lazy { mb { beam(0f, 0f, -0.5f, 0f, 0f, 0.5f, 0.02f, 0xFF8A5A2BL); frustum(0f, 0f, 0.5f, 0.001f, 0.001f, 0.001f, 3, IRON_L); beam(0f, 0f, 0.5f, 0f, 0f, 0.7f, 0.05f, IRON_L); box(0f, 0.05f, -0.5f, 0.02f, 0.1f, 0.2f, 0xFFE0E0E0L) } }
    val arrowFlying: Mesh by lazy { mb { beam(0f, 0f, -0.7f, 0f, 0f, 0.5f, 0.025f, 0xFF8A5A2BL); beam(0f, 0f, 0.5f, 0f, 0f, 0.78f, 0.06f, IRON_L); box(0f, 0.06f, -0.65f, 0.02f, 0.14f, 0.3f, 0xFFF0F0F0L) } }
    val gemItem: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.2f, 0.28f, 0.28f, 4, 0xFF73EFF7L, 0xFF3BA8D0L) } }
    val soulMark: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.4f, 0.8f, 0.4f, 5, 0xFFFFE066L, 0xFFFFB02EL) } }
    val sparkle: Mesh by lazy { mbc(false) { glow = true; gem(0f, 0f, 0f, 0.12f, 0.2f, 0.2f, 4, 0xFFFFF3B0L, 0xFFFFD54FL) } }
    val orb: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.38f, 0.45f, 0.45f, 5, 0xFFFFE066L, 0xFFFF9F1CL) } }
    val orbBad: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.4f, 0.5f, 0.5f, 5, 0xFFFF5252L, 0xFF8E1B1BL) } }
    val orbMagic: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.3f, 0.45f, 0.45f, 5, 0xFF9BE6FFL, 0xFF4BA8E0L) } }
    val shard: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.35f, 0.9f, 0.9f, 4, 0xFF8E4FA8L, 0xFF4B2A7BL) } }
    val shardG: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.3f, 0.7f, 0.5f, 4, 0xFF5DD16BL, 0xFF2E8B3EL) } }
    val shardB: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.3f, 0.7f, 0.5f, 4, 0xFF5DB8FFL, 0xFF2A6CB0L) } }
    val shardR: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.3f, 0.7f, 0.5f, 4, 0xFFFF6B5DL, 0xFFB02A2AL) } }

    // ------------------------------------------------------------------ Brasa, portas, portões
    val bonfireBase: Mesh by lazy { mb {
        for (i in 0 until 7) { val a = i / 7f * 2f * PI.toFloat(); sphere(cos(a) * 0.95f, 0.2f, sin(a) * 0.95f, 0.38f, 0.28f, 0.38f, STONE, 4, 2, STONE2) }
        beam(-0.5f, 0.12f, -0.2f, 0.5f, 0.3f, 0.2f, 0.09f, BARK2); beam(0.5f, 0.12f, -0.2f, -0.5f, 0.3f, 0.2f, 0.09f, BARK)
        beam(-0.2f, 0.12f, 0.5f, 0.2f, 0.3f, -0.5f, 0.09f, BARK)
        box(0f, 0.04f, 0f, 1.6f, 0.08f, 1.6f, 0xFF3A3A40L)
    } }
    val flame: Mesh by lazy { mb { glow = true; frustum(0f, 0.25f, 0f, 0.5f, 0f, 1.4f, 5, 0xFFFF9F1CL); frustum(0f, 0.25f, 0f, 0.3f, 0f, 1.05f, 5, 0xFFFFE066L); frustum(0f, 0.25f, 0f, 0.14f, 0f, 0.7f, 4, 0xFFFFFFC8L) } }
    val flameSmall: Mesh by lazy { mb { glow = true; frustum(0f, 0f, 0f, 0.14f, 0f, 0.42f, 4, 0xFFFF9F1CL); frustum(0f, 0f, 0f, 0.08f, 0f, 0.3f, 4, 0xFFFFE066L) } }
    val flameOff: Mesh by lazy { mb { frustum(0f, 0.2f, 0f, 0.25f, 0f, 0.5f, 4, 0xFF5B5F66L) } }
    val doorWood: Mesh by lazy { mb {
        box(-1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, STONE2, STONE_L); box(1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, STONE2, STONE_L); box(0f, 3.35f, 0f, 3.8f, 0.6f, 0.9f, STONE, STONE_L)
        box(0f, 1.4f, 0f, 2.4f, 2.8f, 0.4f, 0xFF6B4423L)
        for (i in 0 until 3) box(0f, 0.6f + i * 0.9f, 0.22f, 2.4f, 0.12f, 0.06f, 0xFF3C4048L)
        glow = true; box(0f, 1.4f, 0.24f, 0.3f, 0.4f, 0.06f, GOLD)
    } }
    val doorOpen: Mesh by lazy { mb {
        box(-1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, STONE2, STONE_L); box(1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, STONE2, STONE_L); box(0f, 3.35f, 0f, 3.8f, 0.6f, 0.9f, STONE, STONE_L)
        box(-1.0f, 1.4f, -0.3f, 0.12f, 2.8f, 0.9f, 0xFF6B4423L)
        glow = true; box(0f, 1.4f, 0f, 1.7f, 2.7f, 0.04f, 0xFF1A1B24L)
    } }
    val doorIron: Mesh by lazy { mb {
        box(-1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, 0xFF5E6068L, 0xFF7A7C86L); box(1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, 0xFF5E6068L, 0xFF7A7C86L); box(0f, 3.35f, 0f, 3.8f, 0.6f, 0.9f, 0xFF6A6C76L, 0xFF7A7C86L)
        for (i in 0 until 6) box(-1.0f + i * 0.4f, 1.4f, 0f, 0.14f, 2.8f, 0.14f, 0xFF3C4048L)
        box(0f, 1.0f, 0.1f, 2.4f, 0.12f, 0.1f, 0xFF3C4048L); box(0f, 2.0f, 0.1f, 2.4f, 0.12f, 0.1f, 0xFF3C4048L)
        glow = true; box(0f, 1.5f, 0.16f, 0.36f, 0.5f, 0.06f, 0xFF9BE6FFL)
    } }
    val doorIronOpen: Mesh by lazy { mb {
        box(-1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, 0xFF5E6068L, 0xFF7A7C86L); box(1.55f, 1.6f, 0f, 0.7f, 3.2f, 0.9f, 0xFF5E6068L, 0xFF7A7C86L); box(0f, 3.35f, 0f, 3.8f, 0.6f, 0.9f, 0xFF6A6C76L, 0xFF7A7C86L)
        for (i in 0 until 6) box(-1.0f + i * 0.4f, 3.0f, 0f, 0.14f, 0.5f, 0.14f, 0xFF3C4048L)
        glow = true; box(0f, 1.4f, 0f, 1.7f, 2.7f, 0.04f, 0xFF1A1B24L)
    } }
    val crackedWall: Mesh by lazy { mb {
        box(0f, 1.5f, 0f, 3.4f, 3.0f, 0.9f, 0xFF9A9087L, 0xFFB5ABA2L)
        glow = false
        beam(-0.6f, 2.6f, 0.46f, 0.2f, 1.6f, 0.46f, 0.04f, 0xFF3A3530L); beam(0.2f, 1.6f, 0.46f, -0.3f, 0.5f, 0.46f, 0.04f, 0xFF3A3530L); beam(0.2f, 1.6f, 0.46f, 0.9f, 1.0f, 0.46f, 0.04f, 0xFF3A3530L)
    } }
    val rubble: Mesh by lazy { mb {
        sphere(-0.8f, 0.3f, 0f, 0.6f, 0.4f, 0.5f, 0xFF9A9087L, 4, 2); sphere(0.5f, 0.25f, 0.3f, 0.5f, 0.35f, 0.5f, 0xFF8A8077L, 4, 2); sphere(0f, 0.2f, -0.4f, 0.7f, 0.3f, 0.5f, 0xFFA59B92L, 4, 2)
    } }
    val gateCrystal: Mesh by lazy { mb { glow = true; for (i in 0 until 7) { val x = (i - 3) * 1.4f; gem(x, 2.4f, 0f, 0.62f, 2.8f, 2.4f, 5, 0xFF7B4DFFL, 0xFF4B2A9BL) } } }
    val gateCrystalOpen: Mesh by lazy { mb { glow = true; for (i in 0 until 7) { val x = (i - 3) * 1.4f; gem(x, 0.4f, 0f, 0.42f, 0.5f, 0.35f, 5, 0xFF7B4DFFL, 0xFF4B2A9BL) } } }

    // ------------------------------------------------------------------ pontes e píer
    val bridge: Mesh by lazy { mb {
        for (i in 0 until 8) box(-3.5f + i * 1.0f, 0.0f, 0f, 0.92f, 0.12f, 2.2f, if (i % 2 == 0) WOOD_L else WOOD)
        for (sz in floatArrayOf(-1.1f, 1.1f)) { box(0f, 0.55f, sz, 8.0f, 0.1f, 0.1f, WOOD); for (i in 0..4) box(-3.8f + i * 1.9f, 0.3f, sz, 0.14f, 0.7f, 0.14f, BARK2) }
        box(-3.4f, -0.35f, 0.8f, 0.2f, 0.8f, 0.2f, BARK2); box(-3.4f, -0.35f, -0.8f, 0.2f, 0.8f, 0.2f, BARK2); box(3.4f, -0.35f, 0.8f, 0.2f, 0.8f, 0.2f, BARK2); box(3.4f, -0.35f, -0.8f, 0.2f, 0.8f, 0.2f, BARK2)
    } }

    // ------------------------------------------------------------------ armas
    val sword: Mesh by lazy { mb { box(0f, -0.1f, 0f, 0.08f, 0.2f, 0.08f, 0xFF6B4423L); box(0f, -0.22f, 0f, 0.38f, 0.07f, 0.1f, GOLD); box(0f, -0.72f, 0f, 0.1f, 1.0f, 0.04f, IRON_L); frustum(0f, -1.27f, 0f, 0.05f, 0f, 0.1f, 3, IRON_L) } }
    val twigSword: Mesh by lazy { mb { box(0f, -0.1f, 0f, 0.08f, 0.2f, 0.08f, 0xFF6B4423L); box(0f, -0.2f, 0f, 0.3f, 0.06f, 0.1f, 0xFF8A5A2BL); beam(0f, -0.25f, 0f, 0f, -1.1f, 0f, 0.05f, 0xFFB98A52L); beam(0f, -0.7f, 0f, 0.2f, -0.9f, 0f, 0.025f, 0xFF8FB84AL); sphere(0.22f, -0.92f, 0f, 0.08f, 0.06f, 0.08f, 0xFF55B04EL, 3, 2) } }
    val axe: Mesh by lazy { mb { box(0f, -0.55f, 0f, 0.09f, 1.25f, 0.09f, 0xFF6B4423L); box(0f, -1.0f, 0.18f, 0.13f, 0.55f, 0.55f, 0xFF9AA0A6L); box(0f, -0.95f, 0.5f, 0.09f, 0.7f, 0.12f, IRON_L) } }
    val spear: Mesh by lazy { mb { box(0f, -0.9f, 0f, 0.07f, 2.0f, 0.07f, 0xFF8A5A2BL); frustum(0f, -2.05f, 0f, 0.1f, 0f, 0.45f, 4, IRON_L); box(0f, -1.55f, 0f, 0.14f, 0.12f, 0.14f, 0xFFB0302AL) } }
    val rustBlade: Mesh by lazy { mb { box(0f, -0.1f, 0f, 0.09f, 0.22f, 0.09f, 0xFF4A3B2AL); box(0f, -0.24f, 0f, 0.45f, 0.08f, 0.12f, WOOD); box(0f, -0.82f, 0f, 0.16f, 1.2f, 0.05f, 0xFFB5651DL); frustum(0f, -1.42f, 0f, 0.08f, 0f, 0.14f, 3, 0xFFB5651DL); box(0.05f, -0.9f, 0.03f, 0.04f, 0.9f, 0.02f, 0xFFD58A3DL) } }
    val aurelBlade: Mesh by lazy { mb { glow = true; box(0f, -0.1f, 0f, 0.1f, 0.24f, 0.1f, 0xFF4A2A7BL); box(0f, -0.26f, 0f, 0.5f, 0.09f, 0.14f, GOLD); box(0f, -0.95f, 0f, 0.16f, 1.4f, 0.05f, 0xFFB38CFFL); frustum(0f, -1.65f, 0f, 0.08f, 0f, 0.2f, 3, 0xFFD6C0FFL) } }
    val club: Mesh by lazy { mb { frustum(0f, -0.95f, 0f, 0.3f, 0.1f, 0.95f, 5, WOOD); sphere(0f, -1.05f, 0f, 0.4f, 0.42f, 0.4f, 0xFF7A4A21L, 5, 3); box(0.3f, -1.0f, 0f, 0.1f, 0.14f, 0.1f, 0xFFB0B0B8L); box(-0.28f, -1.15f, 0.1f, 0.1f, 0.14f, 0.1f, 0xFFB0B0B8L) } }
    val hammer: Mesh by lazy { mb { box(0f, -0.5f, 0f, 0.09f, 1.1f, 0.09f, 0xFF6B4423L); box(0f, -1.1f, 0f, 0.7f, 0.42f, 0.42f, 0xFF7C8796L) } }
    val staff: Mesh by lazy { mb { box(0f, -0.8f, 0f, 0.07f, 1.8f, 0.07f, WOOD); glow = true; gem(0f, -1.75f, 0f, 0.15f, 0.22f, 0.14f, 5, 0xFF9BE6FFL, 0xFF4BA8E0L) } }
    val crystalStaff: Mesh by lazy { mb { box(0f, -0.8f, 0f, 0.08f, 1.8f, 0.08f, 0xFF4A3B7AL); box(0f, -1.55f, 0f, 0.3f, 0.06f, 0.06f, GOLD); glow = true; gem(0f, -1.85f, 0f, 0.2f, 0.34f, 0.2f, 5, 0xFFD6C0FFL, 0xFF8E6BE0L) } }
    val bow: Mesh by lazy { mbc(false) {
        fun cy(t: Float) = -0.1f - 0.35f * (1f - (2f * t - 1f) * (2f * t - 1f))
        beam(0f, -0.1f, -0.7f, 0f, -0.1f, 0.7f, 0.02f, 0xFFE8E0C8L)
        for (i in 0 until 6) {
            val t0 = i / 6f; val t1 = (i + 1) / 6f
            beam(0f, cy(t0), -0.7f + 1.4f * t0, 0f, cy(t1), -0.7f + 1.4f * t1, 0.05f, 0xFF8A5A2BL)
        }
        box(0f, -0.45f, 0f, 0.12f, 0.12f, 0.24f, 0xFF4A3220L)
    } }
    val shieldWood: Mesh by lazy { mb { box(0f, 0f, 0f, 0.6f, 0.75f, 0.1f, 0xFFA06C32L, 0xFFB57C3AL); box(0f, 0f, 0.06f, 0.2f, 0.2f, 0.05f, GOLD); box(0f, 0f, 0.02f, 0.62f, 0.1f, 0.12f, 0xFF7A4A21L) } }
    val shieldIron: Mesh by lazy { mb { box(0f, 0f, 0f, 0.7f, 0.85f, 0.12f, IRON, IRON_L); box(0f, 0f, 0.07f, 0.25f, 0.25f, 0.06f, IRON_L); box(0f, 0.36f, 0.05f, 0.7f, 0.1f, 0.12f, 0xFF5E6068L) } }
    val shieldMirror: Mesh by lazy { mb { box(0f, 0f, 0f, 0.66f, 0.8f, 0.1f, 0xFFB6E3F0L); glow = true; box(0f, 0f, 0.06f, 0.4f, 0.5f, 0.05f, 0xFFFFFFFFL) } }
    val shieldRust: Mesh by lazy { mb { box(0f, 0f, 0f, 0.74f, 0.9f, 0.12f, 0xFF8E4A2AL, 0xFFB5651DL); glow = true; box(0f, 0f, 0.07f, 0.26f, 0.3f, 0.06f, GOLD) } }

    fun weaponMesh(id: String): Mesh = when (id) {
        "galho" -> twigSword; "espada" -> sword; "machado" -> axe; "lanca" -> spear; "ferrugem" -> rustBlade
        "arco" -> bow; "cajado" -> crystalStaff; "aurora" -> aurelBlade; else -> twigSword
    }
    fun shieldMeshFor(id: String): Mesh = when (id) { "ferro" -> shieldIron; "espelho" -> shieldMirror; "ferrugem_s" -> shieldRust; else -> shieldWood }

    fun propMesh(kind: Int): Mesh = when (kind) {
        PK.PINE -> pine; PK.OAK -> oak; PK.BIRCH -> birch; PK.DEAD -> deadTree; PK.BUSH -> bush
        PK.ROCK -> rock; PK.ROCK_RUST -> rockRust; PK.CLIFF -> cliff
        PK.COTTAGE -> cottage; PK.SMITHY -> smithy; PK.BARN -> barn; PK.ELDER_HOME -> elderHome; PK.WINDMILL -> windmill
        PK.WELL -> well; PK.FENCE -> fence; PK.SIGN -> sign; PK.LANTERN -> lantern; PK.TORCH -> torchPost
        PK.PILLAR -> pillar; PK.PILLAR_BROKEN -> pillarBroken; PK.WALL -> wall; PK.ARCH -> arch; PK.STATUE -> statue
        PK.TOWER -> tower; PK.STUMP -> stump; PK.FLOWER_P -> flowerP; PK.FLOWER_Y -> flowerY; PK.FLOWER_B -> flowerB
        PK.MUSHROOM -> mushroom; PK.GRASS -> grass; PK.REED -> reed; PK.LILY -> lily; PK.BARREL -> barrel
        PK.CRATE -> crate; PK.HAY -> hay; PK.CRYSTAL_B -> crystalB; PK.CRYSTAL_P -> crystalP; PK.STALAG -> stalag
        PK.MOUNTAIN -> mountain; PK.RUNE -> rune; PK.SCARECROW -> scarecrow; PK.STALL -> stall; PK.TEMPLE_COL -> templeCol
        PK.HERB -> herb; PK.LOG -> logM; PK.BANNER -> banner; PK.SNOWPINE -> snowPine
        else -> rock
    }

    /** Versão simplificada para objetos distantes (null = não existe, usa a normal). */
    fun lodMesh(kind: Int): Mesh? = when (kind) {
        PK.PINE, PK.SNOWPINE -> pineFar; PK.OAK, PK.BIRCH -> oakFar
        PK.COTTAGE, PK.ELDER_HOME -> houseFar; PK.CLIFF -> cliffFar
        else -> null
    }

    // ------------------------------------------------------------------ efeitos
    /** Anel plano de aviso, desenhado com triângulos soltos pelo jogo. */
    val ringUnit: Mesh by lazy { mbc(false) { disc(0f, 0f, 0f, 1f, 12, 0xFFFFFFFFL) } }
}
