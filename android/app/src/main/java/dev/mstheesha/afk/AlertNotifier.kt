package dev.mstheesha.afk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts Android alert notifications (on a dedicated channel separate from the
 * ongoing foreground-service notification) and optionally forwards events to a
 * Discord webhook. All network work runs off the engine/UI threads.
 */
object AlertNotifier {
    private const val CHANNEL_ALERTS = "afk_alerts"
    private val nextId = AtomicInteger(1000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Bot alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts for kicks, deaths, low health, whispers and keywords"
                },
            )
        }
    }

    fun notify(
        context: Context,
        serverName: String,
        title: String,
        body: String,
        webhookUrl: String = "",
        colorHex: Int = 0x2E7D32,
    ) {
        postAndroidNotification(context, "$serverName · $title", body)
        val url = webhookUrl.trim()
        if (url.startsWith("https://discord.com/api/webhooks/") ||
            url.startsWith("https://discordapp.com/api/webhooks/")
        ) {
            scope.launch { postDiscordWebhook(url, serverName, title, body, colorHex) }
        }
    }

    private fun postAndroidNotification(context: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title.take(80))
            .setContentText(body.take(240))
            .setStyle(Notification.BigTextStyle().bigText(body.take(500)))
            .setSmallIcon(R.drawable.ic_stat_afk)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        val id = 1000 + (nextId.incrementAndGet() % 32)
        nm.notify(id, notification)
    }

    private fun postDiscordWebhook(
        webhookUrl: String,
        serverName: String,
        title: String,
        body: String,
        colorHex: Int,
    ) {
        runCatching {
            val payload = JSONObject().put(
                "embeds",
                JSONArray().put(
                    JSONObject()
                        .put("title", "$serverName — $title")
                        .put("description", body.take(1000))
                        .put("color", colorHex),
                ),
            )
            val conn = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "Java-AFK-NaNo")
            }
            try {
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }
}
