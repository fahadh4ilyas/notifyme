package com.fahadh.notifyme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that owns the WebSocket connection and keeps the app
 * process alive while mirroring is active, on both the sender and receiver.
 */
class MirrorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        startMirroring()
        return START_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Mirroring active")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun startMirroring() {
        val channelId = AppPrefs.channelId(this)
        val secretKey = AppPrefs.secretKey(this)
        val room = AppPrefs.room(this)
        val role = AppPrefs.role(this)
        val e2eSecret = AppPrefs.e2eSecret(this)
        MirrorConnection.onNotification = { msg -> MirrorNotifier.show(App.instance, msg) }
        MirrorConnection.onRemove = { id -> MirrorNotifier.cancel(App.instance, id) }
        MirrorConnection.onDismiss = { key -> NotificationMirrorListener.instance?.cancelForDismiss(key) }
        MirrorConnection.connect(channelId, secretKey, room, role, e2eSecret)
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mirroring service",
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        MirrorConnection.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "mirror_service"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.fahadh.notifyme.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MirrorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MirrorService::class.java))
        }
    }
}
