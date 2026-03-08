/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

/**
 * Determines whether a parameter value should be stored securely via [SecureKeyManager].
 *
 * Two-layer detection in priority order:
 *  1. **Definition-based** — if the [McpServerDefinition] declares the parameter as
 *     [ParameterType.SECRET], that is authoritative.
 *  2. **Convention-based** — if no definition is available (e.g. user-defined server or
 *     unknown parameter), fall back to keyword matching on the parameter name.
 */
object SecretDetector {

    private val SECRET_NAME_PATTERNS = setOf(
        "key", "token", "secret", "password", "passwd", "pwd",
        "credential", "auth", "apikey", "api_key", "private",
        "cert", "bearer", "access_token", "refresh_token", "pat",
    )

    /**
     * Returns true if [paramKey] should be stored securely.
     *
     * @param paramKey  The parameter name (e.g. "apiKey", "GITHUB_TOKEN")
     * @param definition Optional server definition — checked first for [ParameterType.SECRET]
     */
    fun isSecret(paramKey: String, definition: McpServerDefinition? = null): Boolean {
        // 1. Definition-based — authoritative
        if (definition != null) {
            val param = definition.parameters.find { it.key == paramKey }
            if (param != null) return param.type == ParameterType.SECRET
        }

        // 2. Convention-based fallback
        val normalised = paramKey.lowercase()
            .replace("-", "")
            .replace("_", "")
        return SECRET_NAME_PATTERNS.any { pattern ->
            normalised.contains(pattern.replace("_", ""))
        }
    }
}
