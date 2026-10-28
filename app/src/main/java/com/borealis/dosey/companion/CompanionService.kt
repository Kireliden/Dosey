package com.borealis.dosey.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.borealis.dosey.R

/**
 * User-controlled foreground lifecycle for Dosey Companion.
 * The only network route currently exposed is a TLS-encrypted, non-sensitive status endpoint.
 * Medication data remains unavailable until explicit client pairing and authentication exist.
 */
class CompanionService : Service() {
    private var networkServer: CompanionNetworkServer? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.companion_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.companion_notification_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            networkServer?.stop()
            networkServer = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.companion_notification_title))
                .setContentText(getString(R.string.companion_notification_message))
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
        if (networkServer == null) {
            networkServer = CompanionNetworkServer(applicationContext) { running, error ->
                val manager = getSystemService(NotificationManager::class.java)
                val text = when {
                    error != null -> getString(R.string.companion_listener_error, error)
                    running -> getString(R.string.companion_listener_running)
                    else -> getString(R.string.companion_notification_message)
                }
                manager.notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(getString(R.string.companion_notification_title))
                        .setContentText(text)
                        .setOngoing(running)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                )
            }.also { it.start() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        networkServer?.stop()
        networkServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.borealis.dosey.companion.START"
        const val ACTION_STOP = "com.borealis.dosey.companion.STOP"
        private const val CHANNEL_ID = "dosey_companion"
        private const val NOTIFICATION_ID = 4201
    }
}
