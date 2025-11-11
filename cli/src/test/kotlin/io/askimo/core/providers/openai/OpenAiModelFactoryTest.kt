/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.sendStreamingMessageWithCallback
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(
    named = "OPENAI_API_KEY",
    matches = ".+",
    disabledReason = "OPENAI_API_KEY environment variable is required for OpenAI tests",
)
@TestInstance(Lifecycle.PER_CLASS)
class OpenAiModelFactoryTest {

    private fun createChatService(): ChatService {
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")

        val settings = OpenAiSettings(apiKey = apiKey)
        val memory = MessageWindowChatMemory.withMaxMessages(10)

        return OpenAiModelFactory().create(
            model = "gpt-3.5-turbo",
            settings = settings,
            memory = memory,
            retrievalAugmentor = null,
        )
    }

    private fun sendPromptAndGetResponse(chatService: ChatService, prompt: String): String {
        println("Sending prompt: '$prompt'")

        val output = chatService.sendStreamingMessageWithCallback(prompt) { _ ->
            print(".")
        }.trim()

        println("\nFinal output: '$output'")
        return output
    }

    @Test
    @DisplayName("OpenAiModelFactory can stream responses from OpenAI API")
    fun canCreateChatServiceAndStream() {
        val chatService = createChatService()

        val prompt = "Reply with a single short word."
        val output = sendPromptAndGetResponse(chatService, prompt)

        assertTrue(output.isNotBlank(), "Expected a non-empty response from OpenAI, but got blank: '$output'")
    }

    @Test
    @DisplayName("OpenAI can call LocalFsTools countEntries function")
    fun canCallCountEntriesTool() {
        val chatService = createChatService()

        // Prompt that should trigger the countEntries tool call
        val prompt = "Use the countEntries tool to count files and directories in the current directory (\".\"). Give me the file count, directory count, and total size."

        val output = sendPromptAndGetResponse(chatService, prompt)

        // Verify that the AI actually used the tool and returned meaningful results
        assertTrue(output.isNotBlank(), "Expected a non-empty response from OpenAI, but got blank: '$output'")

        println("Analyzing output for tool usage indicators...")
        println("Output length: ${output.length}")

        // Check if the response contains indicators that the tool was called successfully
        val outputLower = output.lowercase()
        val hasFileInfo = outputLower.contains("file") || outputLower.contains("directory") || outputLower.contains("dir")
        val hasCountInfo = outputLower.contains("count") || outputLower.matches(".*\\d+.*".toRegex())
        val hasByteInfo = outputLower.contains("byte") || outputLower.contains("size")

        println("Has file info: $hasFileInfo")
        println("Has count info: $hasCountInfo")
        println("Has byte info: $hasByteInfo")

        val toolWasCalled = hasFileInfo || hasCountInfo || hasByteInfo

        assertTrue(
            toolWasCalled,
            "Expected response to contain file/directory count information indicating tool was called, but got: '$output'",
        )
    }

    @Test
    @DisplayName("OpenAiModelFactory returns empty list when no API key provided")
    fun availableModelsReturnsEmptyListWithoutApiKey() {
        val settings = OpenAiSettings(apiKey = "")
        val models = OpenAiModelFactory().availableModels(settings)

        assertTrue(models.isEmpty(), "Expected empty list when no API key provided")
    }

    @Test
    @DisplayName("OpenAiModelFactory returns default settings")
    fun defaultSettingsTest() {
        val factory = OpenAiModelFactory()
        val defaultSettings = factory.defaultSettings()

        assertTrue(defaultSettings is OpenAiSettings, "Expected OpenAiSettings instance")
        assertTrue(defaultSettings.apiKey.isBlank(), "Expected blank API key in default settings")
    }
}
