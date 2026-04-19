/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.chat

import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO

interface ChatActions {
    fun sendOrEditMessage(
        creationMode: CreationMode,
        message: String,
        attachments: List<FileAttachmentDTO> = emptyList(),
        editingMessage: ChatMessageDTO? = null,
        disabledServerIds: Set<String> = emptySet(),
    ): String?
    fun cancelResponse()
    fun loadPrevious()
    fun searchMessages(query: String)
    fun clearSearch()
    fun nextSearchResult()
    fun previousSearchResult()
    fun setDirective(directiveId: String?)
    fun updateAIMessage(messageId: String, newContent: String)
    fun retryMessage(messageId: String, disabledServerIds: Set<String> = emptySet())
}
