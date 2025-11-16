/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli

import org.jline.terminal.Terminal
import java.util.concurrent.atomic.AtomicBoolean

class LoadingIndicator(
    private val terminal: Terminal,
    private val message: String = "Thinking…",
    private val doneLabel: String = "Done",
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var startTime: Long = 0

    fun start() {
        if (running.getAndSet(true)) return
        startTime = System.currentTimeMillis()
        val frames = charArrayOf('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')
        val w = terminal.writer()

        thread =
            Thread {
                var i = 0
                while (running.get()) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    val text = "${frames[i % frames.size]}  $message (${elapsed}s)"
                    w.print("\r$text")
                    w.flush()
                    i++
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
    }

    /** Stops the spinner and prints "✓ {doneLabel} in Xs" on its line (kept on screen). */
    fun stopWithElapsed() {
        if (!running.getAndSet(false)) return
        thread?.interrupt()
        thread = null
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        val w = terminal.writer()
        w.print("\r") // go to start of line
        w.print(" ".repeat(80)) // clear the line
        w.print("\r") // go back to start of line
        w.print("✓  $doneLabel in ${elapsed}s")
        w.println() // drop to next line so streamed tokens start cleanly
        w.flush()
    }

    /** For AutoCloseable, default to clearing (if you need that behavior elsewhere). */
    override fun close() {
        if (!running.getAndSet(false)) return
        thread?.interrupt()
        thread = null
        val w = terminal.writer()
        w.print("\r")
        w.print(" ".repeat(terminal.width))
        w.print("\r")
        w.flush()
    }
}
