/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.project.ProjectMeta
import io.askimo.core.project.buildRetrievalAugmentor
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopChatClient
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.isInitialized

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

    @Volatile
    private var autoSaveScheduler: ScheduledExecutorService? = null

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    log.info("Shutdown hook triggered - saving session memory")
                    if (::_chatClient.isInitialized) {
                        runBlocking {
                            _chatClient.saveCurrentSession()
                        }
                        log.info("Successfully saved memory on application shutdown")
                    }
                } catch (e: Exception) {
                    log.error("Failed to save memory on shutdown", e)
                }
            },
        )

        startPeriodicAutoSave()
    }

    /**
     * Start periodic auto-save scheduler
     */
    private fun startPeriodicAutoSave() {
        autoSaveScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "memory-auto-saver").apply {
                isDaemon = true
            }
        }

        autoSaveScheduler?.scheduleAtFixedRate({
            try {
                if (::_chatClient.isInitialized) {
                    runBlocking {
                        _chatClient.saveCurrentSession()
                    }
                    log.debug("Periodic auto-save completed")
                }
            } catch (e: Exception) {
                log.error("Periodic auto-save failed", e)
            }
        }, 5, 5, TimeUnit.MINUTES)
    }

    /**
     * Stop the periodic auto-save scheduler
     */
    fun stopAutoSave() {
        autoSaveScheduler?.shutdown()
        try {
            val terminated = autoSaveScheduler?.awaitTermination(10, TimeUnit.SECONDS) ?: true
            if (!terminated) {
                autoSaveScheduler?.shutdownNow()
            }
        } catch (e: InterruptedException) {
            autoSaveScheduler?.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * System directive for the AI, typically used for language instructions or global behavior.
     * This can be updated when the user changes locale or wants to modify AI's behavior.
     */
    var systemDirective: String? = null

    /**
     * The active chat model for this session.
     * This property is initialized lazily and can only be set through setChatModel().
     */
    private lateinit var _chatClient: ChatClient

    /**
     * Sets the chat model for this session.
     *
     * @param chatClient The chat model to use for this session
     */
    fun setChatClient(chatClient: ChatClient) {
        this._chatClient = chatClient
        if (chatClient is NoopChatClient) {
            (this._chatClient as NoopChatClient).appContext = this
        }
    }

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

    /**
     * Safely calls create on a factory with the given settings.
     * Uses unchecked cast which is safe because the registry ensures
     * factory and settings types match for each provider.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : ProviderSettings> createChatClientFromFactory(
        factory: ChatModelFactory<*>,
        model: String,
        settings: T,
        retrievalAugmentor: RetrievalAugmentor? = null,
        executionMode: ExecutionMode = ExecutionMode.CLI_INTERACTIVE,
    ): ChatClient = (factory as ChatModelFactory<T>).create(model, settings, retrievalAugmentor, executionMode)

    /**
     * Rebuilds and returns a new instance of the active chat model based on current session parameters.
     *
     * @param memoryPolicy Controls whether to keep or reset the chat memory for the current
     *                     provider/model combination. Default is [MemoryPolicy.KEEP_PER_PROVIDER_MODEL].
     * @return A newly created [ChatClient] instance that becomes the active model for this session.
     * @throws IllegalStateException if no model factory is registered for the current provider.
     */
    fun rebuildActiveChatClient(): ChatClient {
        val provider = params.currentProvider
        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val settings = getOrCreateProviderSettings(provider)
        val modelName = params.model

        val newModel = createChatClientFromFactory(factory, modelName, settings, executionMode = mode)
        setChatClient(newModel)
        return newModel
    }

    /**
     * Returns the active [ChatClient]. If a model has not been created yet for the
     * current (provider, model) and settings, it will be built now.
     */
    fun getChatClient(): ChatClient = if (::_chatClient.isInitialized) _chatClient else rebuildActiveChatClient()

    /**
     * Enables Retrieval-Augmented Generation (RAG) for the current session using
     * the provided PgVectorIndexer.
     *
     * This method wires the indexer into a PgVectorContentRetriever and builds a
     * retrieval augmentor, then recreates the active ChatClient with the same
     * provider, model, settings, and memory bucket, but augmented with retrieval.
     *
     * Notes:
     * - Memory is preserved; the conversation context for the current (provider, model)
     *   is reused.
     * - Requires that a model factory is registered for the current provider; otherwise
     *   an IllegalStateException is thrown.
     * - Typically called after setScope(...) when switching to a project that has
     *   indexed content.
     *
     * @param indexer The PgVector-backed indexer to use for retrieving relevant context.
     */
    fun enableRagWith(retriever: ContentRetriever) {
        val rag = buildRetrievalAugmentor(retriever)

        val provider = params.currentProvider
        val model = params.model
        val settings = getCurrentProviderSettings()

        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val upgraded = createChatClientFromFactory(
            factory = factory,
            model = model,
            settings = settings,
            retrievalAugmentor = rag,
            executionMode = mode,
        )
        log.display("RAG enabled for $model")
        setChatClient(upgraded)
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
