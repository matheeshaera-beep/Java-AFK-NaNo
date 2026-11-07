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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.mstheesha.afk.engine.VersionCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        AppGraph.uiVisible = true
    }

    override fun onStop() {
        AppGraph.uiVisible = false
        // Closing the UI with zero bots must not leave a service/notification
        // behind. Bots that are running (or reconnecting) keep it alive.
        if (!AppGraph.hasActiveSessions()) {
            stopService(Intent(this, AfkService::class.java))
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        AppGraph.setContext(applicationContext)
        requestNotificationPermission()
        // Target SDK 35+ enforces edge-to-edge: adjustResize no longer moves
        // content, so the keyboard would cover the chat input. Opt in here
        // and handle the IME inset in Compose (single imePadding, SessionScreen).
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(Prefs.isDark(this, systemDark)) }
            MaterialTheme(colorScheme = if (dark) GreenDark else GreenLight) {
                AfkApp(
                    dark = dark,
                    onToggleDark = { v ->
                        dark = v
                        Prefs.setDark(this, v)
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

/**
 * Battery-optimization exemption, requested only when the user taps the drawer
 * item. Prompting on every launch is hostile and easy to get wrong on OEM ROMs.
 */
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
        // Some OEM builds drop the direct request; open the settings list.
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}

val DividerColor = Color(0xFF5A5764)
val StrongOutlineColor = Color(0xFF77737F)

private val GreenLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EFB0),
    secondary = Color(0xFF43A047),
    tertiary = Color(0xFF66BB6A),
    outline = StrongOutlineColor,
    outlineVariant = DividerColor,
)
private val GreenDark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF005300),
    secondary = Color(0xFFA5D6A7),
    tertiary = Color(0xFF66BB6A),
    outline = StrongOutlineColor,
    outlineVariant = DividerColor,
)

@Composable
fun appSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color(0xFF0B5D1E),
    checkedTrackColor = Color(0xFF7FD18B),
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color(0xFF9A95A3),
    uncheckedTrackColor = Color(0xFF2C2A32),
    uncheckedBorderColor = Color(0xFF77737F),
    disabledCheckedThumbColor = Color(0xFF0B5D1E).copy(alpha = 0.38f),
    disabledCheckedTrackColor = Color(0xFF7FD18B).copy(alpha = 0.38f),
    disabledCheckedBorderColor = Color.Transparent,
    disabledUncheckedThumbColor = Color(0xFF9A95A3).copy(alpha = 0.38f),
    disabledUncheckedTrackColor = Color(0xFF2C2A32).copy(alpha = 0.38f),
    disabledUncheckedBorderColor = Color(0xFF77737F).copy(alpha = 0.38f),
)

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = appSwitchColors(),
    )
}

@Composable
fun appOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedBorderColor = Color(0xFF7FD18B),
    focusedLabelColor = Color(0xFF7FD18B),
    cursorColor = Color(0xFF7FD18B),
)

