package com.gamepadlayout.app.games

import android.content.Context
import java.io.File

/** Migalhas de diagnostico: guarda a ultima etapa do jogo; se o app morrer de repente, a proxima abertura mostra onde foi. */
object Crumb {
    @Volatile private var f: File? = null
    fun init(c: Context) { f = File(c.applicationContext.filesDir, "crumb.txt") }
    fun mark(s: String) { try { f?.writeText(s) } catch (e: Throwable) { } }
    fun clear() { try { f?.delete() } catch (e: Throwable) { } }
    private fun diagFile() = f?.parentFile?.let { File(it, "diag.txt") }
    fun diagOn(): Boolean = try { diagFile()?.exists() == true } catch (e: Throwable) { false }
    fun setDiag(on: Boolean) { try { val d = diagFile() ?: return; if (on) d.writeText("1") else d.delete() } catch (e: Throwable) { } }
}
