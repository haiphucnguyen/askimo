package io.askimo.cli

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle

class MarkdownJLineRenderer {
    private val parser = Parser.builder().build()

    fun markdownToAnsi(markdown: String): String {
        val doc = parser.parse(markdown)
        val sb = AttributedStringBuilder()
        val v = RendererVisitor(sb)
        doc.accept(v)
        return sb.toAnsi()
    }

    private class RendererVisitor(
        private val sb: AttributedStringBuilder,
    ) : AbstractVisitor() {
        private var indent = 0
        private val counters = ArrayDeque<Int>() // stack for ordered list levels

        private fun styleBase() = AttributedStyle.DEFAULT

        private fun styleDim() = styleBase().foreground(AttributedStyle.BLACK)

        private fun styleCode() = styleBase().foreground(AttributedStyle.WHITE).background(AttributedStyle.BLACK)

        private fun styleH() = styleBase().bold().underline().foreground(AttributedStyle.CYAN)

        private fun styleLink() = styleBase().underline().foreground(AttributedStyle.BLUE)

        private fun newline() {
            sb.append('\n')
        }

        private fun indentSpaces() {
            if (indent > 0) sb.append(" ".repeat(indent))
        }

        private inline fun styled(
            style: AttributedStyle,
            block: () -> Unit,
        ) {
            sb.style(style)
            block()
            sb.style(styleBase())
        }

        override fun visit(paragraph: Paragraph) {
            indentSpaces()
            visitChildrenInline(paragraph) // render inline children with base style
            newline()
        }

        override fun visit(heading: Heading) {
            indentSpaces()
            styled(styleH()) { visitChildrenInline(heading) }
            newline()
        }

        override fun visit(bulletList: BulletList) {
            indent += 0
            super.visit(bulletList)
        }

        override fun visit(orderedList: OrderedList) {
            // Push starting number onto stack
            counters.addLast(orderedList.markerStartNumber)
            // Visit children
            var n = orderedList.firstChild
            while (n != null) {
                n.accept(this)
                n = n.next
            }
            counters.removeLast()
        }

        override fun visit(listItem: ListItem) {
            indentSpaces()
            when (val parent = listItem.parent) {
                is BulletList -> {
                    sb.append("• ")
                    indent += 2
                    visitChildrenInlineOrBlocks(listItem)
                    indent -= 2
                    newline()
                }
                is OrderedList -> {
                    // Pop + increment counter
                    val idx = counters.removeLast()
                    sb.append("$idx. ")
                    counters.addLast(idx + 1)

                    indent += (idx.toString().length + 2)
                    visitChildrenInlineOrBlocks(listItem)
                    indent -= (idx.toString().length + 2)
                    newline()
                }
                else -> {
                    visitChildrenInlineOrBlocks(listItem)
                    newline()
                }
            }
        }

        override fun visit(blockQuote: BlockQuote) {
            forEachChild(blockQuote) { child ->
                indentSpaces()
                styled(styleDim()) { sb.append("│ ") }
                indent += 2
                when (child) {
                    is Paragraph, is Heading, is BulletList, is OrderedList,
                    is FencedCodeBlock, is IndentedCodeBlock,
                    -> child.accept(this)
                    else -> child.accept(this)
                }
                indent -= 2
            }
        }

        override fun visit(fencedCodeBlock: FencedCodeBlock) {
            val lang = fencedCodeBlock.info.orEmpty().trim()
            if (lang.isNotEmpty()) {
                styled(styleDim().bold()) { sb.append("[$lang]") }
                newline()
            }

            fencedCodeBlock.literal.lines().forEach { line ->
                indentSpaces()
                styled(styleCode()) { sb.append(line) }
                newline()
            }
            newline()
        }

        override fun visit(indentedCodeBlock: IndentedCodeBlock) {
            val lines = indentedCodeBlock.literal.lines()
            indentSpaces()
            styled(styleDim()) {
                sb.append("┌────────┐")
                newline()
            }
            lines.forEach { line ->
                indentSpaces()
                styled(styleDim()) { sb.append("│ ") }
                styled(styleCode()) { sb.append(line) }
                styled(styleDim()) {
                    sb.append(" │")
                    newline()
                }
            }
            indentSpaces()
            styled(styleDim()) { sb.append("└────────┘") }
            newline()
        }

        override fun visit(thematicBreak: ThematicBreak) {
            styled(styleDim()) { sb.append("—".repeat(32)) }
            newline()
        }

        override fun visit(text: Text) {
            sb.append(text.literal)
        }

        override fun visit(softLineBreak: SoftLineBreak) {
            newline()
            indentSpaces()
        }

        override fun visit(hardLineBreak: HardLineBreak) {
            newline()
            indentSpaces()
        }

        override fun visit(emphasis: Emphasis) {
            styled(styleBase().italic()) { visitChildrenInline(emphasis) }
        }

        override fun visit(strongEmphasis: StrongEmphasis) {
            styled(styleBase().bold()) { visitChildrenInline(strongEmphasis) }
        }

        override fun visit(code: Code) {
            styled(styleCode()) { sb.append(code.literal) }
        }

        override fun visit(link: Link) {
            // label (children) underlined blue
            styled(styleLink()) { visitChildrenInline(link) }
            val url = link.destination.orEmpty()
            if (url.isNotBlank()) styled(styleDim()) { sb.append(" ($url)") }
        }

        override fun visit(image: Image) {
            // Alt text = children as plain inline
            styled(styleBase()) { sb.append("[image]") }
            val alt = collectInlineText(image)
            if (alt.isNotBlank()) styled(styleBase().bold()) { sb.append(' ').append(alt) }
            val url = image.destination.orEmpty()
            if (url.isNotBlank()) styled(styleDim()) { sb.append(' ').append("($url)") }
        }

        private fun visitChildrenInline(container: Node) {
            var n = container.firstChild
            while (n != null) {
                n.accept(this)
                n = n.next
            }
        }

        private fun visitChildrenInlineOrBlocks(container: Node) {
            var n = container.firstChild
            while (n != null) {
                n.accept(this)
                n = n.next
            }
        }

        private fun forEachChild(
            container: Node,
            block: (Node) -> Unit,
        ) {
            var n = container.firstChild
            while (n != null) {
                block(n)
                n = n.next
            }
        }

        private fun collectInlineText(node: Node): String {
            val b = StringBuilder()
            node.accept(
                object : AbstractVisitor() {
                    override fun visit(text: Text) {
                        b.append(text.literal)
                    }

                    override fun visit(code: Code) {
                        b.append(code.literal)
                    }

                    override fun visit(softLineBreak: SoftLineBreak) {
                        b.append('\n')
                    }

                    override fun visit(hardLineBreak: HardLineBreak) {
                        b.append('\n')
                    }

                    override fun visit(emphasis: Emphasis) {
                        visitChildrenInline(emphasis)
                    }

                    override fun visit(strongEmphasis: StrongEmphasis) {
                        visitChildrenInline(strongEmphasis)
                    }

                    override fun visit(link: Link) {
                        visitChildrenInline(link)
                    }

                    override fun visit(image: Image) { /* avoid recursion */ }
                },
            )
            return b.toString().trim()
        }
    }
}
