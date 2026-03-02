/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

/**
 * Resolves embedding token limits for locally-hosted models (Ollama, Docker AI, LocalAI, LMStudio).
 * These providers share the same model ecosystem so limits are resolved by model name pattern.
 */
object LocalEmbeddingTokenLimits {
    /**
     * Returns the maximum token limit for a locally-hosted embedding model.
     * Matches against common model name patterns across Ollama, Docker AI, LocalAI, and LMStudio.
     *
     * @param modelName The embedding model name (will be lowercased internally)
     * @return Maximum number of tokens the model can handle
     */
    fun resolve(modelName: String): Int {
        val name = modelName.lowercase()
        return when {
            name.contains("nomic-embed") || name.contains("nomic_embed") -> 8192
            name.contains("mxbai-embed") || name.contains("mxbai_embed") -> 512
            name.contains("bge-") || name.contains("bge_") -> 512
            name.contains("gte-") || name.contains("gte_") -> 8192
            name.contains("e5-") || name.contains("e5_") -> 512
            name.contains("all-minilm") || name.contains("all_minilm") -> 512
            name.contains("sentence-transformers") -> 512
            name.contains("text-embedding-3") -> 8191
            name.contains("text-embedding") -> 8191
            name.contains("qwen") && name.contains("embed") -> 8192
            else -> 2048
        }
    }
}
