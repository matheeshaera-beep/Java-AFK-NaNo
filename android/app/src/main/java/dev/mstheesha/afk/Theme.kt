package dev.mstheesha.afk

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** True when Android's "Remove animations" accessibility toggle is active. */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Material Design 3 Expressive spring physics tokens:
 * - Spatial springs use playful overshoot (damping 0.6–0.8) for movement & shape morphs.
 * - Effects springs are critically damped (damping 1.0, zero bounce) for color & opacity.
 */
object AfkMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 3800f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1600f)
}

/**
 * Full emerald-tinted Material 3 tonal palettes so sheets, dialogs, cards, and
 * borders harmonize cleanly with the signature green without falling back to
 * default purple-grey Material surfaces.
 */
val GreenLight = lightColorScheme(
    primary = Color(0xFF1B7A43),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5F0C5),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF476451),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9EAD2),
    onSecondaryContainer = Color(0xFF042012),
    tertiary = Color(0xFF2B6777),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB3EBFA),
    onTertiaryContainer = Color(0xFF001F27),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF6),
    onBackground = Color(0xFF161D18),
    surface = Color(0xFFF6FBF6),
    onSurface = Color(0xFF161D18),
    surfaceVariant = Color(0xFFDBE5DC),
    onSurfaceVariant = Color(0xFF3F4942),
    outline = Color(0xFF6F7A71),
    outlineVariant = Color(0xFFBEC9C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F0),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DF),
)

val GreenDark = darkColorScheme(
    primary = Color(0xFF81D899),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFFB5F0C5),
    secondary = Color(0xFFADCEB7),
    onSecondary = Color(0xFF193625),
    secondaryContainer = Color(0xFF2F4D3B),
    onSecondaryContainer = Color(0xFFC9EAD2),
    tertiary = Color(0xFF96CFE0),
    onTertiary = Color(0xFF003642),
    tertiaryContainer = Color(0xFF0C4E5F),
    onTertiaryContainer = Color(0xFFB3EBFA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1510),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0E1510),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF3F4942),
    onSurfaceVariant = Color(0xFFBEC9C0),
    outline = Color(0xFF89938B),
    outlineVariant = Color(0xFF3F4942),
    surfaceContainerLowest = Color(0xFF09100B),
    surfaceContainerLow = Color(0xFF151D17),
    surfaceContainer = Color(0xFF19221B),
    surfaceContainerHigh = Color(0xFF232C26),
    surfaceContainerHighest = Color(0xFF2D3730),
)

val GreenAmoled = GreenDark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080D09),
    surfaceContainer = Color(0xFF0F1511),
    surfaceContainerHigh = Color(0xFF161E18),
    surfaceContainerHighest = Color(0xFF1E2821),
    outlineVariant = Color(0xFF2A352D),
)

val AfkTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** Tabular numeral helper (`tnum`) so timers, ping, and byte counters never jitter horizontally. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun resolveColorScheme(
    context: Context,
    mode: ThemeMode,
    systemDark: Boolean,
    dynamicColor: Boolean,
): Pair<ColorScheme, Boolean> {
    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        useDynamic && isDark -> dynamicDarkColorScheme(context)
        useDynamic && !isDark -> dynamicLightColorScheme(context)
        mode == ThemeMode.AMOLED -> GreenAmoled
        isDark -> GreenDark
        else -> GreenLight
    }
    val scheme = if (mode == ThemeMode.AMOLED && useDynamic) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080D09),
            surfaceContainer = Color(0xFF0F1511),
            surfaceContainerHigh = Color(0xFF161E18),
            surfaceContainerHighest = Color(0xFF1E2821),
        )
    } else {
        base
    }
    return scheme to isDark
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun AfkExpressiveTheme(
    targetScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val scheme = if (reducedMotion) {
        targetScheme
    } else {
        val spec = AfkMotion.defaultEffects<Color>()
        val primary by animateColorAsState(targetScheme.primary, spec, label = "primary")
        val primaryContainer by animateColorAsState(targetScheme.primaryContainer, spec, label = "primaryContainer")
        val background by animateColorAsState(targetScheme.background, spec, label = "background")
        val surface by animateColorAsState(targetScheme.surface, spec, label = "surface")
        val onSurface by animateColorAsState(targetScheme.onSurface, spec, label = "onSurface")
        val surfaceContainerLow by animateColorAsState(targetScheme.surfaceContainerLow, spec, label = "surfaceLow")
        val surfaceContainer by animateColorAsState(targetScheme.surfaceContainer, spec, label = "surfaceCont")
        val surfaceContainerHigh by animateColorAsState(targetScheme.surfaceContainerHigh, spec, label = "surfaceHigh")
        val surfaceContainerHighest by animateColorAsState(targetScheme.surfaceContainerHighest, spec, label = "surfaceHighest")
        targetScheme.copy(
            primary = primary,
            primaryContainer = primaryContainer,
            background = background,
            surface = surface,
            onSurface = onSurface,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AfkTypography,
            content = content,
        )
    }
}
