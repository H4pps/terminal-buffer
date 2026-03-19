package terminalbuffer.ui

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.contracts.TerminalBufferContract
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.render.RenderFrame
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalUiDispatcherTest {
    @Test
    fun `printable key writes text and requests redraw`() {
        val buffer = FakeBuffer()
        val dispatcher = TerminalUiDispatcher(buffer = buffer)

        val outcome = dispatcher.dispatch(InputKey.Printable('X'))

        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, outcome)
        assertEquals(listOf("writeText:X"), buffer.calls)
    }

    @Test
    fun `arrow keys move cursor and request redraw`() {
        val buffer = FakeBuffer(cursorColumn = 2, cursorRow = 1)
        val dispatcher = TerminalUiDispatcher(buffer = buffer)

        val up = dispatcher.dispatch(InputKey.ArrowUp)
        val down = dispatcher.dispatch(InputKey.ArrowDown)
        val left = dispatcher.dispatch(InputKey.ArrowLeft)
        val right = dispatcher.dispatch(InputKey.ArrowRight)

        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, up)
        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, down)
        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, left)
        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, right)
        assertEquals(
            listOf("moveUp:1", "moveDown:1", "moveLeft:1", "moveRight:1"),
            buffer.calls,
        )
    }

    @Test
    fun `enter on non-bottom row moves to next row start`() {
        val buffer = FakeBuffer(screenHeight = 4, cursorColumn = 3, cursorRow = 1)
        val dispatcher = TerminalUiDispatcher(buffer = buffer)

        val outcome = dispatcher.dispatch(InputKey.Enter)

        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, outcome)
        assertEquals(listOf("setCursor:0,2"), buffer.calls)
        assertEquals(0, buffer.cursorColumn)
        assertEquals(2, buffer.cursorRow)
    }

    @Test
    fun `enter on bottom row inserts bottom line and keeps cursor at bottom start`() {
        val buffer = FakeBuffer(screenHeight = 3, cursorColumn = 2, cursorRow = 2)
        val dispatcher = TerminalUiDispatcher(buffer = buffer)

        val outcome = dispatcher.dispatch(InputKey.Enter)

        assertEquals(DispatchOutcome.CONTINUE_WITH_RENDER, outcome)
        assertEquals(listOf("insertEmptyLineAtBottom", "setCursor:0,2"), buffer.calls)
        assertEquals(0, buffer.cursorColumn)
        assertEquals(2, buffer.cursorRow)
    }

    @Test
    fun `unknown key does nothing and does not redraw`() {
        val buffer = FakeBuffer()
        val dispatcher = TerminalUiDispatcher(buffer = buffer)

        val outcome = dispatcher.dispatch(InputKey.Unknown(listOf(0)))

        assertEquals(DispatchOutcome.CONTINUE_NO_RENDER, outcome)
        assertEquals(emptyList(), buffer.calls)
    }

    @Test
    fun `ctrl c and eof exit loop`() {
        val buffer = FakeBuffer()
        val dispatcher = TerminalUiDispatcher(buffer = buffer)

        assertEquals(DispatchOutcome.EXIT, dispatcher.dispatch(InputKey.CtrlC))
        assertEquals(DispatchOutcome.EXIT, dispatcher.dispatch(InputKey.Eof))
        assertEquals(emptyList(), buffer.calls)
    }

    private class FakeBuffer(
        override val screenWidth: Int = 5,
        override val screenHeight: Int = 3,
        override val scrollbackMaxLines: Int = 100,
        cursorColumn: Int = 0,
        cursorRow: Int = 0,
    ) : TerminalBufferContract {
        val calls = mutableListOf<String>()
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

        override fun setCurrentAttributes(attributes: CellAttributes) {
            this.attributes = attributes
        }

        override fun setCursorPosition(
            column: Int,
            row: Int,
        ) {
            calls += "setCursor:$column,$row"
            currentCursor = CursorPosition(column = column, row = row)
        }

        override fun moveCursorUp(cells: Int) {
            calls += "moveUp:$cells"
            currentCursor =
                CursorPosition(
                    column = currentCursor.column,
                    row = (currentCursor.row - cells).coerceAtLeast(0),
                )
        }

        override fun moveCursorDown(cells: Int) {
            calls += "moveDown:$cells"
            currentCursor =
                CursorPosition(
                    column = currentCursor.column,
                    row = (currentCursor.row + cells).coerceAtMost(screenHeight - 1),
                )
        }

        override fun moveCursorLeft(cells: Int) {
            calls += "moveLeft:$cells"
            currentCursor =
                CursorPosition(
                    column = (currentCursor.column - cells).coerceAtLeast(0),
                    row = currentCursor.row,
                )
        }

        override fun moveCursorRight(cells: Int) {
            calls += "moveRight:$cells"
            currentCursor =
                CursorPosition(
                    column = (currentCursor.column + cells).coerceAtMost(screenWidth - 1),
                    row = currentCursor.row,
                )
        }

        override fun writeText(text: String) {
            calls += "writeText:$text"
        }

        override fun insertText(text: String) = Unit

        override fun fillCurrentLine(character: Char?) = Unit

        override fun insertEmptyLineAtBottom() {
            calls += "insertEmptyLineAtBottom"
        }

        override fun clearScreen() = Unit

        override fun clearScreenAndScrollback() = Unit

        override fun characterAt(
            region: BufferRegion,
            row: Int,
            column: Int,
        ): Int? = null

        override fun attributesAt(
            region: BufferRegion,
            row: Int,
            column: Int,
        ): CellAttributes = CellAttributes()

        override fun lineAsString(
            region: BufferRegion,
            row: Int,
        ): String = ""

        override fun screenContentAsString(): String = ""

        override fun screenAndScrollbackContentAsString(): String = ""

        override fun composeRenderFrame(): RenderFrame {
            val rows = List(screenHeight) { List(screenWidth) { TerminalCell() } }
            return RenderFrame(
                width = screenWidth,
                height = screenHeight,
                rowsInput = rows,
                cursorColumn = currentCursor.column,
                cursorRow = currentCursor.row,
            )
        }
    }
}
