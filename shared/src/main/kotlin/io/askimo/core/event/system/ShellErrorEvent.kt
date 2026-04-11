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

/**
 * A [EventType.USER]-visible event emitted when an error occurs while processing or
 * dispatching another event in the shell layer.
 *
 * This event acts as a wrapper so that unexpected failures in event handlers are
 * surfaced to the user (e.g. shown in the notification popup) rather than silently
 * swallowed. The original event type is preserved via [sourceEvent] to aid debugging.
 *
 * @property sourceEvent The [KClass] of the event whose processing triggered the error.
 * @property originalMessage The error message from the original exception, or `null`
 *   if none was available. Falls back to `"Error"` in [getDetails].
 */
class ShellErrorEvent(
    override val timestamp: Instant = Instant.now(),
    val sourceEvent: KClass<out Event>,
    val originalMessage: String?,
) : Event {
    override val source: EventSource = EventSource.SYSTEM
    override val type: EventType = EventType.USER

    override fun getDetails(): String = originalMessage ?: "Error"
}
