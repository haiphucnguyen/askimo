/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Custom LocalDateTime column type that correctly handles SQLite's ISO-8601 format.
 *
 * SQLite stores timestamps as TEXT in ISO-8601 format: `2025-11-22T19:07:23.555482`
 * Exposed's default implementation expects space separator: `2025-11-22 19:07:23.555482`
 *
 * This custom type handles both formats for maximum compatibility.
 */
class SQLiteLocalDateTimeColumnType :
    ColumnType<LocalDateTime>(),
    IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: LocalDateTime): String {
        // Store in SQLite's ISO-8601 format with microsecond precision
        return "'${SQLITE_DATETIME_FORMATTER.format(value)}'"
    }

    override fun valueFromDB(value: Any): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / 1000, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is String -> parseDateTime(value)
        is OffsetDateTime -> value.toLocalDateTime()
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = SQLITE_DATETIME_FORMATTER.format(value)

    /**
     * Parse datetime string in either SQLite ISO format or Exposed's space-separated format.
     */
    private fun parseDateTime(value: String): LocalDateTime = try {
        // Try SQLite's ISO-8601 format first (most common)
        LocalDateTime.parse(value, SQLITE_DATETIME_FORMATTER)
    } catch (e: Exception) {
        try {
            // Fallback to Exposed's space-separated format
            val fractionLength = value.substringAfterLast('.', "").length
            val formatter = exposedDateTimeFormatter(fractionLength)
            LocalDateTime.parse(value, formatter)
        } catch (e2: Exception) {
            // Last resort: try ISO_LOCAL_DATE_TIME
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
    }

    /**
     * Create Exposed-style formatter with space separator (for backward compatibility).
     */
    private fun exposedDateTimeFormatter(fractionDigits: Int): DateTimeFormatter {
        val pattern = if (fractionDigits in 1..9) {
            "yyyy-MM-dd HH:mm:ss." + "S".repeat(fractionDigits)
        } else {
            "yyyy-MM-dd HH:mm:ss"
        }
        return DateTimeFormatter.ofPattern(pattern)
    }

    private fun longToLocalDateTime(millis: Long, nanos: Long = 0): LocalDateTime {
        val seconds = millis / 1000
        val adjustedNanos = (millis % 1000) * 1_000_000 + nanos
        return LocalDateTime.ofEpochSecond(seconds, adjustedNanos.toInt(), java.time.ZoneOffset.UTC)
    }

    companion object {
        /**
         * SQLite's ISO-8601 datetime formatter with microsecond precision.
         * Format: yyyy-MM-ddTHH:mm:ss.SSSSSS
         */
        val SQLITE_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter()
    }
}

/**
 * Extension function to create a SQLite-compatible LocalDateTime column.
 * Use this instead of `datetime()` when working with SQLite databases.
 */
fun Table.sqliteDatetime(name: String): Column<LocalDateTime> = registerColumn(name, SQLiteLocalDateTimeColumnType())
