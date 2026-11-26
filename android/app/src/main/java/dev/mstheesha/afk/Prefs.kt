package dev.mstheesha.afk

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

/**
 * Reactive app preferences backed by [SharedPreferences] + [StateFlow] so
 * changes made in [AppSettingsSheet] update open screens immediately.
 */
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

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColor

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnlyFlow: StateFlow<Boolean> = _wifiOnly

    private val _batteryGuard = MutableStateFlow(0)
    val batteryGuardFlow: StateFlow<Int> = _batteryGuard

    private val _maxBots = MutableStateFlow(0)
    val maxBotsFlow: StateFlow<Int> = _maxBots

    private val _chatTimestamps = MutableStateFlow(false)
    val chatTimestampsFlow: StateFlow<Boolean> = _chatTimestamps

    private val _quickCommands = MutableStateFlow(DEFAULT_QUICK_COMMANDS)
    val quickCommandsFlow: StateFlow<List<String>> = _quickCommands

    @Volatile
    private var initialized = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, _ ->
        syncFrom(sp)
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        val sp = prefs(ctx)
        syncFrom(sp)
        sp.registerOnSharedPreferenceChangeListener(listener)
        initialized = true
    }

    private fun syncFrom(sp: SharedPreferences) {
        _themeMode.value = readThemeMode(sp)
        _dynamicColor.value = sp.getBoolean(KEY_DYNAMIC_COLOR, false)
        _wifiOnly.value = sp.getBoolean(KEY_WIFI_ONLY, false)
        _batteryGuard.value = sp.getInt(KEY_BATTERY_GUARD, 0).coerceIn(0, 50)
        _maxBots.value = sp.getInt(KEY_MAX_BOTS, 0).coerceAtLeast(0)
        _chatTimestamps.value = sp.getBoolean(KEY_CHAT_TIMESTAMPS, false)
        _quickCommands.value = readQuickCommands(sp)
    }

    private fun readThemeMode(sp: SharedPreferences): ThemeMode {
        val saved = sp.getString(KEY_THEME_MODE, null)
        if (saved != null) return ThemeMode.of(saved)
        return if (sp.contains(KEY_DARK)) {
            if (sp.getBoolean(KEY_DARK, false)) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    private fun readQuickCommands(sp: SharedPreferences): List<String> {
        val raw = sp.getString(KEY_QUICK_COMMANDS, null) ?: return DEFAULT_QUICK_COMMANDS
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_QUICK_COMMANDS }
    }

    fun isDark(ctx: Context, systemDark: Boolean): Boolean =
        prefs(ctx).getBoolean(KEY_DARK, systemDark)

    fun setDark(ctx: Context, dark: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DARK, dark).apply()
    }

    fun themeMode(ctx: Context): ThemeMode = readThemeMode(prefs(ctx))

    fun setThemeMode(ctx: Context, mode: ThemeMode) {
        _themeMode.value = mode
        prefs(ctx).edit()
            .putString(KEY_THEME_MODE, mode.key)
            .putBoolean(KEY_DARK, mode == ThemeMode.DARK || mode == ThemeMode.AMOLED)
            .apply()
    }

    fun dynamicColor(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(ctx: Context, enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs(ctx).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun wifiOnly(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(ctx: Context, enabled: Boolean) {
        _wifiOnly.value = enabled
        prefs(ctx).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    /** 0 = disabled, otherwise stop running bots when unplugged battery <= pct. */
    fun batteryGuardPercent(ctx: Context): Int = prefs(ctx).getInt(KEY_BATTERY_GUARD, 0).coerceIn(0, 50)

    fun setBatteryGuardPercent(ctx: Context, pct: Int) {
        val v = pct.coerceIn(0, 50)
        _batteryGuard.value = v
        prefs(ctx).edit().putInt(KEY_BATTERY_GUARD, v).apply()
    }

    /** 0 = unlimited concurrent bots (matches NaNo design); >0 enforces a user cap. */
    fun maxBots(ctx: Context): Int = prefs(ctx).getInt(KEY_MAX_BOTS, 0).coerceAtLeast(0)

    fun setMaxBots(ctx: Context, max: Int) {
        val v = max.coerceAtLeast(0)
        _maxBots.value = v
        prefs(ctx).edit().putInt(KEY_MAX_BOTS, v).apply()
    }

    fun chatTimestamps(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CHAT_TIMESTAMPS, false)

    fun setChatTimestamps(ctx: Context, enabled: Boolean) {
        _chatTimestamps.value = enabled
        prefs(ctx).edit().putBoolean(KEY_CHAT_TIMESTAMPS, enabled).apply()
    }

    fun quickCommands(ctx: Context): List<String> = readQuickCommands(prefs(ctx))

    fun setQuickCommands(ctx: Context, commands: List<String>) {
        val cleaned = commands.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_QUICK_COMMANDS }
        _quickCommands.value = cleaned
        prefs(ctx).edit().putString(KEY_QUICK_COMMANDS, cleaned.joinToString("\n")).apply()
    }
}
