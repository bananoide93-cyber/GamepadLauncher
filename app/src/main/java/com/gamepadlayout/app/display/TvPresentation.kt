package com.gamepadlayout.app.display

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamepadlayout.app.controller.ControllerInfo
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.ui.home.Category
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Estado compartilhado entre o celular (que recebe o controle) e a Home exibida na TV.
 * A Home da TV tem o mesmo layout da Home do celular: linha 0 = categorias, linha 1 = jogos.
 */
object TvNav {
    val settings = MutableStateFlow(AppSettings())
    val games = MutableStateFlow<List<LibraryApp>>(emptyList())
    val controllers = MutableStateFlow<List<ControllerInfo>>(emptyList())
    val row = MutableStateFlow(0)
    val col = MutableStateFlow(0)

    /** true enquanto a tela "Controlando a TV" está aberta no celular. */
    @Volatile var controlling = false
    var onLaunch: ((LibraryApp) -> Unit)? = null
    var onCategory: ((Category) -> Unit)? = null
    var onAddGame: (() -> Unit)? = null

    fun visibleCategories(): List<Category> =
        Category.entries.filter { it.name !in settings.value.hiddenCategories }

    /** Quantidade de itens da linha atual (a linha de jogos tem o botão "Adicionar jogo" no fim). */
    private fun count(r: Int): Int = if (r == 0) visibleCategories().size else games.value.size + 1

    fun clamp() {
        row.value = row.value.coerceIn(0, 1)
        col.value = col.value.coerceIn(0, maxOf(0, count(row.value) - 1))
    }

    fun move(delta: Int) {
        col.value = (col.value + delta).coerceIn(0, maxOf(0, count(row.value) - 1))
    }

    fun moveV(delta: Int) {
        row.value = (row.value + delta).coerceIn(0, 1)
        col.value = col.value.coerceIn(0, maxOf(0, count(row.value) - 1))
    }

    fun focusGames() {
        row.value = 1
        col.value = 0
    }

    fun confirm() {
        if (row.value == 0) {
            visibleCategories().getOrNull(col.value)?.let { onCategory?.invoke(it) }
        } else {
            val g = games.value.getOrNull(col.value)
            if (g != null) onLaunch?.invoke(g) else onAddGame?.invoke()
        }
    }
}

/** Janela (Presentation) que desenha a Home na tela externa. */
class TvPresentation(
    private val activity: ComponentActivity,
    display: Display
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                if (com.gamepadlayout.app.games.ArcadeMirror.game != null) com.gamepadlayout.app.games.ArcadeMirrorCanvas()
                else TvHomeContent()
            }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        setContentView(view)
    }
}

object ExternalSession {
    private var presentation: TvPresentation? = null
    val active = MutableStateFlow(false)

    /** true se o usuário parou manualmente: evita religar sozinho enquanto a tela continuar conectada. */
    @Volatile var userStopped = false

    fun show(activity: ComponentActivity, display: Display): Boolean {
        dismiss()
        return try {
            val p = TvPresentation(activity, display)
            p.setOnDismissListener { active.value = false }
            p.show()
            presentation = p
            active.value = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun dismiss() {
        runCatching { presentation?.dismiss() }
        presentation = null
        active.value = false
    }
}
