package com.gamepadlayout.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(val label: String, val icon: ImageVector) {
    GAMES("Jogos", Icons.Rounded.SportsEsports),
    ARCADE("Arcade", Icons.Rounded.VideogameAsset),
    FILES("Arquivos", Icons.Rounded.FolderOpen),
    DOWNLOADS("Downloads", Icons.Rounded.Download),
    BROWSER("Navegador", Icons.Rounded.Language),
    WIFI("Wi-Fi", Icons.Rounded.Wifi),
    BLUETOOTH("Bluetooth", Icons.Rounded.Bluetooth),
    EXTERNAL("Tela externa", Icons.Rounded.Cast),
    CONTROLLER("Controle", Icons.Rounded.Gamepad),
    APPS("Aplicativos", Icons.Rounded.Apps),
    SYSTEM("Sistema", Icons.Rounded.Info),
    SETTINGS("Configurações", Icons.Rounded.Settings)
}
