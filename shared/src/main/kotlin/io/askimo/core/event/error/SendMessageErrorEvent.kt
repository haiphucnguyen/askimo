/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.event.error

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

class SendMessageErrorEvent(
    val throwable: Throwable,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.ERROR

    override fun getDetails() = "Can not send message. Reason ${throwable.message}"
}
