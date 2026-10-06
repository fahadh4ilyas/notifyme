package com.fahadh.notifyme

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import com.fahadh.notifyme.model.NotificationMessage
import com.fahadh.notifyme.model.RemoveMessage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class NotificationMirrorListener : NotificationListenerService() {

    private val selfDismissed = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName && sbn.packageName !in SYSTEM_PACKAGES) {
            AppPrefs.addSeenApp(this, sbn.getUserId(), sbn.packageName)
        }
        if (!shouldSend(sbn)) return
        buildMessage(sbn)?.let { MirrorConnection.send(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = sbn.key
        if (key != null && selfDismissed.remove(key)) {
            // Removed by our own cancelNotification in response to a receiver-side dismissal.
            return
        }
        if (!shouldSend(sbn)) return
        MirrorConnection.send(RemoveMessage(id = key ?: ""))
    }

    fun cancelForDismiss(key: String) {
        selfDismissed.add(key)
        cancelNotification(key)
    }

    private fun shouldSend(sbn: StatusBarNotification): Boolean {
        if (AppPrefs.role(this) != AppPrefs.ROLE_SENDER) return false
        if (!MirrorConnection.isConnected()) return false
        if (sbn.packageName == packageName) return false
        if (sbn.packageName in SYSTEM_PACKAGES) return false
        val appKey = "${sbn.getUserId()}|${sbn.packageName}"
        if (appKey in AppPrefs.excludedApps(this)) return false
        // Skip persistent ("pinned" / "keep on") notifications.
        val flags = sbn.notification.flags
        if (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR) != 0) return false
        return true
    }

    private fun buildMessage(sbn: StatusBarNotification): NotificationMessage? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            )?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return null

        return NotificationMessage(
            id = sbn.key ?: "${System.currentTimeMillis()}-${sbn.packageName}",
            packageName = sbn.packageName,
            appName = appLabel(sbn.packageName),
            title = title,
            text = text,
            icon = captureIcon(sbn),
            timestamp = sbn.postTime,
        )
    }

    private fun appLabel(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun captureIcon(sbn: StatusBarNotification): String? {
        val source = sbn.notification.largeIcon ?: try {
            drawableToBitmap(packageManager.getApplicationIcon(sbn.packageName))
        } catch (_: Exception) {
            null
        }
        if (source == null) return null

        val icon = scaleDown(source, MAX_ICON_DIM)
        val encoded = bitmapToBase64(icon, Bitmap.CompressFormat.WEBP, ICON_QUALITY)
        return if (encoded.length <= MAX_ICON_BASE64) encoded else null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(format, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        @Volatile
        var instance: NotificationMirrorListener? = null
            private set

        private const val MAX_ICON_DIM = 96
        private const val ICON_QUALITY = 80
        private const val MAX_ICON_BASE64 = 4500

        // System meta notifications ("N other notifications", charging, etc.) — never mirror.
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "android",
        )
    }
}
