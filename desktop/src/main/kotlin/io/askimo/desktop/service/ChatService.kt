/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.providers.sendStreamingMessageWithCallback
import io.askimo.core.session.Session
import io.askimo.core.session.SessionFactory
import io.askimo.core.session.SessionMode
import io.askimo.desktop.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Service for managing chat interactions in the desktop application.
 *
 * This service provides a bridge between the UI and the core chat functionality,
 * handling session management and message streaming without JLine dependencies.
 */
class ChatService {
    private val session: Session = SessionFactory.createSession(mode = SessionMode.DESKTOP)

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                session.chatSessionRepository.close()
            },
        )
    }

    /**
     * Send a message and get a streaming response.
     *
     * @param userMessage The message from the user
     * @return Flow of chat messages representing the streaming response
     */
    fun sendMessage(userMessage: String): Flow<ChatMessage> = callbackFlow {
        // Emit the user message first
        send(ChatMessage(content = userMessage, isUser = true))

        // Build response with streaming
        val responseBuilder = StringBuilder()

        withContext(Dispatchers.IO) {
            // Prepare context and get the prompt to use
            val promptWithContext = session.prepareContextAndGetPrompt(userMessage)

            // Stream the response with token-by-token emission
            val fullResponse = session.getChatService().sendStreamingMessageWithCallback(promptWithContext) { token ->
                responseBuilder.append(token)
                // Send each token as it arrives for real-time streaming effect
                trySend(ChatMessage(content = responseBuilder.toString(), isUser = false))
            }

            session.saveAiResponse(fullResponse)

            session.lastResponse = fullResponse
        }

        close()
    }

    /**
     * Clear the conversation memory.
     */
    fun clearMemory() {
        val provider = session.getActiveProvider()
        val modelName = session.params.getModel(provider)
        session.removeMemory(provider, modelName)
    }

    /**
     * Get the current session for advanced operations.
     */
    fun getSession(): Session = session

    /**
     * Set the language directive based on user's locale selection.
     * This constructs a comprehensive instruction for the AI to communicate in the specified language,
     * with a fallback to English if the language is not supported by the AI.
     *
     * @param locale The user's selected locale (e.g., Locale.JAPANESE, Locale.ENGLISH)
     */
    fun setLanguageDirective(locale: java.util.Locale) {
        val languageDirective = buildLanguageDirective(locale)

        // Replace any existing language directive with the new one
        session.systemDirective = languageDirective
    }

    /**
     * Build a language directive instruction based on the locale.
     * Uses localized templates from resource bundles for scalability.
     * Includes fallback to English if the AI doesn't support the target language.
     *
     * @param locale The target locale
     * @return A complete language directive with fallback instructions
     */
    private fun buildLanguageDirective(locale: java.util.Locale): String {
        val languageCode = locale.language

        // Load the properties file for the target locale directly
        val properties = loadPropertiesForLocale(locale)

        // For English, use simplified template without fallback
        return if (languageCode == "en") {
            properties.getProperty("language.directive.english.only")
                ?: "LANGUAGE INSTRUCTION:\nYou must communicate with the user in English.\n- Read user messages in English and respond in English.\n- Use natural, clear English appropriate for the context."
        } else {
            // Get the language display name
            val languageDisplayName = properties.getProperty("language.name.display")
                ?: locale.getDisplayLanguage(locale)

            // Get templates
            val instructionTemplate = properties.getProperty("language.directive.instruction")
                ?: "LANGUAGE INSTRUCTION:\nYou must communicate with the user in %s.\n- Read user messages in %s and respond in %s.\n- Use natural, conversational %s appropriate for the context."

            val fallbackTemplate = properties.getProperty("language.directive.fallback")
                ?: "\n\nFALLBACK:\nIf you do not support %s or cannot generate proper %s text,\nrespond in English and inform the user that %s is not fully supported."

            // Format templates with language name
            val instruction = String.format(
                instructionTemplate,
                languageDisplayName,
                languageDisplayName,
                languageDisplayName,
                languageDisplayName,
            )

            val fallback = String.format(
                fallbackTemplate,
                languageDisplayName,
                languageDisplayName,
                languageDisplayName,
            )

            instruction + fallback
        }
    }

    /**
     * Load properties file for a specific locale.
     * Handles both language_country (ja_JP) and language-only (ja) formats.
     */
    private fun loadPropertiesForLocale(locale: java.util.Locale): java.util.Properties {
        val properties = java.util.Properties()

        val language = locale.language
        val country = locale.country

        // Try language_country format first (e.g., ja_JP)
        val localeKey = if (country.isNotEmpty()) {
            "${language}_$country"
        } else {
            language
        }

        val resourcePath = if (localeKey.isNotEmpty() && localeKey != "en") {
            "i18n/messages_$localeKey.properties"
        } else {
            "i18n/messages.properties"
        }

        try {
            this::class.java.classLoader.getResourceAsStream(resourcePath)?.use { stream ->
                stream.reader(Charsets.UTF_8).use { reader ->
                    properties.load(reader)
                }
            }
        } catch (e: Exception) {
            // If loading fails, return empty properties (fallback will use defaults)
        }

        return properties
    }
}
