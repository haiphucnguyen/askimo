/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight syntax highlighter for code blocks.
 * Supports multiple programming languages with color-coded syntax.
 */
object CodeHighlighter {
    data class Theme(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val function: Color,
        val type: Color,
        val operator: Color,
        val punctuation: Color,
    )

    fun darkTheme() = Theme(
        keyword = Color(0xFFCC7832),
        string = Color(0xFF6A8759),
        comment = Color(0xFF808080),
        number = Color(0xFF6897BB),
        function = Color(0xFFFFC66D),
        type = Color(0xFFB5CEA8),
        operator = Color(0xFFA9B7C6),
        punctuation = Color(0xFFA9B7C6),
    )

    fun lightTheme() = Theme(
        keyword = Color(0xFFAF00DB), // Purple - vibrant and visible
        string = Color(0xFF008000), // Green - classic and readable
        comment = Color(0xFF707070), // Medium gray - not too light
        number = Color(0xFF0000FF), // Blue - strong contrast
        function = Color(0xFF795E26), // Brown - warm and distinct
        type = Color(0xFF0070C1), // Medium blue - clear distinction
        operator = Color(0xFF000000), // Black - maximum readability
        punctuation = Color(0xFF000000), // Black - maximum readability
    )

    private val kotlinKeywords = setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "crossinline", "data", "delegate", "do", "else",
        "enum", "expect", "external", "false", "field", "file", "final", "finally", "for",
        "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner", "interface",
        "internal", "is", "lateinit", "noinline", "null", "object", "open", "operator", "out",
        "override", "package", "param", "private", "property", "protected", "public", "receiver",
        "reified", "return", "sealed", "set", "setparam", "super", "suspend", "tailrec", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "vararg", "when", "where", "while",
    )

    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "false",
        "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "native", "new", "null", "package", "private", "protected",
        "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while",
    )

    private val pythonKeywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield",
    )

    private val jsKeywords = setOf(
        "abstract", "arguments", "await", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "debugger", "default", "delete", "do", "double", "else",
        "enum", "eval", "export", "extends", "false", "final", "finally", "float", "for",
        "function", "goto", "if", "implements", "import", "in", "instanceof", "int", "interface",
        "let", "long", "native", "new", "null", "package", "private", "protected", "public",
        "return", "short", "static", "super", "switch", "synchronized", "this", "throw",
        "throws", "transient", "true", "try", "typeof", "var", "void", "volatile", "while",
        "with", "yield", "async",
    )

    private val sqlKeywords = setOf(
        "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "BACKUP", "BETWEEN", "CASE", "CHECK",
        "COLUMN", "CONSTRAINT", "CREATE", "DATABASE", "DEFAULT", "DELETE", "DESC", "DISTINCT",
        "DROP", "EXEC", "EXISTS", "FOREIGN", "FROM", "FULL", "GROUP", "HAVING", "IN", "INDEX",
        "INNER", "INSERT", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE", "LIMIT", "NOT", "NULL",
        "OR", "ORDER", "OUTER", "PRIMARY", "PROCEDURE", "RIGHT", "ROWNUM", "SELECT", "SET",
        "TABLE", "TOP", "TRUNCATE", "UNION", "UNIQUE", "UPDATE", "VALUES", "VIEW", "WHERE",
    )

    private val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "select", "while", "until",
        "do", "done", "in", "function", "time", "coproc", "export", "readonly", "local",
        "declare", "typeset", "unset", "set", "shift", "return", "exit", "break", "continue",
        "test", "source", "alias", "unalias", "echo", "printf", "read", "cd", "pwd", "pushd",
        "popd", "dirs", "let", "eval", "exec", "trap", "wait", "jobs", "bg", "fg", "kill",
    )

    fun highlight(code: String, language: String?, theme: Theme): AnnotatedString {
        val lang = language?.lowercase()?.trim() ?: ""

        return when {
            lang in listOf("kotlin", "kt", "kts") -> highlightCode(code, kotlinKeywords, theme)
            lang in listOf("java") -> highlightCode(code, javaKeywords, theme)
            lang in listOf("python", "py") -> highlightCode(code, pythonKeywords, theme, "#")
            lang in listOf("javascript", "js", "typescript", "ts", "jsx", "tsx") ->
                highlightCode(code, jsKeywords, theme)
            lang in listOf("sql", "mysql", "postgresql", "sqlite") ->
                highlightCode(code, sqlKeywords, theme, "--")
            lang in listOf("bash", "sh", "shell", "zsh") -> highlightCode(code, shellKeywords, theme, "#")
            lang in listOf("json") -> highlightJSON(code, theme)
            lang in listOf("xml", "html") -> highlightXML(code, theme)
            else -> buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(code)
                }
            }
        }
    }

    private fun highlightCode(
        code: String,
        keywords: Set<String>,
        theme: Theme,
        commentPrefix: String = "//",
    ): AnnotatedString = buildAnnotatedString {
        val lines = code.split("\n")
        var inMultiLineComment = false

        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")

            // Check for multi-line comment start/end
            if (line.trim().startsWith("/*")) inMultiLineComment = true
            if (inMultiLineComment) {
                withStyle(SpanStyle(color = theme.comment, fontFamily = FontFamily.Monospace)) {
                    append(line)
                }
                if (line.trim().endsWith("*/")) inMultiLineComment = false
                return@forEachIndexed
            }

            // Single-line comment
            val commentIndex = line.indexOf(commentPrefix)
            val codeBeforeComment = if (commentIndex >= 0) line.substring(0, commentIndex) else line
            val comment = if (commentIndex >= 0) line.substring(commentIndex) else null

            // Process code
            if (codeBeforeComment.isNotEmpty()) {
                highlightLine(codeBeforeComment, keywords, theme)
            }

            // Process comment
            if (comment != null) {
                withStyle(SpanStyle(color = theme.comment, fontFamily = FontFamily.Monospace)) {
                    append(comment)
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(
        line: String,
        keywords: Set<String>,
        theme: Theme,
    ) {
        // Regex patterns
        val stringPattern = """"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'""".toRegex()
        val numberPattern = """\b\d+\.?\d*[fFlL]?\b""".toRegex()
        val wordPattern = """\b[a-zA-Z_]\w*\b""".toRegex()

        val tokens = mutableListOf<Triple<IntRange, String, SpanStyle>>()

        // Find strings
        stringPattern.findAll(line).forEach { match ->
            tokens.add(Triple(match.range, "string", SpanStyle(color = theme.string, fontFamily = FontFamily.Monospace)))
        }

        // Find numbers
        numberPattern.findAll(line).forEach { match ->
            tokens.add(Triple(match.range, "number", SpanStyle(color = theme.number, fontFamily = FontFamily.Monospace)))
        }

        // Find keywords and functions
        wordPattern.findAll(line).forEach { match ->
            val word = match.value
            when {
                word in keywords -> tokens.add(
                    Triple(match.range, "keyword", SpanStyle(color = theme.keyword, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)),
                )
                // Check if it's followed by '(' - likely a function
                match.range.last + 1 < line.length && line[match.range.last + 1] == '(' ->
                    tokens.add(Triple(match.range, "function", SpanStyle(color = theme.function, fontFamily = FontFamily.Monospace)))
            }
        }

        // Sort and remove overlaps
        val sortedTokens = tokens.sortedBy { it.first.first }
        val nonOverlapping = mutableListOf<Triple<IntRange, String, SpanStyle>>()
        var lastEnd = -1

        sortedTokens.forEach { token ->
            if (token.first.first > lastEnd) {
                nonOverlapping.add(token)
                lastEnd = token.first.last
            }
        }

        // Build annotated string
        var currentIndex = 0
        nonOverlapping.forEach { (range, _, style) ->
            // Append text before token
            if (currentIndex < range.first) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(line.substring(currentIndex, range.first))
                }
            }
            // Append styled token
            withStyle(style) {
                append(line.substring(range))
            }
            currentIndex = range.last + 1
        }

        // Append remaining text
        if (currentIndex < line.length) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(line.substring(currentIndex))
            }
        }
    }

    private fun highlightJSON(code: String, theme: Theme): AnnotatedString = buildAnnotatedString {
        val stringPattern = """"(?:[^"\\]|\\.)*"""".toRegex()
        val numberPattern = """-?\d+\.?\d*([eE][+-]?\d+)?""".toRegex()
        val boolNullPattern = """\b(true|false|null)\b""".toRegex()

        val tokens = mutableListOf<Pair<IntRange, SpanStyle>>()

        stringPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.string, fontFamily = FontFamily.Monospace))
        }
        boolNullPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.keyword, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
        }
        numberPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.number, fontFamily = FontFamily.Monospace))
        }

        val sorted = tokens.sortedBy { it.first.first }
        val nonOverlapping = mutableListOf<Pair<IntRange, SpanStyle>>()
        var lastEnd = -1

        sorted.forEach { token ->
            if (token.first.first > lastEnd) {
                nonOverlapping.add(token)
                lastEnd = token.first.last
            }
        }

        var currentIndex = 0
        nonOverlapping.forEach { (range, style) ->
            if (currentIndex < range.first) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(code.substring(currentIndex, range.first))
                }
            }
            withStyle(style) {
                append(code.substring(range))
            }
            currentIndex = range.last + 1
        }

        if (currentIndex < code.length) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(code.substring(currentIndex))
            }
        }
    }

    private fun highlightXML(code: String, theme: Theme): AnnotatedString = buildAnnotatedString {
        val tagPattern = """</?[\w:]+|/?>""".toRegex()
        val attrPattern = """\b[\w:-]+(?==)""".toRegex()
        val stringPattern = """"[^"]*"|'[^']*'""".toRegex()
        val commentPattern = """<!--.*?-->""".toRegex()

        val tokens = mutableListOf<Pair<IntRange, SpanStyle>>()

        commentPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.comment, fontFamily = FontFamily.Monospace))
        }
        tagPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.keyword, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
        }
        attrPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.function, fontFamily = FontFamily.Monospace))
        }
        stringPattern.findAll(code).forEach { match ->
            tokens.add(match.range to SpanStyle(color = theme.string, fontFamily = FontFamily.Monospace))
        }

        val sorted = tokens.sortedBy { it.first.first }
        val nonOverlapping = mutableListOf<Pair<IntRange, SpanStyle>>()
        var lastEnd = -1

        sorted.forEach { token ->
            if (token.first.first > lastEnd) {
                nonOverlapping.add(token)
                lastEnd = token.first.last
            }
        }

        var currentIndex = 0
        nonOverlapping.forEach { (range, style) ->
            if (currentIndex < range.first) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(code.substring(currentIndex, range.first))
                }
            }
            withStyle(style) {
                append(code.substring(range))
            }
            currentIndex = range.last + 1
        }

        if (currentIndex < code.length) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(code.substring(currentIndex))
            }
        }
    }
}
