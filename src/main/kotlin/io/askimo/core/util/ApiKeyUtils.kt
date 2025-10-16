/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

object ApiKeyUtils {
    /**
     * Returns a safe API key for use in OpenAI or providers requires api key / LangChain4j builders.
     * - Uses the provided key if non-blank.
     * - Falls back to a harmless default ("sk-dummy") to avoid IllegalArgumentException
     *   during native image or test runs.
     */
    fun safeApiKey(raw: String?): String = raw?.takeIf { it.isNotBlank() } ?: "sk-dummy"
}
