package com.gamepadlayout.app.games

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Medidas comuns de todos os personagens articulados (a espessura muda com "bulk", o comprimento não). */
object RG {
    const val HIP_Y = 0.62f
    const val HIP_X = 0.20f
    const val THIGH = 0.30f
    const val SHIN = 0.32f
    const val WAIST = 0.74f
    const val TORSO_H = 0.62f
    const val SHOULDER_X = 0.54f
    const val SHOULDER_Y = 0.50f
    const val UARM = 0.36f
    const val FARM = 0.34f
    const val NECK_Y = 0.60f
    const val HEAD_C = 0.40f
}

/** Descrição de um humanoide: cores e acessórios. hat: 0 cabelo, 1 chapéu pontudo, 2 elmo, 3 elmo com chifres, 4 capuz, 5 cogumelo, 6 coque, 7 coroa de cristal, 8 chapéu de palha, 9 faixa, 10 orelhas pontudas, 11 boina com pena. */
class HSpec(
    val skin: Long, val shirt: Long, val pants: Long, val hair: Long, val hat: Int = 0, val scarf: Long = 0L,
    val bulk: Float = 1f, val cape: Long = 0L, val pack: Boolean = false, val boots: Long = 0xFF4A3220L,
    val robe: Boolean = false, val trim: Long = 0L, val belt: Long = 0xFF5A3A22L, val eyes: Long = 0xFF1A1C2CL,
    val beard: Long = 0L, val glowEyes: Boolean = false, val pauldron: Long = 0L, val sleeves: Boolean = true
)

/** Personagem articulado: cada peça gira numa junta (ver [AdvSkel]). */
class Rig2(
    val pelvis: Mesh, val torso: Mesh, val head: Mesh, val uArm: Mesh, val fArm: Mesh,
    val thigh: Mesh, val shin: Mesh, val back: Mesh?, val lod: Mesh, val bulk: Float = 1f
)

object AdvChars {
    private fun mb(f: MeshBuilder.() -> Unit): Mesh { val b = MeshBuilder(); b.f(); return b.build() }
    private fun mbc(cull: Boolean, f: MeshBuilder.() -> Unit): Mesh { val b = MeshBuilder(); b.f(); return b.build(cull) }

