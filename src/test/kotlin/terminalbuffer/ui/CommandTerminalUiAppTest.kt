package terminalbuffer.ui

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.contracts.ResizableTerminalBuffer
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.domain.TerminalColor
import terminalbuffer.render.RenderFrame
import terminalbuffer.render.TerminalRenderer
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandTerminalUiAppTest {
    @Test
    fun `run applies write insert and fill commands`() {
        val buffer = FakeBuffer()
        val renderer = PlainTextRenderer()
        val output = ByteArrayOutputStream()
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = renderer,
                input = BufferedReader(StringReader("write hello\ninsert X\nfill Z\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertEquals(
            listOf("writeText:hello", "insertText:X", "fillCurrentLine:Z"),
            buffer.calls,
        )
        assertTrue(buffer.composeRenderFrameCalls > 0)
        assertTrue(renderer.renderCalls > 0)
        assertTrue(output.toString().contains("Commands:"))
        assertTrue(output.toString().contains("+--+-----+"))
        assertTrue(output.toString().contains("|0 |frame|"))
        assertTrue(output.toString().contains("cursor=(0,0) size=5x3"))
    }

    @Test
    fun `empty cells are displayed as dots inside frame`() {
        val output = ByteArrayOutputStream()
        val app =
            CommandTerminalUiApp(
                initialBuffer = FakeBuffer(screenWidth = 5, screenHeight = 3, frameText = "a"),
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("quit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertTrue(output.toString().contains("|0 |a....|"))
        assertTrue(output.toString().contains("|1 |.....|"))
    }

    @Test
    fun `run applies movement and enter commands`() {
        val buffer = FakeBuffer(screenWidth = 5, screenHeight = 3)
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("right 2\nenter\nquit\n")),
                output = PrintStream(ByteArrayOutputStream()),
            )

        app.run()

        assertTrue(buffer.calls.contains("moveRight:2"))
        assertTrue(buffer.calls.contains("setCursor:0,1"))
        assertEquals(0, buffer.cursorColumn)
        assertEquals(1, buffer.cursorRow)
    }

    @Test
    fun `cursor set is blocked for empty target cell`() {
        val output = ByteArrayOutputStream()
        val buffer =
            FakeBuffer(
                screenWidth = 5,
                screenHeight = 3,
                occupiedScreenCells = setOf(0 to 0),
            )
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("cursor set 2 1\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertEquals(CursorPosition(column = 0, row = 0), buffer.cursorPosition)
        assertTrue(output.toString().contains("empty"))
        assertTrue(buffer.calls.none { it == "setCursor:2,1" })
    }

    @Test
    fun `move command is blocked when destination cell is empty`() {
        val output = ByteArrayOutputStream()
        val buffer =
            FakeBuffer(
                screenWidth = 5,
                screenHeight = 3,
                occupiedScreenCells = setOf(0 to 0),
            )
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("right 1\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertEquals(CursorPosition(column = 0, row = 0), buffer.cursorPosition)
        assertTrue(output.toString().contains("empty"))
        assertTrue(buffer.calls.none { it == "moveRight:1" })
    }

    @Test
    fun `cursor get prints cursor and terminal size`() {
        val output = ByteArrayOutputStream()
        val buffer = FakeBuffer(screenWidth = 9, screenHeight = 4, cursorColumn = 2, cursorRow = 1)
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("cursor get\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertTrue(output.toString().contains("cursor=(2,1) size=9x4"))
    }

    @Test
    fun `run supports clear commands`() {
        val buffer = FakeBuffer(screenWidth = 5, screenHeight = 2)
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("clear\nclearall\nquit\n")),
                output = PrintStream(ByteArrayOutputStream()),
            )

        app.run()

        assertEquals(
            listOf("clearScreen", "clearScreenAndScrollback"),
            buffer.calls,
        )
    }

    @Test
    fun `run supports read commands`() {
        val buffer = FakeBuffer()
        val output = ByteArrayOutputStream()
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("char screen 1 2\ncellattr screen 0 0\nline screen 0\nscreen\nall\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        val text = output.toString()
        assertTrue(text.contains("char=65"))
        assertTrue(text.contains("attributes=CellAttributes"))
        assertTrue(text.contains("line-0"))
        assertTrue(text.contains("screen-content"))
        assertTrue(text.contains("all-content"))
    }

    @Test
    fun `run supports attr and resize commands`() {
        val original = FakeBuffer(screenWidth = 5, screenHeight = 2, scrollbackMaxLines = 77)
        val output = ByteArrayOutputStream()
        val app =
            CommandTerminalUiApp(
                initialBuffer = original,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("attr set bright_green default true false true\nresize 8 3\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertTrue(original.calls.contains("resize:8,3"))
        assertEquals(8, original.screenWidth)
        assertEquals(3, original.screenHeight)
        assertTrue(
            original.calls.contains(
                "setCurrentAttributes:BRIGHT_GREEN,DEFAULT,true,false,true",
            ),
        )
        assertTrue(output.toString().contains("+--+--------+"))
    }

    @Test
    fun `line number is printed once for wrapped rows of same logical line`() {
        val output = ByteArrayOutputStream()
        val buffer =
            FakeBuffer(
                screenWidth = 5,
                screenHeight = 3,
                frameText = "aaaaa\nbbbbb\nccccc",
                lineIndexProvider = { row ->
                    when (row) {
                        0, 1 -> 42
                        else -> 43
                    }
                },
            )
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("quit\n")),
                output = PrintStream(output),
            )

        app.run()

        val text = output.toString()
        assertTrue(text.contains("|42 |aaaaa|"))
        assertTrue(text.contains("|   |bbbbb|"))
        assertTrue(text.contains("|43 |ccccc|"))
    }

    @Test
    fun `run covers remaining commands and aliases`() {
        val output = ByteArrayOutputStream()
        val buffer = FakeBuffer(screenWidth = 5, screenHeight = 3)
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input =
                    BufferedReader(
                        StringReader(
                            """
                            h
                            w hi
                            i !
                            fill empty
                            up 2
                            down 1
                            left 1
                            right 3
                            cursor set 1 1
                            bottom
                            insert-empty-line
                            attr get
                            char screen 0 0
                            cellattr screen 0 0
                            line screen 0
                            screen
                            all
                            help
                            q
                            """.trimIndent() + "\n",
                        ),
                    ),
                output = PrintStream(output),
            )

        app.run()

        assertTrue(buffer.calls.contains("writeText:hi"))
        assertTrue(buffer.calls.contains("insertText:!"))
        assertTrue(buffer.calls.contains("fillCurrentLine:null"))
        assertTrue(buffer.calls.contains("moveUp:2"))
        assertTrue(buffer.calls.contains("moveDown:1"))
        assertTrue(buffer.calls.contains("moveLeft:1"))
        assertTrue(buffer.calls.contains("moveRight:3"))
        assertTrue(buffer.calls.contains("setCursor:1,1"))
        assertEquals(2, buffer.calls.count { it == "insertEmptyLineAtBottom" })
        assertTrue(buffer.calls.contains("characterAt:SCREEN,0,0"))
        assertTrue(buffer.calls.contains("attributesAt:SCREEN,0,0"))
        assertTrue(buffer.calls.contains("lineAsString:SCREEN,0"))

        val text = output.toString()
        assertTrue(text.contains("attributes=CellAttributes"))
        assertTrue(text.contains("char=65"))
        assertTrue(text.contains("Commands:"))
    }

    @Test
    fun `unknown command is reported`() {
        val output = ByteArrayOutputStream()
        val app =
            CommandTerminalUiApp(
                initialBuffer = FakeBuffer(),
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("nope\nquit\n")),
                output = PrintStream(output),
            )

        app.run()

        assertTrue(output.toString().contains("Unknown command: 'nope'. Type 'help'."))
    }

    @Test
    fun `exit alias stops command loop`() {
        val buffer = FakeBuffer()
        val app =
            CommandTerminalUiApp(
                initialBuffer = buffer,
                renderer = PlainTextRenderer(),
                input = BufferedReader(StringReader("exit\nwrite should-not-run\n")),
                output = PrintStream(ByteArrayOutputStream()),
            )

        app.run()

        assertTrue(buffer.calls.none { it == "writeText:should-not-run" })
    }

    private class PlainTextRenderer : TerminalRenderer {
        var renderCalls: Int = 0

        override fun render(frame: RenderFrame): String {
            renderCalls += 1
            return frame.rows
                .joinToString(separator = "\n") { row ->
                    row.joinToString(separator = "") { cell ->
                        cell.codePoint?.let { String(Character.toChars(it)) } ?: " "
                    }
                }
        }
    }

    private class FakeBuffer(
        screenWidth: Int = 5,
        screenHeight: Int = 3,
        override val scrollbackMaxLines: Int = 100,
        cursorColumn: Int = 0,
        cursorRow: Int = 0,
        private val frameText: String = "frame",
        private val lineIndexProvider: (Int) -> Int? = { row -> row },
        private val occupiedScreenCells: Set<Pair<Int, Int>>? = null,
        private val allContent: String = "all-content",
    ) : ResizableTerminalBuffer {
        override var screenWidth: Int = screenWidth
            private set

        override var screenHeight: Int = screenHeight
            private set

        val calls = mutableListOf<String>()
        var composeRenderFrameCalls: Int = 0
        private var attributes: CellAttributes = CellAttributes()
        private var currentCursor: CursorPosition = CursorPosition(column = cursorColumn, row = cursorRow)

        override val cursorPosition: CursorPosition
            get() = currentCursor

        override val currentAttributes: CellAttributes
            get() = attributes

        override val cursorColumn: Int
            get() = currentCursor.column

        override val cursorRow: Int
            get() = currentCursor.row

        override fun writeText(text: String) {
            calls += "writeText:$text"
        }

        override fun insertText(text: String) {
            calls += "insertText:$text"
        }

        override fun fillCurrentLine(character: Char?) {
            calls += "fillCurrentLine:${character ?: "null"}"
        }

        override fun moveCursorUp(cells: Int) {
            val targetRow = (currentCursor.row - cells).coerceAtLeast(0)
            ensureCursorTargetHasContent(column = currentCursor.column, row = targetRow)
            calls += "moveUp:$cells"
            currentCursor =
                CursorPosition(
                    column = currentCursor.column,
                    row = targetRow,
                )
        }

        override fun moveCursorDown(cells: Int) {
            val targetRow = (currentCursor.row + cells).coerceAtMost(screenHeight - 1)
            ensureCursorTargetHasContent(column = currentCursor.column, row = targetRow)
            calls += "moveDown:$cells"
            currentCursor =
                CursorPosition(
                    column = currentCursor.column,
                    row = targetRow,
                )
        }

        override fun moveCursorLeft(cells: Int) {
            val targetColumn = (currentCursor.column - cells).coerceAtLeast(0)
            ensureCursorTargetHasContent(column = targetColumn, row = currentCursor.row)
            calls += "moveLeft:$cells"
            currentCursor =
                CursorPosition(
                    column = targetColumn,
                    row = currentCursor.row,
                )
        }

        override fun moveCursorRight(cells: Int) {
            val targetColumn = (currentCursor.column + cells).coerceAtMost(screenWidth - 1)
            ensureCursorTargetHasContent(column = targetColumn, row = currentCursor.row)
            calls += "moveRight:$cells"
            currentCursor =
                CursorPosition(
                    column = targetColumn,
                    row = currentCursor.row,
                )
        }

        override fun setCursorPosition(
            column: Int,
            row: Int,
        ) {
            ensureCursorTargetHasContent(column = column, row = row)
            calls += "setCursor:$column,$row"
            currentCursor = CursorPosition(column = column, row = row)
        }

        override fun insertEmptyLineAtBottom() {
            calls += "insertEmptyLineAtBottom"
        }

        override fun clearScreen() {
            calls += "clearScreen"
        }

        override fun clearScreenAndScrollback() {
            calls += "clearScreenAndScrollback"
        }

        override fun setCurrentAttributes(attributes: CellAttributes) {
            this.attributes = attributes
            calls +=
                "setCurrentAttributes:${attributes.foreground},${attributes.background}," +
                "${attributes.bold},${attributes.italic},${attributes.underline}"
        }

        override fun characterAt(
            region: BufferRegion,
            row: Int,
            column: Int,
        ): Int? {
            calls += "characterAt:$region,$row,$column"
            if (region != BufferRegion.SCREEN) {
                return 'A'.code
            }

            occupiedScreenCells ?: return 'A'.code
            return if (occupiedScreenCells.contains(row to column)) 'A'.code else null
        }

        override fun attributesAt(
            region: BufferRegion,
            row: Int,
            column: Int,
        ): CellAttributes {
            calls += "attributesAt:$region,$row,$column"
            return CellAttributes(foreground = TerminalColor.CYAN)
        }

        override fun lineAsString(
            region: BufferRegion,
            row: Int,
        ): String {
            calls += "lineAsString:$region,$row"
            return "line-$row"
        }

        override fun screenContentAsString(): String {
            calls += "screenContentAsString"
            return "screen-content"
        }

        override fun screenAndScrollbackContentAsString(): String {
            calls += "screenAndScrollbackContentAsString"
            return allContent
        }

        override fun composeRenderFrame(): RenderFrame {
            composeRenderFrameCalls += 1
            val lines = frameText.split('\n')
            val rows =
                (0 until screenHeight).map { row ->
                    val line = lines.getOrNull(row).orEmpty().take(screenWidth)
                    val cells = line.map { TerminalCell.fromChar(it, CellAttributes()) }.toMutableList()
                    while (cells.size < screenWidth) {
                        cells += TerminalCell()
                    }
                    cells.toList()
                }
            return RenderFrame(
                width = screenWidth,
                height = screenHeight,
                rowsInput = rows,
                cursorColumn = currentCursor.column,
                cursorRow = currentCursor.row,
            )
        }

        override fun actualLineIndexForScreenRow(screenRow: Int): Int? = lineIndexProvider(screenRow)

        override fun resize(
            screenWidth: Int,
            screenHeight: Int,
        ) {
            calls += "resize:$screenWidth,$screenHeight"
            this.screenWidth = screenWidth
            this.screenHeight = screenHeight
            currentCursor =
                CursorPosition(
                    column = currentCursor.column.coerceIn(0, screenWidth - 1),
                    row = currentCursor.row.coerceIn(0, screenHeight - 1),
                )
        }

        private fun ensureCursorTargetHasContent(
            column: Int,
            row: Int,
        ) {
            if (column !in 0 until screenWidth || row !in 0 until screenHeight) {
                throw IndexOutOfBoundsException("Cursor target is outside screen bounds.")
            }
            val occupied = occupiedScreenCells ?: return
            if (occupied.isEmpty()) {
                return
            }
            if (occupied.contains(row to column)) {
                return
            }

            throw IndexOutOfBoundsException(
                "Cursor target ($column,$row) is empty and cannot be selected while content exists on screen.",
            )
        }
    }
}
