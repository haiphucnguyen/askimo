/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.session.Session
import io.askimo.core.session.SessionFactory
import io.askimo.core.session.SessionMode

/**
 * Service for managing chat interactions in the desktop application.
 *
 * This service provides a bridge between the UI and the core chat functionality,
 * handling session management and message streaming without JLine dependencies.
 */
class ChatService {
    private val session: Session = SessionFactory.createSession(mode = SessionMode.DESKTOP)
    private val streamingService: StreamingService = StreamingService(session)

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                streamingService.shutdown()
                session.chatSessionRepository.close()
            },
        )
    }

    /**
     * Get the streaming service for checking active streams.
     */
    fun getStreamingService(): StreamingService = streamingService

    /**
     * Send a message and start streaming in the background.
     *
     * Creates a new thread for this question-answer pair.
     * Thread closes automatically after completion.
     *
     * @param userMessage The message from the user
     * @param chatId The chat ID for this message
     * @return threadId if streaming started successfully, null if chat already has active question or max concurrent streams reached
     */
    fun sendMessage(userMessage: String, chatId: String): String? {
        // Start streaming in background - creates new thread for this Q&A
        return streamingService.startStream(
            chatId = chatId,
            userMessage = userMessage,
            onChunkReceived = { _ ->
                // Callback not used - UI subscribes directly to StreamingService
            }
        )
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
