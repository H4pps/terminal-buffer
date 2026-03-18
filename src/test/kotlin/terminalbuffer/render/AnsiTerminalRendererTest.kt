package terminalbuffer.render

import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.TerminalCell
import terminalbuffer.domain.TerminalColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnsiTerminalRendererTest {
    private val renderer = AnsiTerminalRenderer()

    @Test
    fun `renders fixed-size content and spaces for empty cells`() {
        val frame =
            RenderFrame(
                width = 3,
                height = 2,
                rowsInput =
                    listOf(
                        listOf(
                            TerminalCell.fromChar('A'),
                            TerminalCell(),
                            TerminalCell.fromChar('C'),
                        ),
                        listOf(
                            TerminalCell(),
                            TerminalCell.fromChar('X'),
                            TerminalCell(),
                        ),
                    ),
                cursorColumn = 0,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)
        val plain = stripAnsi(rendered)
        val lines = plain.split('\n')

        assertEquals(2, lines.size)
        assertEquals("A C", lines[0])
        assertEquals(" X ", lines[1])
        assertFalse(rendered.endsWith("\n"))
    }

    @Test
    fun `applies reverse-video marker on cursor cell`() {
        val frame =
            RenderFrame(
                width = 2,
                height = 1,
                rowsInput = listOf(listOf(TerminalCell.fromChar('A'), TerminalCell.fromChar('B'))),
                cursorColumn = 0,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)

        assertTrue(rendered.contains(ansi("7")))
        assertTrue(rendered.contains("${ansi("7")}A"))
        assertTrue(rendered.contains(ansi("27")) || rendered.endsWith("${ansi("0")}${ansi("0")}"))
    }

    @Test
    fun `renders empty cursor cell as reversed space`() {
        val frame =
            RenderFrame(
                width = 1,
                height = 1,
                rowsInput = listOf(listOf(TerminalCell())),
                cursorColumn = 0,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)

        assertTrue(rendered.contains("${ansi("7")} "))
    }

    @Test
    fun `maps colors and style flags to ansi sgr codes`() {
        val styledCell =
            TerminalCell(
                codePoint = 'R'.code,
                attributes =
                    CellAttributes(
                        foreground = TerminalColor.RED,
                        background = TerminalColor.BLUE,
                        bold = true,
                        italic = true,
                        underline = true,
                    ),
            )
        val brightCell =
            TerminalCell(
                codePoint = 'G'.code,
                attributes =
                    CellAttributes(
                        foreground = TerminalColor.BRIGHT_GREEN,
                        background = TerminalColor.BRIGHT_MAGENTA,
                    ),
            )
        val frame =
            RenderFrame(
                width = 2,
                height = 1,
                rowsInput = listOf(listOf(styledCell, brightCell)),
                cursorColumn = 1,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)

        assertTrue(rendered.contains("1"))
        assertTrue(rendered.contains("3"))
        assertTrue(rendered.contains("4"))
        assertTrue(rendered.contains("31"))
        assertTrue(rendered.contains("44"))
        assertTrue(rendered.contains("22"))
        assertTrue(rendered.contains("23"))
        assertTrue(rendered.contains("24"))
        assertTrue(rendered.contains("92"))
        assertTrue(rendered.contains("105"))
        assertTrue(rendered.contains("7"))
    }

    @Test
    fun `emits style transitions only when state changes`() {
        val attrs = CellAttributes(foreground = TerminalColor.CYAN, bold = true)
        val row =
            listOf(
                TerminalCell(codePoint = 'a'.code, attributes = attrs),
                TerminalCell(codePoint = 'b'.code, attributes = attrs),
                TerminalCell(codePoint = 'c'.code, attributes = attrs),
            )
        val frame =
            RenderFrame(
                width = 3,
                height = 1,
                rowsInput = listOf(row),
                cursorColumn = 1,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)
        val escapeCount = Regex("\\u001B\\[").findAll(rendered).count()

        assertEquals(5, escapeCount)
    }

    @Test
    fun `emits default color transitions when returning to defaults`() {
        val redBgYellow =
            TerminalCell(
                codePoint = 'X'.code,
                attributes =
                    CellAttributes(
                        foreground = TerminalColor.RED,
                        background = TerminalColor.YELLOW,
                    ),
            )
        val defaults = TerminalCell.fromChar('Y')
        val frame =
            RenderFrame(
                width = 2,
                height = 1,
                rowsInput = listOf(listOf(redBgYellow, defaults)),
                cursorColumn = 1,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)

        assertTrue(rendered.contains("39"))
        assertTrue(rendered.contains("49"))
    }

    @Test
    fun `emits row-end and final reset sequences`() {
        val frame =
            RenderFrame(
                width = 1,
                height = 2,
                rowsInput =
                    listOf(
                        listOf(TerminalCell.fromChar('A')),
                        listOf(TerminalCell.fromChar('B')),
                    ),
                cursorColumn = 0,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)
        val reset = ansi("0")

        assertTrue(rendered.contains("$reset\n"))
        assertTrue(rendered.endsWith("$reset$reset"))
    }

    @Test
    fun `renders supplementary unicode code points`() {
        val grinning = 0x1F600
        val frame =
            RenderFrame(
                width = 1,
                height = 1,
                rowsInput = listOf(listOf(TerminalCell(codePoint = grinning))),
                cursorColumn = 0,
                cursorRow = 0,
            )

        val rendered = renderer.render(frame)
        val expectedChar = String(Character.toChars(grinning))

        assertTrue(rendered.contains(expectedChar))
    }

    private fun ansi(code: String): String = "\u001B[${code}m"

    private fun stripAnsi(text: String): String = text.replace(Regex("\\u001B\\[[0-9;]*m"), "")
}
