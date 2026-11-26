package dev.mstheesha.afk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun SessionScreen(
    serverId: Long,
    isDark: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
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
    val liveKickedCount by session.kickedCount.collectAsState()

    val afkSeconds by session.afkSeconds.collectAsState()
    val sessionData by session.sessionDataBytes.collectAsState()
    val bytesIn by session.bytesIn.collectAsState()
    val bytesOut by session.bytesOut.collectAsState()

    // Reactive Prefs flows so AppSettingsSheet toggles update live immediately.
    val showTimestamps by Prefs.chatTimestampsFlow.collectAsState()
    val quickCommands by Prefs.quickCommandsFlow.collectAsState()

    var lastSeenKicks by remember(serverId) { mutableIntStateOf(liveKickedCount) }
    var chatInput by remember(serverId) { mutableStateOf("") }
    var showChat by remember(serverId) { mutableStateOf(false) }
    var lastSeenChatSeq by remember(serverId) { mutableIntStateOf(chat.lastOrNull()?.seq ?: 0) }
    var showSettings by remember(serverId) { mutableStateOf(false) }
    val recentSent = remember(serverId) { mutableStateListOf<String>() }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val latestChatSeq = chat.lastOrNull()?.seq ?: 0
    val hasUnreadChat = !showChat && latestChatSeq != 0 && latestChatSeq != lastSeenChatSeq
    LaunchedEffect(showChat, latestChatSeq) {
        if (showChat) lastSeenChatSeq = latestChatSeq
    }

    val scope = rememberCoroutineScope()
    val canStart = state == "disconnected" || state == "error"
    val isWorking = state == "connecting" || state == "authenticating" || state == "reconnecting"

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

    // Separate independent LazyListStates for Log and Chat (no scroll jumps).
    val logListState = rememberLazyListState()
    val chatListState = rememberLazyListState()
    var followLog by remember(serverId) { mutableStateOf(true) }
    var followChat by remember(serverId) { mutableStateOf(true) }

    val logAtBottom by remember {
        derivedStateOf {
            val info = logListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index >= info.totalItemsCount - 1
        }
    }
    val chatAtBottom by remember {
        derivedStateOf {
            val info = chatListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index >= info.totalItemsCount - 1
        }
    }

    LaunchedEffect(logs.size, followLog) {
        if (followLog && logs.isNotEmpty()) logListState.scrollToItem(logs.lastIndex)
    }
    LaunchedEffect(chat.size, followChat) {
        if (followChat && chat.isNotEmpty()) chatListState.scrollToItem(chat.lastIndex)
    }
    LaunchedEffect(logListState.isScrollInProgress) {
        if (logListState.isScrollInProgress && !logAtBottom) followLog = false
    }
    LaunchedEffect(chatListState.isScrollInProgress) {
        if (chatListState.isScrollInProgress && !chatAtBottom) followChat = false
    }
    LaunchedEffect(logAtBottom) { if (logAtBottom) followLog = true }
    LaunchedEffect(chatAtBottom) { if (chatAtBottom) followChat = true }

    val imeVisible = WindowInsets.isImeVisible
    val compactHeader = imeVisible && showChat
    LaunchedEffect(imeVisible) {
        if (imeVisible && showChat && chat.isNotEmpty()) {
            followChat = true
            chatListState.scrollToItem(chat.lastIndex)
        }
    }

    LaunchedEffect(serverId) {
        session.flushBuffersIfDirty()
        while (true) {
            delay(1000)
            session.refreshCounters()
        }
    }

    // Reactive kick alert — never misses a kick event.
    LaunchedEffect(liveKickedCount) {
        if (liveKickedCount > lastSeenKicks) {
            snackbarHostState.showSnackbar("Kicked or disconnected: ${session.detail.value}")
        }
        lastSeenKicks = liveKickedCount
    }

    val spatialSize = AfkMotion.defaultSpatial<IntSize>()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val srv = server
        if (srv == null) {
            Text("Select a server from the list first.")
        } else {
            LaunchedEffect(srv.id, srv.name) {
                AppGraph.noteServerName(srv.id, srv.name)
            }

            val statusDotColor = when (state) {
                "connected" -> MaterialTheme.colorScheme.primary
                "connecting", "authenticating", "reconnecting" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            // Keyboard-aware status card: full telemetry normally, 1-line compact bar when typing.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = if (reduced) tween(0) else spatialSize),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                AnimatedContent(
                    targetState = compactHeader,
                    transitionSpec = {
                        fadeIn(tween(if (reduced) 0 else 160)) togetherWith
                            fadeOut(tween(if (reduced) 0 else 120))
                    },
                    label = "header_collapse",
                ) { isCompact ->
                    if (isCompact) {
                        // 1-line compact telemetry strip while keyboard is open in Chat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                AnimatedStatusDot(color = statusDotColor, pulsing = isWorking, size = 8.dp)
                                Text(
                                    stateLabel(state),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                                if (state == "connected") {
                                    val heartsStr = String.format(Locale.US, "%.0f", (health / 2f).coerceAtLeast(0f))
                                    Text(
                                        "♥$heartsStr · ${formatAfkTime(afkSeconds)}",
                                        style = MaterialTheme.typography.labelSmall.tabular(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Server settings", modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        // Full expressive telemetry card
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                        if (isWorking) {
                                            ExpressiveLoadingIndicator(color = statusDotColor, size = 16.dp)
                                        } else {
                                            AnimatedStatusDot(
                                                color = statusDotColor,
                                                pulsing = state == "connected",
                                                size = 9.dp,
                                            )
                                        }
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
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 28.dp),
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

                            AnimatedVisibility(visible = state == "connected") {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val heartsStr = String.format(Locale.US, "%.1f", (health / 2f).coerceAtLeast(0f))
                                        .removeSuffix(".0")
                                    val lowHp = health <= 6f
                                    VitalsPill(
                                        icon = Icons.Default.Favorite,
                                        text = "$heartsStr/10",
                                        tint = if (lowHp) MaterialTheme.colorScheme.error else Color(0xFFE53935),
                                        pulse = lowHp,
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

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
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
                                        AnimatedTimerText(formatAfkTime(afkSeconds))
                                    }
                                }
                                VerticalDivider(modifier = Modifier.height(34.dp))
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
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                            ).tabular(),
                                        )
                                        Text(
                                            "↓${formatBytes(bytesIn)}  ↑${formatBytes(bytesOut)}",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Connected Expressive ButtonGroup — hidden while typing in Chat to maximize chat room
            AnimatedVisibility(visible = !compactHeader) {
                ExpressiveConnectedButtonGroup(
                    items = listOf(
                        ConnectedButtonItem(
                            label = "Start",
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            enabled = canStart,
                            isLoading = isWorking,
                            baseWeight = 1.25f,
                            onClick = {
                                if (!AppGraph.startServer(ctx, srv)) {
                                    val cap = Prefs.maxBots(ctx)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Maximum $cap servers running (adjust in App settings)")
                                    }
                                }
                            },
                        ),
                        ConnectedButtonItem(
                            label = null,
                            icon = Icons.Default.Refresh,
                            contentDescription = "Reconnect now",
                            enabled = state != "disconnected",
                            baseWeight = 0.72f,
                            onClick = { session.reconnect() },
                        ),
                        ConnectedButtonItem(
                            label = "Stop",
                            icon = Icons.Default.Stop,
                            contentDescription = "Stop",
                            enabled = state != "disconnected",
                            isDestructive = true,
                            baseWeight = 1.15f,
                            onClick = { session.stop() },
                        ),
                    ),
                )
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

        // Console header: animated pill segmented switcher + Clear button
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedSegmentedTabs(
                selectedIndex = if (showChat) 1 else 0,
                onSelect = { idx -> showChat = (idx == 1) },
                hasUnreadChat = hasUnreadChat,
            )
            IconButton(onClick = { if (showChat) session.clearChat() else session.clearLogs() }) {
                Icon(
                    Icons.Default.CleaningServices,
                    contentDescription = if (showChat) "Clear chat" else "Clear logs",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Console card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AnimatedContent(
                        targetState = showChat,
                        transitionSpec = {
                            fadeIn(tween(if (reduced) 0 else 150)) togetherWith
                                fadeOut(tween(if (reduced) 0 else 100))
                        },
                        label = "console_pane",
                    ) { isChatTab ->
                        if (isChatTab) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                state = chatListState,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
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
                                    val accentBarColor = when (line.type) {
                                        "out" -> MaterialTheme.colorScheme.primary
                                        "system" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                                        "error" -> MaterialTheme.colorScheme.error
                                        else -> null
                                    }
                                    val formatted = remember(line.styledText, prefix, sender, baseColor, isDark) {
                                        formatMinecraftChat("$prefix$sender${line.styledText}", baseColor, isDark)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .clip(RoundedCornerShape(6.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                                    cm.setPrimaryClip(ClipData.newPlainText("chat", line.text))
                                                    scope.launch { snackbarHostState.showSnackbar("Copied message") }
                                                },
                                            )
                                            .padding(vertical = 1.dp),
                                    ) {
                                        if (accentBarColor != null) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(accentBarColor),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = formatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = baseColor,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                state = logListState,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(logs, key = { it.id }) { line ->
                                    val ts = if (showTimestamps) "[${timeFmt.format(Date(line.ts))}] " else ""
                                    Text(
                                        "$ts${line.text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                                    cm.setPrimaryClip(ClipData.newPlainText("log", line.text))
                                                    scope.launch { snackbarHostState.showSnackbar("Copied log line") }
                                                },
                                            )
                                            .padding(vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }

                    val showJump = if (showChat) !followChat && chat.isNotEmpty() else !followLog && logs.isNotEmpty()
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showJump,
                        enter = if (reduced) fadeIn(tween(100)) else (slideInVertically { it / 2 } + fadeIn() + scaleIn(initialScale = 0.9f)),
                        exit = if (reduced) fadeOut(tween(80)) else (slideOutVertically { it / 2 } + fadeOut() + scaleOut(targetScale = 0.9f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 2.dp,
                        ) {
                            TextButton(
                                onClick = {
                                    if (showChat) {
                                        followChat = true
                                        scope.launch { chatListState.animateScrollToItem(chat.lastIndex) }
                                    } else {
                                        followLog = true
                                        scope.launch { logListState.animateScrollToItem(logs.lastIndex) }
                                    }
                                },
                            ) {
                                Text(
                                    "Jump to latest",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showChat) {
                    Column {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(chatFocusRequester),
                                label = { Text("Message or /command") },
                                minLines = 1,
                                maxLines = 4,
                                shape = RoundedCornerShape(16.dp),
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
