package io.askimo.cli

import org.jline.terminal.Terminal

class MarkdownStreamingSink(
    private val terminal: Terminal,
    private val renderer: MarkdownJLineRenderer,
) {
    private val buf = StringBuilder()

    // scanning state
    private var renderedUpTo = 0 // absolute index in buf rendered so far
    private var fenceOpen = false
    private var fenceStart = -1 // absolute index of opening fence start
    private var fenceStartLineEnd = -1 // absolute index of opening fence newline
    private var fenceTicks = "" // "```" or "~~~"
    private var fenceInfo = "" // e.g. "java"

    // Accepts 3+ ` or ~, any indentation, optional info string
    private val fenceOpenRegex = Regex("""^(\s*)(`{3,}|~{3,})([^\r\n]*)\r?\n?$""")
    private val fenceCloseRegex = Regex("""^(\s*)(`{3,}|~{3,})\s*\r?\n?$""")

    fun append(token: String) {
        if (token.isEmpty()) return
        val oldLen = buf.length
        buf.append(token)

        var scanPos = if (oldLen > renderedUpTo) oldLen else renderedUpTo
        while (true) {
            val nl = buf.indexOf('\n', scanPos)
            if (nl == -1) break

            val lineStart = lineStartIndex(nl)
            val line = buf.substring(lineStart, nl + 1)

            if (!fenceOpen) {
                val m = fenceOpenRegex.find(line)
                if (m != null) {
                    // opening fence found
                    fenceOpen = true
                    fenceStart = lineStart
                    fenceStartLineEnd = nl + 1
                    fenceTicks = m.groupValues[2] // ``` or ~~~ (len >= 3)
                    fenceInfo = m.groupValues[3].trim() // e.g. "java"
                    // render any plain text before fence
                    if (fenceStart > renderedUpTo) {
                        renderSlice(renderedUpTo, fenceStart)
                        renderedUpTo = fenceStart
                    }
                } else {
                    // normal line, render through this newline
                    renderSlice(renderedUpTo, nl + 1)
                    renderedUpTo = nl + 1
                }
            } else {
                // inside a fence; look for closing fence of same kind (``` or ~~~)
                val m = fenceCloseRegex.find(line)
                if (m != null && m.groupValues[2][0] == fenceTicks[0]) {
                    // we have a complete fenced block: [fenceStart .. nl+1]
                    val normalized =
                        normalizeFenceBlock(
                            full = buf.substring(fenceStart, nl + 1),
                            openLine = buf.substring(fenceStart, fenceStartLineEnd),
                            inner = buf.substring(fenceStartLineEnd, lineStart),
                            closeLine = line,
                        )
                    // render normalized block
                    terminal.writer().print(renderer.markdownToAnsi(normalized))
                    terminal.writer().flush()

                    // advance
                    renderedUpTo = nl + 1
                    fenceOpen = false
                    fenceStart = -1
                    fenceStartLineEnd = -1
                    fenceTicks = ""
                    fenceInfo = ""
                }
                // else: keep buffering until we see the closing fence
            }

            scanPos = nl + 1
        }
    }

    fun finish() {
        // best-effort render leftovers (including unterminated fence)
        if (renderedUpTo < buf.length) {
            renderSlice(renderedUpTo, buf.length)
            renderedUpTo = buf.length
        }
    }

    private fun normalizeFenceBlock(
        full: String,
        openLine: String,
        inner: String,
        closeLine: String,
    ): String {
        // 1) compute minimal leading spaces across inner lines (ignore empty)
        val lines = inner.split('\n')
        val nonEmpty = lines.filter { it.isNotEmpty() }
        val minIndent = nonEmpty.minOfOrNull { countLeadingSpaces(it) } ?: 0
        // 2) strip that indent (but not below 0), and reassemble a canonical fenced block
        val lang = fenceInfo
        val open = "```" + if (lang.isNotEmpty()) " $lang" else ""
        val body =
            lines.joinToString("\n") { l ->
                if (minIndent == 0) l else l.drop(minIndent.coerceAtMost(countLeadingSpaces(l)))
            }
        val normalized =
            buildString {
                append(open).append("\n")
                append(body)
                if (!body.endsWith("\n")) append("\n")
                append("```").append("\n")
            }
        return normalized
    }

    private fun renderSlice(
        start: Int,
        endExclusive: Int,
    ) {
        if (endExclusive <= start) return
        terminal.writer().print(renderer.markdownToAnsi(buf.substring(start, endExclusive)))
        terminal.writer().flush()
    }

    private fun lineStartIndex(nlIndex: Int): Int {
        var i = nlIndex
        while (i > 0 && buf[i - 1] != '\n') i--
        return i
    }

    private fun countLeadingSpaces(s: String): Int {
        var i = 0
        while (i < s.length && s[i] == ' ') i++
        return i
    }

    private fun StringBuilder.indexOf(
        ch: Char,
        from: Int,
    ): Int {
        for (i in from.coerceAtLeast(0) until this.length) if (this[i] == ch) return i
        return -1
    }
}
