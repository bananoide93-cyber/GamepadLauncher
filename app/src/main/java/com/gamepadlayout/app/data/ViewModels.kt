package com.gamepadlayout.app.data

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.set(key, value) }
    }

    fun setCategoryHidden(name: String, hidden: Boolean) {
        viewModelScope.launch { repo.setCategoryHidden(name, hidden) }
    }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LibraryRepository(app)
    private val settingsRepo = SettingsRepository(app)

    private val _allApps = MutableStateFlow<List<LibraryApp>>(emptyList())
    val allApps: StateFlow<List<LibraryApp>> = _allApps

    /** Biblioteca = jogos detectados (se ligado) + adicionados manualmente − removidos. */
    val library: StateFlow<List<LibraryApp>> = combine(
        _allApps, settingsRepo.settings, repo.manual, repo.removed, repo.recents
    ) { apps, settings, manual, removed, recents ->
        val order = recents.withIndex().associate { it.value to it.index }
        apps.filter { a ->
            a.packageName !in removed &&
                ((settings.autoDetectGames && a.isGame) || a.packageName in manual)
        }.sortedWith(compareBy({ order[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Sugestões do catálogo + escolhas do usuário (as do usuário vencem). */
    val tags: StateFlow<Map<String, GameTag>> = repo.tags
        .map { GameCatalog.defaults + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameCatalog.defaults)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _allApps.value = repo.loadInstalled() }
    }

    fun add(pkg: String) { viewModelScope.launch { repo.add(pkg) } }
    fun remove(pkg: String) { viewModelScope.launch { repo.remove(pkg) } }
    fun markPlayed(pkg: String) { viewModelScope.launch { repo.markPlayed(pkg) } }
    fun setTag(pkg: String, tag: GameTag) { viewModelScope.launch { repo.setTag(pkg, tag) } }
    fun launch(pkg: String): Boolean = repo.launch(pkg)
}

class ControllerViewModel(app: Application) : AndroidViewModel(app) {
    /** Só escuta o InputManager enquanto a UI está visível (economia de recursos). */
    val controllers: StateFlow<List<com.gamepadlayout.app.controller.ControllerInfo>> =
        com.gamepadlayout.app.controller.ControllerManager(app).flow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class ExternalViewModel(app: Application) : AndroidViewModel(app) {
    private val ed = com.gamepadlayout.app.display.ExternalDisplays(app)

    val displays: StateFlow<List<com.gamepadlayout.app.display.ExternalDisplayInfo>> =
        ed.flow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cast: StateFlow<com.gamepadlayout.app.cast.CastStatus> = com.gamepadlayout.app.cast.CastState.status

    fun display(id: Int) = ed.get(id)
    fun compat() = com.gamepadlayout.app.compat.Compat.report(getApplication<Application>())
    fun audioOutputs() = com.gamepadlayout.app.audio.AudioRouting(getApplication<Application>()).outputs()
    fun openAudioSwitcher() = com.gamepadlayout.app.audio.AudioRouting(getApplication<Application>()).openSystemSwitcher()
}