    fun human2(s: HSpec): Rig2 {
        val bk = s.bulk
        val pelvis = mb {
            box(0f, 0.64f, 0f, 0.8f * bk, 0.24f, 0.5f * bk, C.shade(s.pants, 0.9f))
            box(0f, 0.74f, 0f, 0.86f * bk, 0.07f, 0.56f * bk, s.belt)
            box(0f, 0.74f, 0.29f * bk, 0.14f, 0.09f, 0.04f, 0xFFD8B04AL)
            if (s.robe) { frustum(0f, 0.18f, 0f, 0.62f * bk, 0.46f * bk, 0.58f, 7, s.shirt); if (s.trim != 0L) frustum(0f, 0.18f, 0f, 0.64f * bk, 0.62f * bk, 0.07f, 7, s.trim) }
        }
        val torso = mb {
            frustum(0f, 0f, 0f, 0.44f * bk, 0.54f * bk, RG.TORSO_H, 6, s.shirt, C.shade(s.shirt, 1.1f))
            if (s.trim != 0L) { box(0f, 0.31f, 0.4f * bk, 0.1f, 0.62f, 0.04f, s.trim) }
            if (s.scarf != 0L) { frustum(0f, RG.TORSO_H - 0.12f, 0f, 0.5f * bk, 0.44f * bk, 0.2f, 6, s.scarf); box(0.2f, 0.25f, -0.4f * bk, 0.22f, 0.62f, 0.06f, s.scarf) }
            if (s.pauldron != 0L) { sphere(-0.54f * bk, 0.52f, 0f, 0.26f, 0.2f, 0.26f, s.pauldron, 5, 2); sphere(0.54f * bk, 0.52f, 0f, 0.26f, 0.2f, 0.26f, s.pauldron, 5, 2) }
        }
        val head = mb {
            val c = RG.HEAD_C
            sphere(0f, c, 0f, 0.56f, 0.52f, 0.52f, s.skin, 6, 3)
            box(-0.2f, c + 0.04f, 0.5f, 0.1f, 0.15f, 0.06f, s.eyes); box(0.2f, c + 0.04f, 0.5f, 0.1f, 0.15f, 0.06f, s.eyes)
            if (s.glowEyes) { glow = true; box(-0.2f, c + 0.04f, 0.52f, 0.1f, 0.12f, 0.03f, 0xFFFF6A3CL); box(0.2f, c + 0.04f, 0.52f, 0.1f, 0.12f, 0.03f, 0xFFFF6A3CL); glow = false }
            box(0f, c - 0.08f, 0.55f, 0.11f, 0.1f, 0.1f, C.shade(s.skin, 0.86f))
            box(0f, c - 0.22f, 0.5f, 0.2f, 0.04f, 0.05f, C.shade(s.skin, 0.65f))
            if (s.beard != 0L) { sphere(0f, c - 0.3f, 0.28f, 0.34f, 0.26f, 0.26f, s.beard, 5, 2) }
            when (s.hat) {
                0 -> { sphere(0f, c + 0.18f, -0.08f, 0.6f, 0.42f, 0.58f, s.hair, 6, 3); box(0f, c + 0.5f, 0.2f, 0.2f, 0.2f, 0.2f, s.hair); box(-0.34f, c - 0.05f, -0.2f, 0.2f, 0.4f, 0.3f, s.hair); box(0.34f, c - 0.05f, -0.2f, 0.2f, 0.4f, 0.3f, s.hair) }
                1 -> { frustum(0f, c + 0.3f, 0f, 0.8f, 0.7f, 0.1f, 6, s.shirt); frustum(0f, c + 0.38f, 0f, 0.55f, 0f, 1.0f, 6, s.shirt); frustum(0f, c + 0.4f, 0f, 0.58f, 0.55f, 0.1f, 6, 0xFFE8C040L) }
                2 -> { sphere(0f, c + 0.16f, 0f, 0.62f, 0.5f, 0.6f, 0xFF9AA3B0L, 6, 3); box(0f, c - 0.04f, 0.56f, 0.12f, 0.38f, 0.06f, 0xFF7C8796L); box(0f, c + 0.45f, 0f, 0.1f, 0.14f, 0.5f, 0xFFB0302AL) }
                3 -> { sphere(0f, c + 0.16f, 0f, 0.62f, 0.5f, 0.6f, 0xFF8A94A0L, 6, 3); beam(-0.55f, c + 0.2f, 0f, -0.85f, c + 0.7f, 0f, 0.07f, 0xFFF4E9D0L); beam(0.55f, c + 0.2f, 0f, 0.85f, c + 0.7f, 0f, 0.07f, 0xFFF4E9D0L); box(0f, c - 0.04f, 0.56f, 0.12f, 0.38f, 0.06f, 0xFF6A7480L) }
                4 -> { sphere(0f, c + 0.1f, -0.08f, 0.66f, 0.56f, 0.64f, s.shirt, 6, 3); frustum(0f, c + 0.45f, -0.2f, 0.4f, 0f, 0.5f, 5, s.shirt); sphere(0f, c, 0.4f, 0.4f, 0.42f, 0.2f, C.shade(s.shirt, 0.5f), 4, 2) }
                5 -> { sphere(0f, c + 0.36f, 0f, 0.92f, 0.46f, 0.92f, 0xFFD94A3AL, 6, 3); sphere(0.4f, c + 0.7f, 0.2f, 0.16f, 0.1f, 0.16f, 0xFFF4F4F4L, 3, 2); sphere(-0.3f, c + 0.65f, -0.3f, 0.14f, 0.1f, 0.14f, 0xFFF4F4F4L, 3, 2) }
                6 -> { sphere(0f, c + 0.18f, -0.08f, 0.6f, 0.42f, 0.58f, s.hair, 6, 3); sphere(0f, c + 0.62f, -0.1f, 0.24f, 0.22f, 0.24f, s.hair, 5, 2) }
                7 -> { sphere(0f, c + 0.14f, 0f, 0.6f, 0.4f, 0.58f, 0xFF5A4A8AL, 6, 3); glow = true; for (i in 0 until 5) { val a = i / 5f * 2f * PI.toFloat(); gem(cos(a) * 0.42f, c + 0.5f, sin(a) * 0.42f, 0.1f, 0.4f, 0.04f, 4, 0xFFD6C0FFL, 0xFF8E6BE0L) }; glow = false }
                8 -> { frustum(0f, c + 0.28f, 0f, 0.95f, 0.5f, 0.12f, 7, 0xFFE3C050L); frustum(0f, c + 0.38f, 0f, 0.5f, 0.38f, 0.24f, 6, 0xFFEBCB62L); frustum(0f, c + 0.4f, 0f, 0.52f, 0.5f, 0.06f, 6, 0xFFB0302AL) }
                9 -> { sphere(0f, c + 0.16f, -0.05f, 0.6f, 0.4f, 0.58f, s.hair, 6, 3); box(0f, c + 0.22f, 0.0f, 1.15f, 0.12f, 1.1f, 0xFFB0302AL); box(0.55f, c + 0.1f, -0.5f, 0.14f, 0.3f, 0.1f, 0xFFB0302AL) }
                10 -> { box(-0.7f, c + 0.1f, 0f, 0.4f, 0.12f, 0.1f, s.skin); box(0.7f, c + 0.1f, 0f, 0.4f, 0.12f, 0.1f, s.skin); beam(-0.55f, c + 0.1f, 0f, -0.95f, c + 0.3f, 0f, 0.07f, s.skin); beam(0.55f, c + 0.1f, 0f, 0.95f, c + 0.3f, 0f, 0.07f, s.skin) }
                else -> { sphere(0f, c + 0.18f, -0.08f, 0.6f, 0.42f, 0.58f, s.hair, 6, 3); frustum(0f, c + 0.34f, 0f, 0.62f, 0.5f, 0.16f, 6, 0xFF3F8F3AL); beam(0.2f, c + 0.45f, -0.1f, 0.6f, c + 0.8f, -0.4f, 0.05f, 0xFFF4F4F4L) }
            }
        }
        val sleeve = if (s.sleeves) s.shirt else s.skin
        val uArm = mb {
            box(0f, -0.17f, 0f, 0.23f * bk, 0.37f, 0.23f * bk, sleeve)
            sphere(0f, 0f, 0f, 0.15f * bk, 0.13f, 0.15f * bk, sleeve, 4, 2)
        }
        val fArm = mb {
            box(0f, -0.16f, 0f, 0.2f * bk, 0.33f, 0.2f * bk, if (s.sleeves) C.shade(s.shirt, 0.92f) else s.skin)
            box(0f, -0.28f, 0f, 0.22f * bk, 0.1f, 0.22f * bk, 0xFF6B4A2AL)
            sphere(0f, -0.38f, 0f, 0.14f * bk, 0.13f, 0.14f * bk, s.skin, 4, 2)
        }
        val thigh = mb { box(0f, -0.14f, 0f, 0.27f * bk, 0.3f, 0.29f * bk, s.pants); sphere(0f, 0f, 0f, 0.15f * bk, 0.12f, 0.15f * bk, s.pants, 4, 2) }
        val shin = mb {
            box(0f, -0.13f, 0f, 0.24f * bk, 0.28f, 0.26f * bk, C.shade(s.pants, 0.94f))
            box(0f, -0.27f, 0.05f, 0.29f * bk, 0.14f, 0.43f, s.boots)
        }
        val back: Mesh? = if (s.cape != 0L) mbc(false) {
            val w = 0.46f * bk; val l = 1.05f
            val a = vert(-w, 0f, 0f); val b = vert(w, 0f, 0f); val c = vert(w * 1.25f, -l, -0.16f); val d = vert(-w * 1.25f, -l, -0.16f)
            quadN(a, b, c, d, 0f, 0f, -1f, s.cape); quadN(a, b, c, d, 0f, 0f, 1f, C.shade(s.cape, 0.72f))
            box(0f, 0f, -0.02f, 0.9f * bk, 0.1f, 0.12f, C.shade(s.cape, 0.8f))
        } else if (s.pack) mb { box(0f, -0.3f, -0.18f, 0.7f * bk, 0.7f, 0.36f, 0xFF8A5A2BL, 0xFF9A6A3AL); box(0f, -0.1f, -0.38f, 0.5f * bk, 0.2f, 0.06f, 0xFF6B4423L); box(0f, 0.05f, -0.18f, 0.55f * bk, 0.14f, 0.3f, 0xFFD9C79AL) } else null
        val lod = mb {
            boxNoBottom(0f, 0.9f, 0f, 0.8f * bk, 0.9f, 0.5f * bk, s.shirt)
            box(0f, 0.34f, 0f, 0.5f * bk, 0.68f, 0.34f * bk, s.pants)
            sphere(0f, 1.74f, 0f, 0.56f, 0.52f, 0.52f, s.skin, 5, 2)
            sphere(0f, 1.92f, -0.06f, 0.6f, 0.4f, 0.56f, if (s.hat == 0 || s.hat == 6) s.hair else if (s.hat == 2 || s.hat == 3) 0xFF9AA3B0L else s.shirt, 5, 2)
            box(-0.58f * bk, 1.0f, 0f, 0.2f, 0.7f, 0.22f, s.shirt); box(0.58f * bk, 1.0f, 0f, 0.2f, 0.7f, 0.22f, s.shirt)
            if (s.cape != 0L) box(0f, 0.9f, -0.3f, 0.8f * bk, 1.0f, 0.06f, s.cape)
        }
        return Rig2(pelvis, torso, head, uArm, fArm, thigh, shin, back, lod, bk)
    }

