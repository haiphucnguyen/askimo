/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.security.KeychainManager.OperatingSystem.LINUX
import io.askimo.core.security.KeychainManager.OperatingSystem.MACOS
import io.askimo.core.security.KeychainManager.OperatingSystem.UNKNOWN
import io.askimo.core.security.KeychainManager.OperatingSystem.WINDOWS
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.warn
import java.io.IOException

/**
 * Manages secure storage of API keys using system keychain.
 * Falls back to encrypted storage if keychain is not available.
 */
object KeychainManager {
    private const val SERVICE_NAME = "askimo-cli"

    /**
     * Stores an API key securely in the system keychain.
     *
     * @param provider The provider name (e.g., "openai", "anthropic")
     * @param apiKey The API key to store
     * @return true if stored successfully in keychain, false if keychain failed
     */
    fun storeApiKey(
        provider: String,
        apiKey: String,
    ): Boolean = try {
        when (getOperatingSystem()) {
            MACOS -> storeMacOSKeychain(provider, apiKey)
            LINUX -> storeLinuxKeyring(provider, apiKey)
            WINDOWS -> storeWindowsCredentialManager(provider, apiKey)
            UNKNOWN -> {
                warn("Unknown operating system, keychain storage not available")
                false
            }
        }
    } catch (e: Exception) {
        debug("Failed to store API key in keychain: ${e.message}", e)
        false
    }

    /**
     * Retrieves an API key from the system keychain.
     *
     * @param provider The provider name
     * @return The API key if found, null if not found or keychain failed
     */
    fun retrieveApiKey(provider: String): String? = try {
        when (getOperatingSystem()) {
            MACOS -> retrieveMacOSKeychain(provider)
            LINUX -> retrieveLinuxKeyring(provider)
            WINDOWS -> retrieveWindowsCredentialManager(provider)
            UNKNOWN -> {
                debug("Unknown operating system, keychain retrieval not available")
                null
            }
        }
    } catch (e: Exception) {
        debug("Failed to retrieve API key from keychain: ${e.message}", e)
        null
    }

    /**
     * Removes an API key from the system keychain.
     *
     * @param provider The provider name
     * @return true if removed successfully, false otherwise
     */
    fun removeApiKey(provider: String): Boolean = try {
        when (getOperatingSystem()) {
            MACOS -> removeMacOSKeychain(provider)
            LINUX -> removeLinuxKeyring(provider)
            WINDOWS -> removeWindowsCredentialManager(provider)
            UNKNOWN -> false
        }
    } catch (e: Exception) {
        debug("Failed to remove API key from keychain: ${e.message}")
        false
    }

    private fun storeMacOSKeychain(
        provider: String,
        apiKey: String,
    ): Boolean {
        val account = "askimo-$provider"
        val process =
            ProcessBuilder(
                "security",
                "add-generic-password",
                "-a",
                account,
                "-s",
                SERVICE_NAME,
                "-w",
                apiKey,
                "-U", // Update if exists
            ).start()

        val exitCode = process.waitFor()
        return exitCode == 0
    }

