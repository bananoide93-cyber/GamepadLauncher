package com.gamepadlayout.app.games

/** Registro dos jogos nativos do Arcade. */
object Games {
    fun create(id: String): MiniGame? = when (id) {
        "dungeon" -> DungeonGame()
        "ship" -> ShipGame()
        "chicken" -> ChickenGame()
        "snake" -> SnakeGame()
        "racer" -> RacerGame()
        "runner" -> RunnerGame()
        "breakout" -> BreakoutGame()
        "survive" -> SurviveGame()
        "monsters" -> MonstersGame()
        "vale" -> try { AdventureGame() } catch (e: Throwable) { SnakeGame() }
        else -> null
    }

    val ids: List<String> = listOf("dungeon", "racer", "runner", "ship", "chicken", "snake", "breakout", "survive", "monsters", "vale")

    fun all(): List<MiniGame> = ids.mapNotNull { create(it) }
}
