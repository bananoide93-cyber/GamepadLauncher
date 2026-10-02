package com.gamepadlayout.app.input

/** Ações da interface que podem ser ligadas a qualquer botão do controle. */
enum class PadAction(val label: String) {
    CONFIRM("Confirmar"),
    BACK("Voltar"),
    MENU("Menu rápido"),
    OPTIONS("Opções do item"),
    CLICK("Clique (navegador)"),
    RIGHT_CLICK("Clique direito (navegador)"),
    TAB_NEXT("Próxima aba"),
    TAB_PREV("Aba anterior"),
    RELOAD("Atualizar"),
    FORWARD("Avançar"),
    NEW_TAB("Nova aba"),
    CLOSE_TAB("Fechar aba"),
    NONE("Nenhuma")
}

/** Mapeamento botão -> ação, salvo como texto "A=CONFIRM;B=BACK;..." */
object ControllerMapping {
    val defaults: Map<PadButton, PadAction> = mapOf(
        PadButton.A to PadAction.CONFIRM,
        PadButton.B to PadAction.BACK,
        PadButton.X to PadAction.RELOAD,
        PadButton.Y to PadAction.OPTIONS,
        PadButton.L1 to PadAction.TAB_PREV,
        PadButton.R1 to PadAction.TAB_NEXT,
        PadButton.L2 to PadAction.BACK,
        PadButton.R2 to PadAction.CLICK,
        PadButton.L3 to PadAction.CLOSE_TAB,
        PadButton.R3 to PadAction.NEW_TAB,
        PadButton.START to PadAction.MENU,
        PadButton.SELECT to PadAction.NONE
    )

    fun parse(s: String): Map<PadButton, PadAction> {
        val out = defaults.toMutableMap()
        if (s.isBlank()) return out
        s.split(";").forEach { part ->
            val kv = part.split("=")
            if (kv.size == 2) {
                val b = PadButton.entries.firstOrNull { it.name == kv[0] }
                val a = PadAction.entries.firstOrNull { it.name == kv[1] }
                if (b != null && a != null) out[b] = a
            }
        }
        return out
    }

    fun serialize(m: Map<PadButton, PadAction>): String =
        m.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }
}