    private fun retrieveMacOSKeychain(provider: String): String? {
        val account = "askimo-$provider"
        val process =
            ProcessBuilder(
                "security",
                "find-generic-password",
                "-a",
                account,
                "-s",
                SERVICE_NAME,
                "-w", // Output password only
            ).start()

        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        } else {
            null
        }
    }

    private fun removeMacOSKeychain(provider: String): Boolean {
        val account = "askimo-$provider"
        val process =
            ProcessBuilder(
                "security",
                "delete-generic-password",
                "-a",
                account,
                "-s",
                SERVICE_NAME,
            ).start()

        val exitCode = process.waitFor()
        return exitCode == 0
    }

    private fun storeLinuxKeyring(
        provider: String,
        apiKey: String,
    ): Boolean {
        // Try secret-tool first (libsecret)
        if (isCommandAvailable("secret-tool")) {
            try {
                val process =
                    ProcessBuilder(
                        "secret-tool",
                        "store",
                        "--label",
                        "Askimo API Key for $provider",
                        "service",
                        SERVICE_NAME,
                        "account",
                        "askimo-$provider",
                    ).start()

                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(apiKey)
                    writer.flush()
                }

                val exitCode = process.waitFor()
                return exitCode == 0
            } catch (e: Exception) {
                debug("Failed to store API key with secret-tool: ${e.message}")
                return false
            }
        }

        // Fallback to gnome-keyring if available
        if (isCommandAvailable("gnome-keyring-daemon")) {
            // Implementation for gnome-keyring would go here
            // For now, return false to use encrypted fallback
            debug("gnome-keyring-daemon available but not implemented")
            return false
        }

        debug("No Linux keyring implementation available (secret-tool not found)")
        return false
    }

    private fun retrieveLinuxKeyring(provider: String): String? {
        if (isCommandAvailable("secret-tool")) {
            try {
                val process =
                    ProcessBuilder(
                        "secret-tool",
                        "lookup",
                        "service",
                        SERVICE_NAME,
                        "account",
                        "askimo-$provider",
                    ).start()

                val exitCode = process.waitFor()
                return if (exitCode == 0) {
                    process.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                        .takeIf { it.isNotBlank() }
                } else {
                    debug("secret-tool lookup failed with exit code $exitCode for provider $provider")
                    null
                }
            } catch (e: Exception) {
                debug("Failed to retrieve API key with secret-tool: ${e.message}")
                return null
            }
        }

        debug("secret-tool not available for retrieving API key")
        return null
    }

    private fun removeLinuxKeyring(provider: String): Boolean {
        if (isCommandAvailable("secret-tool")) {
            try {
                val process =
                    ProcessBuilder(
                        "secret-tool",
                        "clear",
                        "service",
                        SERVICE_NAME,
                        "account",
                        "askimo-$provider",
                    ).start()

                val exitCode = process.waitFor()
                return exitCode == 0
            } catch (e: Exception) {
                debug("Failed to remove API key with secret-tool: ${e.message}")
                return false
            }
        }

        debug("secret-tool not available for removing API key")
        return false
    }

    private fun storeWindowsCredentialManager(
        provider: String,
        apiKey: String,
    ): Boolean {
        val target = "$SERVICE_NAME:askimo-$provider"

        try {
            val process = ProcessBuilder(
                "cmdkey",
                "/generic:$target",
                "/user:askimo",
                "/pass:$apiKey",
            ).start()

            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            debug("Failed to store API key with cmdkey: ${e.message}")
            return false
        }
    }

    private fun retrieveWindowsCredentialManager(provider: String): String? {
        val target = "$SERVICE_NAME:askimo-$provider"

        // Try PowerShell approach using CredentialManager API
        try {
            // Build PowerShell script with proper escaping
            val csCode = """using System;
using System.Runtime.InteropServices;
using System.Text;

public class CredentialManager {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    [DllImport(\""advapi32.dll\"", EntryPoint = \""CredReadW\"", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredRead(string target, uint type, int reservedFlag, out IntPtr credentialPtr);

    [DllImport(\""advapi32.dll\"", SetLastError = true)]
    public static extern bool CredFree(IntPtr cred);

    public static string GetPassword(string target) {
        IntPtr credPtr;
        if (CredRead(target, 1, 0, out credPtr)) {
            try {
                CREDENTIAL cred = (CREDENTIAL)Marshal.PtrToStructure(credPtr, typeof(CREDENTIAL));
                if (cred.CredentialBlobSize > 0) {
                    byte[] passwordBytes = new byte[cred.CredentialBlobSize];
                    Marshal.Copy(cred.CredentialBlob, passwordBytes, 0, (int)cred.CredentialBlobSize);
                    return Encoding.Unicode.GetString(passwordBytes);
                }
            } finally {
                CredFree(credPtr);
            }
        }
        return null;
    }
}"""

            val powershellScript = "Add-Type -TypeDefinition '$csCode'; " +
                "\$password = [CredentialManager]::GetPassword('$target'); " +
                "if (\$password -ne \$null) { Write-Output \$password; exit 0 } " +
                "else { [Console]::Error.WriteLine('Credential not found'); exit 1 }"

            val process = ProcessBuilder(
                "powershell.exe",
                "-WindowStyle",
                "Hidden",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                powershellScript,
            ).start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank()) {
                return output
            } else if (errorOutput.isNotBlank()) {
                debug("PowerShell credential retrieval failed with exit code $exitCode: $errorOutput")
            } else {
                debug("PowerShell credential retrieval failed with exit code $exitCode (no error message)")
            }
        } catch (e: Exception) {
            debug("PowerShell credential retrieval failed: ${e.message}")
        }

        // PowerShell failed, check if credential exists via cmdkey
        try {
            val checkProcess = ProcessBuilder("cmdkey", "/list:$target").start()
            val checkOutput = checkProcess.inputStream.bufferedReader().readText()
            val checkExitCode = checkProcess.waitFor()

            if (checkExitCode == 0 && checkOutput.contains(target)) {
                debug("Credential exists in Windows Credential Manager but cannot be retrieved")
            }
        } catch (e: Exception) {
            debug("Failed to check credential existence: ${e.message}")
        }

        return null
    }

    private fun removeWindowsCredentialManager(provider: String): Boolean {
        val target = "$SERVICE_NAME:askimo-$provider"

        try {
            val process = ProcessBuilder(
                "cmdkey",
                "/delete:$target",
            ).start()

            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            debug("Failed to remove API key with cmdkey: ${e.message}")
            return false
        }
    }

    private fun getOperatingSystem(): OperatingSystem {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> MACOS
            osName.contains("linux") -> LINUX
            osName.contains("windows") -> WINDOWS
            else -> UNKNOWN
        }
    }

    private fun isCommandAvailable(command: String): Boolean = try {
        val process = ProcessBuilder("which", command).start()
        process.waitFor() == 0
    } catch (e: IOException) {
        false
    }

    enum class OperatingSystem {
        MACOS,
        LINUX,
        WINDOWS,
        UNKNOWN,
    }
}
