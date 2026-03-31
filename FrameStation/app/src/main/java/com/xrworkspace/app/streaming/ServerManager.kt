// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import android.util.Log
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.xrworkspace.app.model.ServerApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Manages NvHTTP server connections: status checks, pairing, and certificate retrieval.
 * All network methods run on the calling thread — invoke from a background thread.
 *
 * Supports per-host certificate files (e.g. `server_{hostId}.crt`) while maintaining
 * backward compatibility with the legacy single `server.crt`.
 */
class ServerManager(
    private val dataDir: File,
    private val prefs: android.content.SharedPreferences? = null,
) {
    companion object {
        private const val TAG = "ServerManager"
        private const val HTTP_PORT = 47989    // GameStream HTTP control port
        private const val HTTPS_PORT = 47984   // GameStream HTTPS control port
        private const val SERVER_CERT_FILE = "server.crt"

        fun getUniqueId(prefs: android.content.SharedPreferences): String {
            return prefs.getString("device_unique_id", null) ?: run {
                val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
                prefs.edit().putString("device_unique_id", id).apply()
                id
            }
        }
    }

    private val cryptoProvider = PlatformBinding.getCryptoProvider(dataDir)
    private val serverCertFile = File(dataDir, SERVER_CERT_FILE)
    private val uniqueId: String
        get() = prefs?.let { getUniqueId(it) } ?: "0123456789ABCDEF"

    /** The currently active cert filename, or null to use the legacy default. */
    var activeCertFileName: String? = null

    data class ServerInfo(
        val hostname: String,
        val address: String,
        val isPaired: Boolean,
        val isReachable: Boolean,
        val gpuType: String?,
        val currentGame: String?,
    )

    /**
     * Check server status and pair state.
     * Uses [activeCertFileName] if set, otherwise falls back to the legacy cert.
     */
    fun checkServer(address: String): Result<ServerInfo> {
        return try {
            val addressTuple = ComputerDetails.AddressTuple(address, HTTP_PORT)
            val savedCert = loadServerCert(activeCertFileName)
            val nvhttp = NvHTTP(addressTuple, HTTPS_PORT, uniqueId, savedCert, cryptoProvider)
            val serverInfo = nvhttp.getServerInfo(true)
            val pairState = nvhttp.getPairState(serverInfo)

            val info = ServerInfo(
                hostname = parseXmlValue(serverInfo, "hostname") ?: address,
                address = address,
                isPaired = pairState == PairingManager.PairState.PAIRED,
                isReachable = true,
                gpuType = parseXmlValue(serverInfo, "gputype"),
                currentGame = parseXmlValue(serverInfo, "currentgame"),
            )
            Log.i(TAG, "Server $address: paired=${info.isPaired}, gpu=${info.gpuType}")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reach server $address", e)
            Result.failure(e)
        }
    }

    fun generatePin(): String {
        return String.format("%04d", java.security.SecureRandom().nextInt(10000))
    }

    /**
     * Pair with the server. On success, saves the server certificate to disk.
     */
    fun pair(address: String, pin: String): Result<PairingManager.PairState> {
        return try {
            val addressTuple = ComputerDetails.AddressTuple(address, HTTP_PORT)
            val nvhttp = NvHTTP(addressTuple, HTTPS_PORT, uniqueId, null, cryptoProvider)
            val serverInfo = nvhttp.getServerInfo(true)
            val pm = nvhttp.getPairingManager()
            val pairState = pm.pair(serverInfo, pin)
            Log.i(TAG, "Pairing result for $address: $pairState")

            // Save the server cert after successful pairing
            if (pairState == PairingManager.PairState.PAIRED) {
                val certFile = activeCertFileName
                val cert = pm.pairedCert
                Log.i(TAG, "PairedCert after pairing: ${cert?.subjectDN ?: "NULL"}")
                if (cert != null) {
                    saveServerCert(cert, certFile)
                    Log.i(TAG, "Server certificate saved to ${certFile ?: SERVER_CERT_FILE}")
                } else {
                    // Cert wasn't captured during pairing — try to get it via NvHTTP's internal state
                    Log.w(TAG, "Pairing succeeded but getPairedCert() returned null. Trying alternative...")
                    // The server cert was set on NvHTTP via setServerCert() during pairing.
                    // Try fetching serverinfo again with HTTPS — the TLS handshake will have the cert.
                    try {
                        val certFromTls = extractCertFromTlsHandshake(address)
                        if (certFromTls != null) {
                            saveServerCert(certFromTls, certFile)
                            Log.i(TAG, "Server cert saved via TLS extraction: ${certFromTls.subjectDN}")
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "TLS cert extraction also failed", e2)
                    }
                }
            }

            Result.success(pairState)
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed for $address", e)
            Result.failure(e)
        }
    }

    /**
     * Extract the server certificate directly from a TLS handshake.
     * This is a fallback when PairingManager doesn't return the cert.
     */
    private fun extractCertFromTlsHandshake(address: String): X509Certificate? {
        return try {
            val url = java.net.URL("https://$address:$HTTPS_PORT/serverinfo?uniqueid=$uniqueId")
            val conn = url.openConnection() as javax.net.ssl.HttpsURLConnection
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }

            // Trust all certs to get the server's cert
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            val sc = javax.net.ssl.SSLContext.getInstance("TLS")
            sc.init(null, trustAll, java.security.SecureRandom())
            conn.sslSocketFactory = sc.socketFactory

            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            val serverCerts = conn.serverCertificates
            conn.disconnect()

            if (serverCerts.isNotEmpty() && serverCerts[0] is X509Certificate) {
                serverCerts[0] as X509Certificate
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "extractCertFromTlsHandshake failed", e)
            null
        }
    }

    /**
     * Load the saved server certificate from disk.
     * @param certFileName optional per-host cert filename; falls back to legacy [SERVER_CERT_FILE].
     */
    fun loadServerCert(certFileName: String? = null): X509Certificate? {
        val file = File(dataDir, certFileName ?: SERVER_CERT_FILE)
        return try {
            if (!file.exists()) {
                Log.i(TAG, "No saved server cert found at ${file.name}")
                return null
            }
            val cf = CertificateFactory.getInstance("X.509")
            FileInputStream(file).use { fis ->
                cf.generateCertificate(fis) as X509Certificate
            }.also {
                Log.i(TAG, "Loaded saved server cert from ${file.name}: ${it.subjectDN}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load server cert from ${file.name}", e)
            null
        }
    }

    /**
     * Save a server certificate to disk.
     * @param certFileName optional per-host cert filename; falls back to legacy [SERVER_CERT_FILE].
     */
    private fun saveServerCert(cert: X509Certificate, certFileName: String? = null) {
        val file = File(dataDir, certFileName ?: SERVER_CERT_FILE)
        try {
            FileOutputStream(file).use { fos ->
                fos.write(cert.encoded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save server cert to ${file.name}", e)
        }
    }

    /**
     * Fetch the list of apps available on the server.
     * Maps moonlight-core [NvApp] objects to UI-friendly [ServerApp] instances.
     * The [isRunning] flag is set for the app whose ID matches the server's current game.
     */
    fun getAppList(address: String): Result<List<ServerApp>> {
        return try {
            val addressTuple = ComputerDetails.AddressTuple(address, HTTP_PORT)
            val savedCert = loadServerCert(activeCertFileName)
            val nvhttp = NvHTTP(addressTuple, HTTPS_PORT, uniqueId, savedCert, cryptoProvider)
            val serverInfo = nvhttp.getServerInfo(true)
            val runningGameId = nvhttp.getCurrentGame(serverInfo)
            val nvApps = nvhttp.getAppList()

            val apps = nvApps.map { nvApp ->
                ServerApp(
                    appId = nvApp.appId,
                    appName = nvApp.appName,
                    isHdrSupported = nvApp.isHdrSupported,
                    isRunning = nvApp.appId == runningGameId && runningGameId != 0,
                )
            }
            Log.i(TAG, "Fetched ${apps.size} apps from $address (running=$runningGameId)")
            Result.success(apps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch app list from $address", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a per-host certificate file.
     */
    fun deleteServerCert(certFileName: String) {
        val file = File(dataDir, certFileName)
        if (file.exists()) {
            try {
                file.delete()
                Log.i(TAG, "Deleted cert file: $certFileName")
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not delete cert file (SecurityException): $certFileName", e)
            }
        }
    }

    private fun parseXmlValue(xml: String, tag: String): String? {
        val regex = Regex("<$tag>(.*?)</$tag>")
        return regex.find(xml)?.groupValues?.get(1)
    }
}
