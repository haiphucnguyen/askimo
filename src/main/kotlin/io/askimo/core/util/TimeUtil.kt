/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object TimeUtil {
    private val shortFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun stamp(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun shortStamp(): String = LocalDateTime.now().format(shortFmt)
}
