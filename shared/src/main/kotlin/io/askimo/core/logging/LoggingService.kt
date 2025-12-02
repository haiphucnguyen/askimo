/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shared logging service for managing application log levels at runtime.
 * Used by both CLI and Desktop applications.
 *
 * This service allows dynamic log level changes without requiring application restart.
 * The changes are applied to the root logger, affecting all application loggers.
 */
object LoggingService {
    private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    private val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

    /**
     * Updates the log level for io.askimo loggers only.
     * This change takes effect immediately without requiring application restart.
     *
     * @param level The desired log level
     */
    fun updateLogLevel(level: LogLevel) {
        val logbackLevel = when (level) {
            LogLevel.ERROR -> Level.ERROR
            LogLevel.WARN -> Level.WARN
            LogLevel.INFO -> Level.INFO
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.TRACE -> Level.TRACE
        }

        // Get or create the io.askimo logger and update its level
        val askimoLogger = loggerContext.getLogger("io.askimo")
        askimoLogger.level = logbackLevel

        // Update all existing io.askimo loggers to ensure the change takes effect
        loggerContext.loggerList.forEach { logger ->
            if (logger.name.startsWith("io.askimo")) {
                logger.level = logbackLevel
            }
        }

        // Log the change (will be visible if new level permits)
        LoggerFactory.getLogger(LoggingService::class.java)
            .info("Log level changed to: ${level.name} for io.askimo loggers")
    }

    /**
     * Gets the path to the current log file.
     * Returns null if file appender is not configured.
     *
     * @return Path to the log file, or null if not found
     */
    fun getLogFilePath(): Path? {
        val fileAppender = rootLogger.iteratorForAppenders()
            .asSequence()
            .filterIsInstance<FileAppender<ILoggingEvent>>()
            .firstOrNull()

        return fileAppender?.file?.let { Paths.get(it) }
    }

    /**
     * Gets the log directory path.
     * Typically ~/.askimo/logs or similar.
     *
     * @return Path to the log directory
     */
    fun getLogDirectory(): Path = getLogFilePath()?.parent
        ?: Paths.get(System.getProperty("user.home"), ".askimo", "logs")
}
