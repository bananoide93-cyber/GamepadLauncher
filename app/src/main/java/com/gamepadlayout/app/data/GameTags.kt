package com.gamepadlayout.app.data

/** Classificação dos jogos quanto ao suporte a controle. O usuário pode mudar a qualquer momento. */
enum class GameTag(val label: String, val hint: String) {
    NATIVE("Com suporte a controle", "O jogo reconhece o controle sozinho"),
    EMULATOR("Emuladores", "Emuladores têm suporte completo a controle"),
    MAPPER("Precisam de mapeador (beta)", "Jogos só de toque: o mapeador de botões chega numa próxima versão"),
    UNKNOWN("Sem classificação", "Segure o jogo (ou aperte Y) para classificar")
}

/**
 * Sugestões iniciais para jogos conhecidos. É só um ponto de partida: o Android não informa
 * se um jogo aceita controle, por isso a classificação final é sempre sua.
 */
object GameCatalog {
    val defaults: Map<String, GameTag> = mapOf(
        "com.mojang.minecraftpe" to GameTag.NATIVE,
        "com.miHoYo.GenshinImpact" to GameTag.NATIVE,
        "com.activision.callofduty.shooter" to GameTag.NATIVE,
        "com.roblox.client" to GameTag.NATIVE,
        "com.innersloth.spacemafia" to GameTag.NATIVE,
        "com.reLogic.terraria" to GameTag.NATIVE,
        "org.ppsspp.ppsspp" to GameTag.EMULATOR,
        "org.ppsspp.ppssppgold" to GameTag.EMULATOR,
        "org.dolphinemu.dolphinemu" to GameTag.EMULATOR,
        "com.retroarch" to GameTag.EMULATOR,
        "com.retroarch.aarch64" to GameTag.EMULATOR,
        "org.vita3k.emulator" to GameTag.EMULATOR
    )

    fun parse(s: String): Map<String, GameTag> {
        val out = mutableMapOf<String, GameTag>()
        s.split(";").forEach { part ->
            val kv = part.split("=")
            if (kv.size == 2) {
                val t = GameTag.entries.firstOrNull { it.name == kv[1] }
                if (t != null && kv[0].isNotBlank()) out[kv[0]] = t
            }
        }
        return out
    }

    fun serialize(m: Map<String, GameTag>): String =
        m.entries.joinToString(";") { "${it.key}=${it.value.name}" }
}
