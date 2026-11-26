package dev.mstheesha.afk

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bounded LRU memory cache for server favicons (replaces the old clear-all map).
 */
object FaviconCache {
    private val cache = object : LruCache<String, Bitmap>(64) {}

    operator fun get(key: String): Bitmap? = cache.get(key)
    operator fun set(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}

/**
 * Server favicon with smooth crossfade from initial-letter tile to fetched bitmap.
 */
@Composable
fun ServerAvatar(host: String, port: Int, name: String, size: Dp) {
    val ctx = LocalContext.current
    val cacheKey = "$host:$port"
    var favicon by remember(cacheKey) { mutableStateOf(FaviconCache[cacheKey]) }

    LaunchedEffect(cacheKey) {
        val cached = FaviconCache[cacheKey]
        if (cached != null) {
            favicon = cached
        } else {
            val bmp = withContext(Dispatchers.IO) {
                ServerPinger.fetchFavicon(ctx, host, port)
            }
            if (bmp != null) FaviconCache[cacheKey] = bmp
            favicon = bmp
        }
    }

    val reduced = LocalReducedMotion.current
    Crossfade(
        targetState = favicon,
        animationSpec = tween(if (reduced) 0 else 220),
        label = "favicon",
    ) { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Server icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Zenith-style Material 3 Expressive morphing polygon loading indicator.
 * Smoothly morphs between organic rounded polygons while rotating; degrades
 * to a calm static pulsing dot when Remove Animations is enabled.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 20.dp,
) {
    val reduced = LocalReducedMotion.current
    if (reduced) {
        Box(
            modifier = modifier
                .size(size * 0.55f)
                .clip(CircleShape)
                .background(color),
        )
        return
    }

    val infinite = rememberInfiniteTransition(label = "m3e_loader")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loader_rot",
    )
    val morph by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loader_morph",
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = angle },
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseR = this.size.minDimension * 0.42f
        // Morph smoothly between a 4-lobed clover and a 6-lobed scalloped star
        val lobes = 4f + 2f * morph
        val amp = 0.16f + 0.06f * (1f - morph)
        val steps = 48
        val path = Path()
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2.0 * PI)
            val r = baseR * (1f + amp * cos(lobes * theta).toFloat())
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = color)
    }
}

/**
 * Morphing quick-toggle button for Server List cards:
 * - Circle (24.dp) when stopped -> Rounded square (12.dp) when running
 * - Swaps icon smoothly between Play, ExpressiveLoadingIndicator, and Stop
 * - Subtle spring press scale & haptic feedback
 */
@Composable
fun MorphingToggleButton(
    state: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val isConnected = state == "connected"
    val isWorking = state == "connecting" || state == "authenticating" || state == "reconnecting"
    val isActive = isConnected || isWorking

    val spatial = AfkMotion.fastSpatial<Dp>()
    val floatSpatial = AfkMotion.fastSpatial<Float>()
    val effects = AfkMotion.fastEffects<Color>()

    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 10.dp
            isActive -> 12.dp
            else -> 22.dp
        },
        animationSpec = if (reduced) tween(0) else spatial,
        label = "toggle_corner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.92f else 1f,
        animationSpec = if (reduced) tween(0) else floatSpatial,
        label = "toggle_scale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.errorContainer
            isWorking -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = if (reduced) tween(0) else effects,
        label = "toggle_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.onErrorContainer
            isWorking -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = if (reduced) tween(0) else effects,
        label = "toggle_fg",
    )

    Surface(
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = when {
                    isWorking -> "working"
                    isConnected -> "stop"
                    else -> "start"
                },
                transitionSpec = {
                    if (reduced) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    } else {
                        (fadeIn(tween(150)) + scaleIn(initialScale = 0.78f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.78f))
                    }
                },
                label = "toggle_icon",
            ) { mode ->
                when (mode) {
                    "working" -> ExpressiveLoadingIndicator(color = contentColor, size = 20.dp)
                    "stop" -> Icon(Icons.Default.Stop, contentDescription = "Stop")
                    else -> Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                }
            }
        }
    }
}

/**
 * Connected Material 3 Expressive ButtonGroup for Session actions (Start · Reconnect · Stop):
 * Pressing any button smoothly expands its flex weight and morphs its corners while
 * gently nudging adjacent buttons aside.
 */
data class ConnectedButtonItem(
    val label: String?,
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val isDestructive: Boolean = false,
    val isLoading: Boolean = false,
    val baseWeight: Float = 1f,
    val onClick: () -> Unit,
)

