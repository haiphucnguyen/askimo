package io.askimo.core.util

object Logger {
    private val isDebugEnabled: Boolean = System.getenv("ASKIMO_DEBUG") == "true"

    /**
     * Print an info message to the user. Always visible.
     */
    fun info(message: String) {
        println(message)
    }

    /**
     * Print a debug message only when debug mode is enabled via ASKIMO_DEBUG=true environment variable.
     */
    fun debug(message: String) {
        if (isDebugEnabled) {
            println("[DEBUG] $message")
        }
    }

    /**
     * Print a debug message with exception stack trace only when debug mode is enabled.
     */
    fun debug(message: String, throwable: Throwable) {
        if (isDebugEnabled) {
            println("[DEBUG] $message")
            throwable.printStackTrace()
        }
    }

    /**
     * Print exception stack trace only when debug mode is enabled.
     */
    fun debug(throwable: Throwable) {
        if (isDebugEnabled) {
            println("[DEBUG] Exception occurred:")
            throwable.printStackTrace()
        }
    }
}