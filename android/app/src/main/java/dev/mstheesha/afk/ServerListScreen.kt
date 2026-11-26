package dev.mstheesha.afk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Home server command center. No nested [Scaffold] — the Add Server FAB is
 * hosted in [AfkApp]'s single top-level Scaffold so insets and snackbars align.
 */
@Composable
fun ServerListScreen(
    servers: List<ServerEntity>,
    snackbarHostState: SnackbarHostState,
    showAddSheet: Boolean,
    onDismissAddSheet: () -> Unit,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val dao = AppGraph.db.serverDao()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ServerEntity?>(null) }
    var deleting by remember { mutableStateOf<ServerEntity?>(null) }

    if (servers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
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
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    onRequestDelete = { deleting = server },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showAddSheet) {
        val draft = remember { mutableStateOf(ServerDraft()) }
        ServerEditorSheet(
            title = "Add server",
            draft = draft.value,
            onChange = { draft.value = it },
            onDismiss = onDismissAddSheet,
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
                    onDismissAddSheet()
                }
            },
        )
    }

    editing?.let { server ->
        val ed = remember(server.id) { mutableStateOf(ServerDraft.from(server)) }
        ServerEditorSheet(
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

    deleting?.let { server ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete server?") },
            text = {
                Text("Remove \"${server.name}\" (${server.host}:${server.port}) and stop its session if running?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = server
                        deleting = null
                        AppGraph.removeSession(target.id)
                        scope.launch { dao.delete(target) }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ServerRow(
    server: ServerEntity,
    onSelect: (Long) -> Unit,
    onQuickToggle: (start: Boolean) -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
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

    val reduced = LocalReducedMotion.current
    val effects = AfkMotion.defaultEffects<androidx.compose.ui.graphics.Color>()
    val cardColor by animateColorAsState(
        targetValue = if (isConnected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = if (reduced) tween(0) else effects,
        label = "card_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isConnected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        },
        animationSpec = if (reduced) tween(0) else effects,
        label = "card_border",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor),
        onClick = { onSelect(server.id) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerAvatar(
                host = server.host,
                port = server.port,
                name = server.name,
                size = 48.dp,
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
                            .background(statusColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }
                if (isConnected) {
                    val hearts = String.format(Locale.US, "%.0f", (health / 2f).coerceAtLeast(0f))
                    Text(
                        "♥ $hearts/10 · ${formatBytes(dataBytes)}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            MorphingToggleButton(
                state = state,
                onClick = { onQuickToggle(!isActive) },
            )

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
                            onRequestDelete()
                        },
                    )
                }
            }
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

/**
 * Keyboard-friendly Material 3 ModalBottomSheet for adding or editing a server
 * (replaces the rigid AlertDialog) with smooth [AnimatedVisibility] on offline username.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerEditorSheet(
    title: String,
    draft: ServerDraft,
    onChange: (ServerDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Name") },
                placeholder = { Text("My Survival SMP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft.host,
                    onValueChange = { raw ->
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
                    modifier = Modifier.weight(1.7f),
                )
                OutlinedTextField(
                    value = draft.port,
                    onValueChange = { onChange(draft.copy(port = it)) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !draft.portValid,
                    modifier = Modifier.weight(0.9f),
                )
            }
            if (!draft.portValid) {
                Text(
                    "Port must be between 1 and 65535",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Microsoft account (online)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Turn off for offline / cracked servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = draft.onlineMode,
                    onCheckedChange = { onChange(draft.copy(onlineMode = it)) },
                )
            }

            AnimatedVisibility(visible = !draft.onlineMode) {
                OutlinedTextField(
                    value = draft.username,
                    onValueChange = { onChange(draft.copy(username = it)) },
                    label = { Text("Offline username") },
                    placeholder = { Text("AFKBot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    enabled = draft.host.isNotBlank() && draft.portValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
