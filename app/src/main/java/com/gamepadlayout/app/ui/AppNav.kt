package com.gamepadlayout.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamepadlayout.app.console.ConsoleMode
import com.gamepadlayout.app.console.findActivity
import com.gamepadlayout.app.controller.ControllerInfo
import com.gamepadlayout.app.controller.ControllerScreen
import com.gamepadlayout.app.data.ControllerViewModel
import com.gamepadlayout.app.data.ExternalViewModel
import com.gamepadlayout.app.data.GameTag
import com.gamepadlayout.app.data.LibraryApp
import com.gamepadlayout.app.data.LibraryViewModel
import com.gamepadlayout.app.data.SettingsKeys as K
import com.gamepadlayout.app.data.SettingsViewModel
import com.gamepadlayout.app.display.ExternalDisplayInfo
import com.gamepadlayout.app.display.ExternalSession
import com.gamepadlayout.app.display.TvNav
import com.gamepadlayout.app.games.ArcadeGameScreen
import com.gamepadlayout.app.games.ArcadeScreen
import com.gamepadlayout.app.input.ControllerMapping
import com.gamepadlayout.app.input.GamepadInput
import com.gamepadlayout.app.input.PadAction
import com.gamepadlayout.app.system.SystemIntents
import com.gamepadlayout.app.ui.boot.BootScreen
import com.gamepadlayout.app.ui.browser.BrowserScreen
import com.gamepadlayout.app.ui.components.AppIcon
import com.gamepadlayout.app.ui.components.ConsoleSurface
import com.gamepadlayout.app.ui.components.ScreenScaffold
import com.gamepadlayout.app.ui.external.ExternalScreen
import com.gamepadlayout.app.ui.home.Category
import com.gamepadlayout.app.ui.home.HomeScreen
import com.gamepadlayout.app.ui.library.AppPickerScreen
import com.gamepadlayout.app.ui.library.AppsScreen
import com.gamepadlayout.app.ui.library.LibraryScreen
import com.gamepadlayout.app.ui.settings.SettingsScreen
import com.gamepadlayout.app.ui.theme.GamepadLayoutTheme
import com.gamepadlayout.app.ui.theme.LocalConsoleStyle
import kotlinx.coroutines.delay
import kotlin.math.max

sealed interface Screen {
    data object Boot : Screen
    data object Home : Screen
    data object Library : Screen
    data object AppPicker : Screen
    data object Apps : Screen
    data object Browser : Screen
    data object Settings : Screen
    data object Controller : Screen
    data object External : Screen
    data object TvControl : Screen
    data object Arcade : Screen
    data class ArcadeGame(val id: String) : Screen
    data class Soon(val title: String) : Screen
    data class Files(val title: String, val path: String) : Screen
    data object SystemInfo : Screen
    data object Unlock : Screen
    data class Connect(val bluetooth: Boolean) : Screen
}

