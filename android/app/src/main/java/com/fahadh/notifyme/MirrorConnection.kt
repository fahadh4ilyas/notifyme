package com.fahadh.notifyme

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.fahadh.notifyme.model.DismissMessage
import com.fahadh.notifyme.model.NotificationMessage
import com.fahadh.notifyme.model.RemoveMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Scaledrone v3 client. Owns the WebSocket to api.scaledrone.com and drives the
 * handshake -> JWT authenticate -> subscribe/publish flow. Both devices publish
 * and subscribe to the same room: the sender publishes notifications/removes,
 * the receiver publishes dismissals. Payloads are AES-GCM encrypted end-to-end
 * with the shared E2EE key. Unexpected drops auto-reconnect after a short delay.
 */
object MirrorConnection {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private const val SERVER_URL = "wss://api.scaledrone.com/v3/websocket"
    private const val TAG = "Notifyme"
    private const val MAX_MESSAGE_BYTES = 9000
    private const val RECONNECT_DELAY_MS = 3000L

    private val gson = Gson()

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val okHttp = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val reconnectHandler = Handler(Looper.getMainLooper())

    private var socket: WebSocket? = null
    private var channelId = ""
    private var secretKey = ""
    private var room = ""
    private var role = AppPrefs.ROLE_SENDER
    private var e2eKey = ByteArray(0)
    private var disconnectRequested = false
    private var myClientId: String? = null

    var onNotification: ((NotificationMessage) -> Unit)? = null
    var onRemove: ((String) -> Unit)? = null
    var onDismiss: ((String) -> Unit)? = null

    fun isConnected(): Boolean = _state.value == State.CONNECTED

    fun connect(channelId: String, secretKey: String, room: String, role: String, e2eSecret: String) {
        reconnectHandler.removeCallbacksAndMessages(null)
        socket?.close(1000, "bye")
        socket = null

        this.channelId = channelId.trim()
        this.secretKey = secretKey.trim()
        this.room = room.trim()
        this.role = role
        this.e2eKey = AesGcm.keyFromSecret(e2eSecret.trim())
        disconnectRequested = false
        openSocket()
    }

    fun disconnect() {
        disconnectRequested = true
        reconnectHandler.removeCallbacksAndMessages(null)
        socket?.close(1000, "bye")
        socket = null
        _state.value = State.DISCONNECTED
    }

    private fun openSocket() {
        _state.value = State.CONNECTING
        _errorMessage.value = null
        socket = okHttp.newWebSocket(Request.Builder().url(SERVER_URL).build(), listener)
    }

    private fun scheduleReconnect() {
        if (disconnectRequested) return
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({
            if (!disconnectRequested) {
                Log.d(TAG, "Reconnecting…")
                openSocket()
            }
        }, RECONNECT_DELAY_MS)
    }

    fun send(obj: Any) {
        val ws = socket ?: return
        val encoded = fitAndEncode(obj)
        if (encoded == null) {
            Log.w(TAG, "Message dropped — still too big after reduction")
            return
        }
        Log.d(TAG, "Publishing ${encoded.length} bytes to room $room")
        val frame = JsonObject().apply {
            addProperty("type", "publish")
            addProperty("room", room)
            addProperty("message", encoded)
        }
        ws.send(gson.toJson(frame))
    }

    /** Encodes to the wire format, shrinking the payload if it exceeds the limit. */
    private fun fitAndEncode(obj: Any): String? {
        var encoded = encode(obj)
        if (encoded.length <= MAX_MESSAGE_BYTES) return encoded

        val notification = obj as? NotificationMessage ?: return null

        // 1. Drop the icon first — keeps the full title/text.
        var msg = notification.copy(icon = null)
        encoded = encode(msg)
        if (encoded.length <= MAX_MESSAGE_BYTES) return encoded

        // 2. Halve the text until it fits.
        var text = msg.text
        while (text.isNotEmpty() && encoded.length > MAX_MESSAGE_BYTES) {
            text = text.take(text.length / 2)
            msg = msg.copy(text = text)
            encoded = encode(msg)
        }
        if (encoded.length <= MAX_MESSAGE_BYTES) return encoded

        // 3. Last resort: truncate the title and drop the text.
        msg = msg.copy(title = msg.title.take(64), text = "")
        encoded = encode(msg)
        return encoded.takeIf { it.length <= MAX_MESSAGE_BYTES }
    }

