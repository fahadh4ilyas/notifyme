package com.fahadh.notifyme

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Minimal HS256 JWT signer for Scaledrone's v3 authentication. */
object ScaledroneJwt {

    private val gson = Gson()

    fun sign(secretKey: String, clientId: String, channelId: String): String {
        val header = JsonObject().apply {
            addProperty("alg", "HS256")
            addProperty("typ", "JWT")
        }

        val permissions = JsonObject().apply {
            add(
                ".*",
                JsonObject().apply {
                    addProperty("publish", true)
                    addProperty("subscribe", true)
                },
            )
        }

        val payload = JsonObject().apply {
            addProperty("client", clientId)
            addProperty("channel", channelId)
            add("permissions", permissions)
            addProperty("exp", System.currentTimeMillis() / 1000 + 30L * 24 * 3600)
        }

        val headerB64 = base64Url(gson.toJson(header).toByteArray(Charsets.UTF_8))
        val payloadB64 = base64Url(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        val signingInput = "$headerB64.$payloadB64"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signatureB64 = base64Url(mac.doFinal(signingInput.toByteArray(Charsets.UTF_8)))

        return "$signingInput.$signatureB64"
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
