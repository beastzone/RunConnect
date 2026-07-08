package com.runconnect.app.data.remote.garmin

import android.util.Base64
import java.net.URLEncoder
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object OAuth1Signer {

    private const val SIGNATURE_METHOD = "HMAC-SHA1"
    private const val OAUTH_VERSION = "1.0"

    fun buildAuthorizationHeader(
        method: String,
        url: String,
        consumerKey: String,
        consumerSecret: String,
        accessToken: String = "",
        tokenSecret: String = "",
        extraParams: Map<String, String> = emptyMap(),
    ): String {
        val timestamp = Instant.now().epochSecond.toString()
        val nonce = Random.nextLong().toString(36).removePrefix("-")

        val oauthParams = mutableMapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to nonce,
            "oauth_signature_method" to SIGNATURE_METHOD,
            "oauth_timestamp" to timestamp,
            "oauth_version" to OAUTH_VERSION,
        )
        if (accessToken.isNotBlank()) {
            oauthParams["oauth_token"] = accessToken
        }

        val allParams = (oauthParams + extraParams).toSortedMap()
        val paramString = allParams.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }

        val baseString = "${method.uppercase()}&${encode(url)}&${encode(paramString)}"
        val signingKey = "${encode(consumerSecret)}&${encode(tokenSecret)}"
        val signature = hmacSha1(baseString, signingKey)
        oauthParams["oauth_signature"] = signature

        val headerParams = oauthParams.entries.joinToString(", ") {
            """${encode(it.key)}="${encode(it.value)}""""
        }
        return "OAuth $headerParams"
    }

    private fun hmacSha1(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
        mac.init(secretKey)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
