package io.askimo.core.providers.openai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.service.AiServices
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider.OPEN_AI
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.core.util.appJson
import io.askimo.tools.fs.LocalFsTools
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

class OpenAiModelFactory : ChatModelFactory {
    override val provider = OPEN_AI

    override fun availableModels(settings: ProviderSettings): List<String> {
        val apiKey =
            (settings as? OpenAiSettings)?.apiKey?.takeIf { it.isNotBlank() }
                ?: return emptyList()

        return try {
            val url = URI("https://api.openai.com/v1/models").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")

            connection.inputStream.bufferedReader().use { reader ->
                val jsonElement = appJson.parseToJsonElement(reader.readText())

                val data = jsonElement.jsonObject["data"]?.jsonArray.orEmpty()

                data
                    .mapNotNull { element ->
                        element.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    }.distinct()
                    .sorted()
            }
        } catch (e: Exception) {
            println("⚠️ Failed to fetch models from OpenAI: ${e.message}")
            emptyList()
        }
    }

    override fun defaultModel(): String = "gpt-4o"

    override fun defaultSettings(): ProviderSettings = OpenAiSettings()

    override fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
    ): ChatService {
        require(settings is OpenAiSettings) {
            "Invalid settings type for OpenAI: ${settings::class.simpleName}"
        }

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .apiKey(settings.apiKey)
                .modelName(model)
                .apply {
                    if (supportsSampling(model)) {
                        val s = samplingFor(settings.presets.style)
                        temperature(s.temperature).topP(s.topP)
                    }
                }.build()

        return AiServices
            .builder(ChatService::class.java)
            .streamingChatModel(chatModel)
            .chatMemory(memory)
            .tools(LocalFsTools())
            .systemMessageProvider { systemMessage(verbosityInstruction(settings.presets.verbosity)) }
            .build()
    }

    private fun supportsSampling(model: String): Boolean {
        val m = model.lowercase()
        return !(m.startsWith("o") || m.startsWith("gpt-5") || m.contains("reasoning"))
    }
}
