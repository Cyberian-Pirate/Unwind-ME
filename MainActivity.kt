package com.unwind.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Pinned to Home the first time it runs (only apps that are installed show up).
private val DEFAULT_FAVS = listOf(
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "in.startv.hotstar",
    "in.startv.hotstar.dplus.tv",
    "com.jio.media.ondemand",
    "com.apple.atve.androidtv.appletv",
    "com.spotify.tv.android",
)

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pressing Home while Unwind is already open should bring you back to the Home page.
        addOnNewIntentListener { intent ->
            if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick++
        }
        setContent { LauncherApp(resumeTick, homeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
fun WallpaperLayer(wall: WallData?) {
    Crossfade(targetState = wall, animationSpec = tween(700), label = "wallpaper") { w ->
        if (w != null) {
            Image(
                w.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFD9A760), Color(0xFF8FA27A), Color(0xFF2F4A35), Color(0xFF101C12)),
                    ),
                ),
            )
        }
    }
}

@Composable
fun LauncherApp(tick: Int, homeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var saved by remember {
        mutableStateOf(prefs.getString("favs", null)?.split(",")?.filter { it.isNotBlank() })
    }
    var settings by remember { mutableStateOf(loadSettings(ctx)) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var popup by remember { mutableStateOf(Popup.None) }
    var focusKick by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var backdrops by remember { mutableStateOf<Backdrops?>(null) }
    var wall by remember { mutableStateOf<WallData?>(null) }
    var wallVersion by remember { mutableIntStateOf(0) }
    var wallStatus by remember { mutableStateOf("") }
    var gallery by remember { mutableStateOf<List<GalleryItem>?>(null) }
    var wifi by remember { mutableStateOf<WifiState?>(null) }
    var acct by remember { mutableStateOf<AcctState?>(null) }
    var inputs by remember { mutableStateOf(emptyList<InputItem>()) }
    // The welcome screen shows the very first time Unwind opens (and again from Preferences)
    var showIntro by remember { mutableStateOf(!prefs.getBoolean("introSeen", false)) }
    var greetPart by remember { mutableStateOf(DayPart.Morning) }
    var folderMap by remember { mutableStateOf(loadFolderMap(prefs)) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var moveApp by remember { mutableStateOf<AppEntry?>(null) }
    val moveFocus = remember { FocusRequester() }

    val wp = wall?.wp ?: WALLPAPERS[settings.wallpaper.coerceIn(0, WALLPAPERS.lastIndex)]
    val storagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val locOk = remember(permTick) { hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) }
    val accOk = remember(permTick) { hasPerm(ctx, Manifest.permission.GET_ACCOUNTS) }
    val storageOk = remember(permTick) { hasPerm(ctx, storagePerm) }
    val isHome = remember(tick, homeTick) { isDefaultHome(ctx) }
    val popupFocus = remember { FocusRequester() }

    // Focus targets for moving around with the D-pad
    val railReqs = remember { List(4) { FocusRequester() } }
    val pillReq = remember { FocusRequester() }
    val contentReq = remember { FocusRequester() }

    val locationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val accountLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val storageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    fun updateSettings(s: LauncherSettings) {
        settings = s
        saveSettings(ctx, s)
    }

    fun applyPicked(uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveCustomWallpaper(ctx, uri) }
            if (ok) {
                updateSettings(settings.copy(wallSource = WallSource.Custom))
                wallVersion++
                screen = Screen.Settings
            } else {
                Toast.makeText(ctx, "Couldn't open that picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val docLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) applyPicked(uri)
        }

    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            screen = Screen.Home
            popup = Popup.None
            focusKick++
        }
    }

    // Load whichever wallpaper is selected (stock, Bing daily, or your own picture)
    LaunchedEffect(settings.wallSource, settings.wallpaper, wallVersion) {
        val loaded = withContext(Dispatchers.IO) { loadWallpaper(ctx, settings) }
        if (loaded != null) wall = loaded
    }
    // Glass needs blurred copies of the wallpaper, cut to the screen's shape
    LaunchedEffect(wall, rootSize) {
        val w = wall
        if (w != null && rootSize.width > 0 && rootSize.height > 0) {
            val aspect = rootSize.width.toFloat() / rootSize.height
            backdrops = withContext(Dispatchers.Default) { makeBackdrops(w.bitmap, aspect) }
        }
    }
    // Bing image of the day: check when opened and then every half hour while the launcher is up
    LaunchedEffect(settings.wallSource, tick) {
        if (settings.wallSource == WallSource.Bing) {
            if (wallStatus.isBlank()) wallStatus = "Checking for today's wallpaper..."
            while (true) {
                val r = withContext(Dispatchers.IO) { refreshBing(ctx) }
                wallStatus = when {
                    !r.ok -> r.message
                    r.message.isBlank() -> "Today's wallpaper"
                    else -> "Today: ${r.message}"
                }
                if (r.changed) wallVersion++
                delay(if (r.ok) 30 * 60 * 1000L else 2 * 60 * 1000L)
            }
        } else {
            wallStatus = ""
        }
    }
    LaunchedEffect(screen, storageOk) {
        if (screen == Screen.Gallery && storageOk) {
            gallery = null
            gallery = withContext(Dispatchers.IO) { queryImages(ctx) }
        }
    }
    LaunchedEffect(permTick) { acct = withContext(Dispatchers.IO) { readAccount(ctx) } }
    LaunchedEffect(popup, permTick) {
        when (popup) {
            Popup.Wifi -> {
                while (true) {
                    wifi = withContext(Dispatchers.IO) { readWifi(ctx) }
                    delay(3000)
                }
            }
            Popup.Inputs -> {
                inputs = withContext(Dispatchers.IO) { readInputs(ctx) }
            }
            Popup.Account -> {
                acct = withContext(Dispatchers.IO) { readAccount(ctx) }
            }
            Popup.None -> {}
        }
    }
    LaunchedEffect(popup, wifi?.nets?.size, inputs.size, acct?.emails?.size, locOk, accOk) {
        if (popup != Popup.None) {
            delay(150)
            runCatching { popupFocus.requestFocus() }
        }
    }

    val installed = apps.map { it.pkg }.toSet()
    val favPkgs: List<String> = saved ?: DEFAULT_FAVS.filter { it in installed }
    val favApps = favPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

    fun toggleFav(pkg: String) {
        val next = if (pkg in favPkgs) favPkgs - pkg else favPkgs + pkg
        saved = next
        prefs.edit().putString("favs", next.joinToString(",")).apply()
    }

    fun requestLocation() {
        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun requestAccounts() {
        accountLauncher.launch(arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.READ_CONTACTS))
    }

    fun openGallery() {
        screen = Screen.Gallery
        if (!storageOk) storageLauncher.launch(storagePerm)
    }

    fun browseFiles() {
        try {
            docLauncher.launch(arrayOf("image/*"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "This TV has no file browser. Use the picture list instead.", Toast.LENGTH_LONG).show()
        }
    }

    fun openPopup(p: Popup) {
        popup = p
        if (p == Popup.Wifi && !locOk) requestLocation()
        if (p == Popup.Account && !accOk) requestAccounts()
    }

    fun closePopup() {
        popup = Popup.None
        focusKick++
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Gallery || screen == Screen.Greeting) Screen.Settings else Screen.Home
    }
    // Inside a folder, Back goes to the folder list first
    BackHandler(enabled = screen == Screen.Apps && openFolder != null && settings.appsView == AppsView.Folders) {
        openFolder = null
        focusKick++
    }
    BackHandler(enabled = moveApp != null) { moveApp = null }
    // When Unwind is the Home app, Back on the Home page does nothing (it shouldn't close the launcher)
    BackHandler(enabled = isHome && screen == Screen.Home && popup == Popup.None) { }
    BackHandler(enabled = popup != Popup.None) { closePopup() }

    LaunchedEffect(screen) {
        if (screen != Screen.Apps) openFolder = null
    }

    fun moveTo(app: AppEntry, folder: String?) {
        val next = if (folder == null || folder == app.folder) folderMap - app.pkg else folderMap + (app.pkg to folder)
        folderMap = next
        saveFolderMap(prefs, next)
        moveApp = null
    }

    val ui = UiCtx(settings, wp, backdrops, rootSize)
    val railSel = when (screen) {
        Screen.Home -> 0
        Screen.Search -> 1
        Screen.Apps, Screen.Edit -> 2
        Screen.Settings, Screen.Gallery, Screen.Greeting -> 3
    }
    val nav = Nav(contentReq, pillReq, railReqs[railSel])
    val title = when (screen) {
        Screen.Home -> "Home"
        Screen.Search -> "Search"
        Screen.Apps -> "Apps"
        Screen.Edit -> "Favorites"
        Screen.Settings, Screen.Gallery, Screen.Greeting -> "Preferences"
    }
    val scrimA = (0.45f + 0.35f * wp.luma).coerceIn(0.45f, 0.85f)

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
        CompositionLocalProvider(LocalUi provides ui) {
            // While a popup is open, nothing behind it can take focus.
            Box(Modifier.fillMaxSize().focusProperties { canFocus = popup == Popup.None && !showIntro && moveApp == null }) {
                WallpaperLayer(wall)
                // Soft scrims keep text readable on any wallpaper (stronger on bright pictures)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to Color.Black.copy(alpha = scrimA), 0.62f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = scrimA * 0.9f)),
                    ),
                )

                when (screen) {
                    Screen.Home -> HomeContent(
                        favApps = favApps,
                        focusKick = focusKick,
                        nav = nav,
                        onLaunch = { launchApp(ctx, it) },
                        onAdd = { screen = Screen.Edit },
                    )
                    Screen.Apps -> if (settings.appsView == AppsView.Folders) {
                        AppFolders(
                            apps = apps,
                            folderMap = folderMap,
                            openFolder = openFolder,
                            focusKick = focusKick,
                            nav = nav,
                            onOpenFolder = { openFolder = it },
                            onLaunch = { launchApp(ctx, it) },
                            onMove = { moveApp = it },
                        )
                    } else {
                        AppGrid(apps, false, favPkgs.toSet(), focusKick, nav) { launchApp(ctx, it) }
                    }
                    Screen.Edit -> AppGrid(apps, true, favPkgs.toSet(), focusKick, nav) { toggleFav(it.pkg) }
                    Screen.Search -> SearchScreen(apps, focusKick, nav) { launchApp(ctx, it) }
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        focusKick = focusKick,
                        nav = nav,
                        wallVersion = wallVersion,
                        wallStatus = wallStatus,
                        onChange = { updateSettings(it) },
                        onPickCustom = {
                            if (customFile(ctx).exists() && settings.wallSource != WallSource.Custom) {
                                updateSettings(settings.copy(wallSource = WallSource.Custom))
                            } else {
                                openGallery()
                            }
                        },
                        onOpenHomeSettings = { openHomeSettings(ctx) },
                        onEditGreeting = { greetPart = it; screen = Screen.Greeting },
                        onReplayIntro = { showIntro = true },
                    )
                    Screen.Greeting -> GreetingEditor(
                        part = greetPart,
                        initial = settings.greetings[greetPart] ?: DEFAULT_GREETINGS.getValue(greetPart),
                        nav = nav,
                        onSave = { g ->
                            updateSettings(settings.copy(greetings = settings.greetings + (greetPart to g)))
                            screen = Screen.Settings
                        },
                        onCancel = { screen = Screen.Settings },
                    )
                    Screen.Gallery -> GalleryScreen(
                        items = gallery,
                        hasPerm = storageOk,
                        nav = nav,
                        onGrant = { storageLauncher.launch(storagePerm) },
                        onBrowse = { browseFiles() },
                        onPick = { applyPicked(it.uri) },
                    )
                }

                // Header: always says which screen you're on
                Crossfade(
                    targetState = title,
                    animationSpec = tween(220),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 33.dp, top = 26.dp),
                    label = "header",
                ) { t ->
                    Text(
                        t,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.3.sp,
                        style = TextStyle(shadow = Shadow(Color(0x66000000), blurRadius = 12f)),
                    )
                }
                TopPill(
                    acct = acct,
                    nav = nav,
                    onSearch = { screen = Screen.Search },
                    onSystem = { openSystemSettings(ctx) },
                    onWifi = { openPopup(Popup.Wifi) },
                    onInputs = { openPopup(Popup.Inputs) },
                    onAccount = { openPopup(Popup.Account) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 24.dp),
                )
                Rail(
                    selected = railSel,
                    onClick = { i ->
                        when (i) {
                            0 -> screen = Screen.Home
                            1 -> screen = Screen.Search
                            2 -> screen = Screen.Apps
                            3 -> screen = Screen.Settings
                            else -> {}
                        }
                    },
                    reqs = railReqs,
                    toContent = contentReq,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 33.dp, top = 24.dp),
                )
            }

            if (popup != Popup.None) {
                PopupLayer(popup, onDismiss = { closePopup() }) {
                    when (popup) {
                        Popup.Wifi -> WifiPopup(
                            state = wifi,
                            locOk = locOk,
                            focus = popupFocus,
                            onGrant = { requestLocation() },
                            onSettings = { openWifiSettings(ctx) },
                        )
                        Popup.Inputs -> InputsPopup(
                            items = inputs,
                            focus = popupFocus,
                            onPick = { switchInput(ctx, it); closePopup() },
                            onSettings = { openSystemSettings(ctx) },
                        )
                        Popup.Account -> AccountPopup(
                            acct = acct,
                            accOk = accOk,
                            focus = popupFocus,
                            onGrant = { requestAccounts() },
                            onManage = { openAccountSettings(ctx) },
                        )
                        Popup.None -> {}
                    }
                }
            }

            moveApp?.let { a ->
                MoveAppDialog(
                    app = a,
                    currentFolder = folderMap[a.pkg] ?: a.folder,
                    isManual = folderMap.containsKey(a.pkg),
                    focus = moveFocus,
                    onPick = { moveTo(a, it) },
                    onAuto = { moveTo(a, null) },
                    onDismiss = { moveApp = null },
                )
            }

            AnimatedVisibility(visible = showIntro, enter = fadeIn(tween(400)), exit = fadeOut(tween(600))) {
                IntroScreen(
                    onDone = {
                        prefs.edit().putBoolean("introSeen", true).apply()
                        showIntro = false
                        focusKick++
                    },
                )
            }
        }
    }
}
