/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.event.system

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant
import kotlin.reflect.KClass

class ShellErrorEvent(
    override val timestamp: Instant = Instant.now(),
    val sourceEvent: KClass<out Event>,
    val originalMessage: String?,
) : Event {
    override val source: EventSource = EventSource.SYSTEM
    override val type: EventType = EventType.USER

    override fun getDetails(): String = originalMessage ?: "Error"
}
