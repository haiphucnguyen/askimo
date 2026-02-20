/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import io.askimo.core.context.MessageRole
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime

/**
 * Custom serializer for LocalDateTime to support Kotlinx Serialization
 */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

/**
 * Represents a chat message in memory with serialization support.
 * This is the persistent representation of a chat message that can be stored in the database.
 *
 * @property content The text content of the message
 * @property type The message type (USER, ASSISTANT, SYSTEM, TOOL_EXECUTION_RESULT_MESSAGE)
 * @property createdAt Timestamp when the message was created
 */
@Serializable
data class MemoryMessage(
    val content: String,
    val type: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * Convert this MemoryMessage to a LangChain4j ChatMessage
     */
    fun toChatMessage(): ChatMessage = when (this.type) {
        MessageRole.USER.value -> UserMessage.from(this.content)
        MessageRole.ASSISTANT.value -> AiMessage.from(this.content)
        MessageRole.SYSTEM.value -> SystemMessage.from(this.content)
        MessageRole.TOOL_EXECUTION_RESULT_MESSAGE.value ->
            ToolExecutionResultMessage.builder().text(this.content).build()
        else -> UserMessage.from(this.content) // fallback
    }

    companion object {
        /**
         * Create a MemoryMessage from a LangChain4j ChatMessage
         */
        fun from(chatMessage: ChatMessage): MemoryMessage {
            val content = chatMessage.getTextContent()
            val type = when (chatMessage) {
                is UserMessage -> MessageRole.USER.value
                is AiMessage -> MessageRole.ASSISTANT.value
                is SystemMessage -> MessageRole.SYSTEM.value
                is ToolExecutionResultMessage -> MessageRole.TOOL_EXECUTION_RESULT_MESSAGE.value
                else -> MessageRole.USER.value // fallback
            }
            return MemoryMessage(
                content = content,
                type = type,
                createdAt = LocalDateTime.now(),
            )
        }
    }
}

/**
 * Extension function to extract text content from ChatMessage
 */
fun ChatMessage.getTextContent(): String = when (this) {
    is UserMessage -> this.singleText() ?: ""
    is AiMessage -> this.text() ?: ""
    is SystemMessage -> this.text() ?: ""
    is ToolExecutionResultMessage -> this.text() ?: ""
    else -> ""
}

/**
 * Extension function to convert ChatMessage to MemoryMessage
 * This is the reverse operation of MemoryMessage.toChatMessage()
 */
fun ChatMessage.toMemoryMessage(): MemoryMessage = MemoryMessage.from(this)