    // ------------------------------------------------------------------ criaturas
    fun slime(c: Long, c2: Long, face: Long = 0xFF1A1C2CL): Mesh = mb {
        sphere(0f, 0.55f, 0f, 0.88f, 0.62f, 0.88f, c, 6, 3, c2)
        sphere(0.3f, 0.78f, 0.7f, 0.13f, 0.17f, 0.08f, face, 4, 2); sphere(-0.3f, 0.78f, 0.7f, 0.13f, 0.17f, 0.08f, face, 4, 2)
        box(0f, 0.55f, 0.84f, 0.3f, 0.05f, 0.04f, face)
        sphere(-0.35f, 1.02f, 0.2f, 0.2f, 0.1f, 0.2f, C.shade(c, 1.25f), 4, 2)
    }
    val slimeG: Mesh by lazy { slime(0xFF6BCB5BL, 0xFF5BB84BL) }
    val slimeB: Mesh by lazy { slime(0xFF5BB8E8L, 0xFF4AA2D2L) }
    val slimeR: Mesh by lazy { slime(0xFFD9703CL, 0xFFC0602EL) }

    val hare: Mesh by lazy { mb {
        sphere(0f, 0.45f, 0f, 0.4f, 0.4f, 0.55f, 0xFFC9A074L, 5, 3, 0xFFB8905FL)
        sphere(0f, 0.7f, 0.45f, 0.26f, 0.26f, 0.28f, 0xFFD6AE84L, 5, 3)
        box(-0.1f, 1.15f, 0.4f, 0.1f, 0.5f, 0.06f, 0xFFD6AE84L); box(0.1f, 1.15f, 0.4f, 0.1f, 0.5f, 0.06f, 0xFFD6AE84L)
        box(-0.12f, 0.78f, 0.7f, 0.06f, 0.06f, 0.04f, 0xFF1A1C2CL); box(0.12f, 0.78f, 0.7f, 0.06f, 0.06f, 0.04f, 0xFF1A1C2CL)
        sphere(0f, 0.4f, -0.55f, 0.16f, 0.16f, 0.16f, 0xFFF4EEDCL, 4, 2)
        box(-0.25f, 0.12f, 0.3f, 0.14f, 0.24f, 0.3f, 0xFFB8905FL); box(0.25f, 0.12f, 0.3f, 0.14f, 0.24f, 0.3f, 0xFFB8905FL)
        box(0f, 0.55f, -0.1f, 0.5f, 0.5f, 0.3f, 0xFF8A5A2BL)
    } }
    val bunny: Mesh by lazy { mb {
        sphere(0f, 0.3f, 0f, 0.3f, 0.28f, 0.4f, 0xFFF1EBDDL, 5, 3)
        sphere(0f, 0.48f, 0.32f, 0.2f, 0.2f, 0.22f, 0xFFF6F0E4L, 5, 2)
        box(-0.07f, 0.85f, 0.3f, 0.08f, 0.4f, 0.05f, 0xFFF6F0E4L); box(0.07f, 0.85f, 0.3f, 0.08f, 0.4f, 0.05f, 0xFFF6F0E4L)
        sphere(0f, 0.28f, -0.4f, 0.12f, 0.12f, 0.12f, 0xFFFFFFFFL, 3, 2)
    } }
    val frog: Mesh by lazy { mb {
        sphere(0f, 0.45f, 0f, 0.7f, 0.45f, 0.65f, 0xFF7B9A4AL, 6, 3, 0xFF6A8A3CL)
        sphere(-0.35f, 0.9f, 0.4f, 0.2f, 0.2f, 0.2f, 0xFFE8D870L, 4, 2); sphere(0.35f, 0.9f, 0.4f, 0.2f, 0.2f, 0.2f, 0xFFE8D870L, 4, 2)
        box(-0.35f, 0.92f, 0.57f, 0.07f, 0.12f, 0.04f, 0xFF1A1C2CL); box(0.35f, 0.92f, 0.57f, 0.07f, 0.12f, 0.04f, 0xFF1A1C2CL)
        box(0f, 0.38f, 0.62f, 0.5f, 0.05f, 0.04f, 0xFF3A4A22L)
        box(-0.55f, 0.15f, -0.3f, 0.2f, 0.3f, 0.5f, 0xFF6A8A3CL); box(0.55f, 0.15f, -0.3f, 0.2f, 0.3f, 0.5f, 0xFF6A8A3CL)
        box(0f, 0.7f, -0.1f, 0.5f, 0.12f, 0.4f, 0xFFB5651DL)
    } }
    val batBody: Mesh by lazy { mb { glow = false
        sphere(0f, 0f, 0f, 0.32f, 0.3f, 0.34f, 0xFF4A3A7AL, 5, 3)
        glow = true; box(-0.1f, 0.06f, 0.3f, 0.07f, 0.07f, 0.04f, 0xFFFFE066L); box(0.1f, 0.06f, 0.3f, 0.07f, 0.07f, 0.04f, 0xFFFFE066L)
        gem(0f, 0.25f, -0.05f, 0.1f, 0.28f, 0.0f, 4, 0xFF9BE6FFL)
        glow = false; box(-0.12f, 0.3f, 0.12f, 0.08f, 0.2f, 0.05f, 0xFF3A2A6AL); box(0.12f, 0.3f, 0.12f, 0.08f, 0.2f, 0.05f, 0xFF3A2A6AL)
    } }
    val batWing: Mesh by lazy { mbc(false) {
        val a = vert(0f, 0.05f, 0.15f); val b = vert(0.85f, 0.25f, 0.05f); val c = vert(0.55f, -0.25f, -0.25f); val d = vert(0f, -0.1f, -0.2f)
        quadN(a, b, c, d, 0f, 1f, 0f, 0xFF6A5AAAL); quadN(a, b, c, d, 0f, -1f, 0f, 0xFF4A3A7AL)
        beam(0f, 0.05f, 0.1f, 0.9f, 0.3f, 0.05f, 0.03f, 0xFF3A2A6AL)
    } }
    val bird: Mesh by lazy { mb { sphere(0f, 0f, 0f, 0.2f, 0.16f, 0.3f, 0xFF5B8FD0L, 4, 2); sphere(0f, 0.08f, 0.26f, 0.11f, 0.11f, 0.11f, 0xFF5B8FD0L, 4, 2); frustum(0f, 0.08f, 0.34f, 0.04f, 0f, 0.1f, 3, 0xFFFFB02EL); box(0f, 0f, -0.35f, 0.14f, 0.03f, 0.2f, 0xFF3F6FB0L) } }
    val birdWing: Mesh by lazy { mbc(false) { val a = vert(0f, 0f, 0.15f); val b = vert(0.5f, 0.05f, 0f); val c = vert(0f, 0f, -0.2f); triN(a, b, c, 0f, 1f, 0f, 0xFF4A7FC0L); triN(a, b, c, 0f, -1f, 0f, 0xFF3F6FB0L) } }
    val butterfly: Mesh by lazy { mbc(false) { glow = true; val a = vert(0f, 0f, 0f); val b = vert(0.22f, 0.12f, 0.1f); val c = vert(0.2f, -0.05f, -0.12f); triN(a, b, c, 0f, 1f, 0f, 0xFFFFA8D8L); triN(a, b, c, 0f, -1f, 0f, 0xFFFF88C0L) } }
    val fish: Mesh by lazy { mb { sphere(0f, 0f, 0f, 0.14f, 0.12f, 0.32f, 0xFFFFA23CL, 4, 2); box(0f, 0f, -0.34f, 0.03f, 0.2f, 0.16f, 0xFFFF8A1CL) } }
    val firefly: Mesh by lazy { mbc(false) { glow = true; gem(0f, 0f, 0f, 0.05f, 0.07f, 0.07f, 4, 0xFFF4FF8AL) } }

