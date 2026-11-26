package dev.mstheesha.afk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mstheesha.afk.engine.ChatTrigger
import dev.mstheesha.afk.engine.VersionCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipeable 4-tab Material 3 Expressive bottom sheet for per-server settings:
 * General · Commands · AFK · Safety.
 *
 * Each page owns its own [rememberScrollState], and pending edits are always
 * flushed synchronously on dismiss/dispose via [AppGraph.ioScope] so rapid
 * toggles are never lost.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerSettingsSheet(
    server: ServerEntity,
    session: ServerSession,
    snackbarHostState: SnackbarHostState,
    onOpenAccounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dao = AppGraph.db.serverDao()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accounts by EngineHost.accountsFlow().collectAsState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

    var protocolValue by remember(server.id) {
        mutableIntStateOf(
            if (server.protocolOverride == 0 || VersionCatalog.nameOf(server.protocolOverride) != null) {
                server.protocolOverride
            } else {
                0
            },
        )
    }
    var protocolPicked by remember(server.id) { mutableStateOf(false) }
    var viewDistance by remember(server.id) { mutableIntStateOf(server.viewDistance.coerceIn(2, 12)) }
    var chatMode by remember(server.id) { mutableStateOf(server.chatMode) }
    var accountKey by remember(server.id) { mutableStateOf(server.accountKey) }

    var chatCommand by remember(server.id) { mutableStateOf(server.chatCommand) }
    var delayText by remember(server.id) { mutableStateOf(server.commandDelaySeconds.toString()) }
    var loopCommand by remember(server.id) { mutableStateOf(server.loopCommand) }
    var triggers by remember(server.id) { mutableStateOf(ServerSession.parseTriggers(server.chatTriggersJson)) }

    var antiAfkMode by remember(server.id) {
        mutableStateOf(server.antiAfkMode.ifBlank { if (server.autoJump) "jump" else "off" })
    }
    var autoRespawn by remember(server.id) { mutableStateOf(server.autoRespawn) }
    var autoStopMinutes by remember(server.id) { mutableIntStateOf(server.autoStopMinutes) }

    var lowHealthThreshold by remember(server.id) { mutableIntStateOf(server.lowHealthDisconnect) }
    var alertsMask by remember(server.id) { mutableIntStateOf(server.alertsMask) }
    var alertKeyword by remember(server.id) { mutableStateOf(server.alertKeyword) }
    var webhookUrl by remember(server.id) { mutableStateOf(server.webhookUrl) }

    fun snapshotEntity(base: ServerEntity): ServerEntity = base.copy(
        protocolOverride = if (protocolPicked) protocolValue else base.protocolOverride,
        viewDistance = viewDistance.coerceIn(2, 12),
        chatMode = chatMode,
        accountKey = accountKey,
        chatCommand = chatCommand,
        commandDelaySeconds = delayText.toIntOrNull()?.coerceIn(1, 3600) ?: base.commandDelaySeconds,
        loopCommand = loopCommand,
        autoJump = antiAfkMode != "off",
        antiAfkMode = antiAfkMode,
        autoRespawn = autoRespawn,
        autoStopMinutes = autoStopMinutes,
        lowHealthDisconnect = lowHealthThreshold.coerceIn(0, 18),
        chatTriggersJson = ServerSession.serializeTriggers(triggers),
        alertsMask = alertsMask,
        alertKeyword = alertKeyword,
        webhookUrl = webhookUrl.trim(),
    )

    val latestSnapshotBuilder by rememberUpdatedState { base: ServerEntity -> snapshotEntity(base) }

    fun flushNow() {
        val builder = latestSnapshotBuilder
        val id = server.id
        AppGraph.ioScope.launch {
            val current = dao.get(id) ?: return@launch
            val updated = builder(current)
            if (updated != current) {
                dao.update(updated)
                session.pushLiveSettings(updated)
            }
        }
    }

    // Debounced live save while the sheet stays open.
    val saveKey = listOf(
        protocolValue, protocolPicked, viewDistance, chatMode, accountKey,
        chatCommand, delayText, loopCommand, triggers,
        antiAfkMode, autoRespawn, autoStopMinutes,
        lowHealthThreshold, alertsMask, alertKeyword, webhookUrl,
    )
    LaunchedEffect(saveKey) {
        delay(450)
        val current = dao.get(server.id) ?: return@LaunchedEffect
        val updated = snapshotEntity(current)
        if (updated != current) {
            dao.update(updated)
            session.pushLiveSettings(updated)
        }
    }

    // Guarantee zero lost edits if the user taps Done or swipes down within 450 ms.
    DisposableEffect(server.id) {
        onDispose { flushNow() }
    }

    fun dismissAndFlush() {
        flushNow()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::dismissAndFlush,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${server.host}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = ::dismissAndFlush) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            val tabs = listOf("General", "Commands", "AFK", "Safety")
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = pagerState.currentPage == idx,
                        onClick = {
                            if (pagerState.currentPage != idx) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    if (reduced) pagerState.scrollToPage(idx)
                                    else pagerState.animateScrollToPage(idx)
                                }
                            }
                        },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 340.dp, max = 520.dp),
                verticalAlignment = Alignment.Top,
            ) { page ->
                // Each page owns its own independent ScrollState.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (page) {
                        0 -> GeneralSettingsTab(
                            server = server,
                            protocolValue = protocolValue,
                            onPickProtocol = { v ->
                                protocolPicked = true
                                protocolValue = v
                            },
                            viewDistance = viewDistance,
                            onViewDistance = { viewDistance = it },
                            chatMode = chatMode,
                            onChatMode = { chatMode = it },
                            accountKey = accountKey,
                            accounts = accounts,
                            onAccountKey = { accountKey = it },
                            onOpenAccounts = {
                                flushNow()
                                onOpenAccounts()
                            },
                            onClearToken = {
                                val key = accountKey.ifBlank { KeystoreTokenStore.DEFAULT_KEY }
                                session.clearToken(key)
                                scope.launch { snackbarHostState.showSnackbar("Saved login token cleared") }
                            },
                        )
                        1 -> CommandsSettingsTab(
                            chatCommand = chatCommand,
                            onChatCommand = { chatCommand = it },
                            delayText = delayText,
                            onDelayText = { delayText = it },
                            loopCommand = loopCommand,
                            onLoopCommand = { loopCommand = it },
                            triggers = triggers,
                            onTriggers = { triggers = it },
                            onDeleteTrigger = { idx ->
                                val removed = triggers[idx]
                                triggers = triggers.filterIndexed { i, _ -> i != idx }
                                scope.launch {
                                    val res = snackbarHostState.showSnackbar(
                                        message = "Removed trigger \"${removed.match}\"",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        val insertAt = idx.coerceIn(0, triggers.size)
                                        triggers = triggers.toMutableList().apply { add(insertAt, removed) }
                                    }
                                }
                            },
                        )
                        2 -> AfkSettingsTab(
                            antiAfkMode = antiAfkMode,
                            onAntiAfkMode = { antiAfkMode = it },
                            autoRespawn = autoRespawn,
                            onAutoRespawn = { autoRespawn = it },
                            autoStopMinutes = autoStopMinutes,
                            onAutoStopMinutes = { autoStopMinutes = it },
                        )
                        3 -> SafetySettingsTab(
                            lowHealthThreshold = lowHealthThreshold,
                            onLowHealthThreshold = { lowHealthThreshold = it },
                            alertsMask = alertsMask,
                            onAlertsMask = { alertsMask = it },
                            alertKeyword = alertKeyword,
                            onAlertKeyword = { alertKeyword = it },
                            webhookUrl = webhookUrl,
                            onWebhookUrl = { webhookUrl = it },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralSettingsTab(
    server: ServerEntity,
    protocolValue: Int,
    onPickProtocol: (Int) -> Unit,
    viewDistance: Int,
    onViewDistance: (Int) -> Unit,
    chatMode: String,
    onChatMode: (String) -> Unit,
    accountKey: String,
    accounts: List<AccountInfo>,
    onAccountKey: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onClearToken: () -> Unit,
) {
    var versionMenuOpen by remember { mutableStateOf(false) }

    SettingsCard("Connection") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Minecraft version", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Auto-detect works on most servers. Applies on next Start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { versionMenuOpen = true }) {
                    Text(
                        VersionCatalog.nameOf(protocolValue) ?: "Auto",
                        maxLines = 1,
                    )
                }
                DropdownMenu(
                    expanded = versionMenuOpen,
                    onDismissRequest = { versionMenuOpen = false },
                ) {
                    Column(
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        VersionPickerItem("Auto-detect (recommended)", protocolValue == 0) {
                            onPickProtocol(0)
                            versionMenuOpen = false
                        }
                        VersionCatalog.releases.asReversed().forEach { r ->
                            VersionPickerItem("${r.name} (${r.protocol})", protocolValue == r.protocol) {
                                onPickProtocol(r.protocol)
                                versionMenuOpen = false
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("View distance", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (viewDistance == 2) "2 chunks · min data" else "$viewDistance chunks",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = viewDistance.toFloat(),
                onValueChange = { onViewDistance(it.roundToInt().coerceIn(2, 12)) },
                valueRange = 2f..12f,
                steps = 9,
            )
            Text(
                "Lower saves battery & data; 2 chunks is enough for AFK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SettingsCard(
        title = "Incoming chat",
        subtitle = "Filtered at protocol level to save bandwidth. Sending always works.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "enabled" to "All",
                "commandsOnly" to "Commands only",
                "messagesOnly" to "Players only",
                "hidden" to "Hidden",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = chatMode == value,
                    onClick = { onChatMode(value) },
                    label = { Text(label) },
                )
            }
        }
    }

    SettingsCard("Account") {
        if (server.onlineMode) {
            var accountMenuOpen by remember { mutableStateOf(false) }
            val activeKey = accountKey.ifBlank { KeystoreTokenStore.DEFAULT_KEY }
            val activeLabel = accounts.firstOrNull { it.key == activeKey }?.name
                ?: if (activeKey == KeystoreTokenStore.DEFAULT_KEY) "Default Microsoft account" else activeKey
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Microsoft account", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        activeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    OutlinedButton(onClick = { accountMenuOpen = true }) {
                        Text("Switch")
                    }
                    DropdownMenu(
                        expanded = accountMenuOpen,
                        onDismissRequest = { accountMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default slot") },
                            onClick = {
                                onAccountKey("")
                                accountMenuOpen = false
                            },
                            trailingIcon = if (activeKey == KeystoreTokenStore.DEFAULT_KEY) {
                                { Icon(Icons.Default.Check, null) }
                            } else null,
                        )
                        accounts.filter { it.key != KeystoreTokenStore.DEFAULT_KEY }.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    onAccountKey(acc.key)
                                    accountMenuOpen = false
                                },
                                trailingIcon = if (activeKey == acc.key) {
                                    { Icon(Icons.Default.Check, null) }
                                } else null,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAccounts) {
                    Icon(Icons.Default.ManageAccounts, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage accounts")
                }
                TextButton(onClick = onClearToken) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear token")
                }
            }
        } else {
            Text(
                "Offline mode · Username: ${server.username.ifBlank { "AFKBot" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Switch to Microsoft account from the server Edit menu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommandsSettingsTab(
    chatCommand: String,
    onChatCommand: (String) -> Unit,
    delayText: String,
    onDelayText: (String) -> Unit,
    loopCommand: Boolean,
    onLoopCommand: (Boolean) -> Unit,
    triggers: List<ChatTrigger>,
    onTriggers: (List<ChatTrigger>) -> Unit,
    onDeleteTrigger: (Int) -> Unit,
) {
    var showAddTrigger by remember { mutableStateOf(false) }

    SettingsCard(
        title = "Startup commands",
        subtitle = "One command or message per line — sent in order after joining.",
    ) {
        OutlinedTextField(
            value = chatCommand,
            onValueChange = onChatCommand,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/login mypass\n/server survival\n/home farm") },
            minLines = 2,
            maxLines = 5,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = delayText,
                onValueChange = onDelayText,
                modifier = Modifier.weight(1f),
                label = { Text("Delay (sec)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Repeat", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = loopCommand, onCheckedChange = onLoopCommand)
            }
        }
    }

    SettingsCard(
        title = "Chat auto-triggers",
        subtitle = "Automatically reply when chat contains specific text (e.g. lobby kicks or /tpa).",
    ) {
        if (triggers.isEmpty()) {
            Text(
                "No triggers configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            triggers.forEachIndexed { idx, t ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "\"${t.match}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "→ ${t.command}  (${t.delaySeconds}s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onDeleteTrigger(idx) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove trigger")
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { showAddTrigger = true }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add trigger")
        }
    }

    if (showAddTrigger) {
        var matchText by remember { mutableStateOf("") }
        var cmdText by remember { mutableStateOf("") }
        var trigDelay by remember { mutableStateOf("2") }
        AlertDialog(
            onDismissRequest = { showAddTrigger = false },
            title = { Text("Add chat trigger") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = matchText,
                        onValueChange = { matchText = it },
                        label = { Text("When chat contains") },
                        placeholder = { Text("kicked to lobby") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = cmdText,
                        onValueChange = { cmdText = it },
                        label = { Text("Send command or text") },
                        placeholder = { Text("/server survival") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = trigDelay,
                        onValueChange = { trigDelay = it },
                        label = { Text("Delay (seconds)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = matchText.isNotBlank() && cmdText.isNotBlank(),
                    onClick = {
                        val d = trigDelay.toIntOrNull()?.coerceIn(0, 120) ?: 2
                        onTriggers(triggers + ChatTrigger(matchText.trim(), cmdText.trim(), d))
                        showAddTrigger = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddTrigger = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AfkSettingsTab(
    antiAfkMode: String,
    onAntiAfkMode: (String) -> Unit,
    autoRespawn: Boolean,
    onAutoRespawn: (Boolean) -> Unit,
    autoStopMinutes: Int,
    onAutoStopMinutes: (Int) -> Unit,
) {
    SettingsCard(
        title = "Anti-AFK movement",
        subtitle = "Runs on a randomized 20–40 s schedule to look natural.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "off" to "Off",
                "jump" to "Jump",
                "look" to "Look",
                "mix" to "Mix (Jump + Look)",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = antiAfkMode == value,
                    onClick = { onAntiAfkMode(value) },
                    label = { Text(label) },
                )
            }
        }
        val desc = when (antiAfkMode) {
            "off" -> "Bot stays completely still (only answers server keep-alives)."
            "look" -> "Subtle random head turns without changing coordinates."
            "mix" -> "Alternates between gentle jumps and subtle head turns."
            else -> "Small in-place jumps every 20–40 s."
        }
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsCard("Session lifecycle") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Auto-respawn on death", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Turn off to disconnect and stay safe if killed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoRespawn, onCheckedChange = onAutoRespawn)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Auto-stop timer", style = MaterialTheme.typography.bodyLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    0 to "Off",
                    60 to "1h",
                    120 to "2h",
                    240 to "4h",
                    480 to "8h",
                    720 to "12h",
                ).forEach { (mins, label) ->
                    FilterChip(
                        selected = autoStopMinutes == mins,
                        onClick = { onAutoStopMinutes(mins) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetySettingsTab(
    lowHealthThreshold: Int,
    onLowHealthThreshold: (Int) -> Unit,
    alertsMask: Int,
    onAlertsMask: (Int) -> Unit,
    alertKeyword: String,
    onAlertKeyword: (String) -> Unit,
    webhookUrl: String,
    onWebhookUrl: (String) -> Unit,
) {
    var webhookExpanded by remember { mutableStateOf(webhookUrl.isNotBlank()) }

    SettingsCard("Low-health protection") {
        val enabled = lowHealthThreshold > 0
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Disconnect on low health", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Stops bot immediately if hurt by mobs or players.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on -> onLowHealthThreshold(if (on) 8 else 0) },
            )
        }
        AnimatedVisibility(visible = enabled) {
            Column {
                val hearts = (lowHealthThreshold.coerceIn(2, 18)) / 2
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Disconnect at or below", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "♥ $hearts / 10 hearts",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Slider(
                    value = lowHealthThreshold.coerceIn(2, 18).toFloat(),
                    onValueChange = {
                        val even = ((it / 2f).roundToInt() * 2).coerceIn(2, 18)
                        onLowHealthThreshold(even)
                    },
                    valueRange = 2f..18f,
                    steps = 7,
                )
            }
        }
    }

    SettingsCard(
        title = "Alerts",
        subtitle = "Send a phone notification (and webhook if set) when these happen.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                AlertFlags.KICK to "Kick / Ban",
                AlertFlags.DEATH to "Death",
                AlertFlags.LOW_HEALTH to "Low HP",
                AlertFlags.WHISPER to "Whispers",
                AlertFlags.KEYWORD to "Keyword",
            ).forEach { (flag, label) ->
                val checked = (alertsMask and flag) != 0
                FilterChip(
                    selected = checked,
                    onClick = {
                        onAlertsMask(if (checked) alertsMask and flag.inv() else alertsMask or flag)
                    },
                    label = { Text(label) },
                )
            }
        }
        AnimatedVisibility(visible = (alertsMask and AlertFlags.KEYWORD) != 0) {
            OutlinedTextField(
                value = alertKeyword,
                onValueChange = onAlertKeyword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Alert keyword") },
                placeholder = { Text("e.g. your username or admin") },
                singleLine = true,
            )
        }
    }

    SettingsCard("Discord webhook (optional)") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (webhookUrl.isNotBlank()) "Configured" else "Forward alerts to Discord",
                style = MaterialTheme.typography.bodyMedium,
                color = if (webhookUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { webhookExpanded = !webhookExpanded }) {
                Icon(
                    if (webhookExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle webhook",
                )
            }
        }
        AnimatedVisibility(visible = webhookExpanded) {
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = onWebhookUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Webhook URL") },
                placeholder = { Text("https://discord.com/api/webhooks/...") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun VersionPickerItem(label: String, selected: Boolean, onClick: () -> Unit) {
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
        } else null,
    )
}
