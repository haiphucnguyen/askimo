package io.askimo.core.session

import io.askimo.cli.Logger.log
import io.askimo.core.providers.NoopChatService
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings

object SessionFactory {
    // TODO: create session for both cli and web application. Now, the web application is simple while it serves for one user only
    // If in the future, we decide to let web application can serve multiple users then
    // this function is subject to change
    fun createSession(params: SessionParams = SessionConfigManager.load()): Session {
        log { "Current provider: $params" }

        val session = Session(params)

        val provider = session.params.currentProvider
        val modelName = session.params.getModel(provider)

        val settings: ProviderSettings =
            session.params.providerSettings[provider]
                ?: ProviderRegistry.getFactory(provider)?.defaultSettings()
                ?: NoopProviderSettings

        val memory = session.getOrCreateMemory(provider, modelName, settings)

        val factory = ProviderRegistry.getFactory(provider)
        val chatService =
            factory
                ?.create(
                    modelName,
                    settings,
                    memory,
                )
                ?: NoopChatService

        session.setChatService(chatService)
        return session
    }
}