    // ------------------------------------------------------------------ chefes especiais
    val kingBody: Mesh by lazy { mb {
        glow = false
        gem(0f, 3.2f, 0f, 1.5f, 2.4f, 2.1f, 6, 0xFF4B2A7BL, 0xFF2B1850L)
        frustum(0f, 5.4f, 0f, 1.05f, 0.75f, 0.25f, 6, AdvModels.GOLD)
        for (i in 0 until 6) { val a = i / 6f * 2f * PI.toFloat(); frustum(cos(a) * 0.95f, 5.6f, sin(a) * 0.95f, 0.18f, 0f, 0.9f, 3, AdvModels.GOLD) }
        glow = true; box(0f, 3.5f, 1.4f, 1.1f, 0.22f, 0.12f, 0xFFFF3B3BL)
        box(-0.6f, 3.9f, 1.3f, 0.28f, 0.18f, 0.1f, 0xFFFF6A3CL); box(0.6f, 3.9f, 1.3f, 0.28f, 0.18f, 0.1f, 0xFFFF6A3CL)
        glow = false
        // armadura ferrugem rachada
        box(0f, 3.0f, 1.3f, 1.2f, 0.12f, 0.1f, 0xFFB5651DL); box(0f, 2.5f, 1.2f, 0.9f, 0.1f, 0.1f, 0xFF8A4A1CL)
    } }
    val kingBodyFree: Mesh by lazy { mb {
        glow = true
        gem(0f, 3.2f, 0f, 1.5f, 2.4f, 2.1f, 6, 0xFFB38CFFL, 0xFF6B4CD0L)
        frustum(0f, 5.4f, 0f, 1.05f, 0.75f, 0.25f, 6, AdvModels.GOLD)
        for (i in 0 until 6) { val a = i / 6f * 2f * PI.toFloat(); frustum(cos(a) * 0.95f, 5.6f, sin(a) * 0.95f, 0.18f, 0f, 0.9f, 3, 0xFFFFE066L) }
    } }
    val shardOrbit: Mesh by lazy { mb { glow = true; gem(0f, 0f, 0f, 0.35f, 0.9f, 0.9f, 4, 0xFF8E4FA8L, 0xFF4B2A7BL) } }
    val crystalSpike: Mesh by lazy { mb { glow = true; gem(0f, 0.9f, 0f, 0.35f, 1.4f, 0.0f, 4, 0xFFB38CFFL, 0xFF6B4CD0L) } }
    val goldenHalo: Mesh by lazy { mbc(false) { glow = true; for (i in 0 until 10) { val a0 = i / 10f * 2f * PI.toFloat(); val a1 = (i + 1) / 10f * 2f * PI.toFloat(); beam(cos(a0) * 1.6f, 0f, sin(a0) * 1.6f, cos(a1) * 1.6f, 0f, sin(a1) * 1.6f, 0.07f, 0xFFFFE066L) } } }

