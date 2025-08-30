/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.db.ConnectionStore
import io.askimo.core.db.DbConnection
import io.askimo.core.db.DbLauncher
import io.askimo.core.db.DbRuntime
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings

/**
 * Controls what happens to the *chat memory* when the active [ChatService] is re-created
 * (e.g., after `:setparam`, switching provider/model, or any programmatic rebuild).
 *
 * The “combo” refers to the pair **(provider, modelName)** used as the memory bucket key.
 *
 * - [KEEP_PER_PROVIDER_MODEL] – Reuse the existing memory bucket for the current combo.
 *   If the provider or model changes, a different bucket is selected automatically.
 *   Choose this to preserve conversation context across minor setting changes.
 *
 * - [RESET_FOR_THIS_COMBO] – Drop and recreate the memory bucket for the current combo
 *   before rebuilding. Choose this when you want a clean slate (benchmarks, prompt iteration,
 *   avoiding context carryover).
 */
enum class MemoryPolicy {
    /**
     * Reuse the existing memory for the current **(provider, modelName)**.
     *
     * If the provider/model changes, the session naturally switches to a different memory bucket
     * keyed by that new combo. Best for normal chat UX where continuity is expected.
     */
    KEEP_PER_PROVIDER_MODEL,

    /**
     * Clear and recreate the memory for the current **(provider, modelName)** before rebuilding.
     *
     * Useful when prior context could bias results or when you want reproducible, clean runs
     * after each parameter change (e.g., style/verbosity tweaks, API key changes, etc.).
     */
    RESET_FOR_THIS_COMBO,
}

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
 */
class Session(
    val params: SessionParams,
    val db: DbSessionState = DbSessionState.disabled(),
) {
    private val memoryMap = mutableMapOf<String, ChatMemory>()

    var lastResponse: String? = null

    /**
     * The active chat model for this session.
     * This property is initialized lazily and can only be set through setChatModel().
     */
    lateinit var chatService: ChatService
        private set

    /**
     * Sets the chat model for this session.
     *
     * @param chatService The chat model to use for this session
     */
    fun setChatService(chatService: ChatService) {
        this.chatService = chatService
    }

    /**
     * Checks if a chat service has been initialized for this session.
     *
     * @return true if chat service has been set, false otherwise
     */
    fun hasChatService(): Boolean = ::chatService.isInitialized

    /**
     * Gets the currently active model provider for this session.
     */
    fun getActiveProvider(): ModelProvider = params.currentProvider

    /**
     * Gets the current provider's settings.
     */
    fun getCurrentProviderSettings(): ProviderSettings =
        params.providerSettings[params.currentProvider]
            ?: getModelFactory()?.defaultSettings()
            ?: NoopProviderSettings

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
     * Returns the registered factory for the current provider.
     */
    fun getModelFactory(): ChatModelFactory? = ProviderRegistry.getFactory(params.currentProvider)

    /**
     * Returns the registered factory for the given provider.
     */
    fun getModelFactory(provider: ModelProvider): ChatModelFactory? = ProviderRegistry.getFactory(provider)

    /**
     * Gets the provider-specific settings map, or creates defaults if missing.
     */
    fun getOrCreateProviderSettings(provider: ModelProvider): ProviderSettings =
        params.providerSettings.getOrPut(provider) {
            getModelFactory(provider)?.defaultSettings() ?: NoopProviderSettings
        }

    /**
     * Retrieves an existing chat memory for the given provider and model combination,
     * or creates a new one if none exists.
     *
     * @param provider The model provider to get or create memory for
     * @param model The model name to get or create memory for
     * @param settings Provider-specific settings that may influence memory creation
     * @return A [ChatMemory] instance that maintains conversation history for the specified
     *         provider/model combination
     */
    fun getOrCreateMemory(
        provider: ModelProvider,
        model: String,
        settings: ProviderSettings,
    ): ChatMemory {
        val key = "${provider.name}/$model"
        return memoryMap.getOrPut(key) {
            ProviderRegistry.getFactory(provider)?.createMemory(model, settings)
                ?: MessageWindowChatMemory.withMaxMessages(200)
        }
    }

    fun removeMemory(
        provider: ModelProvider,
        modelName: String,
    ) {
        memoryMap.remove("${provider.name}/$modelName")
    }

    /**
     * Rebuilds and returns a new instance of the active chat model based on current session parameters.
     *
     * @param memoryPolicy Controls whether to keep or reset the chat memory for the current
     *                     provider/model combination. Default is [MemoryPolicy.KEEP_PER_PROVIDER_MODEL].
     * @return A newly created [ChatService] instance that becomes the active model for this session.
     * @throws IllegalStateException if no model factory is registered for the current provider.
     */
    fun rebuildActiveChatService(memoryPolicy: MemoryPolicy = MemoryPolicy.KEEP_PER_PROVIDER_MODEL): ChatService {
        val provider = params.currentProvider
        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val settings = getOrCreateProviderSettings(provider)
        val modelName = params.model

        if (memoryPolicy == MemoryPolicy.RESET_FOR_THIS_COMBO) {
            val key = "${provider.name}/$modelName"
            memoryMap.remove(key)
        }

        val memory = getOrCreateMemory(provider, modelName, settings)
        val newModel = factory.create(modelName, settings, memory)
        setChatService(newModel)
        return newModel
    }

    /**
     * Returns the active [ChatService]. If a model has not been created yet for the
     * current (provider, model) and settings, it will be built now.
     *
     * @param memoryPolicy Controls whether the existing memory bucket for this
     * (provider, model) is reused or reset when building for the first time.
     */
    fun getChatService(memoryPolicy: MemoryPolicy = MemoryPolicy.KEEP_PER_PROVIDER_MODEL): ChatService =
        if (hasChatService()) chatService else rebuildActiveChatService(memoryPolicy)

    suspend fun attachDb(id: String): Boolean {
        val conn = db.store.get(id) ?: return false
        db.active?.let { runCatching { it.close() } }
        db.active = db.launcher.launch(conn)
        return true
    }

    suspend fun detachDb() {
        db.active?.let { runCatching { it.close() } }
        db.active = null
    }
}

data class DbSessionState(
    var active: DbRuntime? = null,
    val store: ConnectionStore,
    val launcher: DbLauncher,
) {
    companion object {
        fun disabled(): DbSessionState =
            DbSessionState(
                active = null,
                store = EmptyStore,
                launcher = DisabledLauncher,
            )
    }
}

object EmptyStore : ConnectionStore {
    override fun get(id: String) = null

    override fun list(): List<DbConnection> = emptyList()

    override fun put(conn: DbConnection) { }

    override fun remove(id: String) = false

    override fun save() { }
}

object DisabledLauncher : DbLauncher {
    override suspend fun launch(conn: DbConnection): DbRuntime = throw IllegalStateException("DB is not configured for this session")
}
