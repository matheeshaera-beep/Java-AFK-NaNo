package dev.mstheesha.afk

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val GreenLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EFB0),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF43A047),
    tertiary = Color(0xFF66BB6A),
)

val GreenDark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF005300),
    onPrimaryContainer = Color(0xFFB7EFB0),
    secondary = Color(0xFFA5D6A7),
    tertiary = Color(0xFF66BB6A),
)

val GreenAmoled = GreenDark.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0D0B),
    surfaceContainer = Color(0xFF101411),
    surfaceContainerHigh = Color(0xFF161B17),
    surfaceContainerHighest = Color(0xFF1E2420),
)

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
            surfaceContainerLow = Color(0xFF0A0D0B),
            surfaceContainer = Color(0xFF101411),
            surfaceContainerHigh = Color(0xFF161B17),
            surfaceContainerHighest = Color(0xFF1E2420),
        )
    } else {
        base
    }
    return scheme to isDark
}
