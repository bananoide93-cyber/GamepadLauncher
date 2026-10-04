package com.gamepadlayout.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { PURPLE("Roxo"), DARK("Escuro"), BLACK("Preto") }
enum class CastQuality(val label: String, val maxSide: Int, val jpeg: Int) {
    LOW("Latência mínima (360p)", 640, 55),
    Q480("480p (leve)", 854, 65),
    Q720("720p (equilibrado)", 1280, 70),
    Q1080("1080p (pesado)", 1920, 75)
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.PURPLE,
    val effects: Float = 0.7f,
    val transparency: Float = 0.5f,
    val animations: Boolean = true,
    val animSpeed: Float = 1f,
    val iconScale: Float = 1f,
    val hiddenCategories: Set<String> = emptySet(),
    val autoDetectGames: Boolean = true,
    val userName: String = "Jogador",
    val cursorSpeed: Float = 1f,
    val zoom: Int = 100,
    val consoleMode: Boolean = true,
    val gameMode: Boolean = true,
    val autoTv: Boolean = true,
    val powerSaver: Boolean = false,
    val castQuality: CastQuality = CastQuality.Q720,
    val castFps: Int = 24,
    val castLandscape: Boolean = true,
    val mapping: String = "",
    val wallpaper: String = "default",
    val wallpaperDim: Float = 0.25f,
    val wallpaperRev: Int = 0,
    val controllerOnly: Boolean = true
)

object SettingsKeys {
    val THEME = stringPreferencesKey("theme")
    val EFFECTS = floatPreferencesKey("effects")
    val TRANSPARENCY = floatPreferencesKey("transparency")
    val ANIMATIONS = booleanPreferencesKey("animations")
    val ANIM_SPEED = floatPreferencesKey("anim_speed")
    val ICON_SCALE = floatPreferencesKey("icon_scale")
    val HIDDEN_CATEGORIES = stringSetPreferencesKey("hidden_categories")
    val AUTO_DETECT = booleanPreferencesKey("auto_detect_games")
    val USER_NAME = stringPreferencesKey("user_name")
    val CURSOR_SPEED = floatPreferencesKey("cursor_speed")
    val ZOOM = intPreferencesKey("zoom")
    val CONSOLE_MODE = booleanPreferencesKey("console_mode")
    val GAME_MODE = booleanPreferencesKey("game_mode")
    val AUTO_TV = booleanPreferencesKey("auto_tv")
    val POWER_SAVER = booleanPreferencesKey("power_saver")
    val CAST_QUALITY = stringPreferencesKey("cast_quality")
    val CAST_FPS = intPreferencesKey("cast_fps")
    val CAST_LANDSCAPE = booleanPreferencesKey("cast_landscape")
    val MAPPING = stringPreferencesKey("mapping")
    val WALLPAPER = stringPreferencesKey("wallpaper")
    val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
    val WALLPAPER_REV = intPreferencesKey("wallpaper_rev")
    val CONTROLLER_ONLY = booleanPreferencesKey("controller_only")
}

private inline fun <reified E : Enum<E>> parse(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.appDataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = parse(p[SettingsKeys.THEME], d.theme),
            effects = p[SettingsKeys.EFFECTS] ?: d.effects,
            transparency = p[SettingsKeys.TRANSPARENCY] ?: d.transparency,
            animations = p[SettingsKeys.ANIMATIONS] ?: d.animations,
            animSpeed = p[SettingsKeys.ANIM_SPEED] ?: d.animSpeed,
            iconScale = p[SettingsKeys.ICON_SCALE] ?: d.iconScale,
            hiddenCategories = p[SettingsKeys.HIDDEN_CATEGORIES] ?: d.hiddenCategories,
            autoDetectGames = p[SettingsKeys.AUTO_DETECT] ?: d.autoDetectGames,
            userName = p[SettingsKeys.USER_NAME] ?: d.userName,
            cursorSpeed = p[SettingsKeys.CURSOR_SPEED] ?: d.cursorSpeed,
            zoom = p[SettingsKeys.ZOOM] ?: d.zoom,
            consoleMode = p[SettingsKeys.CONSOLE_MODE] ?: d.consoleMode,
            gameMode = p[SettingsKeys.GAME_MODE] ?: d.gameMode,
            autoTv = p[SettingsKeys.AUTO_TV] ?: d.autoTv,
            powerSaver = p[SettingsKeys.POWER_SAVER] ?: d.powerSaver,
            castQuality = parse(p[SettingsKeys.CAST_QUALITY], d.castQuality),
            castFps = p[SettingsKeys.CAST_FPS] ?: d.castFps,
            castLandscape = p[SettingsKeys.CAST_LANDSCAPE] ?: d.castLandscape,
            mapping = p[SettingsKeys.MAPPING] ?: d.mapping,
            wallpaper = p[SettingsKeys.WALLPAPER] ?: d.wallpaper,
            wallpaperDim = p[SettingsKeys.WALLPAPER_DIM] ?: d.wallpaperDim,
            wallpaperRev = p[SettingsKeys.WALLPAPER_REV] ?: d.wallpaperRev,
            controllerOnly = p[SettingsKeys.CONTROLLER_ONLY] ?: d.controllerOnly
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.appDataStore.edit { it[key] = value }
    }

    suspend fun setCategoryHidden(name: String, hidden: Boolean) {
        context.appDataStore.edit { p ->
            val cur = p[SettingsKeys.HIDDEN_CATEGORIES] ?: emptySet()
            p[SettingsKeys.HIDDEN_CATEGORIES] = if (hidden) cur + name else cur - name
        }
    }
}