@Composable
private fun MinecraftHudBar(
    health: Float,
    food: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Health",
                tint = Color(0xFFFF4D4D),
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "${health.toInt()}/20",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFF4D4D),
            )
            Spacer(Modifier.width(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (i in 0 until 10) {
                    val hpForHeart = health - (i * 2)
                    val color = when {
                        hpForHeart >= 2f -> Color(0xFFFF3333)
                        hpForHeart >= 1f -> Color(0xFFFF8888)
                        else -> Color(0xFF442222)
                    }
                    Box(
                        Modifier
                            .size(width = 6.dp, height = 7.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(color),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (i in 0 until 10) {
                    val foodForStick = food - (i * 2)
                    val color = when {
                        foodForStick >= 2 -> Color(0xFFD97706)
                        foodForStick >= 1 -> Color(0xFFF59E0B)
                        else -> Color(0xFF452B10)
                    }
                    Box(
                        Modifier
                            .size(width = 6.dp, height = 7.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(color),
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = "$food/20",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFD97706),
            )
            Icon(
                Icons.Default.Restaurant,
                contentDescription = "Food",
                tint = Color(0xFFD97706),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AfkApp(dark: Boolean, onToggleDark: (Boolean) -> Unit) {
    // Real selection state so the session screen reacts to the chosen server
    // (rememberSaveable survives rotation/process recreation).
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun openServer(id: Long) {
        selectedId = id
        scope.launch { drawerState.close() }
    }

    // Back returns from session to server list instead of exiting
    BackHandler(enabled = selectedId != null) {
        selectedId = null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                dark = dark,
                selectedId = selectedId,
                onToggleDark = onToggleDark,
                onSelectServer = { id -> openServer(id) },
                drawerOpen = drawerState.isOpen,
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Java AFK NaNo",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (selectedId == null) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        } else {
                            IconButton(
                                onClick = { selectedId = null },
                                // Center-align with the server settings gear
                                // below: TopAppBar actions end 4 dp from the
                                // edge, the gear ends 16+16 dp in.
                                modifier = Modifier.padding(end = 28.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    ServerListScreen(onSelect = { serverId -> openServer(serverId) })
                } else {
                    SessionScreen(
                        serverId = id,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    dark: Boolean,
    selectedId: Long?,
    onToggleDark: (Boolean) -> Unit,
    onSelectServer: (Long) -> Unit,
    drawerOpen: Boolean,
) {
    val dao = AppGraph.db.serverDao()
    val servers by dao.all().collectAsState(initial = emptyList())
    val activeCount by AppGraph.activeCount.collectAsState(initial = 0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight(),
    ) {
        // App brand header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Java AFK NaNo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Lightweight AFK Client",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )

        // Servers Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = "SERVERS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Surface(
                shape = CircleShape,
                color = if (activeCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "$activeCount/${EngineHost.MAX_BOTS} ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (activeCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        // Servers list or empty state
        if (servers.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "No servers added",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Tap + on the home screen to add one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            servers.forEach { server ->
                val session = remember(server.id) { AppGraph.sessionFor(server.id) }
                val state by session.state.collectAsState()
                val isSelected = server.id == selectedId
                NavigationDrawerItem(
                    icon = {
                        ServerAvatarStatus(
                            host = server.host,
                            port = server.port,
                            name = server.name,
                            state = state,
                            size = 32.dp,
                        )
                    },
                    label = {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (state == "connected") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    selected = isSelected,
                    onClick = { onSelectServer(server.id) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Compact System Health Card (CPU & RAM)
        ResourcesPanel(active = drawerOpen)

        Spacer(Modifier.height(10.dp))

        // Settings / Utility Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Dark mode row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDark(!dark) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedContent(targetState = dark, label = "theme_icon") { isDark ->
                            Icon(
                                if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = "Dark mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    AppSwitch(
                        checked = dark,
                        onCheckedChange = onToggleDark,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // Battery optimization row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { requestIgnoreBatteryOptimizations(context) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Battery optimization",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // Export servers row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (servers.isEmpty()) {
                                Toast.makeText(context, "No servers to export", Toast.LENGTH_SHORT).show()
                            } else {
                                val json = ServerBackup.exportToJson(servers)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Java AFK NaNo Servers", json))
                                Toast.makeText(context, "Exported ${servers.size} servers to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Export servers (JSON)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // Import servers row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip
                                val text = clip?.getItemAt(0)?.text?.toString() ?: ""
                                val imported = ServerBackup.importFromJson(text)
                                if (imported.isEmpty()) {
                                    Toast.makeText(context, "Clipboard has no valid server JSON", Toast.LENGTH_SHORT).show()
                                } else {
                                    imported.forEach { dao.insert(it) }
                                    Toast.makeText(context, "Imported ${imported.size} servers", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Import servers (JSON)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Version Footer
        Text(
            text = "Java AFK NaNo v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp)
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
            delay(5000)
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // CPU stat
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "CPU",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "%.0f%%".format(smoothCpu),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LinearProgressIndicator(
                    progress = { cpuAnim },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .height(28.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            // RAM stat
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$ram MB",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LinearProgressIndicator(
                    progress = { ramAnim },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
private fun ServerListScreen(onSelect: (Long) -> Unit) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val servers by dao.all().collectAsState(initial = emptyList())
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
                Text("No servers yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(servers, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        onSelect = onSelect,
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
                            name = draft.value.name.trim().ifEmpty { draft.value.host },
                            host = draft.value.host.trim(),
                            port = draft.value.portInt,
                            chatCommand = draft.value.chatCommand.trim(),
                            commandDelaySeconds = draft.value.delayInt,
                            onlineMode = draft.value.onlineMode,
                            username = draft.value.username.trim(),
                            viewDistance = draft.value.viewDistanceInt,
                            chatMode = draft.value.chatMode,
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
                            name = ed.value.name.trim().ifEmpty { ed.value.host },
                            host = ed.value.host.trim(),
                            port = ed.value.portInt,
                            chatCommand = ed.value.chatCommand.trim(),
                            commandDelaySeconds = ed.value.delayInt,
                            onlineMode = ed.value.onlineMode,
                            username = ed.value.username.trim(),
                            viewDistance = ed.value.viewDistanceInt,
                            chatMode = ed.value.chatMode,
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

private fun formatUptime(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val days = hours / 24
    val remHours = hours % 24
    return when {
        days > 0 -> "up ${days}d ${remHours}h"
        hours > 0 -> "up ${hours}h ${minutes}m"
        else -> "up ${minutes}m"
    }
}

@Composable
private fun ServerAvatarStatus(
    host: String,
    port: Int,
    name: String,
    state: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsDisabled = remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val isOnline = state == "connected"
    val isConnecting = state == "connecting" || state == "authenticating" || state == "reconnecting"
    val isError = state == "error"
    val isOffline = !isOnline && !isConnecting && !isError

    val targetColor = when {
        isOnline -> Color(0xFF7FD18B)
        isConnecting -> Color(0xFFFFB74D)
        isError -> Color(0xFFE57373)
        else -> Color.Transparent
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 250),
        label = "avatar_status_color",
    )

    val contentDesc = when {
        isOnline -> "Online"
        isConnecting -> if (state == "reconnecting") "Reconnecting" else "Connecting"
        isError -> "Error"
        else -> "Offline"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "avatar_status_anim")

    // Pulse animation for Online (scale 1.0 -> 1.12, alpha 0.9 -> 0.3, ~1.8 s loop)
    val pulseScale by if (isOnline && !animationsDisabled) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_scale",
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }

    val pulseAlpha by if (isOnline && !animationsDisabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
    } else {
        remember { mutableFloatStateOf(0.7f) }
    }

    // Rotating sweeping arc for Connecting
    val sweepAngle by if (isConnecting && !animationsDisabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "connecting_sweep",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val ringExtra = 6.dp
    val containerSize = size + ringExtra * 2

    Box(
        modifier = modifier
            .size(containerSize)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        if (!isOffline) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (isOnline) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    },
            ) {
                val strokeWidth = 2.dp.toPx()
                val radius = (size.toPx() / 2f) + 2.dp.toPx()
                val centerOffset = center

                when {
                    isOnline -> {
                        drawCircle(
                            color = animatedColor.copy(alpha = pulseAlpha),
                            radius = radius,
                            center = centerOffset,
                            style = Stroke(width = strokeWidth),
                        )
                    }
                    isConnecting -> {
                        val arcSize = radius * 2
                        drawArc(
                            color = animatedColor,
                            startAngle = sweepAngle,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                    isError -> {
                        drawCircle(
                            color = animatedColor,
                            radius = radius,
                            center = centerOffset,
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    if (isOffline) {
                        alpha = 0.55f
                    }
                },
        ) {
            ServerAvatar(
                host = host,
                port = port,
                name = name,
                size = size,
                desaturate = isOffline,
            )
        }

        if (isOnline || isError) {
            val dotSize = if (size >= 40.dp) 11.dp else 8.dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .background(animatedColor, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerEntity,
    onSelect: (Long) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val session = remember(server.id) { AppGraph.sessionFor(server.id) }
    // Feed the foreground notification's server-name list.
    LaunchedEffect(server.id, server.name) {
        AppGraph.noteServerName(server.id, server.name)
    }
    val state by session.state.collectAsState()
    val afkSeconds by session.afkSeconds.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    val isConnected = state == "connected"
    val context = LocalContext.current
    var pingStatus by remember(server.host, server.port) { mutableStateOf<ServerPinger.ServerStatus?>(null) }
    LaunchedEffect(server.host, server.port) {
        withContext(Dispatchers.IO) {
            pingStatus = ServerPinger.ping(server.host, server.port)
        }
    }
    LaunchedEffect(isConnected) {
        while (isConnected) {
            session.refreshCounters()
            delay(60_000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        onClick = { onSelect(server.id) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            if (isConnected) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF7FD18B)),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerAvatarStatus(
                    host = server.host,
                    port = server.port,
                    name = server.name,
                    state = state,
                    size = 48.dp,
                )
                Spacer(Modifier.width(12.dp))

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            server.host,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        pingStatus?.let { ps ->
                            if (ps.latencyMs >= 0) {
                                val pingColor = when {
                                    ps.latencyMs < 100 -> Color(0xFF7FD18B)
                                    ps.latencyMs < 250 -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = pingColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, pingColor.copy(alpha = 0.4f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(pingColor),
                                        )
                                        Text(
                                            text = "${ps.latencyMs}ms",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = pingColor,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                        if (isConnected) {
                            Text(
                                formatUptime(afkSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF7FD18B),
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isActive = state in setOf("connecting", "connected", "reconnecting", "authenticating")
                    IconButton(
                        onClick = {
                            if (isActive) {
                                session.stop()
                            } else {
                                session.start(buildConfig(server))
                                context.startForegroundService(
                                    Intent(context, AfkService::class.java).setAction(AfkService.ACTION_START),
                                )
                            }
                        },
                    ) {
                        if (isActive) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color(0xFFE57373),
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                tint = Color(0xFF7FD18B),
                            )
                        }
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
    }
}

/** Server icon shared by the main list and the drawer: live favicon when the
 *  server has one, initial-letter tile otherwise (memory-cached). */
@Composable
private fun ServerAvatar(
    host: String,
    port: Int,
    name: String,
    size: Dp,
    desaturate: Boolean = false,
) {
    val cacheKey = "$host:$port"
    var favicon by remember(cacheKey) {
        mutableStateOf(faviconCache[cacheKey])
    }

    LaunchedEffect(cacheKey) {
        if (faviconCache.containsKey(cacheKey)) {
            favicon = faviconCache[cacheKey]
        } else {
            val bmp = withContext(Dispatchers.IO) {
                ServerPinger.fetchFavicon(host, port)
            }
            if (bmp != null) cacheFavicon(cacheKey, bmp)
            favicon = bmp
        }
    }

    val desatFilter = remember(desaturate) {
        if (desaturate) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    }

    if (favicon != null) {
        Image(
            bitmap = favicon!!.asImageBitmap(),
            contentDescription = "Server icon",
            contentScale = ContentScale.Fit,
            colorFilter = desatFilter,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (desaturate) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                style = if (size >= 40.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                color = if (desaturate) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class ServerDraft(
    val name: String = "",
    val host: String = "",
    val port: String = "25565",
    val chatCommand: String = "",
    val commandDelaySeconds: String = "5",
    val onlineMode: Boolean = false,
    val username: String = "",
    val viewDistance: String = "2",
    val chatMode: String = "enabled", // enabled | commandsOnly | hidden
) {
    val portInt: Int get() = port.toIntOrNull()?.coerceIn(1, 65535) ?: 25565
    val delayInt: Int get() = commandDelaySeconds.toIntOrNull()?.coerceIn(0, 3600) ?: 5
    val viewDistanceInt: Int get() = viewDistance.toIntOrNull()?.coerceIn(2, 12) ?: 2
    val portValid: Boolean get() = port.toIntOrNull()?.let { it in 1..65535 } ?: false

    companion object {
        fun from(s: ServerEntity) = ServerDraft(
            name = s.name,
            host = s.host,
            port = s.port.toString(),
            chatCommand = s.chatCommand,
            commandDelaySeconds = s.commandDelaySeconds.toString(),
            onlineMode = s.onlineMode,
            username = s.username,
            viewDistance = s.viewDistance.toString(),
            chatMode = s.chatMode,
        )
    }
}

@Composable
private fun ChatModeChip(label: String, value: String, current: String, onClick: () -> Unit) {
    val selected = current == value
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = draft.host,
                    onValueChange = { onChange(draft.copy(host = it)) },
                    label = { Text("Host") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = draft.port,
                    onValueChange = { onChange(draft.copy(port = it)) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !draft.portValid,
                    supportingText = if (!draft.portValid) { { Text("Port must be 1\u201365535") } } else null,
                    colors = appOutlinedTextFieldColors(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Microsoft account (online mode)")
                    AppSwitch(checked = draft.onlineMode, onCheckedChange = { onChange(draft.copy(onlineMode = it)) })
                }
                if (!draft.onlineMode) {
                    OutlinedTextField(
                        value = draft.username,
                        onValueChange = { onChange(draft.copy(username = it)) },
                        label = { Text("Offline username") },
                        placeholder = { Text("AFKBot") },
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
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

// Per-server settings, moved off the main session screen. Edits auto-save
// to the database with the same debounce as the old inline fields.
private suspend fun updateServerField(
    dao: ServerDao,
    id: Long,
    transform: (ServerEntity) -> ServerEntity,
) {
    // Read the row again instead of writing the composable's snapshot: two
    // fields changing inside the same debounce window would otherwise clobber
    // each other with an old copy.
    val current = dao.get(id) ?: return
    val updated = transform(current)
    if (updated != current) dao.update(updated)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ServerSettingsDialog(
    server: ServerEntity,
    session: ServerSession,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val dao = AppGraph.db.serverDao()
    val scope = rememberCoroutineScope()
    var chatCommandText by remember(server.id) { mutableStateOf(server.chatCommand) }
    var delayText by remember(server.id) { mutableStateOf(server.commandDelaySeconds.toString()) }
    var viewDistanceText by remember(server.id) { mutableStateOf(server.viewDistance.toString()) }
    var chatModeValue by remember(server.id) { mutableStateOf(server.chatMode) }
    var autoJumpValue by remember(server.id) { mutableStateOf(server.autoJump) }
    var loopCommandValue by remember(server.id) { mutableStateOf(server.loopCommand) }
    var disconnectLowHealth by remember(server.id) { mutableStateOf(server.disconnectOnLowHealth) }
    var lowHealthThreshold by remember(server.id) { mutableIntStateOf(server.lowHealthThreshold) }
    var disconnectDeath by remember(server.id) { mutableStateOf(server.disconnectOnDeath) }
    var disconnectKick by remember(server.id) { mutableStateOf(server.disconnectOnKick) }
    var notifyMention by remember(server.id) { mutableStateOf(server.notifyOnMention) }
    // 0 = auto-detect. An unknown/legacy protocol is displayed as auto-detect
    // but is only overwritten in the DB once the user picks a version.
    var protocolValue by remember(server.id) {
        mutableStateOf(
            if (server.protocolOverride == 0 || VersionCatalog.nameOf(server.protocolOverride) != null) {
                server.protocolOverride
            } else {
                0
            },
        )
    }
    var protocolPicked by remember(server.id) { mutableStateOf(false) }
    var protocolMenuOpen by remember(server.id) { mutableStateOf(false) }
    fun pushLive() = session.pushLiveConfig(
        chatCommand = chatCommandText,
        delaySeconds = delayText.toIntOrNull() ?: server.commandDelaySeconds,
        viewDistance = viewDistanceText.toIntOrNull() ?: server.viewDistance,
        chatMode = chatModeValue,
        autoJump = autoJumpValue,
        loopCommand = loopCommandValue,
        disconnectOnLowHealth = disconnectLowHealth,
        lowHealthThreshold = lowHealthThreshold.toFloat(),
        disconnectOnDeath = disconnectDeath,
        disconnectOnKick = disconnectKick,
        notifyOnMention = notifyMention,
    )
    LaunchedEffect(chatCommandText) {
        if (chatCommandText != server.chatCommand) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(chatCommand = chatCommandText) }
            pushLive()
        }
    }
    LaunchedEffect(delayText) {
        val parsed = delayText.toIntOrNull()
        if (parsed != null && parsed != server.commandDelaySeconds) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(commandDelaySeconds = parsed) }
            pushLive()
        }
    }
    LaunchedEffect(viewDistanceText) {
        val parsed = viewDistanceText.toIntOrNull()?.coerceIn(2, 12)
        if (parsed != null && parsed != server.viewDistance) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(viewDistance = parsed) }
            pushLive()
        }
    }
    LaunchedEffect(chatModeValue) {
        if (chatModeValue != server.chatMode) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(chatMode = chatModeValue) }
            pushLive()
        }
    }
    LaunchedEffect(autoJumpValue) {
        if (autoJumpValue != server.autoJump) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(autoJump = autoJumpValue) }
            pushLive()
        }
    }
    LaunchedEffect(loopCommandValue) {
        if (loopCommandValue != server.loopCommand) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(loopCommand = loopCommandValue) }
            pushLive()
        }
    }
    LaunchedEffect(protocolValue, protocolPicked) {
        if (protocolPicked && protocolValue != server.protocolOverride) {
            delay(600)
            updateServerField(dao, server.id) { it.copy(protocolOverride = protocolValue) }
        }
    }
    LaunchedEffect(disconnectLowHealth) {
        if (disconnectLowHealth != server.disconnectOnLowHealth) {
            delay(400)
            updateServerField(dao, server.id) { it.copy(disconnectOnLowHealth = disconnectLowHealth) }
            pushLive()
        }
    }
    LaunchedEffect(lowHealthThreshold) {
        if (lowHealthThreshold != server.lowHealthThreshold) {
            delay(400)
            updateServerField(dao, server.id) { it.copy(lowHealthThreshold = lowHealthThreshold) }
            pushLive()
        }
    }
    LaunchedEffect(disconnectDeath) {
        if (disconnectDeath != server.disconnectOnDeath) {
            delay(400)
            updateServerField(dao, server.id) { it.copy(disconnectOnDeath = disconnectDeath) }
            pushLive()
        }
    }
    LaunchedEffect(disconnectKick) {
        if (disconnectKick != server.disconnectOnKick) {
            delay(400)
            updateServerField(dao, server.id) { it.copy(disconnectOnKick = disconnectKick) }
            pushLive()
        }
    }
    LaunchedEffect(notifyMention) {
        if (notifyMention != server.notifyOnMention) {
            delay(400)
            updateServerField(dao, server.id) { it.copy(notifyOnMention = notifyMention) }
            pushLive()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${server.name} settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                SettingsSection("Connection") {
                    // Friendly version picker instead of a raw protocol number.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Version", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Auto-detect uses Server List Ping. Pick a version only if the server rejects the detected one. Applies on the next Start.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            OutlinedButton(
                                onClick = { protocolMenuOpen = true },
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Text(
                                    VersionCatalog.nameOf(protocolValue)?.let { "$it ($protocolValue)" }
                                        ?: "Auto-detect",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = protocolMenuOpen,
                                onDismissRequest = { protocolMenuOpen = false },
                            ) {
                                Column(
                                    Modifier
                                        .heightIn(max = 360.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    VersionMenuItem("Auto-detect (recommended)", protocolValue == 0) {
                                        protocolPicked = true
                                        protocolValue = 0
                                        protocolMenuOpen = false
                                    }
                                    VersionCatalog.releases.asReversed().forEach { release ->
                                        VersionMenuItem(
                                            "${release.name} (${release.protocol})",
                                            protocolValue == release.protocol,
                                        ) {
                                            protocolPicked = true
                                            protocolValue = release.protocol
                                            protocolMenuOpen = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("View distance", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Chunks loaded from the server (2\u201312). Lower saves battery and data; 2 is enough for AFK.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = viewDistanceText,
                            onValueChange = { viewDistanceText = it },
                            label = { Text("Chunks") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = appOutlinedTextFieldColors(),
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSection("Chat") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Public chat", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ChatModeChip("Enabled", "enabled", chatModeValue) { chatModeValue = "enabled" }
                            ChatModeChip("Commands only", "commandsOnly", chatModeValue) { chatModeValue = "commandsOnly" }
                            ChatModeChip("Messages only", "messagesOnly", chatModeValue) { chatModeValue = "messagesOnly" }
                            ChatModeChip("Hidden", "hidden", chatModeValue) { chatModeValue = "hidden" }
                        }
                        Text(
                            "What the server sends you. Commands only hides player chat; Messages only hides join/leave/system lines. Send always works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSection("Commands") {
                    OutlinedTextField(
                        value = chatCommandText,
                        onValueChange = { chatCommandText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Command sent after spawning") },
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = delayText,
                        onValueChange = { delayText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Delay before command (seconds)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = appOutlinedTextFieldColors(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Repeat command", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Send the command again every delay instead of once.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(
                            checked = loopCommandValue,
                            onCheckedChange = { loopCommandValue = it },
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSection("Automation") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-jump", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Jump every 25\u201340 s so anti-AFK plugins see movement.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(
                            checked = autoJumpValue,
                            onCheckedChange = { autoJumpValue = it },
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSection("Safety & Alerts") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Disconnect on low health", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Disconnect immediately if health drops to protect gear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(
                            checked = disconnectLowHealth,
                            onCheckedChange = { disconnectLowHealth = it },
                        )
                    }
                    if (disconnectLowHealth) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Threshold: ${lowHealthThreshold / 2} hearts (${lowHealthThreshold} HP)", style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedTextField(
                                value = lowHealthThreshold.toString(),
                                onValueChange = { lowHealthThreshold = it.toIntOrNull()?.coerceIn(1, 19) ?: lowHealthThreshold },
                                label = { Text("HP (1-19)") },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = appOutlinedTextFieldColors(),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Disconnect on death", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Do not auto-respawn if killed; disconnect cleanly instead.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(
                            checked = disconnectDeath,
                            onCheckedChange = { disconnectDeath = it },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Disconnect on kick", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Do not auto-reconnect if kicked from the server.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(
                            checked = disconnectKick,
                            onCheckedChange = { disconnectKick = it },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Notify on mentions & whispers", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Vibrate and alert when your username is mentioned in chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppSwitch(
                            checked = notifyMention,
                            onCheckedChange = { notifyMention = it },
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSection("Account") {
                    OutlinedButton(
                        onClick = {
                            session.clearToken()
                            scope.launch { snackbarHostState.showSnackbar("Saved login token cleared") }
                        },
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text(" Clear token")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun VersionMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun SessionScreen(
    serverId: Long,
    snackbarHostState: SnackbarHostState,
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
    val activeCount by AppGraph.activeCount.collectAsState(initial = 0)
    var kickedCount by remember { mutableIntStateOf(0) }
    var chatInput by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }
    var showSettings by remember(serverId) { mutableStateOf(false) }
    // Session-owned counters: keep ticking while connected even when this
    // screen is closed and reopened.
    val afkSeconds by session.afkSeconds.collectAsState()
    val sessionData by session.sessionDataBytes.collectAsState()
    val bytesIn by session.bytesIn.collectAsState()
    val bytesOut by session.bytesOut.collectAsState()
    val health by session.health.collectAsState()
    val food by session.food.collectAsState()
    val scope = rememberCoroutineScope()
    val canStart = state == "disconnected" || state == "error"

    // Single send path for the keyboard Send key and the arrow button:
    // trim, ignore blank, clear after sending, keep focus/keyboard open.
    // Never fails silently: while Disconnected both show "Not connected".
    val chatFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    fun sendChatMessage() {
        val msg = chatInput.trim()
        if (msg.isBlank()) return
        if (state != "connected") {
            scope.launch { snackbarHostState.showSnackbar("Not connected") }
            return
        }
        session.sendChat(msg) { err ->
            scope.launch { snackbarHostState.showSnackbar(err) }
        }
        chatInput = ""
        chatFocusRequester.requestFocus()
        keyboardController?.show()
    }

    // Follow-newest log/chat: sticks to the tail on new lines, releases when
    // the user scrolls up, re-engages at the bottom or via the jump button.
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
    // Keyboard opened: jump to the newest message so it stays visible above
    // the keyboard. Visibility only — no keyboard height is tracked anywhere.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && showChat && itemCount > 0) {
            followNewest = true
            listState.scrollToItem(itemCount - 1)
        }
    }

    // Chat is pulled in the session poll loop even with the tab closed; this
    // just tops it up the moment the tab opens.
    LaunchedEffect(showChat) {
        session.setChatPolling(showChat)
        if (showChat) {
            // Ensure server chat already ingested is shown, even if status poll is idle.
            session.refreshChatNow()
        }
    }

    // 1 s counter ticker, alive only while this screen is composed (the
    // session itself runs no background ticker anymore).
    LaunchedEffect(serverId) {
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

            val statusDotColor =
                if (state == "connected") MaterialTheme.colorScheme.primary
                else if (state == "connecting" || state == "authenticating" || state == "reconnecting") MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            // Header + buttons wrap their content and scroll if the window
            // gets short. The log/chat panel below owns weight(1f) plus the
            // single imePadding, so the input can never be pushed off-screen.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Header group: title, status and detail stacked tightly
                    // and centered. The gear overlays the top-right corner so
                    // it never pushes the title off-center.
                    Box(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                srv.name,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
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
                                    "Status: ${stateLabel(state)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            if (detail.isNotBlank()) {
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
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
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        formatBytes(bytesIn),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Uploaded",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        formatBytes(bytesOut),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                    if (state == "connected") {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        MinecraftHudBar(health = health, food = food)
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
                        if (activeCount >= EngineHost.MAX_BOTS) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Maximum ${EngineHost.MAX_BOTS} servers can run at the same time.")
                            }
                        } else {
                            session.start(buildConfig(srv))
                            ctx.startForegroundService(Intent(ctx, AfkService::class.java).setAction(AfkService.ACTION_START))
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
                ServerSettingsDialog(
                    server = srvd,
                    session = session,
                    snackbarHostState = snackbarHostState,
                    onDismiss = { showSettings = false },
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
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

            // Chat tab = Column(fillMaxSize) { messages list (weight 1f);
            // input row }. The ONE imePadding in this screen sits on the
            // panel: the input always rests directly above the keyboard
            // (gesture and 3-button nav alike), the list takes the rest, and
            // with the keyboard closed the padding is 0 so the layout is
            // exactly as before. No keyboard height is tracked manually.
            Card(
                Modifier.fillMaxWidth().weight(1f).imePadding(),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = listState,
                        ) {
                            if (showChat) {
                                items(chat, key = { "${it.seq}:${it.ts}:${it.text.hashCode()}" }) { line ->
                                    val sender = line.sender?.takeIf { it != "null" }?.let { "<$it> " } ?: ""
                                    val color = when (line.type) {
                                        "chat" -> MaterialTheme.colorScheme.onSurface
                                        "system" -> MaterialTheme.colorScheme.tertiary
                                        "whisper" -> MaterialTheme.colorScheme.secondary
                                        "error" -> MaterialTheme.colorScheme.error
                                        "out" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val fullRawText = "$sender${line.text}"
                                    val formatted = remember(fullRawText, color) {
                                        ChatFormatter.format(fullRawText, defaultColor = color)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                                                    clipboard?.setPrimaryClip(
                                                        ClipData.newPlainText("Chat message", line.text),
                                                    )
                                                    Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                            )
                                            .padding(vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = formatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            } else {
                                items(logs, key = { it.id }) { line ->
                                    Text(
                                        line.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        if (!followNewest && itemCount > 0) {
                            TextButton(
                                onClick = {
                                    followNewest = true
                                    scope.launch { listState.scrollToItem(itemCount - 1) }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) { Text("Jump to latest") }
                        }
                    }
                    if (showChat) {
                        val macros = listOf("/afk", "/spawn", "/home", "/tpa yes", "/rules", "/ping")
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(macros) { cmd ->
                                AssistChip(
                                    onClick = {
                                        if (state == "connected") {
                                            session.sendChat(cmd) { err ->
                                                scope.launch { snackbarHostState.showSnackbar(err) }
                                            }
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("Not connected") }
                                        }
                                    },
                                    label = {
                                        Text(
                                            cmd,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = Color(0xFF7FD18B),
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier.weight(1f).focusRequester(chatFocusRequester),
                                label = { Text("Type a message or /command") },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = StrongOutlineColor,
                                    focusedBorderColor = Color(0xFF7FD18B),
                                    focusedLabelColor = Color(0xFF7FD18B),
                                    cursorColor = Color(0xFF7FD18B),
                                ),
                                minLines = 1,
                                maxLines = 5,
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
                    Text("Please sign in to your Microsoft account to verify this device, then return to the app.")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Code: $code", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                        TextButton(onClick = {
                            val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("device code", code))
                        }) { Text("Copy code") }
                    }
                    Text(url, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Open browser") }
            },
            dismissButton = {
                TextButton(onClick = { session.authRequired.value = null }) { Text("Done") }
            },
        )
    }
}

/** One bottom-sheet row: a window slot plus collapsed duplicates (more). */

private fun formatAfkTime(totalSeconds: Long): String {    val h = totalSeconds / 3600
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

private fun buildConfig(server: ServerEntity): String =
    JSONObject()
        .put("host", server.host)
        .put("port", server.port)
        .put("chatCommand", server.chatCommand.trim())
        .put("commandDelaySeconds", if (server.commandDelaySeconds > 0) server.commandDelaySeconds else 5)
        .put("auth", if (server.onlineMode) "microsoft" else "offline")
        .put("username", server.username.ifBlank { "AFKBot" })
        .put("viewDistance", server.viewDistance.coerceIn(2, 12))
        .put("chatMode", if (server.chatMode in CALLABLE_CHAT_MODES) server.chatMode else "enabled")
        .put("autoJump", server.autoJump)
        .put("loopCommand", server.loopCommand)
        .put("protocolOverride", server.protocolOverride.coerceIn(0, 1_000_000))
        .put("disconnectOnLowHealth", server.disconnectOnLowHealth)
        .put("lowHealthThreshold", server.lowHealthThreshold)
        .put("disconnectOnDeath", server.disconnectOnDeath)
        .put("disconnectOnKick", server.disconnectOnKick)
        .put("notifyOnMention", server.notifyOnMention)
        .toString()

private val CALLABLE_CHAT_MODES = setOf("enabled", "commandsOnly", "messagesOnly", "hidden")