@Composable
fun GamepadLayoutRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val libraryVm: LibraryViewModel = viewModel()
    val controllerVm: ControllerViewModel = viewModel()
    val externalVm: ExternalViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val library by libraryVm.library.collectAsStateWithLifecycle()
    val allApps by libraryVm.allApps.collectAsStateWithLifecycle()
    val tags by libraryVm.tags.collectAsStateWithLifecycle()
    val controllers by controllerVm.controllers.collectAsStateWithLifecycle()
    val displays by externalVm.displays.collectAsStateWithLifecycle()
    val castStatus by externalVm.cast.collectAsStateWithLifecycle()
    val tvActive by ExternalSession.active.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    GamepadLayoutTheme(settings) {
        val st = LocalConsoleStyle.current
        val stack = remember { mutableStateListOf<Screen>(Screen.Boot) }
        val current = stack.last()
        var optionsFor by remember { mutableStateOf<LibraryApp?>(null) }
        var quickMenu by remember { mutableStateOf(false) }
        var gameTarget by remember { mutableStateOf<LibraryApp?>(null) }
        val overlayOpen = quickMenu || optionsFor != null || gameTarget != null

        fun push(s: Screen) { stack.add(s) }
        fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

        fun startGame(app: LibraryApp) {
            libraryVm.markPlayed(app.packageName)
            if (!libraryVm.launch(app.packageName)) {
                Toast.makeText(ctx, "Não foi possível abrir ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }
        /** Modo Jogo: mostra o preparo antes de abrir; desligado = abre direto. */
        fun launchGame(app: LibraryApp) {
            if (settings.gameMode) gameTarget = app else startGame(app)
        }
        fun startTv(d: ExternalDisplayInfo) {
            val act = activity ?: return
            val disp = externalVm.display(d.id) ?: return
            ExternalSession.userStopped = false
            if (ExternalSession.show(act, disp)) {
                if (stack.last() !is Screen.TvControl) push(Screen.TvControl)
            } else {
                Toast.makeText(ctx, "Não foi possível usar essa tela externa", Toast.LENGTH_SHORT).show()
            }
        }
        fun stopTv() {
            ExternalSession.userStopped = true
            ExternalSession.dismiss()
            stack.removeAll { it is Screen.TvControl }
            if (stack.isEmpty()) stack.add(Screen.Home)
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { libraryVm.refresh() }
        // Home é a raiz (comportamento de launcher): voltar nela não fecha o app.
        BackHandler(enabled = current !is Screen.Boot) { pop() }

        // Modo Console + orientação + tela acesa (só quando útil)
        LaunchedEffect(settings.consoleMode, castStatus.running, controllers.isNotEmpty(), tvActive) {
            activity?.let {
                ConsoleMode.update(
                    it,
                    enabled = settings.consoleMode,
                    keepAwake = controllers.isNotEmpty() || castStatus.running || tvActive
                )
            }
        }
        LaunchedEffect(settings.mapping) { GamepadInput.mapping = ControllerMapping.parse(settings.mapping) }
        LaunchedEffect(settings) { TvNav.settings.value = settings }
        LaunchedEffect(library) {
            TvNav.games.value = library
            TvNav.clamp()
        }
        LaunchedEffect(controllers) { TvNav.controllers.value = controllers }
        SideEffect {
            GamepadInput.overlayOpen = overlayOpen
            GamepadInput.controllerConnected = controllers.isNotEmpty()
            TvNav.onLaunch = { startGame(it) }
        }
        LaunchedEffect(Unit) {
            GamepadInput.events.collect { a ->
                if (a == PadAction.MENU && stack.last() !is Screen.Boot && stack.last() !is Screen.ArcadeGame) quickMenu = !quickMenu
            }
        }
        // Botão Home do Android (quando o app é a tela inicial) volta para a Home do app
        LaunchedEffect(Unit) {
            GamepadInput.homeRequests.collect {
                if (stack.last() !is Screen.Boot) {
                    quickMenu = false
                    optionsFor = null
                    gameTarget = null
                    stack.clear()
                    stack.add(Screen.Home)
                }
            }
        }
        // Tela externa: mostra a Home em modo TV quando conectar (se o usuário não parou manualmente)
        LaunchedEffect(displays, settings.autoTv, stack.lastOrNull()) {
            if (displays.isEmpty()) {
                ExternalSession.userStopped = false
                if (ExternalSession.active.value) ExternalSession.dismiss()
                stack.removeAll { it is Screen.TvControl }
                if (stack.isEmpty()) stack.add(Screen.Home)
            } else if (settings.autoTv && !ExternalSession.active.value &&
                !ExternalSession.userStopped && stack.last() == Screen.Home
            ) {
                startTv(displays.first())
            }
        }

        fun open(c: Category) {
            when (c) {
                Category.GAMES -> push(Screen.Library)
                Category.BROWSER -> push(Screen.Browser)
                Category.APPS -> push(Screen.Apps)
                Category.SETTINGS -> push(Screen.Settings)
                Category.EXTERNAL -> push(Screen.External)
                Category.CONTROLLER -> push(Screen.Controller)
                Category.ARCADE -> push(Screen.Arcade)
                Category.FILES -> push(Screen.Files("Arquivos", android.os.Environment.getExternalStorageDirectory().path))
                Category.SYSTEM -> push(Screen.SystemInfo)
                Category.DOWNLOADS -> push(Screen.Files("Downloads", android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).path))
                Category.WIFI -> push(Screen.Connect(false))
                Category.BLUETOOTH -> push(Screen.Connect(true))
            }
        }

        // Home da TV: categorias abrem a tela no celular; "Jogos" desce para a fileira de jogos
        SideEffect {
            TvNav.onCategory = { c -> if (c == Category.GAMES) TvNav.focusGames() else open(c) }
            TvNav.onAddGame = { push(Screen.AppPicker) }
        }

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().focusProperties { canFocus = !overlayOpen }) {
                Crossfade(targetState = current, animationSpec = tween(st.dur(220)), label = "screen") { screen ->
                    when (screen) {
                        Screen.Boot -> BootScreen {
                            stack.clear()
                            stack.add(Screen.Home)
                        }
                        Screen.Home -> HomeScreen(
                            userName = settings.userName,
                            controllers = controllers,
                            hiddenCategories = settings.hiddenCategories,
                            games = library,
                            onCategory = { open(it) },
                            onLaunch = { launchGame(it) },
                            onAddGame = { push(Screen.AppPicker) },
                            onGameOptions = { optionsFor = it },
                            onProfile = { push(Screen.Settings) }
                        )
                        Screen.Library -> LibraryScreen(
                            games = library,
                            tags = tags,
                            onBack = { pop() },
                            onLaunch = { launchGame(it) },
                            onAdd = { push(Screen.AppPicker) },
                            onOptions = { optionsFor = it }
                        )
                        Screen.AppPicker -> AppPickerScreen(
                            allApps = allApps,
                            library = library,
                            onToggle = { app, inLibrary ->
                                if (inLibrary) libraryVm.remove(app.packageName) else libraryVm.add(app.packageName)
                            },
                            onBack = { pop() }
                        )
                        Screen.Apps -> AppsScreen(allApps, onBack = { pop() }, onLaunch = { libraryVm.launch(it.packageName) })
                        Screen.Browser -> BrowserScreen(settings, hasController = controllers.isNotEmpty(), onExit = { pop() })
                        Screen.Settings -> SettingsScreen(
                            settings, settingsVm,
                            onOpenController = { push(Screen.Controller) },
                            onOpenExternal = { push(Screen.External) },
                            onOpenUnlock = { push(Screen.Unlock) },
                            onBack = { pop() }
                        )
                        Screen.Controller -> ControllerScreen(
                            controllers, settings, settingsVm,
                            onOpenBluetooth = { SystemIntents.bluetooth(ctx) },
                            onBack = { pop() }
                        )
                        Screen.External -> ExternalScreen(
                            settings, settingsVm, externalVm, tvActive,
                            onShowTv = { startTv(it) },
                            onStopTv = { stopTv() },
                            onBack = { pop() }
                        )
                        Screen.TvControl -> TvControlScreen(onStop = { stopTv() })
                        Screen.Arcade -> ArcadeScreen(onBack = { pop() }, onOpen = { push(Screen.ArcadeGame(it)) })
                        is Screen.ArcadeGame -> ArcadeGameScreen(screen.id, onExit = { pop() })
                        is Screen.Files -> com.gamepadlayout.app.ui.modules.FilesScreen(screen.title, screen.path, onBack = { pop() })
                        Screen.Unlock -> com.gamepadlayout.app.unlock.UnlockScreen(onBack = { pop() })
                        Screen.SystemInfo -> com.gamepadlayout.app.ui.modules.SystemScreen(onBack = { pop() })
                        is Screen.Connect -> com.gamepadlayout.app.ui.modules.ConnectScreen(screen.bluetooth, onBack = { pop() })
                        is Screen.Soon -> ScreenScaffold(screen.title, onBack = { pop() }) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Em breve — este módulo entra numa próxima fase.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
            optionsFor?.let { app ->
                QuickMenuOverlay(
                    title = app.label,
                    items = listOf(
                        "Marcar: com suporte a controle" to { libraryVm.setTag(app.packageName, GameTag.NATIVE) },
                        "Marcar: emulador" to { libraryVm.setTag(app.packageName, GameTag.EMULATOR) },
                        "Marcar: precisa de mapeador (beta)" to { libraryVm.setTag(app.packageName, GameTag.MAPPER) },
                        "Remover da biblioteca" to { libraryVm.remove(app.packageName) }
                    ),
                    onDismiss = { optionsFor = null }
                )
            }
            gameTarget?.let { app ->
                GameModeOverlay(
                    app = app,
                    tag = tags[app.packageName] ?: GameTag.UNKNOWN,
                    controllers = controllers,
                    tvActive = tvActive,
                    casting = castStatus.running,
                    onStart = { gameTarget = null; startGame(app) },
                    onCancel = { gameTarget = null }
                )
            }
            if (quickMenu) {
                QuickMenuOverlay(
                    items = listOf(
                        "Modo Console: ${if (settings.consoleMode) "Ligado" else "Desligado"}" to
                            { settingsVm.set(K.CONSOLE_MODE, !settings.consoleMode) },
                        "Tela externa / transmitir" to { push(Screen.External) },
                        "Controle" to { push(Screen.Controller) },
                        "Configurações" to { push(Screen.Settings) },
                        "Pausar abrir ao desbloquear (1 h)" to {
                            com.gamepadlayout.app.unlock.UnlockPrefs.pauseFor(ctx, 60)
                            Toast.makeText(ctx, "Pausado por 1 hora", Toast.LENGTH_SHORT).show()
                        },
                        "Sair do app (voltar ao Android)" to { ctx.findActivity()?.moveTaskToBack(true); Unit },
                        "Ir para o início" to {
                            stack.clear()
                            stack.add(Screen.Home)
                            Unit
                        }
                    ),
                    onDismiss = { quickMenu = false }
                )
            }
        }
    }
}

/** Celular como controle: a Home está na TV; D-pad/A no controle (ou os botões na tela) navegam nela. */
@Composable
private fun TvControlScreen(onStop: () -> Unit) {
    DisposableEffect(Unit) {
        TvNav.controlling = true
        onDispose { TvNav.controlling = false }
    }
    BackHandler { onStop() }
    val row by TvNav.row.collectAsStateWithLifecycle()
    val col by TvNav.col.collectAsStateWithLifecycle()
    val games by TvNav.games.collectAsStateWithLifecycle()
    val tvSettings by TvNav.settings.collectAsStateWithLifecycle()
    val cats = Category.entries.filter { it.name !in tvSettings.hiddenCategories }
    val label = if (row == 0) cats.getOrNull(col)?.label ?: "" else games.getOrNull(col)?.label ?: "Adicionar jogo"

    ScreenScaffold("Controlando a TV", onBack = onStop) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("A Home do Gamepad Layout está na tela externa.", fontSize = 14.sp)
            Text(
                "D-pad e A no controle, ou os botões abaixo. B volta ao celular.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ConsoleSurface(onClick = { TvNav.move(-1) }, modifier = Modifier.size(46.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.ChevronLeft, null, Modifier.align(Alignment.Center).size(26.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsoleSurface(onClick = { TvNav.moveV(-1) }, modifier = Modifier.size(46.dp), shape = CircleShape) {
                        Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.align(Alignment.Center).size(26.dp))
                    }
                    ConsoleSurface(onClick = { TvNav.confirm() }, modifier = Modifier.width(110.dp).height(46.dp), shape = RoundedCornerShape(23.dp)) {
                        Text("OK", Modifier.align(Alignment.Center), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    ConsoleSurface(onClick = { TvNav.moveV(1) }, modifier = Modifier.size(46.dp), shape = CircleShape) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.align(Alignment.Center).size(26.dp))
                    }
                }
                ConsoleSurface(onClick = { TvNav.move(1) }, modifier = Modifier.size(46.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.align(Alignment.Center).size(26.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            ConsoleSurface(onClick = onStop, modifier = Modifier.width(240.dp).height(40.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Parar TV e voltar ao celular", Modifier.align(Alignment.Center), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GameModeOverlay(
    app: LibraryApp,
    tag: GameTag,
    controllers: List<ControllerInfo>,
    tvActive: Boolean,
    casting: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val st = LocalConsoleStyle.current
    val fr = remember { FocusRequester() }
    val hasPad = controllers.isNotEmpty()
    BackHandler { onCancel() }
    LaunchedEffect(app.packageName, hasPad) {
        delay(80)
        runCatching { fr.requestFocus() }
        // Com controle conectado abre sozinho; sem controle espera a sua decisão.
        if (hasPad) {
            delay(1600)
            onStart()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1245))
                .border(1.dp, st.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Modo Jogo", fontSize = 12.sp, color = st.accentSoft)
                    Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasPad) "Controle: ${controllers.first().name} (conectado)"
                else "Nenhum controle conectado. Conecte um ou abra mesmo assim.",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                "Tela externa: " + when {
                    casting -> "transmitindo pelo Wi-Fi"
                    tvActive -> "Home na TV"
                    else -> "desligada"
                },
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                when (tag) {
                    GameTag.NATIVE -> "Suporte a controle: sim (classificado por você ou pelo catálogo)"
                    GameTag.EMULATOR -> "Emulador: o controle funciona direto dentro dele"
                    GameTag.MAPPER -> "Jogo só de toque: o mapeador de botões chega numa próxima versão"
                    GameTag.UNKNOWN -> "Suporte a controle: não classificado (teste e marque nas opções do jogo)"
                },
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                "Os jogos funcionam normalmente com o controle; o Gamepad Layout não interfere neles.",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsoleSurface(onClick = onCancel, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Cancelar", Modifier.align(Alignment.Center), fontSize = 14.sp)
                }
                ConsoleSurface(
                    onClick = onStart,
                    modifier = Modifier.weight(1f).height(44.dp),
                    focusRequester = fr,
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Abrir agora", Modifier.align(Alignment.Center), fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun QuickMenuOverlay(
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    title: String = "Menu rápido"
) {
    val st = LocalConsoleStyle.current
    val fr = remember { FocusRequester() }
    BackHandler { onDismiss() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1245))
                .border(1.dp, st.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            items.forEachIndexed { i, (label, action) ->
                ConsoleSurface(
                    onClick = { action(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    focusRequester = if (i == 0) fr else null,
                    focusScale = 1.03f,
                    shape = RoundedCornerShape(14.dp)
                ) { Text(label, Modifier.align(Alignment.CenterStart).padding(start = 14.dp), fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun ConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val st = LocalConsoleStyle.current
    val fr = remember { FocusRequester() }
    BackHandler { onDismiss() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1245))
                .border(1.dp, st.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsoleSurface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    focusRequester = fr,
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Cancelar", Modifier.align(Alignment.Center), fontSize = 14.sp) }
                ConsoleSurface(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(confirmLabel, Modifier.align(Alignment.Center), fontSize = 14.sp) }
            }
        }
    }
}
