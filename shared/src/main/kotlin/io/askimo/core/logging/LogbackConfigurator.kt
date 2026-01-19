/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

/**
 * Utility to dynamically register/unregister EventBusAppender to logback at runtime.
 */
object LogbackConfigurator {

    /**
     * Registers EventBusAppender to the root logger.
     * Should be called when developer mode is enabled and active.
     */
    fun registerEventBusAppender() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        // Check if already registered
        if (rootLogger.getAppender("EVENTBUS") != null) {
            return
        }

        // Create and configure the appender
        val appender = EventBusAppender().apply {
            name = "EVENTBUS"
            context = loggerContext
            start()
        }

        // Add to root logger
        rootLogger.addAppender(appender)
    }

    /**
     * Unregisters EventBusAppender from the root logger.
     * Should be called when developer mode is turned off.
     */
    fun unregisterEventBusAppender() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        // Get and remove the appender
        val appender = rootLogger.getAppender("EVENTBUS")
        if (appender != null) {
            rootLogger.detachAppender(appender)
            appender.stop()
        }
    }
}
