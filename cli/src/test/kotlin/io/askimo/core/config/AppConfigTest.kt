/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for AppConfig field update methods.
 * These tests verify that the domain object update methods work correctly.
 */
class AppConfigTest {

    @Test
    fun `updateRagField should handle useAbsolutePathInCitations`() {
        val config = RagConfig(useAbsolutePathInCitations = true)

        val updated = updateRagFieldHelper(config, "useAbsolutePathInCitations", false)

        assertFalse(updated.useAbsolutePathInCitations)
    }

    @Test
    fun `updateRagField should handle all numeric fields`() {
        val config = RagConfig()

        var updated = updateRagFieldHelper(config, "vectorSearchMaxResults", 50)
        assertEquals(50, updated.vectorSearchMaxResults)

        updated = updateRagFieldHelper(config, "vectorSearchMinScore", 0.5)
        assertEquals(0.5, updated.vectorSearchMinScore, 0.001)

        updated = updateRagFieldHelper(config, "hybridMaxResults", 25)
        assertEquals(25, updated.hybridMaxResults)

        updated = updateRagFieldHelper(config, "rankFusionConstant", 100)
        assertEquals(100, updated.rankFusionConstant)
    }

    @Test
    fun `updateModelsField should handle all provider vision models`() {
        val config = ModelsConfig()

        // Test OpenAI
        var updated = updateModelsFieldHelper(config, "openai.visionModel", "gpt-4-vision-preview")
        assertEquals("gpt-4-vision-preview", updated.openai.visionModel)

        // Test Anthropic
        updated = updateModelsFieldHelper(config, "anthropic.visionModel", "claude-3-opus-20240229")
        assertEquals("claude-3-opus-20240229", updated.anthropic.visionModel)

        // Test Gemini
        updated = updateModelsFieldHelper(config, "gemini.visionModel", "gemini-pro-vision")
        assertEquals("gemini-pro-vision", updated.gemini.visionModel)

        // Test XAI
        updated = updateModelsFieldHelper(config, "xai.visionModel", "grok-vision-beta")
        assertEquals("grok-vision-beta", updated.xai.visionModel)

        // Test Ollama
        updated = updateModelsFieldHelper(config, "ollama.visionModel", "llava:13b")
        assertEquals("llava:13b", updated.ollama.visionModel)

        // Test Docker
        updated = updateModelsFieldHelper(config, "docker.visionModel", "llava:latest")
        assertEquals("llava:latest", updated.docker.visionModel)

        // Test LocalAI
        updated = updateModelsFieldHelper(config, "localai.visionModel", "bakllava")
        assertEquals("bakllava", updated.localai.visionModel)

        // Test LMStudio
        updated = updateModelsFieldHelper(config, "lmstudio.visionModel", "llava-v1.6-mistral-7b")
        assertEquals("llava-v1.6-mistral-7b", updated.lmstudio.visionModel)
    }

    @Test
    fun `updateModelsField should handle embedding models`() {
        val config = ModelsConfig()

        // Test OpenAI
        var updated = updateModelsFieldHelper(config, "openai.embeddingModel", "text-embedding-3-large")
        assertEquals("text-embedding-3-large", updated.openai.embeddingModel)

        // Test Gemini
        updated = updateModelsFieldHelper(config, "gemini.embeddingModel", "text-embedding-004")
        assertEquals("text-embedding-004", updated.gemini.embeddingModel)

        // Test Ollama
        updated = updateModelsFieldHelper(config, "ollama.embeddingModel", "mxbai-embed-large")
        assertEquals("mxbai-embed-large", updated.ollama.embeddingModel)
    }

    @Test
    fun `updateEmbeddingField should handle all fields`() {
        val config = EmbeddingConfig()

        var updated = updateEmbeddingFieldHelper(config, "maxCharsPerChunk", 5000)
        assertEquals(5000, updated.maxCharsPerChunk)

        updated = updateEmbeddingFieldHelper(config, "chunkOverlap", 300)
        assertEquals(300, updated.chunkOverlap)

        updated = updateEmbeddingFieldHelper(config, "preferredDim", 1536)
        assertEquals(1536, updated.preferredDim)
    }

