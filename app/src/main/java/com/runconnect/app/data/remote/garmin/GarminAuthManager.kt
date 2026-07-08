package com.runconnect.app.data.remote.garmin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GarminAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("garmin_access_token")
        private val KEY_TOKEN_SECRET = stringPreferencesKey("garmin_token_secret")
        private val KEY_CONSUMER_KEY = stringPreferencesKey("garmin_consumer_key")
        private val KEY_CONSUMER_SECRET = stringPreferencesKey("garmin_consumer_secret")

        private const val REQUEST_TOKEN_URL =
            "https://connectapi.garmin.com/oauth-service/oauth/request_token"
        private const val AUTHORIZE_URL = "https://connect.garmin.com/oauthConfirm"
        private const val ACCESS_TOKEN_URL =
            "https://connectapi.garmin.com/oauth-service/oauth/access_token"
        private const val CALLBACK_URL = "runconnect://garmin-auth"
    }

    private val httpClient = OkHttpClient()

    val isAuthenticated: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ACCESS_TOKEN]?.isNotBlank() == true
    }

    suspend fun saveCredentials(consumerKey: String, consumerSecret: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CONSUMER_KEY] = consumerKey
            prefs[KEY_CONSUMER_SECRET] = consumerSecret
        }
    }

    suspend fun getConsumerKey(): String =
        dataStore.data.first()[KEY_CONSUMER_KEY] ?: ""

    suspend fun getConsumerSecret(): String =
        dataStore.data.first()[KEY_CONSUMER_SECRET] ?: ""

    suspend fun getAccessToken(): GarminOAuthToken? {
        val prefs = dataStore.data.first()
        val token = prefs[KEY_ACCESS_TOKEN] ?: return null
        val secret = prefs[KEY_TOKEN_SECRET] ?: return null
        return if (token.isBlank() || secret.isBlank()) null
        else GarminOAuthToken(token, secret)
    }

    /**
     * Step 1: Get a request token, then launch the browser for user authorization.
     * After the user approves, the app receives the callback at runconnect://garmin-auth
     */
    suspend fun startOAuthFlow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val consumerKey = getConsumerKey()
            val consumerSecret = getConsumerSecret()
            require(consumerKey.isNotBlank()) { "Garmin consumer key not configured" }

            val authHeader = OAuth1Signer.buildAuthorizationHeader(
                method = "POST",
                url = REQUEST_TOKEN_URL,
                consumerKey = consumerKey,
                consumerSecret = consumerSecret,
                extraParams = mapOf("oauth_callback" to CALLBACK_URL),
            )

            val request = Request.Builder()
                .url(REQUEST_TOKEN_URL)
                .post(FormBody.Builder().build())
                .addHeader("Authorization", authHeader)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response from Garmin")

            val params = parseQueryString(body)
            val requestToken = params["oauth_token"] ?: throw Exception("No request token")
            val requestSecret = params["oauth_token_secret"] ?: ""

            // Save request secret temporarily
            dataStore.edit { prefs ->
                prefs[KEY_TOKEN_SECRET] = requestSecret
            }

            val authorizeUrl = "$AUTHORIZE_URL?oauth_token=$requestToken&oauth_callback=${Uri.encode(CALLBACK_URL)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Step 2: Handle the OAuth callback after user approves in browser.
     * Call this from MainActivity when the deep link arrives.
     */
    suspend fun handleOAuthCallback(callbackUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val oauthToken = callbackUri.getQueryParameter("oauth_token")
                ?: throw Exception("Missing oauth_token in callback")
            val oauthVerifier = callbackUri.getQueryParameter("oauth_verifier")
                ?: throw Exception("Missing oauth_verifier in callback")

            val consumerKey = getConsumerKey()
            val consumerSecret = getConsumerSecret()
            val tokenSecret = dataStore.data.first()[KEY_TOKEN_SECRET] ?: ""

            val authHeader = OAuth1Signer.buildAuthorizationHeader(
                method = "POST",
                url = ACCESS_TOKEN_URL,
                consumerKey = consumerKey,
                consumerSecret = consumerSecret,
                accessToken = oauthToken,
                tokenSecret = tokenSecret,
                extraParams = mapOf("oauth_verifier" to oauthVerifier),
            )

            val request = Request.Builder()
                .url(ACCESS_TOKEN_URL)
                .post(FormBody.Builder().build())
                .addHeader("Authorization", authHeader)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val params = parseQueryString(body)

            val accessToken = params["oauth_token"] ?: throw Exception("No access token")
            val accessSecret = params["oauth_token_secret"] ?: throw Exception("No token secret")

            dataStore.edit { prefs ->
                prefs[KEY_ACCESS_TOKEN] = accessToken
                prefs[KEY_TOKEN_SECRET] = accessSecret
            }
            Unit
        }
    }

    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_TOKEN_SECRET)
        }
    }

    private fun parseQueryString(query: String): Map<String, String> =
        query.split("&").mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
        }.toMap()
}
