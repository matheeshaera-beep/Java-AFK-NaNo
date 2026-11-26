package dev.mstheesha.afk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        AppGraph.uiVisible = true
    }

    override fun onStop() {
        AppGraph.uiVisible = false
        if (!AppGraph.hasActiveSessions()) {
            stopService(Intent(this, AfkService::class.java))
        }
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            AppGraph.trimMemory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        AppGraph.setContext(applicationContext)
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeMode by Prefs.themeModeFlow.collectAsState()
            val dynamicColor by Prefs.dynamicColorFlow.collectAsState()
            val (colorScheme, isDark) = resolveColorScheme(this, themeMode, systemDark, dynamicColor)

            // Sync status bar and navigation bar icon contrast with the active in-app theme.
            DisposableEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose {}
            }

            AfkExpressiveTheme(targetScheme = colorScheme) {
                AfkApp(isDark = isDark)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AfkApp(isDark: Boolean) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val servers by dao.all().collectAsState(initial = emptyList())
    val activeCount by AppGraph.activeCount.collectAsState(initial = 0)
    val reduced = LocalReducedMotion.current

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showAccountsSheet by remember { mutableStateOf(false) }
    var showAppSettingsSheet by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun openServer(id: Long) {
        backProgress = 0f
        selectedId = id
        scope.launch { drawerState.close() }
    }

    // Predictive back gesture from SessionScreen -> ServerListScreen
    PredictiveBackHandler(enabled = selectedId != null) { progressFlow ->
        try {
            progressFlow.collect { event ->
                backProgress = event.progress
            }
            backProgress = 0f
            selectedId = null
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                servers = servers,
                activeCount = activeCount,
                selectedId = selectedId,
                drawerOpen = drawerState.isOpen,
                onSelectServer = { id -> openServer(id) },
                onOpenAccounts = {
                    scope.launch { drawerState.close() }
                    showAccountsSheet = true
                },
                onOpenAppSettings = {
                    scope.launch { drawerState.close() }
                    showAppSettingsSheet = true
                },
            )
        },
    ) {
        val scaffoldModifier = if (selectedId == null) {
            Modifier.nestedScroll(listScrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        }

        Scaffold(
            modifier = scaffoldModifier,
            topBar = {
                if (selectedId == null) {
                    LargeTopAppBar(
                        scrollBehavior = listScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        title = {
                            Text(
                                "Java AFK",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        actions = {
                            if (servers.isNotEmpty()) {
                                if (activeCount > 0) {
                                    TextButton(onClick = { AppGraph.stopAllSessions() }) {
                                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Stop all")
                                    }
                                } else {
                                    TextButton(onClick = {
                                        var started = 0
                                        for (s in servers) {
                                            if (AppGraph.startServer(ctx, s)) started++ else break
                                        }
                                        if (started < servers.size) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Reached max concurrent bots limit ($started started)")
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Start all")
                                    }
                                }
                            }
                        },
                    )
                } else {
                    val currentName = servers.firstOrNull { it.id == selectedId }?.name ?: "Session"
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { selectedId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            Text(
                                currentName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = selectedId == null,
                    enter = if (reduced) fadeIn(tween(120)) else (scaleIn(initialScale = 0.8f) + fadeIn()),
                    exit = if (reduced) fadeOut(tween(100)) else (scaleOut(targetScale = 0.8f) + fadeOut()),
                ) {
                    FloatingActionButton(
                        onClick = { showAddSheet = true },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add server")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val spatialSlide = AfkMotion.defaultSpatial<IntOffset>()
            val effectsFade = AfkMotion.defaultEffects<Float>()

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                AnimatedContent(
                    targetState = selectedId,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                        } else if (targetState != null && initialState == null) {
                            // Forward: Server List -> Session Screen
                            (slideInHorizontally(spatialSlide) { it / 4 } + fadeIn(effectsFade)) togetherWith
                                (slideOutHorizontally(spatialSlide) { -it / 6 } + fadeOut(effectsFade))
                        } else {
                            // Back: Session Screen -> Server List
                            (slideInHorizontally(spatialSlide) { -it / 6 } + fadeIn(effectsFade)) togetherWith
                                (slideOutHorizontally(spatialSlide) { it / 4 } + fadeOut(effectsFade))
                        }
                    },
                    label = "root_nav",
                ) { id ->
                    if (id == null) {
                        ServerListScreen(
                            servers = servers,
                            snackbarHostState = snackbarHostState,
                            showAddSheet = showAddSheet,
                            onDismissAddSheet = { showAddSheet = false },
                            onSelect = { serverId -> openServer(serverId) },
                        )
                    } else {
                        val previewScale = 1f - (backProgress * 0.06f)
                        val previewShift = backProgress * 48f
                        SessionScreen(
                            serverId = id,
                            isDark = isDark,
                            snackbarHostState = snackbarHostState,
                            onOpenAccounts = { showAccountsSheet = true },
                            modifier = Modifier.graphicsLayer {
                                scaleX = previewScale
                                scaleY = previewScale
                                translationX = previewShift
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAccountsSheet) {
        AccountsSheet(onDismiss = { showAccountsSheet = false })
    }

    if (showAppSettingsSheet) {
        AppSettingsSheet(onDismiss = { showAppSettingsSheet = false })
    }
}