package com.fahadh.notifyme

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fahadh.notifyme.model.NotificationMessage

/** Re-posts a mirrored notification so it looks like the source app's. */
object MirrorNotifier {
    const val EXTRA_DISMISS_KEY = "dismiss_key"

    private const val CHANNEL_ID = "mirrored"
    private const val CHANNEL_NAME = "Mirrored notifications"
    private const val TAG = "Notifyme"

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, msg: NotificationMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — dropping mirrored notification")
            return
        }
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(msg.title.ifBlank { msg.appName })
            .setContentText(msg.text)
            .setSubText("From: ${msg.appName}")
            .setLargeIcon(msg.icon?.let(::base64ToBitmap))
            .setAutoCancel(true)
            .setWhen(msg.timestamp)
            .setShowWhen(true)
            .setContentIntent(tapIntent)
            .setDeleteIntent(dismissPendingIntent(context, msg.id))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(msg.id, 0, builder.build())
        Log.d(TAG, "Posted mirrored notification (app=${msg.appName})")
    }

    fun cancel(context: Context, id: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id, 0)
    }

    private fun dismissPendingIntent(context: Context, key: String): PendingIntent {
        val intent = Intent(context, DismissReceiver::class.java).putExtra(EXTRA_DISMISS_KEY, key)
        return PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun base64ToBitmap(encoded: String): Bitmap? = try {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}