    @Test
    fun `updateChatField should handle all fields including nested sampling`() {
        var config = ChatConfig()

        // Test top-level fields
        config = updateChatFieldHelper(config, "maxTokens", 10000)
        assertEquals(10000, config.maxTokens)

        config = updateChatFieldHelper(config, "summarizationThreshold", 0.8)
        assertEquals(0.8, config.summarizationThreshold, 0.001)

        config = updateChatFieldHelper(config, "enableAsyncSummarization", false)
        assertFalse(config.enableAsyncSummarization)

        // Test nested sampling fields
        config = updateChatFieldHelper(config, "sampling.temperature", 0.7)
        assertEquals(0.7, config.sampling.temperature, 0.001)

        config = updateChatFieldHelper(config, "sampling.topP", 0.9)
        assertEquals(0.9, config.sampling.topP, 0.001)

        config = updateChatFieldHelper(config, "sampling.enabled", false)
        assertFalse(config.sampling.enabled)

        // Verify all fields are correct after multiple updates
        assertEquals(10000, config.maxTokens)
        assertEquals(0.8, config.summarizationThreshold, 0.001)
        assertFalse(config.enableAsyncSummarization)
        assertEquals(0.7, config.sampling.temperature, 0.001)
        assertEquals(0.9, config.sampling.topP, 0.001)
        assertFalse(config.sampling.enabled)
    }

    @Test
    fun `updateDeveloperField should handle all fields`() {
        val config = DeveloperConfig()

        var updated = updateDeveloperFieldHelper(config, "enabled", true)
        assertTrue(updated.enabled)

        updated = updateDeveloperFieldHelper(config, "active", true)
        assertTrue(updated.active)
    }

    private fun updateRagFieldHelper(config: RagConfig, field: String, value: Any): RagConfig {
        val method = AppConfig::class.java.getDeclaredMethod(
            "updateRagField",
            RagConfig::class.java,
            String::class.java,
            Any::class.java,
        )
        method.isAccessible = true
        return method.invoke(AppConfig, config, field, value) as RagConfig
    }

    private fun updateModelsFieldHelper(config: ModelsConfig, field: String, value: Any): ModelsConfig {
        val method = AppConfig::class.java.getDeclaredMethod(
            "updateModelsField",
            ModelsConfig::class.java,
            String::class.java,
            Any::class.java,
        )
        method.isAccessible = true
        return method.invoke(AppConfig, config, field, value) as ModelsConfig
    }

    private fun updateEmbeddingFieldHelper(config: EmbeddingConfig, field: String, value: Any): EmbeddingConfig {
        val method = AppConfig::class.java.getDeclaredMethod(
            "updateEmbeddingField",
            EmbeddingConfig::class.java,
            String::class.java,
            Any::class.java,
        )
        method.isAccessible = true
        return method.invoke(AppConfig, config, field, value) as EmbeddingConfig
    }

    private fun updateChatFieldHelper(config: ChatConfig, field: String, value: Any): ChatConfig {
        val method = AppConfig::class.java.getDeclaredMethod(
            "updateChatField",
            ChatConfig::class.java,
            String::class.java,
            Any::class.java,
        )
        method.isAccessible = true
        return method.invoke(AppConfig, config, field, value) as ChatConfig
    }

    private fun updateDeveloperFieldHelper(config: DeveloperConfig, field: String, value: Any): DeveloperConfig {
        val method = AppConfig::class.java.getDeclaredMethod(
            "updateDeveloperField",
            DeveloperConfig::class.java,
            String::class.java,
            Any::class.java,
        )
        method.isAccessible = true
        return method.invoke(AppConfig, config, field, value) as DeveloperConfig
    }
}
