/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import kotlinx.serialization.Serializable

/**
 * Test-specific provider settings that implement the same interfaces as real providers
 * but use safe test names that won't conflict with actual user data.
 */
@Serializable
data class TestProviderSettings(
    override var apiKey: String = "",
    override val defaultModel: String = "test-model",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> = listOf(
        "apiKey: ${apiKey.take(5)}***",
        "presets: $presets",
    )

    override fun toString(): String = "TestProviderSettings(apiKey=****, presets=$presets)"
}

/**
 * Test-specific ModelProvider enum values that use safe names
 */
enum class TestModelProvider {
    TEST_OPENAI_PROVIDER,
    TEST_GEMINI_PROVIDER,
    TEST_XAI_PROVIDER,
    TEST_ANTHROPIC_PROVIDER,
    TEST_OLLAMA_PROVIDER,
    TEST_UNKNOWN_PROVIDER,
    ;

    /**
     * Convert to a safe provider name for keychain storage
     */
    fun toSafeProviderName(): String = "test_${name.lowercase()}"
}
