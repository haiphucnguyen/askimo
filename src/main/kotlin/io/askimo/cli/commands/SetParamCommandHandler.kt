package io.askimo.cli.commands

import io.askimo.cli.session.MemoryPolicy
import io.askimo.cli.session.ParamKey
import io.askimo.cli.session.Session
import io.askimo.cli.session.SessionConfigManager
import org.jline.reader.ParsedLine

/**
 * Handles the command to set model parameters.
 *
 * This class allows users to configure specific parameters for the current model provider,
 * such as temperature, max tokens, or API keys. It validates the parameter key and value,
 * updates the session configuration, and rebuilds the active chat model with the new settings.
 */
class SetParamCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword = ":setparam"
    override val description = "Set a model parameter (use :params --list for available keys)"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)

        if (args.size != 2) {
            println("Usage: :setparam <key> <value>")
            println("Type :params --list to see valid keys and descriptions.")
            return
        }

        val keyInput = args[0]
        val valueInput = args[1]

        val key = ParamKey.fromInput(keyInput)
        if (key == null) {
            println("Unknown parameter: '$keyInput'. Try :params --list to see valid keys.")
            return
        }

        try {
            val provider = session.params.currentProvider
            val factory = session.getModelFactory(provider)
            if (factory == null) {
                println("❌ No model factory registered for provider: ${provider.name.lowercase()}")
                return
            }

            val providerSettings =
                session.params.providerSettings
                    .getOrPut(provider) { factory.defaultSettings() }

            key.applyTo(session.params, providerSettings, valueInput)

            session.params.providerSettings[provider] = providerSettings
            SessionConfigManager.save(session.params)

            session.rebuildActiveChatService(MemoryPolicy.KEEP_PER_PROVIDER_MODEL)
            println("✅ '${key.key}' is updated")
        } catch (e: IllegalArgumentException) {
            println("❌ Invalid value for '$keyInput': ${e.message}")
        }
    }
}
