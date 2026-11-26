package dev.mstheesha.afk

import android.content.Context

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED"),
    ;

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

object Prefs {
    private const val FILE = "afk_prefs"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_BATTERY_GUARD = "battery_guard_pct"
    private const val KEY_MAX_BOTS = "max_bots"
    private const val KEY_CHAT_TIMESTAMPS = "chat_timestamps"
    private const val KEY_QUICK_COMMANDS = "quick_commands"

    private val DEFAULT_QUICK_COMMANDS = listOf("/bal", "/home", "/afk", "/spawn")

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isDark(ctx: Context, systemDark: Boolean): Boolean =
        prefs(ctx).getBoolean(KEY_DARK, systemDark)

    fun setDark(ctx: Context, dark: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DARK, dark).apply()
    }

    fun themeMode(ctx: Context): ThemeMode {
        val p = prefs(ctx)
        val saved = p.getString(KEY_THEME_MODE, null)
        if (saved != null) return ThemeMode.of(saved)
        return if (p.contains(KEY_DARK)) {
            if (p.getBoolean(KEY_DARK, false)) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(ctx: Context, mode: ThemeMode) {
        prefs(ctx).edit()
            .putString(KEY_THEME_MODE, mode.key)
            .putBoolean(KEY_DARK, mode == ThemeMode.DARK || mode == ThemeMode.AMOLED)
            .apply()
    }

    fun dynamicColor(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun wifiOnly(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    /** 0 = disabled, otherwise stop running bots when unplugged battery <= pct. */
    fun batteryGuardPercent(ctx: Context): Int = prefs(ctx).getInt(KEY_BATTERY_GUARD, 0).coerceIn(0, 50)

    fun setBatteryGuardPercent(ctx: Context, pct: Int) {
        prefs(ctx).edit().putInt(KEY_BATTERY_GUARD, pct.coerceIn(0, 50)).apply()
    }

    /** 0 = unlimited concurrent bots (matches NaNo design); >0 enforces a user cap. */
    fun maxBots(ctx: Context): Int = prefs(ctx).getInt(KEY_MAX_BOTS, 0).coerceAtLeast(0)

    fun setMaxBots(ctx: Context, max: Int) {
        prefs(ctx).edit().putInt(KEY_MAX_BOTS, max.coerceAtLeast(0)).apply()
    }

    fun chatTimestamps(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CHAT_TIMESTAMPS, false)

    fun setChatTimestamps(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CHAT_TIMESTAMPS, enabled).apply()
    }

    fun quickCommands(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_QUICK_COMMANDS, null) ?: return DEFAULT_QUICK_COMMANDS
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_QUICK_COMMANDS }
    }

    fun setQuickCommands(ctx: Context, commands: List<String>) {
        prefs(ctx).edit().putString(KEY_QUICK_COMMANDS, commands.joinToString("\n")).apply()
    }
}
