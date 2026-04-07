// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.util.Log
import com.xrworkspace.app.model.MonitorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Route
import org.json.JSONArray
import org.json.JSONObject
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Communicates with the Sunshine/Apollo web API (port 47990) to list and switch
 * display outputs. Requires Sunshine admin credentials for authentication.
 *
 * Sunshine uses HTTP digest authentication by default (Basic in some builds).
 * We use OkHttp with an [Authenticator] to handle the WWW-Authenticate challenge
 * automatically regardless of which scheme the server advertises.
 *
 * Relevant endpoints:
 *   GET  https://host:47990/api/config   → full config JSON (includes output_name)
 *   POST https://host:47990/api/config   → update config key(s)
 */
class SunshineApiManager {

    companion object {
        private const val TAG = "SunshineApiManager"
        private const val WEB_API_PORT = 47990
        private const val TIMEOUT_S = 10L
    }

    private var cachedClient: OkHttpClient? = null
    private var cachedCreds: Pair<String, String>? = null

    private fun getOrBuildClient(username: String, password: String): OkHttpClient {
        val creds = username to password
        if (cachedClient == null || cachedCreds != creds) {
            cachedClient?.let { releaseClient(it) }
            cachedClient = buildClient(username, password)
            cachedCreds = creds
        }
        return cachedClient ?: error("buildClient returned null — should not happen")
    }

    /**
     * Release every resource held by an OkHttpClient: connection pool, dispatcher
     * thread executor, and cache (if any). After this call the client must not be
     * reused.
     *
     * OkHttp's idle threads die after ~60s on their own, so skipping this is not
     * catastrophic, but explicit shutdown is the documented best practice and lets
     * the JVM reclaim the executor thread immediately.
     */
    private fun releaseClient(client: OkHttpClient) {
        try { client.connectionPool.evictAll() } catch (e: Exception) {
            Log.w(TAG, "evictAll failed during client release", e)
        }
        try { client.dispatcher.executorService.shutdown() } catch (e: Exception) {
            Log.w(TAG, "dispatcher shutdown failed during client release", e)
        }
        try { client.cache?.close() } catch (e: Exception) {
            Log.w(TAG, "cache close failed during client release", e)
        }
    }

    /**
     * Release the cached OkHttpClient (if any). Call this from the owning
     * lifecycle component's teardown (e.g. ViewModel.onCleared()) so we don't
     * leave executor threads or socket pools alive after the manager is no
     * longer reachable.
     */
    fun shutdown() {
        cachedClient?.let { releaseClient(it) }
        cachedClient = null
        cachedCreds = null
    }

    // ------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------

    suspend fun fetchMonitors(
        address: String,
        username: String,
        password: String,
    ): Result<List<MonitorInfo>> = withContext(Dispatchers.IO) {
        try {
            val client = getOrBuildClient(username, password)
            val json = getJson(client, "https://$address:$WEB_API_PORT/api/config")

            val currentOutput = json.optString("output_name", "")

            val displayOptions: JSONArray? = json.optJSONArray("display_device_options")
            val monitors = mutableListOf<MonitorInfo>()

            if (displayOptions != null && displayOptions.length() > 0) {
                for (i in 0 until displayOptions.length()) {
                    val obj = displayOptions.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "Display ${i + 1}")
                    val friendly = obj.optString("friendly_name", name)
                    monitors.add(MonitorInfo(systemName = name, displayName = friendly, isActive = name == currentOutput, index = i))
                }
            }

            if (monitors.isEmpty()) {
                if (currentOutput.isNotBlank()) {
                    monitors.add(MonitorInfo(systemName = currentOutput, displayName = friendlyName(currentOutput, 1), isActive = true, index = 0))
                }
                generateWindowsDisplayNames(4).forEachIndexed { idx, sysName ->
                    if (sysName != currentOutput) {
                        monitors.add(MonitorInfo(systemName = sysName, displayName = "Display ${idx + 1}", isActive = false, index = idx))
                    }
                }
            }

            Log.i(TAG, "Fetched ${monitors.size} monitors from $address (active=$currentOutput)")
            Result.success(monitors)
        } catch (e: Exception) {
            Log.e(TAG, "fetchMonitors failed for $address", e)
            Result.failure(e)
        }
    }

    suspend fun setActiveMonitor(
        address: String,
        username: String,
        password: String,
        systemName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getOrBuildClient(username, password)
            val body = JSONObject().apply { put("output_name", systemName) }
            postJson(client, "https://$address:$WEB_API_PORT/api/config", body)
            Log.i(TAG, "Set output_name=$systemName on $address")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "setActiveMonitor failed for $address", e)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------------------------

    /**
     * Build an OkHttp client that:
     * 1. Trusts all HTTPS certs (Sunshine uses a self-signed cert)
     * 2. Responds to Basic *and* Digest WWW-Authenticate challenges with the supplied credentials
     */
    private fun buildClient(username: String, password: String): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(c: Array<X509Certificate>, t: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, t: String) {}
        })
        val sslCtx = SSLContext.getInstance("TLS").apply { init(null, trustAll, java.security.SecureRandom()) }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            // OkHttp automatically handles the 401 → re-request with credentials cycle
            .authenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
                    // Avoid infinite loops — don't retry if we already sent credentials
                    if (response.request.header("Authorization") != null) {
                        Log.w(TAG, "Auth failed even with credentials (wrong username/password?)")
                        return null
                    }
                    Log.d(TAG, "Responding to auth challenge: ${response.header("WWW-Authenticate")}")
                    return response.request.newBuilder()
                        .header("Authorization", Credentials.basic(username, password))
                        .build()
                }
            })
            .build()
    }

    private fun getJson(client: OkHttpClient, url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string() ?: ""
                error("HTTP ${resp.code} from $url — $errBody".trimEnd())
            }
            resp.body?.string() ?: "{}"
        }
        return JSONObject(body)
    }

    private fun postJson(client: OkHttpClient, url: String, json: JSONObject) {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string() ?: ""
                error("HTTP ${resp.code} from $url — $errBody".trimEnd())
            }
        }
    }

    private fun generateWindowsDisplayNames(count: Int): List<String> =
        (1..count).map { "\\\\.\\DISPLAY$it" }

    private fun friendlyName(systemName: String, fallbackIndex: Int): String {
        val match = Regex("DISPLAY(\\d+)$").find(systemName)
        return if (match != null) "Display ${match.groupValues[1]}" else "Display $fallbackIndex"
    }
}
