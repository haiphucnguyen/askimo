/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.rag.content.retriever.ContentRetriever
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.logger
import io.askimo.core.project.ProjectMeta
import io.askimo.core.project.buildRetrievalAugmentor
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

data class Scope(
    val projectName: String,
    val projectDir: Path,
)

/**
 * Manages a chat session with language models, handling model creation, provider settings, and conversation memory.
 *
 * The Session class serves as the central coordinator for interactions between the CLI and language model providers.
 * It maintains:
 * - The active chat model instance
 * - Provider-specific settings
 * - Conversation memory for each provider/model combination
 * - Session parameters that control behavior
 *
 * Session instances are responsible for creating and configuring chat models based on the current
 * session parameters, managing conversation history through memory buckets, and providing access
 * to the active model for sending prompts and receiving responses.
 *
 * @property params The parameters that configure this session, including the current provider, model name,
 *                  and provider-specific settings
 * @property mode The execution mode indicating how the user is running the application
 */
class AppContext(
    val params: AppContextParams,
    val mode: ExecutionMode = ExecutionMode.CLI_INTERACTIVE,
) {
    private val log = logger<AppContext>()

    /**
     * System directive for the AI, typically used for language instructions or global behavior.
     * This can be updated when the user changes locale or wants to modify AI's behavior.
     */
    var systemDirective: String? = null

    /**
     * Gets the currently active model provider for this session.
     */
    fun getActiveProvider(): ModelProvider = params.currentProvider

    /**
     * Current project scope for this session, if any.
     *
     * When set via setScope(ProjectEntry), it records the human-readable project name
     * and the normalized absolute path to the project's root directory. A null value
     * means the session is not bound to any project (and project-specific RAG features
     * may be disabled).
     *
     * This property is read-only to callers; use setScope(...) to activate a project
     * and clearScope() to remove the association.
     */
    var scope: Scope? = null
        private set

    fun setScope(project: ProjectMeta) {
        scope = Scope(project.name, Paths.get(project.root).toAbsolutePath().normalize())
    }

    /**
     * Gets the current provider's settings.
     */
    fun getCurrentProviderSettings(): ProviderSettings = params.providerSettings[params.currentProvider]
        ?: ProviderRegistry.getFactory(params.currentProvider)?.defaultSettings()
        ?: NoopProviderSettings

    /**
     * Gets the provider-specific settings map, or creates defaults if missing.
     */
    fun getOrCreateProviderSettings(provider: ModelProvider): ProviderSettings = params.providerSettings.getOrPut(provider) {
        getModelFactory(provider)?.defaultSettings() ?: NoopProviderSettings
    }

    /**
     * Sets the provider-specific settings into the map.
     */
    fun setProviderSetting(
        provider: ModelProvider,
        settings: ProviderSettings,
    ) {
        params.providerSettings[provider] = settings
    }

    /**
     * Returns the registered factory for the given provider.
     */
    fun getModelFactory(provider: ModelProvider): ChatModelFactory<*>? = ProviderRegistry.getFactory(provider)

    fun getStatelessChatClient(): ChatClient {
        val provider = params.currentProvider
        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val settings = getOrCreateProviderSettings(provider)
        val modelName = params.model

        @Suppress("UNCHECKED_CAST")
        return (factory as ChatModelFactory<ProviderSettings>).create(
            model = modelName,
            settings = settings,
            executionMode = ExecutionMode.CLI_PROMPT,
        )
    }

    /**
     * Creates a fresh ChatClient instance without using cache.
     * This should be used when you need a clean client as a base delegate for session-specific clients.
     *
     * Unlike getChatClient(), this method:
     * - Does NOT use the cached _chatClient
     * - Creates a new instance every time
     * - Properly integrates memory into the LangChain4j AI service
     *
     * @param retriever Optional content retriever for RAG (Retrieval-Augmented Generation).
     *                  If provided, the client will be created with RAG capabilities.
     * @param memory Optional chat memory for conversation context. If provided, memory will be
     *               integrated into the LangChain4j AI service.
     * @return A newly created [ChatClient] instance
     * @throws IllegalStateException if no model factory is registered for the current provider.
     */
    fun createStatefulChatSession(
        executionMode: ExecutionMode = ExecutionMode.CLI_INTERACTIVE,
        retriever: ContentRetriever? = null,
        memory: ChatMemory,
    ): ChatClient {
        val provider = params.currentProvider
        val factory = getModelFactory(provider)
            ?: error("No model factory registered for $provider")
        val settings = getOrCreateProviderSettings(provider)
        val modelName = params.model

        val retrievalAugmentor = retriever?.let { buildRetrievalAugmentor(it) }

        @Suppress("UNCHECKED_CAST")
        return (factory as ChatModelFactory<ProviderSettings>).create(
            model = modelName,
            settings = settings,
            executionMode = ExecutionMode.DESKTOP,
            retrievalAugmentor = retrievalAugmentor,
            chatMemory = memory,
        )
    }

    /**
     * Set the language directive based on user's locale selection.
     * This constructs a comprehensive instruction for the AI to communicate in the specified language,
     * with a fallback to English if the language is not supported by the AI.
     *
     * @param locale The user's selected locale (e.g., Locale.JAPANESE, Locale.ENGLISH)
     */
    fun setLanguageDirective(locale: Locale) {
        systemDirective = buildLanguageDirective(locale)
    }

    /**
     * Build a language directive instruction based on the locale.
     * Uses LocalizationManager to access localized templates.
     * Includes fallback to English if the AI doesn't support the target language.
     *
     * @param locale The target locale
     * @return A complete language directive with fallback instructions
     */
    private fun buildLanguageDirective(locale: Locale): String {
        // Temporarily set locale to get the correct translations
        val previousLocale = Locale.getDefault()
        LocalizationManager.setLocale(locale)

        try {
            val languageCode = locale.language

            // For English, use simplified template without fallback
            return if (languageCode == "en") {
                LocalizationManager.getString("language.directive.english.only")
            } else {
                // Get the language display name
                val languageDisplayName = LocalizationManager.getString("language.name.display")

                // Get templates and format with language name
                val instruction = LocalizationManager.getString(
                    "language.directive.instruction",
                    languageDisplayName,
                    languageDisplayName,
                    languageDisplayName,
                    languageDisplayName,
                )

                val fallback = LocalizationManager.getString(
                    "language.directive.fallback",
                    languageDisplayName,
                    languageDisplayName,
                    languageDisplayName,
                )

                instruction + fallback
            }
        } finally {
            // Restore previous locale if it was different
            if (previousLocale != locale) {
                LocalizationManager.setLocale(previousLocale)
            }
        }
    }
}