@Composable
fun ExpressiveConnectedButtonGroup(
    items: List<ConnectedButtonItem>,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val floatSpatial = AfkMotion.fastSpatial<Float>()
    val dpSpatial = AfkMotion.fastSpatial<Dp>()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()

            val weight by animateFloatAsState(
                targetValue = if (pressed && item.enabled && !reduced) item.baseWeight * 1.18f else item.baseWeight,
                animationSpec = if (reduced) tween(0) else floatSpatial,
                label = "btn_w_$index",
            )
            val innerCorner by animateDpAsState(
                targetValue = if (pressed && item.enabled && !reduced) 16.dp else 8.dp,
                animationSpec = if (reduced) tween(0) else dpSpatial,
                label = "btn_c_$index",
            )
            val outerCorner = 24.dp
            val shape = when {
                items.size == 1 -> RoundedCornerShape(outerCorner)
                index == 0 -> RoundedCornerShape(
                    topStart = outerCorner,
                    bottomStart = outerCorner,
                    topEnd = innerCorner,
                    bottomEnd = innerCorner,
                )
                index == items.lastIndex -> RoundedCornerShape(
                    topStart = innerCorner,
                    bottomStart = innerCorner,
                    topEnd = outerCorner,
                    bottomEnd = outerCorner,
                )
                else -> RoundedCornerShape(innerCorner)
            }

            val colors = when {
                item.isDestructive -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                index == 0 -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                else -> ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    item.onClick()
                },
                enabled = item.enabled,
                interactionSource = interaction,
                shape = shape,
                colors = colors,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(weight)
                    .height(46.dp),
            ) {
                if (item.isLoading) {
                    ExpressiveLoadingIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        size = 18.dp,
                    )
                } else {
                    Icon(item.icon, contentDescription = item.contentDescription, modifier = Modifier.size(18.dp))
                }
                if (item.label != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * Pill-shaped segmented switcher for Log / Chat with a spring-driven sliding
 * indicator pill and an unread emerald dot on Chat when messages arrive off-tab.
 */
@Composable
fun AnimatedSegmentedTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hasUnreadChat: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val spatial = AfkMotion.fastSpatial<Dp>()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(36.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(176.dp)
                .padding(3.dp),
        ) {
            val pillWidth = maxWidth / 2
            val offsetX by animateDpAsState(
                targetValue = if (selectedIndex == 0) 0.dp else pillWidth,
                animationSpec = if (reduced) tween(0) else spatial,
                label = "seg_pill",
            )

            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )

            Row(Modifier.fillMaxSize()) {
                val labels = listOf("Log", "Chat")
                labels.forEachIndexed { idx, label ->
                    val selected = selectedIndex == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(17.dp))
                            .clickable {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(idx)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (idx == 1 && hasUnreadChat && !selected) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Breathing status indicator dot (static when reduced motion is enabled). */
@Composable
fun AnimatedStatusDot(
    color: Color,
    pulsing: Boolean,
    size: Dp = 9.dp,
) {
    val reduced = LocalReducedMotion.current
    val alpha = if (pulsing && !reduced) {
        val inf = rememberInfiniteTransition(label = "dot_pulse")
        val a by inf.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_alpha",
        )
        a
    } else {
        1f
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** Compact telemetry pill with tabular numerals and optional low-health heartbeat scale. */
@Composable
fun VitalsPill(
    icon: ImageVector?,
    text: String,
    tint: Color,
    pulse: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val scale = if (pulse && !reduced) {
        val inf = rememberInfiniteTransition(label = "hp_pulse")
        val s by inf.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(550),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "hp_scale",
        )
        s
    } else {
        1f
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.tabular(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Smoothly rolling tabular counter for AFK timer seconds. */
@Composable
fun AnimatedTimerText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    ).tabular()

    if (reduced) {
        Text(text, style = style, modifier = modifier)
    } else {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn(tween(140))) togetherWith
                    (slideOutVertically { -it / 3 } + fadeOut(tween(100)))
            },
            label = "afk_timer",
            modifier = modifier,
        ) { value ->
            Text(value, style = style)
        }
    }
}

fun formatBytes(bytes: Long): String {
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

fun formatAfkTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

fun stateLabel(state: String): String = when (state) {
    "connected" -> "Connected"
    "connecting" -> "Connecting"
    "reconnecting" -> "Reconnecting"
    "authenticating" -> "Authenticating"
    "error" -> "Error"
    else -> "Disconnected"
}