    // ------------------------------------------------------------------ rigs prontos
    val hero by lazy { human2(HSpec(0xFFE8B98AL, 0xFF2FA89AL, 0xFF6B4A2AL, 0xFF5A3A22L, 0, scarf = 0xFFFF8A3DL, cape = 0xFF3B5DC9L, belt = 0xFF6B4423L)) }
    val elder by lazy { human2(HSpec(0xFFD9A77AL, 0xFF7E57C2L, 0xFF5E3FA0L, 0xFFF1F1F1L, 6, bulk = 1.1f, robe = true, trim = 0xFFE8C040L, beard = 0xFFF1F1F1L)) }
    val smith by lazy { human2(HSpec(0xFFC98E62L, 0xFF8A5A2BL, 0xFF3A2A1AL, 0xFF1A1A1AL, 9, bulk = 1.35f, sleeves = false, beard = 0xFF2A1A12L, belt = 0xFF3A2A1AL)) }
    val lia by lazy { human2(HSpec(0xFFE3B58AL, 0xFF3FA05AL, 0xFF2E7A44L, 0xFF4A2A1AL, 4, pack = true, trim = 0xFFE8C040L)) }
    val farmer by lazy { human2(HSpec(0xFFD9A066L, 0xFFC89A4AL, 0xFF4A6FA8L, 0xFF6A4A2AL, 8, bulk = 1.1f, beard = 0xFF6A4A2AL)) }
    val fisher by lazy { human2(HSpec(0xFFC8946AL, 0xFF5B8FD0L, 0xFF3A4A66L, 0xFF8A8A8AL, 8, bulk = 1.05f, beard = 0xFFBDBDBDL)) }
    val guardNpc by lazy { human2(HSpec(0xFFD9A77AL, 0xFF8A94A0L, 0xFF4A5560L, 0xFF3A2A1AL, 2, bulk = 1.2f, cape = 0xFF3B5DC9L, pauldron = 0xFFB0B8C4L)) }
    val child by lazy { human2(HSpec(0xFFE8B98AL, 0xFFE06A8AL, 0xFF5A6FA8L, 0xFF4A2A1AL, 6, bulk = 0.85f)) }
    val witch by lazy { human2(HSpec(0xFFB8D0A0L, 0xFF4B3A8AL, 0xFF3A2A6AL, 0xFF2A1A3AL, 1, robe = true, trim = 0xFF9BE6FFL, bulk = 0.95f)) }
    val cogu by lazy { human2(HSpec(0xFFD9A066L, 0xFF8B5A2BL, 0xFF5A3B1CL, 0xFF3A2A1AL, 5, bulk = 1.15f)) }
    val casc by lazy { human2(HSpec(0xFF2A2A35L, 0xFFB8C0CCL, 0xFF8A94A0L, 0xFF2A2A35L, 2, bulk = 1.2f, glowEyes = true, pauldron = 0xFFD0D6DEL)) }
    val ferr by lazy { human2(HSpec(0xFF3A2A2AL, 0xFFB5651DL, 0xFF7A4A21L, 0xFF2A1A1AL, 3, bulk = 1.25f, cape = 0xFF8E1B1BL, glowEyes = true, pauldron = 0xFF8A4A1CL)) }
    val archer by lazy { human2(HSpec(0xFF8FB07AL, 0xFF4F7A3AL, 0xFF5A4A2AL, 0xFF3F5A2FL, 11, bulk = 0.95f, cape = 0xFF3F5A2FL)) }
    val elite by lazy { human2(HSpec(0xFF2A2A35L, 0xFF7C2A22L, 0xFF4A1A1AL, 0xFF2A1A1AL, 3, bulk = 1.4f, cape = 0xFF2A1A1AL, glowEyes = true, pauldron = 0xFFB5651DL, trim = 0xFFE8C040L)) }
    val statueGuard by lazy { human2(HSpec(0xFF9AA3B0L, 0xFF7C8796L, 0xFF626C7AL, 0xFF9AA3B0L, 2, bulk = 1.35f, glowEyes = true, pauldron = 0xFF9AA3B0L)) }
    val troll by lazy { human2(HSpec(0xFF7B9966L, 0xFF4F6B3FL, 0xFF5A4A2AL, 0xFF3F5A2FL, 0, bulk = 1.5f, sleeves = false, beard = 0xFF5A7A48L)) }
    val sentinel by lazy { human2(HSpec(0xFF9AA3B0L, 0xFF7C8796L, 0xFF626C7AL, 0xFF9AA3B0L, 2, bulk = 1.4f, glowEyes = true, pauldron = 0xFFB0B8C4L, trim = 0xFF9BE6FFL)) }
    val captain by lazy { human2(HSpec(0xFF2A2A35L, 0xFFB5651DL, 0xFF6B3A1AL, 0xFF2A1A1AL, 3, bulk = 1.3f, cape = 0xFFB0302AL, glowEyes = true, pauldron = 0xFFD58A3DL, trim = 0xFFE8C040L)) }
    val crystalGuard by lazy { human2(HSpec(0xFF6A5A9AL, 0xFF5A4A8AL, 0xFF3A2A6AL, 0xFF6A5A9AL, 7, bulk = 1.55f, glowEyes = true, pauldron = 0xFF9B7BE0L, sleeves = false)) }
    val npcExtra by lazy { human2(HSpec(0xFFE0B080L, 0xFF9A5AB0L, 0xFF4A3A6AL, 0xFF2A1A1AL, 0)) }
}
