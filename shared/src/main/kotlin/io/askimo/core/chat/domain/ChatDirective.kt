/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.chat.domain

import java.time.Instant
import java.util.UUID

/**
 * Discriminates who owns and can mutate a directive.
 *
 * - [PERSONAL] — owned by an individual user; synced across their own devices only.
 * - [TEAM]     — created by a tenant admin; distributed read-only to all tenant members
 *                via the pull delta. Clients must never push TEAM directives back.
 */
enum class DirectiveScope { PERSONAL, TEAM }

/**
 * Represents a custom instruction/directive that users can apply to chat sessions
 * to influence AI behavior (tone, format, style, etc.)
 */
data class ChatDirective(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    /** Ownership scope — defaults to [DirectiveScope.PERSONAL] for all existing rows. */
    val scope: DirectiveScope = DirectiveScope.PERSONAL,
    /** userId of the creator; null for legacy personal directives seeded locally. */
    val createdBy: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
)

const val DIRECTIVE_NAME_MAX_LENGTH = 128
const val DIRECTIVE_CONTENT_MAX_LENGTH = 32768