    private fun encode(obj: Any): String {
        val plaintext = gson.toJson(obj).toByteArray(Charsets.UTF_8)
        val compressed = Gzip.compress(plaintext)
        val encrypted = AesGcm.encrypt(e2eKey, compressed)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Connected to Scaledrone")
            val handshake = JsonObject().apply {
                addProperty("type", "handshake")
                addProperty("channel", channelId)
                addProperty("callback", 0)
            }
            webSocket.send(gson.toJson(handshake))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket) return // stale socket, replaced by reconnect/manual connect
            Log.w(TAG, "WebSocket closed (code=$code, reason=$reason)")
            if (_state.value != State.ERROR) {
                _state.value = State.DISCONNECTED
            }
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket) return
            _errorMessage.value = t.message ?: t.javaClass.simpleName
            _state.value = State.ERROR
            Log.w(TAG, "WebSocket failure", t)
            scheduleReconnect()
        }
    }

    private fun handleMessage(text: String) {
        try {
            val obj = gson.fromJson(text, JsonObject::class.java)
            val type = obj.get("type")?.asString

            if (obj.has("error")) {
                _errorMessage.value = obj.get("error")?.asString ?: "unknown error"
                _state.value = State.ERROR
                Log.w(TAG, "Scaledrone error frame: $text")
                return
            }

            when {
                type == "publish" -> {
                    val senderId = obj.get("client_id")?.asString
                    if (senderId != null && senderId == myClientId) {
                        Log.d(TAG, "Ignoring own publish echo")
                    } else {
                        val message = obj.get("message")?.asString
                        Log.d(TAG, "Received publish (${message?.length ?: 0} bytes)")
                        dispatch(message)
                    }
                }

                obj.has("client_id") -> authenticate(obj.get("client_id")?.asString)
                obj.get("callback")?.asInt == 1 -> onAuthenticated()
                obj.get("callback")?.asInt == 2 -> Unit // subscribed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse frame: $text", e)
        }
    }

    private fun authenticate(clientId: String?) {
        if (clientId.isNullOrBlank()) return
        myClientId = clientId
        Log.d(TAG, "Authenticating client $clientId on channel $channelId")
        val token = ScaledroneJwt.sign(secretKey, clientId, channelId)
        val auth = JsonObject().apply {
            addProperty("type", "authenticate")
            addProperty("token", token)
            addProperty("callback", 1)
        }
        socket?.send(gson.toJson(auth))
    }

    private fun onAuthenticated() {
        Log.d(TAG, "Authenticated (role=$role)")
        val subscribe = JsonObject().apply {
            addProperty("type", "subscribe")
            addProperty("room", room)
            addProperty("callback", 2)
        }
        socket?.send(gson.toJson(subscribe))
        _state.value = State.CONNECTED
    }

    private fun dispatch(encryptedBase64: String?) {
        if (encryptedBase64.isNullOrBlank()) {
            Log.w(TAG, "Publish with empty message")
            return
        }
        val decrypted = try {
            val data = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            AesGcm.decrypt(e2eKey, data)
        } catch (e: Exception) {
            Log.w(TAG, "Base64 decode failed", e)
            null
        }
        if (decrypted == null) {
            Log.w(TAG, "AES-GCM decryption failed — E2EE key mismatch?")
            return
        }

        val json = try {
            String(Gzip.decompress(decrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Gzip decompress failed", e)
            return
        }
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val msgType = obj.get("type")?.asString
            Log.d(TAG, "Decrypted message type=$msgType")
            when (msgType) {
                "notification" -> onNotification?.invoke(
                    gson.fromJson(json, NotificationMessage::class.java),
                )

                "remove" -> onRemove?.invoke(
                    gson.fromJson(json, RemoveMessage::class.java).id,
                )

                "dismiss" -> onDismiss?.invoke(
                    gson.fromJson(json, DismissMessage::class.java).id,
                )

                else -> Log.w(TAG, "Unknown message type: $msgType")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse decrypted message", e)
        }
    }
}
