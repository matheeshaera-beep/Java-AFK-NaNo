package dev.mstheesha.afk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AfkService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var networkGuard: NetworkGuard? = null
    private val scope = MainScope()

    private var foregroundStarted = false
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AFK session", NotificationManager.IMPORTANCE_LOW),
            )
        }
        networkGuard = NetworkGuard(this).also { it.start() }
        scope.launch {
            AppGraph.namesTick.collect { refreshNotification() }
        }
        scope.launch {
            AppGraph.activeCount.collect { onActiveCount(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopping = false
        if (intent == null) {
            stopEverything()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                AppGraph.stopAllSessions()
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                AppGraph.reconnectAllSessions()
                refreshNotification()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_GUARD -> {
                networkGuard?.evaluateNetwork()
                return START_NOT_STICKY
            }
        }
        if (!AppGraph.hasActiveSessions()) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        return START_NOT_STICKY
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            AppGraph.trimMemory()
        }
    }

    private fun onActiveCount(n: Int) {
        if (stopping) return
        if (n > 0) {
            if (!foregroundStarted) return
            acquireWakeLock()
            refreshNotification()
            return
        }
        if (!foregroundStarted) return
        if (AppGraph.hasActiveSessions()) return
        stopEverything()
    }

    private fun startForegroundCompat() {
        val names = AppGraph.activeServerNames()
        if (names.isEmpty()) {
            stopEverything()
            return
        }
        val notification = buildNotification(names)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        acquireWakeLock()
    }

    private fun buildNotification(names: List<String>): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val reconnectAll = PendingIntent.getService(
            this,
            2,
            Intent(this, AfkService::class.java).setAction(ACTION_RECONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopService = PendingIntent.getService(
            this,
            1,
            Intent(this, AfkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (names.size == 1) "Java AFK · 1 bot active" else "Java AFK · ${names.size} bots active"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setSmallIcon(R.drawable.ic_stat_afk)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_popup_sync),
                    "Reconnect",
                    reconnectAll,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop all",
                    stopService,
                ).build(),
            )
            .build()
    }

    private fun refreshNotification() {
        if (!foregroundStarted || stopping) return
        if (!AppGraph.hasActiveSessions()) {
            stopEverything()
            return
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(AppGraph.activeServerNames()))
    }

    private fun stopEverything() {
        if (stopping) return
        stopping = true
        releaseWakeLock()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        } else {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "afk:session").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        stopping = true
        networkGuard?.stop()
        networkGuard = null
        scope.cancel()
        releaseWakeLock()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "afk_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "dev.mstheesha.afk.START"
        const val ACTION_STOP = "dev.mstheesha.afk.STOP"
        const val ACTION_RECONNECT = "dev.mstheesha.afk.RECONNECT"
        const val ACTION_REFRESH_GUARD = "dev.mstheesha.afk.REFRESH_GUARD"
    }
}