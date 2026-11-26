package dev.mstheesha.afk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

/**
 * Clean bottom sheet for managing multiple encrypted Microsoft accounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accounts by EngineHost.accountsFlow().collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var signingIn by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<Pair<String, String>?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }

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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Microsoft accounts", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Stored with Android Keystore AES-GCM encryption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            if (accounts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No Microsoft accounts signed in yet. Add one here or start any online-mode server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                accounts.forEach { acc ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    acc.name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (acc.key == KeystoreTokenStore.DEFAULT_KEY) "Default slot"
                                    else "Alt · ${acc.uuid.take(8)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { EngineHost.clearTokens(acc.key) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove account")
                            }
                        }
                    }
                }
            }

            if (statusError != null) {
                Text(
                    statusError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    if (signingIn) return@Button
                    signingIn = true
                    statusError = null
                    val slotKey = if (accounts.none { it.key == KeystoreTokenStore.DEFAULT_KEY }) {
                        KeystoreTokenStore.DEFAULT_KEY
                    } else {
                        "acct_${UUID.randomUUID().toString().take(8)}"
                    }
                    EngineHost.signInAccount(
                        accountKey = slotKey,
                        onCode = { url, code -> deviceCode = url to code },
                        onResult = { res ->
                            signingIn = false
                            deviceCode = null
                            res.onFailure { statusError = it.message ?: "Sign-in failed" }
                        },
                    )
                },
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (signingIn) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Waiting for sign-in…")
                } else {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Microsoft account")
                }
            }
        }
    }

    deviceCode?.let { (url, code) ->
        AlertDialog(
            onDismissRequest = { deviceCode = null },
            title = { Text("Finish Microsoft sign-in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open $url and enter this code:")
                    Text(
                        code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", code))
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Copy & Open") }
            },
            dismissButton = {
                TextButton(onClick = { deviceCode = null }) { Text("Close") }
            },
        )
    }
}

/**
 * Clean bottom sheet for app-wide appearance, battery/network guardrails,
 * bot concurrency limit, and quick chat commands.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsSheet(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var wifiOnly by remember { mutableStateOf(Prefs.wifiOnly(context)) }
    var batteryGuard by remember { mutableIntStateOf(Prefs.batteryGuardPercent(context)) }
    var maxBots by remember { mutableIntStateOf(Prefs.maxBots(context)) }
    var timestamps by remember { mutableStateOf(Prefs.chatTimestamps(context)) }
    var quickCmdsText by remember { mutableStateOf(Prefs.quickCommands(context).joinToString("\n")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("App settings", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            // Appearance
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Material You colors", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Match wallpaper accent colors instead of signature green.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                    }
                }
            }

            // Network & Battery Guardrails
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Battery & network guardrails",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Pause reconnects on mobile data to avoid overnight data usage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { v ->
                                wifiOnly = v
                                Prefs.setWifiOnly(context, v)
                                if (AppGraph.hasActiveSessions()) {
                                    context.startService(
                                        Intent(context, AfkService::class.java)
                                            .setAction(AfkService.ACTION_REFRESH_GUARD),
                                    )
                                }
                            },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Auto-stop on low battery (unplugged)", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Off", 10 to "10%", 15 to "15%", 20 to "20%").forEach { (pct, label) ->
                                FilterChip(
                                    selected = batteryGuard == pct,
                                    onClick = {
                                        batteryGuard = pct
                                        Prefs.setBatteryGuardPercent(context, pct)
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Max concurrent bots", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Unlimited", 3 to "3", 6 to "6", 10 to "10").forEach { (cap, label) ->
                                FilterChip(
                                    selected = maxBots == cap,
                                    onClick = {
                                        maxBots = cap
                                        Prefs.setMaxBots(context, cap)
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            // Console preferences
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Chat console",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Show chat timestamps", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = timestamps,
                            onCheckedChange = {
                                timestamps = it
                                Prefs.setChatTimestamps(context, it)
                            },
                        )
                    }
                    OutlinedTextField(
                        value = quickCmdsText,
                        onValueChange = {
                            quickCmdsText = it
                            val list = it.split('\n').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                            Prefs.setQuickCommands(context, list)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Quick-command chips (one per line)") },
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            }
        }
    }
}
