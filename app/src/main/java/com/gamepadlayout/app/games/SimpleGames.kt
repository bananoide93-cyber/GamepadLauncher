package com.gamepadlayout.app.games

/** Registro dos jogos nativos do Arcade. */
object Games {
    fun create(id: String): MiniGame? = when (id) {
        "dungeon" -> DungeonGame()
        "ship" -> ShipGame()
        "chicken" -> ChickenGame()
        "snake" -> SnakeGame()
        "breakout" -> BreakoutGame()
        else -> null
    }

    val ids: List<String> = listOf("dungeon", "ship", "chicken", "snake", "breakout")

    fun all(): List<MiniGame> = ids.mapNotNull { create(it) }
}
