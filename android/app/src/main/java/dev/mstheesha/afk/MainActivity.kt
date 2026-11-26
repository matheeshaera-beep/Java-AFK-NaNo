package dev.mstheesha.afk

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            var themeModeKey by rememberSaveable { mutableStateOf(Prefs.themeMode(this).key) }
            var dynamicColor by rememberSaveable { mutableStateOf(Prefs.dynamicColor(this)) }
            val themeMode = ThemeMode.of(themeModeKey)
            val (colorScheme, isDark) = resolveColorScheme(this, themeMode, systemDark, dynamicColor)

            MaterialTheme(colorScheme = colorScheme) {
                AfkApp(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    isDark = isDark,
                    onThemeMode = { m ->
                        themeModeKey = m.key
                        Prefs.setThemeMode(this, m)
                    },
                    onDynamicColor = { enabled ->
                        dynamicColor = enabled
                        Prefs.setDynamicColor(this, enabled)
                    },
                )
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

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AfkApp(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    isDark: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val servers by dao.all().collectAsState(initial = emptyList())
    val activeCount by AppGraph.activeCount.collectAsState(initial = 0)

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAccountsSheet by remember { mutableStateOf(false) }
    var showAppSettingsSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun openServer(id: Long) {
        selectedId = id
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = selectedId != null) {
        selectedId = null
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
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (selectedId != null) {
                            IconButton(onClick = { selectedId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    title = {
                        val currentName = selectedId?.let { id -> servers.firstOrNull { it.id == id }?.name }
                        Text(
                            currentName ?: "Java AFK",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (selectedId == null && servers.isNotEmpty()) {
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
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val id = selectedId
                if (id == null) {
                    ServerListScreen(
                        servers = servers,
                        snackbarHostState = snackbarHostState,
                        onSelect = { serverId -> openServer(serverId) },
                    )
                } else {
                    SessionScreen(
                        serverId = id,
                        isDark = isDark,
                        snackbarHostState = snackbarHostState,
                        onOpenAccounts = { showAccountsSheet = true },
                    )
                }
            }
        }
    }

    if (showAccountsSheet) {
        AccountsSheet(onDismiss = { showAccountsSheet = false })
    }

    if (showAppSettingsSheet) {
        AppSettingsSheet(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeMode = onThemeMode,
            onDynamicColor = onDynamicColor,
            onDismiss = { showAppSettingsSheet = false },
        )
    }
}

@Composable
private fun DrawerContent(
    servers: List<ServerEntity>,
    activeCount: Int,
    selectedId: Long?,
    drawerOpen: Boolean,
    onSelectServer: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    val maxCap = Prefs.maxBots(context)
    val countLabel = if (maxCap > 0) "$activeCount/$maxCap" else "$activeCount running"

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Text(
            "Java AFK NaNo",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                "Servers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (servers.isEmpty()) {
                Text(
                    "No servers yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                servers.forEach { server ->
                    val session = remember(server.id) { AppGraph.sessionFor(server.id) }
                    val st by session.state.collectAsState()
                    val dotColor = when (st) {
                        "connected" -> MaterialTheme.colorScheme.primary
                        "connecting", "reconnecting", "authenticating" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    NavigationDrawerItem(
                        icon = {
                            ServerAvatar(
                                host = server.host,
                                port = server.port,
                                name = server.name,
                                size = 30.dp,
                            )
                        },
                        label = { Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        badge = {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                        },
                        selected = selectedId == server.id,
                        onClick = { onSelectServer(server.id) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        ResourcesPanel(active = drawerOpen)

        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null) },
            label = { Text("Microsoft accounts") },
            selected = false,
            onClick = onOpenAccounts,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Tune, contentDescription = null) },
            label = { Text("App settings") },
            selected = false,
            onClick = onOpenAppSettings,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.BatterySaver, contentDescription = null) },
            label = { Text("Battery optimization") },
            selected = false,
            onClick = { requestIgnoreBatteryOptimizations(context) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        Text(
            "Java AFK NaNo v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

@Composable
private fun ResourcesPanel(active: Boolean) {
    val app = LocalContext.current.applicationContext as Application
    var stats by remember { mutableStateOf<AppStats?>(null) }

    LaunchedEffect(active) {
        while (active) {
            stats = withContext(Dispatchers.IO) { ResourceMonitor.sample(app) }
            delay(4000)
        }
    }

    var smoothCpu by remember { mutableStateOf(0f) }
    LaunchedEffect(stats) {
        stats?.let {
            smoothCpu =
                if (smoothCpu == 0f) it.cpuPercent
                else smoothCpu * 0.65f + it.cpuPercent * 0.35f
        }
    }
    val ram = stats?.rssMB ?: 0L
    val ramFrac = stats?.let { it.rssMB.toFloat() / it.totalMemMB } ?: 0f

    val cpuAnim by animateFloatAsState(
        targetValue = (smoothCpu / 100f).coerceIn(0f, 1f),
        label = "cpu",
    )
    val ramAnim by animateFloatAsState(targetValue = ramFrac.coerceIn(0f, 1f), label = "ram")

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "App usage",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Speed, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("CPU %.0f%%".format(smoothCpu), style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { cpuAnim },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Memory, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("RAM $ram MB", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { ramAnim },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var u = 0
    while (value >= 1024 && u < units.size - 1) {
        value /= 1024
        u++
    }
    return String.format(Locale.US, "%.1f %s", value, units[u])
}

@Composable
private fun ServerListScreen(
    servers: List<ServerEntity>,
    snackbarHostState: SnackbarHostState,
    onSelect: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ServerEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add server")
            }
        },
    ) { padding ->
        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No servers yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to add your first Minecraft Java server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(servers, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        onSelect = onSelect,
                        onQuickToggle = { start ->
                            if (start) {
                                if (!AppGraph.startServer(ctx, server)) {
                                    val cap = Prefs.maxBots(ctx)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Maximum $cap bots running (adjust in App settings)")
                                    }
                                }
                            } else {
                                AppGraph.sessionFor(server.id).stop()
                            }
                        },
                        onEdit = { editing = server },
                        onDelete = {
                            AppGraph.removeSession(server.id)
                            scope.launch { dao.delete(server) }
                        },
                    )
                }
            }
        }
    }

    val draft = remember { mutableStateOf(ServerDraft()) }
    if (showAdd) {
        ServerDialog(
            title = "Add server",
            draft = draft.value,
            onChange = { draft.value = it },
            onDismiss = {
                showAdd = false
                draft.value = ServerDraft()
            },
            onSave = {
                scope.launch {
                    dao.insert(
                        ServerEntity(
                            name = draft.value.name.trim().ifEmpty { draft.value.host.trim() },
                            host = draft.value.host.trim(),
                            port = draft.value.portInt,
                            onlineMode = draft.value.onlineMode,
                            username = draft.value.username.trim(),
                        ),
                    )
                    showAdd = false
                    draft.value = ServerDraft()
                }
            },
        )
    }

    editing?.let { server ->
        val ed = remember(server.id) { mutableStateOf(ServerDraft.from(server)) }
        ServerDialog(
            title = "Edit server",
            draft = ed.value,
            onChange = { ed.value = it },
            onDismiss = { editing = null },
            onSave = {
                scope.launch {
                    dao.update(
                        server.copy(
                            name = ed.value.name.trim().ifEmpty { ed.value.host.trim() },
                            host = ed.value.host.trim(),
                            port = ed.value.portInt,
                            onlineMode = ed.value.onlineMode,
                            username = ed.value.username.trim(),
                        ),
                    )
                    editing = null
                }
            },
        )
    }
}

private val faviconCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

private fun cacheFavicon(key: String, bmp: android.graphics.Bitmap) {
    if (faviconCache.size >= 32) faviconCache.clear()
    faviconCache[key] = bmp
}

@Composable
private fun ServerRow(
    server: ServerEntity,
    onSelect: (Long) -> Unit,
    onQuickToggle: (start: Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val session = remember(server.id) { AppGraph.sessionFor(server.id) }
    LaunchedEffect(server.id, server.name) {
        AppGraph.noteServerName(server.id, server.name)
    }
    val state by session.state.collectAsState()
    val health by session.health.collectAsState()
    val dataBytes by session.sessionDataBytes.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    val isConnected = state == "connected"
    val isWorking = state == "connecting" || state == "authenticating" || state == "reconnecting"
    val isActive = isConnected || isWorking
    val statusLabel = when {
        isConnected -> "Connected"
        isWorking -> state.replaceFirstChar { it.uppercase() }
        else -> "Offline"
    }
    val statusColor = when {
        isConnected -> MaterialTheme.colorScheme.primary
        isWorking -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onClick = { onSelect(server.id) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerAvatar(
                host = server.host,
                port = server.port,
                name = server.name,
                size = 46.dp,
            )
            Spacer(Modifier.width(12.dp))

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        server.host,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }
                if (isConnected) {
                    val hearts = String.format(Locale.US, "%.0f", (health / 2f).coerceAtLeast(0f))
                    Text(
                        "♥ $hearts/10 · ${formatBytes(dataBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // One-tap Start / Stop directly on the server card.
            FilledTonalIconButton(
                onClick = { onQuickToggle(!isActive) },
                colors = if (isActive) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
            ) {
                Icon(
                    if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isActive) "Stop" else "Start",
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Server options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerAvatar(host: String, port: Int, name: String, size: Dp) {
    val ctx = LocalContext.current
    val cacheKey = "$host:$port"
    var favicon by remember(cacheKey) {
        mutableStateOf(faviconCache[cacheKey])
    }

    LaunchedEffect(cacheKey) {
        if (faviconCache.containsKey(cacheKey)) {
            favicon = faviconCache[cacheKey]
        } else {
            val bmp = withContext(Dispatchers.IO) {
                ServerPinger.fetchFavicon(ctx, host, port)
            }
            if (bmp != null) cacheFavicon(cacheKey, bmp)
            favicon = bmp
        }
    }

    if (favicon != null) {
        Image(
            bitmap = favicon!!.asImageBitmap(),
            contentDescription = "Server icon",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                style = if (size >= 40.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private data class ServerDraft(
    val name: String = "",
    val host: String = "",
    val port: String = "25565",
    val onlineMode: Boolean = false,
    val username: String = "",
) {
    val portInt: Int get() = port.toIntOrNull()?.coerceIn(1, 65535) ?: 25565
    val portValid: Boolean get() = port.toIntOrNull()?.let { it in 1..65535 } ?: false

    companion object {
        fun from(s: ServerEntity) = ServerDraft(
            name = s.name,
            host = s.host,
            port = s.port.toString(),
            onlineMode = s.onlineMode,
            username = s.username,
        )
    }
}

@Composable
private fun ServerDialog(
    title: String,
    draft: ServerDraft,
    onChange: (ServerDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    draft.name,
                    { onChange(draft.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    draft.host,
                    { raw ->
                        // Auto-split "play.example.com:25566" on paste.
                        val colon = raw.lastIndexOf(':')
                        if (colon > 0 && raw.indexOf(':') == colon) {
                            val h = raw.substring(0, colon)
                            val p = raw.substring(colon + 1)
                            if (p.all(Char::isDigit) && p.isNotEmpty()) {
                                onChange(draft.copy(host = h, port = p))
                                return@OutlinedTextField
                            }
                        }
                        onChange(draft.copy(host = raw))
                    },
                    label = { Text("Host (or host:port)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    draft.port,
                    { onChange(draft.copy(port = it)) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !draft.portValid,
                    supportingText = if (!draft.portValid) {
                        { Text("Port must be 1–65535") }
                    } else null,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Microsoft account (online)")
                    Switch(checked = draft.onlineMode, onCheckedChange = { onChange(draft.copy(onlineMode = it)) })
                }
                if (!draft.onlineMode) {
                    OutlinedTextField(
                        draft.username,
                        { onChange(draft.copy(username = it)) },
                        label = { Text("Offline username") },
                        placeholder = { Text("AFKBot") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.host.isNotBlank() && draft.portValid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun SessionScreen(
    serverId: Long,
    isDark: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenAccounts: () -> Unit,
) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val session = remember(serverId) { AppGraph.sessionFor(serverId) }

    val server by dao.byId(serverId).collectAsState(initial = null)
    val state by session.state.collectAsState()
    val detail by session.detail.collectAsState()
    val logs by session.logs.collectAsState()
    val chat by session.chat.collectAsState()
    val authRequired by session.authRequired.collectAsState()
    val health by session.health.collectAsState()
    val food by session.food.collectAsState()
    val pingMs by session.pingMs.collectAsState()
    val versionName by session.versionName.collectAsState()

    val afkSeconds by session.afkSeconds.collectAsState()
    val sessionData by session.sessionDataBytes.collectAsState()
    val bytesIn by session.bytesIn.collectAsState()
    val bytesOut by session.bytesOut.collectAsState()

    var kickedCount by remember { mutableIntStateOf(session.kickedCount) }
    var chatInput by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }
    var showSettings by remember(serverId) { mutableStateOf(false) }
    val recentSent = remember(serverId) { mutableStateListOf<String>() }
    val showTimestamps = Prefs.chatTimestamps(ctx)
    val quickCommands = remember(showSettings) { Prefs.quickCommands(ctx) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val scope = rememberCoroutineScope()
    val canStart = state == "disconnected" || state == "error"

    val chatFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun sendText(raw: String) {
        val msg = raw.trim()
        if (msg.isBlank()) return
        if (state != "connected") {
            scope.launch { snackbarHostState.showSnackbar("Not connected") }
            return
        }
        session.sendChat(msg) { err ->
            scope.launch { snackbarHostState.showSnackbar(err) }
        }
        recentSent.remove(msg)
        recentSent.add(0, msg)
        if (recentSent.size > 6) recentSent.removeAt(recentSent.lastIndex)
    }

    fun sendChatMessage() {
        val msg = chatInput.trim()
        if (msg.isBlank()) return
        sendText(msg)
        chatInput = ""
        chatFocusRequester.requestFocus()
        keyboardController?.show()
    }

    val listState = rememberLazyListState()
    var followNewest by remember { mutableStateOf(true) }
    LaunchedEffect(showChat) { followNewest = true }
    val itemCount = if (showChat) chat.size else logs.size
    LaunchedEffect(itemCount, followNewest) {
        if (followNewest && itemCount > 0) listState.scrollToItem(itemCount - 1)
    }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index == info.totalItemsCount - 1
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !atBottom) followNewest = false
    }
    LaunchedEffect(atBottom) {
        if (atBottom) followNewest = true
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && showChat && itemCount > 0) {
            followNewest = true
            listState.scrollToItem(itemCount - 1)
        }
    }

    LaunchedEffect(serverId) {
        session.flushBuffersIfDirty()
        while (true) {
            delay(1000)
            session.refreshCounters()
        }
    }

    LaunchedEffect(session.kickedCount) {
        if (session.kickedCount > kickedCount) {
            snackbarHostState.showSnackbar("Kicked or disconnected: ${session.detail.value}")
        }
        kickedCount = session.kickedCount
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (server == null) {
            Text("Select a server from the list first.")
        } else {
            val srv = server!!
            LaunchedEffect(srv.id, srv.name) {
                AppGraph.noteServerName(srv.id, srv.name)
            }

            val statusDotColor = when (state) {
                "connected" -> MaterialTheme.colorScheme.primary
                "connecting", "authenticating", "reconnecting" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Card(Modifier.fillMaxWidth().animateContentSize()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(statusDotColor),
                                    )
                                    Text(
                                        stateLabel(state),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Server settings")
                            }
                        }

                        // Compact live vitals + ping + version pills when connected
                        AnimatedVisibility(visible = state == "connected") {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val heartsStr = String.format(Locale.US, "%.1f", (health / 2f).coerceAtLeast(0f))
                                    .removeSuffix(".0")
                                VitalsPill(
                                    icon = Icons.Default.Favorite,
                                    text = "$heartsStr/10",
                                    tint = if (health <= 6f) MaterialTheme.colorScheme.error else Color(0xFFE53935),
                                )
                                VitalsPill(
                                    icon = Icons.Default.Restaurant,
                                    text = "$food/20",
                                    tint = Color(0xFFFB8C00),
                                )
                                if (pingMs > 0) {
                                    VitalsPill(
                                        icon = Icons.Default.SignalCellularAlt,
                                        text = "${pingMs}ms",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (versionName.isNotBlank()) {
                                    VitalsPill(
                                        icon = null,
                                        text = versionName,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column {
                                    Text(
                                        "AFK TIME",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        formatAfkTime(afkSeconds),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            VerticalDivider(modifier = Modifier.height(36.dp))
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                Icon(
                                    Icons.Default.DataUsage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column {
                                    Text(
                                        "DATA USED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        formatBytes(sessionData),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        "↓${formatBytes(bytesIn)}  ↑${formatBytes(bytesOut)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (!AppGraph.startServer(ctx, srv)) {
                                val cap = Prefs.maxBots(ctx)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Maximum $cap servers running (adjust in App settings)")
                                }
                            }
                        },
                        enabled = canStart,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(" Start")
                    }
                    Button(
                        onClick = { session.reconnect() },
                        enabled = state != "disconnected",
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect now")
                    }
                    Button(
                        onClick = { session.stop() },
                        enabled = state != "disconnected",
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text(" Stop")
                    }
                }
            }
        }

        server?.let { srvd ->
            if (showSettings) {
                ServerSettingsSheet(
                    server = srvd,
                    session = session,
                    snackbarHostState = snackbarHostState,
                    onOpenAccounts = {
                        showSettings = false
                        onOpenAccounts()
                    },
                    onDismiss = { showSettings = false },
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showChat = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = "Log",
                            tint = if (!showChat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showChat = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat",
                            tint = if (showChat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { if (showChat) session.clearChat() else session.clearLogs() }) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = if (showChat) "Clear chat" else "Clear logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 8.dp)
                    .imePadding(),
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (showChat) {
                                items(chat, key = { "${it.seq}:${it.ts}:${it.text.hashCode()}" }) { line ->
                                    val sender = line.sender?.takeIf { it != "null" }?.let { "<$it> " } ?: ""
                                    val prefix = if (showTimestamps) "[${timeFmt.format(Date(line.ts))}] " else ""
                                    val baseColor = when (line.type) {
                                        "chat" -> MaterialTheme.colorScheme.onSurface
                                        "system" -> MaterialTheme.colorScheme.tertiary
                                        "whisper" -> MaterialTheme.colorScheme.secondary
                                        "error" -> MaterialTheme.colorScheme.error
                                        "out" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val formatted = remember(line.styledText, prefix, sender, baseColor, isDark) {
                                        formatMinecraftChat("$prefix$sender${line.styledText}", baseColor, isDark)
                                    }
                                    Text(
                                        text = formatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = baseColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                                cm.setPrimaryClip(ClipData.newPlainText("chat", line.text))
                                                scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                            },
                                    )
                                }
                            } else {
                                items(logs, key = { it.id }) { line ->
                                    val ts = if (showTimestamps) "[${timeFmt.format(Date(line.ts))}] " else ""
                                    Text(
                                        "$ts${line.text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                                cm.setPrimaryClip(ClipData.newPlainText("log", line.text))
                                                scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                            },
                                    )
                                }
                            }
                        }
                        if (!followNewest && itemCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        followNewest = true
                                        scope.launch { listState.scrollToItem(itemCount - 1) }
                                    },
                                ) { Text("Jump to latest") }
                            }
                        }
                    }

                    if (showChat) {
                        val chips = remember(quickCommands, recentSent.toList()) {
                            (recentSent + quickCommands).distinct().take(8)
                        }
                        if (chips.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                chips.forEach { cmd ->
                                    AssistChip(
                                        onClick = { sendText(cmd) },
                                        label = { Text(cmd, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier.weight(1f).focusRequester(chatFocusRequester),
                                label = { Text("Message or /command") },
                                minLines = 1,
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Send,
                                    capitalization = KeyboardCapitalization.Sentences,
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { sendChatMessage() },
                                ),
                            )
                            IconButton(onClick = { sendChatMessage() }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
    }

    authRequired?.let { (url, code) ->
        AlertDialog(
            onDismissRequest = { session.authRequired.value = null },
            title = { Text("Finish sign-in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sign in to your Microsoft account to verify this device, then return to the app.")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                        TextButton(onClick = {
                            val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("device code", code))
                        }) { Text("Copy") }
                    }
                    Text(url, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("device code", code))
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Copy & Open") }
            },
            dismissButton = {
                TextButton(onClick = { session.authRequired.value = null }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun VitalsPill(icon: ImageVector?, text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun formatAfkTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun stateLabel(state: String): String = when (state) {
    "connected" -> "Connected"
    "connecting" -> "Connecting"
    "reconnecting" -> "Reconnecting"
    "authenticating" -> "Authenticating"
    "error" -> "Error"
    else -> "Disconnected"
}