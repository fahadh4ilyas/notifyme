package com.fahadh.notifyme

import android.content.Context

object AppPrefs {
    private const val NAME = "notifyme"
    private const val KEY_ROLE = "role"
    private const val KEY_ROOM = "room"
    private const val KEY_CHANNEL = "channel_id"
    private const val KEY_SECRET = "secret_key"
    private const val KEY_E2E = "e2e_secret"
    private const val KEY_EXCLUDED = "excluded_apps"
    private const val KEY_SEEN = "seen_apps"

    const val ROLE_SENDER = "sender"
    const val ROLE_RECEIVER = "receiver"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun role(ctx: Context): String = prefs(ctx).getString(KEY_ROLE, ROLE_SENDER) ?: ROLE_SENDER

    fun setRole(ctx: Context, role: String) = prefs(ctx).edit().putString(KEY_ROLE, role).apply()

    fun room(ctx: Context): String = prefs(ctx).getString(KEY_ROOM, "") ?: ""

    fun setRoom(ctx: Context, room: String) = prefs(ctx).edit().putString(KEY_ROOM, room).apply()

    fun channelId(ctx: Context): String = prefs(ctx).getString(KEY_CHANNEL, "") ?: ""

    fun setChannelId(ctx: Context, id: String) = prefs(ctx).edit().putString(KEY_CHANNEL, id).apply()

    fun secretKey(ctx: Context): String = prefs(ctx).getString(KEY_SECRET, "") ?: ""

    fun setSecretKey(ctx: Context, key: String) = prefs(ctx).edit().putString(KEY_SECRET, key).apply()

    fun e2eSecret(ctx: Context): String = prefs(ctx).getString(KEY_E2E, "") ?: ""

    fun setE2eSecret(ctx: Context, key: String) = prefs(ctx).edit().putString(KEY_E2E, key).apply()

    // Excluded apps, keyed by "userId|packageName".
    fun excludedApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()

    fun setExcludedApps(ctx: Context, apps: Set<String>) =
        prefs(ctx).edit().putStringSet(KEY_EXCLUDED, apps).apply()

    // Apps seen posting notifications, keyed by "userId|packageName".
    fun seenApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_SEEN, emptySet())?.toSet() ?: emptySet()

    fun addSeenApp(ctx: Context, userId: Int, packageName: String) {
        val key = "$userId|$packageName"
        val current = seenApps(ctx)
        if (key in current) return
        prefs(ctx).edit().putStringSet(KEY_SEEN, current + key).apply()
    }
}
