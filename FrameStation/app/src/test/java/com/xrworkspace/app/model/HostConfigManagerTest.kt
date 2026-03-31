// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.model

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In-memory SharedPreferences implementation for unit testing.
 * Avoids Android framework dependency.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val editor = FakeEditor()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (data[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int =
        data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long =
        data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float =
        data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = editor
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {}
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {}

    inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            key?.let { pending[it] = values }
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { removals.add(it) }
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }
        override fun commit(): Boolean {
            applyChanges()
            return true
        }
        override fun apply() {
            applyChanges()
        }
        private fun applyChanges() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
            pending.clear()
            removals.clear()
            clearAll = false
        }
    }
}

class HostConfigManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: HostConfigManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = HostConfigManager(prefs)
    }

    @Test
    fun `loadHosts returns empty list when no data`() {
        val hosts = manager.loadHosts()
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `saveHosts and loadHosts round-trip`() {
        val hosts = listOf(
            HostConfig(id = "h1", name = "PC1", address = "192.168.1.10"),
            HostConfig(
                id = "h2",
                name = "PC2",
                address = "10.0.0.5",
                macAddress = "AA:BB:CC:DD:EE:FF",
                isPaired = true,
                gpuType = "RTX 4090",
                certFileName = "server_h2.crt",
                lastConnected = 1700000000L,
            ),
        )
        manager.saveHosts(hosts)
        val loaded = manager.loadHosts()

        assertEquals(2, loaded.size)
        assertEquals("h1", loaded[0].id)
        assertEquals("PC1", loaded[0].name)
        assertEquals("192.168.1.10", loaded[0].address)
        assertNull(loaded[0].macAddress)
        assertEquals(false, loaded[0].isPaired)

        assertEquals("h2", loaded[1].id)
        assertEquals("PC2", loaded[1].name)
        assertEquals("10.0.0.5", loaded[1].address)
        assertEquals("AA:BB:CC:DD:EE:FF", loaded[1].macAddress)
        assertEquals(true, loaded[1].isPaired)
        assertEquals("RTX 4090", loaded[1].gpuType)
        assertEquals("server_h2.crt", loaded[1].certFileName)
        assertEquals(1700000000L, loaded[1].lastConnected)
    }

    @Test
    fun `addHost appends to existing list`() {
        val host1 = HostConfig(id = "h1", name = "PC1", address = "192.168.1.1")
        manager.addHost(host1)
        assertEquals(1, manager.loadHosts().size)

        val host2 = HostConfig(id = "h2", name = "PC2", address = "192.168.1.2")
        manager.addHost(host2)
        val hosts = manager.loadHosts()
        assertEquals(2, hosts.size)
        assertEquals("h1", hosts[0].id)
        assertEquals("h2", hosts[1].id)
    }

    @Test
    fun `removeHost removes by id`() {
        manager.saveHosts(listOf(
            HostConfig(id = "h1", name = "PC1", address = "1.1.1.1"),
            HostConfig(id = "h2", name = "PC2", address = "2.2.2.2"),
            HostConfig(id = "h3", name = "PC3", address = "3.3.3.3"),
        ))
        manager.removeHost("h2")
        val hosts = manager.loadHosts()
        assertEquals(2, hosts.size)
        assertEquals("h1", hosts[0].id)
        assertEquals("h3", hosts[1].id)
    }

    @Test
    fun `removeHost clears active host if it was removed`() {
        manager.saveHosts(listOf(
            HostConfig(id = "h1", name = "PC1", address = "1.1.1.1"),
        ))
        manager.setActiveHostId("h1")
        assertEquals("h1", manager.getActiveHostId())

        manager.removeHost("h1")
        assertNull(manager.getActiveHostId())
    }

    @Test
    fun `removeHost does not clear active host if different host removed`() {
        manager.saveHosts(listOf(
            HostConfig(id = "h1", name = "PC1", address = "1.1.1.1"),
            HostConfig(id = "h2", name = "PC2", address = "2.2.2.2"),
        ))
        manager.setActiveHostId("h1")
        manager.removeHost("h2")
        assertEquals("h1", manager.getActiveHostId())
    }

    @Test
    fun `updateHost replaces matching host`() {
        manager.saveHosts(listOf(
            HostConfig(id = "h1", name = "PC1", address = "1.1.1.1"),
            HostConfig(id = "h2", name = "PC2", address = "2.2.2.2"),
        ))
        val updated = HostConfig(id = "h1", name = "Updated PC", address = "9.9.9.9", isPaired = true)
        manager.updateHost(updated)

        val hosts = manager.loadHosts()
        assertEquals(2, hosts.size)
        assertEquals("Updated PC", hosts[0].name)
        assertEquals("9.9.9.9", hosts[0].address)
        assertEquals(true, hosts[0].isPaired)
        // h2 unchanged
        assertEquals("PC2", hosts[1].name)
    }

    @Test
    fun `active host id persists`() {
        manager.setActiveHostId("test-id")
        assertEquals("test-id", manager.getActiveHostId())

        manager.setActiveHostId(null)
        assertNull(manager.getActiveHostId())
    }

    @Test
    fun `migrateFromSingleServer creates host from legacy address`() {
        prefs.edit().putString("server_address", "192.168.1.50").apply()

        val hosts = manager.migrateFromSingleServer()
        assertEquals(1, hosts.size)
        assertEquals("192.168.1.50", hosts[0].address)
        assertEquals("192.168.1.50", hosts[0].name)
        assertEquals("server.crt", hosts[0].certFileName)
        assertNotNull(hosts[0].id)

        // Active host should be set
        assertEquals(hosts[0].id, manager.getActiveHostId())

        // Should be persisted
        val loaded = manager.loadHosts()
        assertEquals(1, loaded.size)
        assertEquals(hosts[0].id, loaded[0].id)
    }

    @Test
    fun `migrateFromSingleServer skips default address`() {
        prefs.edit().putString("server_address", "192.168.1.100").apply()
        val hosts = manager.migrateFromSingleServer()
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `migrateFromSingleServer skips when hosts already exist`() {
        manager.saveHosts(listOf(
            HostConfig(id = "existing", name = "Existing", address = "10.0.0.1"),
        ))
        prefs.edit().putString("server_address", "192.168.1.50").apply()

        val hosts = manager.migrateFromSingleServer()
        assertEquals(1, hosts.size)
        assertEquals("existing", hosts[0].id)
    }

    @Test
    fun `migrateFromSingleServer skips when no legacy address`() {
        val hosts = manager.migrateFromSingleServer()
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `certFileNameForHost generates expected format`() {
        val name = manager.certFileNameForHost("abc-123")
        assertEquals("server_abc-123.crt", name)
    }

    @Test
    fun `loadHosts handles corrupted JSON gracefully`() {
        prefs.edit().putString("host_configs_json", "not valid json!!!").apply()
        val hosts = manager.loadHosts()
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `saveHosts with empty list clears data`() {
        manager.addHost(HostConfig(id = "h1", name = "PC1", address = "1.1.1.1"))
        assertEquals(1, manager.loadHosts().size)

        manager.saveHosts(emptyList())
        assertTrue(manager.loadHosts().isEmpty())
    }
}
