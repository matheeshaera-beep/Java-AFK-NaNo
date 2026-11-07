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
    private val scope = MainScope()

    /** True once startForeground() succeeded, so teardown only touches a
     *  notification that actually exists. */
    private var foregroundStarted = false

    /** Set by [stopEverything] so the state collectors cannot trigger a second
     *  teardown. Cleared by the next start command, which revives the service. */
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
        // Rebuild the notification whenever the active-server set changes.
        scope.launch {
            AppGraph.namesTick.collect { refreshNotification() }
        }
        // The wake lock follows the active-session count: held while at
        // least one bot runs, released as soon as the last one stops so the
        // phone can sleep with zero bots.
        scope.launch {
            AppGraph.activeCount.collect { onActiveCount(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A fresh start command revives the service: clear the teardown latch
        // so a bot started again right after the last one stopped can post its
        // notification and hold its wake lock.
        stopping = false
        // START_NOT_STICKY: after a process kill Android must NOT restart us
        // with a null intent (that used to leave a notification + wake lock
        // with no Node and no bot behind).
        if (intent == null) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_STOP) {
            // Force-stop: disconnect every session first so nothing is left
            // showing a stale Connected state, then drop the foreground
            // notification, release the wake lock, and stop the service.
            // (The app window itself stays open; it is only a viewer.)
            AppGraph.stopAllSessions()
            stopEverything()
            return START_NOT_STICKY
        }
        // ACTION_START. Only become a foreground service when a bot is
        // actually connecting/running: the Start button flips the session to
        // 'connecting' before sending this intent, so no active session here
        // means the start did not take and there is nothing to keep alive.
        if (!AppGraph.hasActiveSessions()) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        return START_NOT_STICKY
    }

    private fun onActiveCount(n: Int) {
        if (stopping) return
        if (n > 0) {
            // A session became active. If ACTION_START already ran, keep the
            // wake lock held and refresh the text; otherwise onStartCommand
            // posts the notification itself.
            if (!foregroundStarted) return
            acquireWakeLock()
            refreshNotification()
            return
        }
        // Count reached zero. The StateFlow emission can lag a transition, so
        // re-check the sessions synchronously: a bot that just started (or is
        // reconnecting) must not tear the service down.
        if (!foregroundStarted) return
        if (AppGraph.hasActiveSessions()) return
        stopEverything()
    }

    private fun startForegroundCompat() {
        // No active session means no notification: never post a placeholder.
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
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopService = PendingIntent.getService(
            this, 1,
            Intent(this, AfkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Java AFK NaNo active")
            .setContentText(names.joinToString(", "))
            .setSmallIcon(R.drawable.ic_stat_afk)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop service",
                    stopService,
                ).build(),
            )
            .build()
    }

    private fun refreshNotification() {
        if (!foregroundStarted || stopping) return
        // Never keep a notification (or the service) alive with no sessions.
        if (!AppGraph.hasActiveSessions()) {
            stopEverything()
            return
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(AppGraph.activeServerNames()))
    }

    /** Single teardown path: wake lock, foreground notification, service. */
    private fun stopEverything() {
        if (stopping) return
        stopping = true
        releaseWakeLock()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        } else {
            // Drop any notification left over from an earlier incarnation.
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
    }
}