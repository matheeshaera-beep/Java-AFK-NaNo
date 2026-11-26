package dev.mstheesha.afk

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DrawerContent(
    servers: List<ServerEntity>,
    activeCount: Int,
    selectedId: Long?,
    drawerOpen: Boolean,
    onSelectServer: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    val maxCap by Prefs.maxBotsFlow.collectAsState()
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
                    style = MaterialTheme.typography.labelSmall.tabular(),
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
                            AnimatedStatusDot(
                                color = dotColor,
                                pulsing = st == "connecting" || st == "reconnecting" || st == "authenticating",
                                size = 8.dp,
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
                    Text("CPU %.0f%%".format(smoothCpu), style = MaterialTheme.typography.labelMedium.tabular())
                }
                LinearProgressIndicator(
                    progress = { cpuAnim },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Memory, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("RAM $ram MB", style = MaterialTheme.typography.labelMedium.tabular())
                }
                LinearProgressIndicator(
                    progress = { ramAnim },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
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
