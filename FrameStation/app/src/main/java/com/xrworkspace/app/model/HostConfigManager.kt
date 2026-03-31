// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistence of host configurations via SharedPreferences JSON.
 * Follows the same pattern as [BookmarkManager].
 */
class HostConfigManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "host_configs_json"
        private const val KEY_ACTIVE_HOST = "active_host_id"
        private const val KEY_LEGACY_ADDRESS = "server_address"
        private const val LEGACY_CERT_FILE = "server.crt"
    }

    fun loadHosts(): List<HostConfig> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HostConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    address = obj.getString("address"),
                    macAddress = obj.optString("macAddress", "").ifEmpty { null },
                    isPaired = obj.optBoolean("isPaired", false),
                    gpuType = obj.optString("gpuType", "").ifEmpty { null },
                    certFileName = obj.optString("certFileName", "").ifEmpty { null },
                    lastConnected = obj.optLong("lastConnected", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHosts(hosts: List<HostConfig>) {
        val arr = JSONArray()
        hosts.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("address", h.address)
                put("macAddress", h.macAddress ?: "")
                put("isPaired", h.isPaired)
                put("gpuType", h.gpuType ?: "")
                put("certFileName", h.certFileName ?: "")
                put("lastConnected", h.lastConnected)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun addHost(host: HostConfig) {
        val hosts = loadHosts().toMutableList()
        hosts.add(host)
        saveHosts(hosts)
    }

    fun removeHost(id: String) {
        val hosts = loadHosts().filter { it.id != id }
        saveHosts(hosts)
        // Clear active host if it was the removed one
        if (getActiveHostId() == id) {
            setActiveHostId(null)
        }
    }

    fun updateHost(host: HostConfig) {
        val hosts = loadHosts().map { if (it.id == host.id) host else it }
        saveHosts(hosts)
    }

    fun getActiveHostId(): String? {
        return prefs.getString(KEY_ACTIVE_HOST, null)
    }

    fun setActiveHostId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_HOST, id).apply()
    }

    /**
     * Migrate from single-server config to multi-host format.
     * If no host configs exist but a legacy server_address is saved,
     * creates a HostConfig entry from it.
     * Returns the migrated host list (may be empty if nothing to migrate).
     */
    fun migrateFromSingleServer(): List<HostConfig> {
        val existing = loadHosts()
        if (existing.isNotEmpty()) return existing

        val legacyAddress = prefs.getString(KEY_LEGACY_ADDRESS, null)
        if (legacyAddress.isNullOrBlank() || legacyAddress == "192.168.1.100") {
            return emptyList()
        }

        val migratedHost = HostConfig(
            name = legacyAddress,
            address = legacyAddress,
            certFileName = LEGACY_CERT_FILE,
        )
        val hosts = listOf(migratedHost)
        saveHosts(hosts)
        setActiveHostId(migratedHost.id)
        return hosts
    }

    /**
     * Generate a per-host cert filename.
     */
    fun certFileNameForHost(hostId: String): String {
        return "server_${hostId}.crt"
    }
}
