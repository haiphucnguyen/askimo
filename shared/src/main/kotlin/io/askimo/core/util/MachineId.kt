/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.util

import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID

/**
 * Resolves a stable, hardware-bound device identifier for the current machine.
 *
 * The raw value is **always SHA-256 hashed** and formatted as a UUID so:
 *  - the shape is consistent across platforms
 *  - no raw hardware serial / MAC address is ever stored or transmitted
 *
 * Resolution order per platform:
 *
 * | OS      | Primary source                                  | Secondary            |
 * |---------|-------------------------------------------------|----------------------|
 * | macOS   | `ioreg` → `IOPlatformUUID`                      | MAC address hash     |
 * | Linux   | `/etc/machine-id`                               | `/var/lib/dbus/machine-id` → MAC |
 * | Windows | `wmic csproduct get UUID`                       | MAC address hash     |
 * | Unknown | MAC address hash                                | —                    |
 *
 * Returns `null` only when every strategy fails (extremely unlikely in practice).
 * The caller ([io.askimo.ui.common.preferences.ApplicationPreferences.getOrCreateDeviceId]) falls back to a
 * persisted random UUID in that case.
 */
object MachineId {

    /** Attempt to resolve a hardware-bound ID; returns null if every strategy fails. */
    fun resolve(): String? {
        val raw = when (os()) {
            OS.MACOS -> macOsUuid() ?: macAddress()
            OS.LINUX -> linuxMachineId() ?: macAddress()
            OS.WINDOWS -> windowsUuid() ?: macAddress()
            OS.UNKNOWN -> macAddress()
        } ?: return null

        return sha256uuid(raw)
    }

    // ── macOS ─────────────────────────────────────────────────────────────────

    /**
     * Reads the IOPlatformUUID from the I/O Registry — unique per logic board,
     * survives OS reinstalls, does not change on user account changes.
     *
     * Sample output line:  "IOPlatformUUID" = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"
     */
    private fun macOsUuid(): String? = runCatching {
        val proc = ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines
                .firstOrNull { it.contains("IOPlatformUUID") }
                ?.substringAfter("= ")
                ?.trim()
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() }
        }.also { proc.waitFor() }
    }.getOrNull()

    // ── Linux ─────────────────────────────────────────────────────────────────

    /**
     * Reads `/etc/machine-id` (systemd) or `/var/lib/dbus/machine-id` (D-Bus).
     * Both are 128-bit hex strings written once at first boot, stable across reboots.
     */
    private fun linuxMachineId(): String? {
        for (path in listOf("/etc/machine-id", "/var/lib/dbus/machine-id")) {
            val text = runCatching {
                File(path).readText().trim()
            }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    // ── Windows ───────────────────────────────────────────────────────────────

    /**
     * Reads the motherboard UUID via WMI — stable across OS reinstalls.
     *
     * `wmic csproduct get UUID` outputs:
     * ```
     * UUID
     * XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
     * ```
     */
    private fun windowsUuid(): String? = runCatching {
        val proc = ProcessBuilder("wmic", "csproduct", "get", "UUID")
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && it != "UUID" }
                ?.takeIf { it.isNotBlank() && it.uppercase() != "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF" }
        }.also { proc.waitFor() }
    }.getOrNull()

    // ── Cross-platform fallback ───────────────────────────────────────────────

    /**
     * Concatenates the MAC addresses of all non-loopback, non-virtual interfaces
     * and hashes the result. Not perfectly stable (adding a USB NIC changes it)
     * but far better than a random UUID as a machine fingerprint.
     */
    private fun macAddress(): String? {
        val macs = NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.filter { !it.isLoopback && !it.isVirtual && it.hardwareAddress != null }
            ?.mapNotNull { nic ->
                nic.hardwareAddress
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(":") { "%02X".format(it) }
            }
            ?.sorted() // deterministic order
            ?.joinToString("|")
        return macs?.takeIf { it.isNotBlank() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * SHA-256 hashes [input] and formats the first 128 bits as a type-5-like UUID string.
     * The hash ensures raw hardware values are never stored or transmitted.
     */
    private fun sha256uuid(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        // Force version 5 (name-based) and variant bits for a valid UUID shape
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        return UUID.nameUUIDFromBytes(digest).toString()
    }

    private enum class OS { MACOS, LINUX, WINDOWS, UNKNOWN }

    private fun os(): OS {
        val name = System.getProperty("os.name").lowercase()
        return when {
            name.contains("mac") || name.contains("darwin") -> OS.MACOS
            name.contains("linux") -> OS.LINUX
            name.contains("windows") -> OS.WINDOWS
            else -> OS.UNKNOWN
        }
    }
}
