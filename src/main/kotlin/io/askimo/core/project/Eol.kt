package io.askimo.core.project

/** Detect end-of-line style for display and preservation hints. */
fun detectEol(text: String): String =
    when {
        text.contains("\r\n") && text.contains('\n') -> "MIXED"
        text.contains("\r\n") -> "CRLF"
        else -> "LF"
    }