package com.gamepadlayout.app.display

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.data.LibraryApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado compartilhado entre o celular (controle) e a Home exibida na TV. */
object TvNav {
    val settings = MutableStateFlow(AppSettings())
    val games = MutableStateFlow<List<LibraryApp>>(emptyList())
    val selected = MutableStateFlow(0)

    /** true enquanto a tela "Controlando a TV" está aberta no celular. */
    @Volatile var controlling = false
    var onLaunch: ((LibraryApp) -> Unit)? = null

    fun move(delta: Int) {
        val n = games.value.size
        if (n == 0) return
        selected.value = (selected.value + delta).coerceIn(0, n - 1)
    }

    fun confirm() {
        games.value.getOrNull(selected.value)?.let { onLaunch?.invoke(it) }
    }
}

/** Janela (Presentation) que desenha a Home em modo TV na tela externa. */
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
            setContent { TvHomeContent() }
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
    val activeFlow: StateFlow<Boolean> get() = active

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
