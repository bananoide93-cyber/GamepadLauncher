package com.gamepadlayout.app.audio

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast

data class AudioOutput(val name: String, val kind: String, val priority: Int)

/**
 * O Android NÃO permite que apps comuns forcem a saída de áudio nem informa qual está ativa.
 * Aqui listamos as saídas disponíveis (AudioManager.getDevices), indicamos a mais provável
 * pela prioridade padrão do Android e abrimos o seletor oficial do sistema.
 */
class AudioRouting(private val context: Context) {

    fun outputs(): List<AudioOutput> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { d ->
                val (kind, prio) = when (d.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fones com fio" to 4
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB" to 4
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth" to 3
                    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI" to 2
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante do celular" to 1
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_TELEPHONY -> return@mapNotNull null
                    else -> return@mapNotNull null
                }
                val name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: kind
                AudioOutput(name, kind, prio)
            }
            .distinctBy { it.kind + it.name }
            .sortedByDescending { it.priority }
    }

    fun openSystemSwitcher() {
        val intents = listOf(
            Intent(Settings.Panel.ACTION_VOLUME),
            Intent(Settings.ACTION_SOUND_SETTINGS)
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) { }
        }
        Toast.makeText(context, "Não foi possível abrir as configurações de som", Toast.LENGTH_SHORT).show()
    }
}
