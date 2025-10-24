/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.security.SecureApiKeyManager
import io.askimo.core.security.SecureSessionManager
import io.askimo.core.session.Session
import io.askimo.core.session.SessionConfigManager
import io.askimo.core.util.Logger.info

/**
 * Handles API key security management commands.
 */
class ApiKeySecurityCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":security"
    override val description: String = "Manage API key security settings"

    override fun handle(line: org.jline.reader.ParsedLine) {
        val args = line.words().drop(1)

        if (args.isEmpty()) {
            showSecurityStatus()
            return
        }

        when (args[0].lowercase()) {
            "status" -> showSecurityStatus()
            "migrate" -> migrateApiKeys()
            "help" -> showHelp()
            else -> {
                info("Unknown security command: ${args[0]}")
                showHelp()
            }
        }
    }

    private fun showSecurityStatus() {
        info("API Key Security Status:")
        info("=" + "=".repeat(49))

        val params = session.params

        var hasAnyKeys = false

        params.providerSettings.forEach { (provider, settings) ->
            if (settings is io.askimo.core.providers.HasApiKey && settings.apiKey.isNotBlank()) {
                hasAnyKeys = true
                val hasSecure = SecureApiKeyManager.hasSecureApiKey(provider.name.lowercase())

                val status =
                    if (hasSecure) {
                        "🔒 Stored securely"
                    } else {
                        // Check if it's encrypted in session file
                        if (settings.apiKey.startsWith("encrypted:")) {
                            "🔐 Encrypted in config file"
                        } else if (settings.apiKey == "***keychain***") {
                            "🔒 Using keychain"
                        } else {
                            "⚠️ STORED AS PLAIN TEXT"
                        }
                    }

                info("${provider.name}: $status")
            }
        }

        if (!hasAnyKeys) {
            info("No API keys configured.")
        }

        info("")
        info("Security Recommendations:")
        info("• Use ':security migrate' to move plain text keys to secure storage")
        info("• System keychain is the most secure option")
        info("• Encrypted storage is better than plain text but less secure than keychain")
        info("• Consider using environment variables for CI/CD environments")
    }

    private fun migrateApiKeys() {
        info("Migrating API keys to secure storage...")

        val params = session.params
        val secureSessionManager = SecureSessionManager()
        val migrationResult = secureSessionManager.migrateExistingApiKeys(params)

        if (migrationResult.results.isEmpty()) {
            info("No API keys found to migrate.")
            return
        }

        info("")
        migrationResult.getSecurityReport().forEach { info(it) }

        // Save the updated session
        SessionConfigManager.save(session.params)
        info("")
        info("Migration completed. Updated configuration saved.")
    }

    private fun showHelp() {
        info("API Key Security Commands:")
        info("  :security status  - Show current API key security status")
        info("  :security migrate - Migrate API keys to secure storage")
        info("  :security help    - Show this help message")
        info("")
        info("Security levels (from most to least secure):")
        info("  🔒 System Keychain - OS-managed secure storage")
        info("  🔐 Encrypted      - AES-256 encrypted with local key")
        info("  ⚠️ Plain Text     - Unencrypted (INSECURE)")
    }
}
