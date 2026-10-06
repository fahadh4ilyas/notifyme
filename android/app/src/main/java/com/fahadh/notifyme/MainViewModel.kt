package com.fahadh.notifyme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app
    private val gson = Gson()

    val role = MutableStateFlow(AppPrefs.role(ctx))
    val room = MutableStateFlow(AppPrefs.room(ctx))
    val channelId = MutableStateFlow(AppPrefs.channelId(ctx))
    val secretKey = MutableStateFlow(AppPrefs.secretKey(ctx))
    val e2eSecret = MutableStateFlow(AppPrefs.e2eSecret(ctx))
    val connectionState: StateFlow<MirrorConnection.State> = MirrorConnection.state
    val errorMessage: StateFlow<String?> = MirrorConnection.errorMessage

    fun setRole(value: String) {
        role.value = value
        AppPrefs.setRole(ctx, value)
    }

    fun setRoom(value: String) {
        room.value = value
        AppPrefs.setRoom(ctx, value)
    }

    fun setChannelId(value: String) {
        channelId.value = value
        AppPrefs.setChannelId(ctx, value)
    }

    fun setSecretKey(value: String) {
        secretKey.value = value
        AppPrefs.setSecretKey(ctx, value)
    }

    fun setE2eSecret(value: String) {
        e2eSecret.value = value
        AppPrefs.setE2eSecret(ctx, value)
    }

    fun generateRoom() {
        setRoom(generateCode(6))
        setE2eSecret(AesGcm.generateSecret())
    }

    fun generateE2eSecret() = setE2eSecret(AesGcm.generateSecret())

    fun pairingPayload(): String {
        val obj = JsonObject().apply {
            addProperty("channel", channelId.value)
            addProperty("secret", secretKey.value)
            addProperty("room", room.value)
            addProperty("e2e", e2eSecret.value)
        }
        return gson.toJson(obj)
    }

    fun applyPairing(json: String): Boolean {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val channel = obj.get("channel")?.asString
            val secret = obj.get("secret")?.asString
            val room = obj.get("room")?.asString
            val e2e = obj.get("e2e")?.asString
            if (channel.isNullOrBlank() || secret.isNullOrBlank() ||
                room.isNullOrBlank() || e2e.isNullOrBlank()
            ) {
                return false
            }
            setChannelId(channel)
            setSecretKey(secret)
            setRoom(room)
            setE2eSecret(e2e)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun connect() {
        if (room.value.isBlank() || channelId.value.isBlank() || secretKey.value.isBlank()) return
        if (e2eSecret.value.isBlank()) generateE2eSecret()
        MirrorService.start(ctx)
    }

    fun disconnect() = MirrorService.stop(ctx)

    private fun generateCode(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
