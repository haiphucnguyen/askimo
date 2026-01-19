/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.rag.content.retriever.ContentRetriever
import io.askimo.core.config.AppConfig
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ModelChangedEvent
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.telemetry.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Application context holding session-specific parameters and state.
 * Implemented as a singleton to ensure a single instance across the application.
 *
 * @param params The parameters defining the current application context.
 */
class AppContext private constructor(
    val params: AppContextParams,
) {
    private val log = logger<AppContext>()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        @Volatile
        private var instance: AppContext? = null

        private var executionMode: ExecutionMode = ExecutionMode.STATELESS_MODE

        /**
         * Initialize the AppContext with execution mode.
         * Must be called once at application startup before getInstance().
         *
         * @param mode The execution mode for the application
         * @param params Optional parameters to use for initialization. If not provided,
         *               parameters will be loaded from AppContextConfigManager.
         * @return The initialized AppContext instance
         */
        fun initialize(mode: ExecutionMode, params: AppContextParams? = null): AppContext {
            require(instance == null) { "AppContext already initialized" }
            executionMode = mode

            synchronized(this) {
                val contextParams = params ?: AppContextConfigManager.load()
                return AppContext(contextParams).also { instance = it }
            }
        }

        /**
         * Gets the singleton instance of AppContext.
         * Must be called after initialize() has been invoked.
         *
         * @return The singleton AppContext instance
         * @throws IllegalStateException if AppContext has not been initialized
         */
        fun getInstance(): AppContext = instance ?: error("AppContext not initialized. Call AppContext.initialize() first.")

        /**
         * Resets the singleton instance. Useful for testing or when configuration changes require a fresh instance.
         * Note: This will invalidate any cached clients and event listeners in the previous instance.
         */
        fun reset() {
            synchronized(this) {
                instance = null
                executionMode = ExecutionMode.STATELESS_MODE
            }
        }
    }

    /**
     * System directive for the AI, typically used for language instructions or global behavior.
     * This can be updated when the user changes locale or wants to modify AI's behavior.
     */
    var systemDirective: String? = null

    /**
     * Telemetry collector for tracking RAG and LLM metrics.
     * Shared across all chat clients in this context.
     */
    val telemetry = TelemetryCollector()

    /**
     * Cached utility client for lightweight operations (classification, intent detection).
     * Invalidated when the model or provider changes.
     */
    @Volatile
    private var cachedUtilityClient: ChatClient? = null

    init {
        // Listen for model change events and invalidate the cached utility client
        eventScope.launch {
            EventBus.internalEvents
                .filterIsInstance<ModelChangedEvent>()
                .collect { event ->
                    handleModelChanged(event)
                }
        }
    }

    /**
     * Handle model change event - clear the cached utility client since it uses the old model.
     */
    private fun handleModelChanged(event: ModelChangedEvent) {
        log.info("Model changed to ${event.newModel} for provider ${event.provider}, clearing cached utility client")
        synchronized(this) {
            cachedUtilityClient = null
        }
    }

    /**
     * Gets the currently active model provider for this session.
     */
    fun getActiveProvider(): ModelProvider = params.currentProvider

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
            executionMode = ExecutionMode.STATELESS_MODE,
        )
    }

    /**
     * Creates an intent classification client for RAG decisions.
     * Returns a cheap, fast model suitable for YES/NO classification.
     *
     * For cloud providers: uses a cheap model (e.g., GPT-3.5-turbo for OpenAI)
     * For local providers: uses the current model (no extra cost)
     *
     * This client is stateless and lightweight - no tools, transformers, or custom messages.
     * The client is cached and reused until the model or provider changes.
     *
     * @return ChatClient configured with a classification model
     */
    fun createUtilityClient(): ChatClient {
        // Return cached client if available
        cachedUtilityClient?.let { return it }

        // Create new client if cache is empty
        synchronized(this) {
            // Double-check after acquiring lock
            cachedUtilityClient?.let { return it }

            val provider = params.currentProvider
            val factory = getModelFactory(provider)
                ?: error("No model factory registered for $provider")

            val settings = getOrCreateProviderSettings(provider)

            @Suppress("UNCHECKED_CAST")
            val client = (factory as ChatModelFactory<ProviderSettings>).createUtilityClient(
                settings = settings,
            )

            cachedUtilityClient = client
            log.debug("Created and cached utility client for provider {} with model {}", provider, params.model)
            return client
        }
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
        sessionId: String,
        retriever: ContentRetriever? = null,
        memory: ChatMemory,
        useVision: Boolean = false,
    ): ChatClient {
        val provider = params.currentProvider
        val factory = getModelFactory(provider)
            ?: error("No model factory registered for $provider")
        val settings = getOrCreateProviderSettings(provider)

        // Select model based on vision requirement
        val modelName = if (useVision) {
            getVisionModelForProvider(provider)
        } else {
            params.model
        }

        @Suppress("UNCHECKED_CAST")
        return (factory as ChatModelFactory<ProviderSettings>).create(
            sessionId = sessionId,
            model = modelName,
            settings = settings,
            retriever = retriever,
            executionMode = executionMode,
            chatMemory = memory,
        )
    }

    /**
     * Get the vision model name for the current provider.
     */
    private fun getVisionModelForProvider(provider: ModelProvider): String = when (provider) {
        ModelProvider.OPENAI -> AppConfig.models.openai.visionModel
        ModelProvider.ANTHROPIC -> AppConfig.models.anthropic.visionModel
        ModelProvider.GEMINI -> AppConfig.models.gemini.visionModel
        ModelProvider.XAI -> AppConfig.models.xai.visionModel
        ModelProvider.OLLAMA -> AppConfig.models.ollama.visionModel
        ModelProvider.DOCKER -> AppConfig.models.docker.visionModel
        ModelProvider.LOCALAI -> AppConfig.models.localai.visionModel
        ModelProvider.LMSTUDIO -> AppConfig.models.lmstudio.visionModel
        ModelProvider.UNKNOWN -> params.model // Fallback to current model
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
