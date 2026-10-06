package com.gamepadlayout.app.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamepadlayout.app.data.AppSettings
import com.gamepadlayout.app.data.SettingsKeys as K
import com.gamepadlayout.app.data.SettingsViewModel
import com.gamepadlayout.app.input.ControllerMapping
import com.gamepadlayout.app.input.GamepadInput
import com.gamepadlayout.app.input.PadAction
import com.gamepadlayout.app.input.PadButton
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.settings.ActionRow
import com.gamepadlayout.app.ui.settings.Header
import com.gamepadlayout.app.ui.settings.InfoCard
import com.gamepadlayout.app.ui.settings.SettingRow

@Composable
fun ControllerScreen(
    controllers: List<ControllerInfo>,
    settings: AppSettings,
    vm: SettingsViewModel,
    onOpenBluetooth: () -> Unit,
    onBack: () -> Unit
) {
    val map = ControllerMapping.parse(settings.mapping)
    val last by GamepadInput.lastButton.collectAsStateWithLifecycle()
    val actions = PadAction.entries

    fun change(b: PadButton, dir: Int) {
        val cur = map[b] ?: PadAction.NONE
        val next = actions[(actions.indexOf(cur) + dir + actions.size) % actions.size]
        vm.set(K.MAPPING, ControllerMapping.serialize(map + (b to next)))
    }

    ScreenScaffold("Controle", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Header("Controles conectados") }
            if (controllers.isEmpty()) {
                item {
                    InfoCard(
                        "Nenhum controle conectado",
                        "Pareie um controle Bluetooth (ou USB) nas configurações do Android. " +
                            "Ele aparece aqui automaticamente."
                    )
                }
            } else {
                controllers.forEach { c ->
                    item {
                        val bat = when {
                            c.battery == null -> "Bateria: indisponível neste controle/Android"
                            c.charging -> "Bateria: ${c.battery}% (carregando)"
                            else -> "Bateria: ${c.battery}%"
                        }
                        InfoCard(c.name, "Estado: conectado\n$bat")
                    }
                }
            }
            item { ActionRow("Abrir Bluetooth do sistema (parear controle)", onOpenBluetooth) }

            item { Header("Teste de botões") }
            item {
                val txt = last?.let { "Último botão: ${it.name} → ${GamepadInput.actionFor(it).label}" }
                    ?: "Aperte qualquer botão do controle para testar"
                InfoCard("Teste", txt)
            }

            item { Header("Mapeamento de botões (←/→ muda a ação)") }
            PadButton.entries.forEach { b ->
                item {
                    SettingRow(
                        title = "Botão ${b.name}",
                        value = (map[b] ?: PadAction.NONE).label,
                        onPrev = { change(b, -1) },
                        onNext = { change(b, 1) }
                    )
                }
            }
            item { ActionRow("Restaurar mapeamento padrão") { vm.set(K.MAPPING, "") } }
            item {
                InfoCard(
                    "Observações",
                    "O D-pad e o analógico esquerdo sempre movem a seleção. Dentro de jogos, o controle " +
                        "funciona normalmente: o Gamepad Layout não interfere nos jogos."
                )
            }
        }
    }
}
