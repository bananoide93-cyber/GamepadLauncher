package com.gamepadlayout.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class LibraryApp(val packageName: String, val label: String, val isGame: Boolean)

/**
 * Biblioteca de jogos. Usa apenas APIs oficiais: PackageManager (apps com ícone de launcher,
 * visíveis via <queries> no manifest) e ApplicationInfo.category para detectar jogos.
 */
class LibraryRepository(private val context: Context) {
    private val manualKey = stringSetPreferencesKey("library_manual")
    private val removedKey = stringSetPreferencesKey("library_removed")
    private val recentKey = stringPreferencesKey("library_recent")

    val manual: Flow<Set<String>> = context.appDataStore.data.map { it[manualKey] ?: emptySet() }
    val removed: Flow<Set<String>> = context.appDataStore.data.map { it[removedKey] ?: emptySet() }

    /** Jogos jogados por último (mais recente primeiro). */
    val recents: Flow<List<String>> = context.appDataStore.data.map { p ->
        (p[recentKey] ?: "").split(",").filter { it.isNotBlank() }
    }

    suspend fun markPlayed(pkg: String) {
        context.appDataStore.edit { p ->
            val cur = (p[recentKey] ?: "").split(",").filter { it.isNotBlank() && it != pkg }
            p[recentKey] = (listOf(pkg) + cur).take(12).joinToString(",")
        }
    }

    suspend fun add(pkg: String) {
        context.appDataStore.edit { p ->
            p[manualKey] = (p[manualKey] ?: emptySet()) + pkg
            p[removedKey] = (p[removedKey] ?: emptySet()) - pkg
        }
    }

    suspend fun remove(pkg: String) {
        context.appDataStore.edit { p ->
            p[manualKey] = (p[manualKey] ?: emptySet()) - pkg
            p[removedKey] = (p[removedKey] ?: emptySet()) + pkg
        }
    }

    @Suppress("DEPRECATION")
    suspend fun loadInstalled(): List<LibraryApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (ai.packageName == context.packageName) return@mapNotNull null
                LibraryApp(ai.packageName, ri.loadLabel(pm).toString(), isGame(ai))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun isGame(ai: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) return true
        return (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    fun launch(pkg: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }
}
