// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.wol.WakeOnLanSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin-friendly wrapper around [WakeOnLanSender] for sending Wake-on-LAN magic packets.
 *
 * WoL packets are "fire and forget" — there is no response from the target machine.
 * A successful [Result] means the packet was sent, not that the machine woke up.
 */
class WolManager {

    /**
     * Send a Wake-on-LAN magic packet to the given address and MAC.
     *
     * @param address  IP address of the target machine (used for address resolution).
     * @param macAddress  MAC address in XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX format.
     * @param port  HTTP port of the streaming server (default 47989).
     * @return [Result.success] if the packet was sent, [Result.failure] on error.
     */
    suspend fun sendWakePacket(
        address: String,
        macAddress: String,
        port: Int = DEFAULT_PORT,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedMac = normalizeMacAddress(macAddress)

            val computer = ComputerDetails().apply {
                this.localAddress = ComputerDetails.AddressTuple(address, port)
                this.macAddress = normalizedMac
            }

            WakeOnLanSender.sendWolPacket(computer)
        }
    }

    /**
     * Validate whether [mac] is a well-formed MAC address.
     * Accepts colon-separated (AA:BB:CC:DD:EE:FF) and dash-separated (AA-BB-CC-DD-EE-FF) formats.
     */
    fun isValidMacAddress(mac: String): Boolean {
        return MAC_REGEX.matches(mac.trim())
    }

    companion object {
        private const val DEFAULT_PORT = 47989

        /** Matches XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX (case-insensitive hex, consistent separator). */
        private val MAC_REGEX = Regex(
            "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$|^([0-9A-Fa-f]{2}-){5}[0-9A-Fa-f]{2}$"
        )

        /**
         * Normalize a MAC address to colon-separated uppercase format.
         * WakeOnLanSender internally parses with ':' as delimiter.
         */
        internal fun normalizeMacAddress(mac: String): String {
            return mac.trim().replace('-', ':').uppercase()
        }
    }
}
