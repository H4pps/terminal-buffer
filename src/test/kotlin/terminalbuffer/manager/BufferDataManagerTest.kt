package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.domain.TerminalColor
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BufferDataManagerTest {
    @Test
    fun `constructor rejects invalid screen dimensions and scrollback max`() {
        val storage = InMemoryLineStorage()

        assertFailsWith<IllegalArgumentException> {
            BufferDataManager(storage = storage, screenWidth = 0, screenHeight = 24, scrollbackMaxLines = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            BufferDataManager(storage = storage, screenWidth = 80, screenHeight = 0, scrollbackMaxLines = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            BufferDataManager(storage = storage, screenWidth = 80, screenHeight = 24, scrollbackMaxLines = -1)
        }
    }

    @Test
    fun `constructor stores configuration and initializes default state`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 120,
                screenHeight = 40,
                scrollbackMaxLines = 500,
            )

        assertEquals(120, manager.screenWidth)
        assertEquals(40, manager.screenHeight)
        assertEquals(500, manager.scrollbackMaxLines)
        assertEquals(CursorPosition(column = 0, row = 0), manager.cursorPosition)
        assertEquals(CellAttributes(), manager.currentAttributes)
        assertEquals(0, manager.viewportTopLineIndex)
        assertTrue(manager.viewportPinnedToBottom)
    }

    @Test
    fun `constructor bootstraps storage with exactly screen height empty lines`() {
        val storage = InMemoryLineStorage()

        BufferDataManager(
            storage = storage,
            screenWidth = 80,
            screenHeight = 3,
            scrollbackMaxLines = 100,
        )

        assertEquals(3, storage.lineCount)
        for (index in 0 until storage.lineCount) {
            assertTrue(storage.lineSnapshot(index).isEmpty())
        }
    }

    @Test
    fun `constructor clears pre-populated storage before bootstrapping`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("abc"))
        storage.appendLine(lineOf("def"))

        BufferDataManager(
            storage = storage,
            screenWidth = 80,
            screenHeight = 2,
            scrollbackMaxLines = 100,
        )

        assertEquals(2, storage.lineCount)
        assertTrue(storage.lineSnapshot(0).isEmpty())
        assertTrue(storage.lineSnapshot(1).isEmpty())
    }

    @Test
    fun `max viewport top line index is zero when storage lines are fewer than screen height`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.removeFirstLine()
        storage.removeFirstLine()

        assertEquals(0, manager.maxViewportTopLineIndex)
    }

    @Test
    fun `max viewport top line index is zero when storage lines equal screen height`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        assertEquals(0, manager.maxViewportTopLineIndex)
    }

    @Test
    fun `max viewport top line index matches overflow when storage lines exceed screen height`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))

        assertEquals(2, manager.maxViewportTopLineIndex)
    }

    @Test
    fun `set viewport top line index validates bounds`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.appendLine(lineOf("a"))

        assertFailsWith<IndexOutOfBoundsException> { manager.setViewportTopLineIndex(-1) }
        assertFailsWith<IndexOutOfBoundsException> { manager.setViewportTopLineIndex(2) }
    }

    @Test
    fun `set viewport top line index updates pinned-to-bottom flag`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))

        manager.setViewportTopLineIndex(1)
        assertEquals(1, manager.viewportTopLineIndex)
        assertTrue(!manager.viewportPinnedToBottom)

        manager.setViewportTopLineIndex(2)
        assertEquals(2, manager.viewportTopLineIndex)
        assertTrue(manager.viewportPinnedToBottom)
    }

    @Test
    fun `pin viewport to bottom sets top index to max and pinned true`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))
        manager.setViewportTopLineIndex(0)

        manager.pinViewportToBottom()

        assertEquals(manager.maxViewportTopLineIndex, manager.viewportTopLineIndex)
        assertTrue(manager.viewportPinnedToBottom)
    }

    @Test
    fun `storage index for screen row validates row bounds`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        assertFailsWith<IndexOutOfBoundsException> { manager.storageIndexForScreenRow(-1) }
        assertFailsWith<IndexOutOfBoundsException> { manager.storageIndexForScreenRow(3) }
    }

    @Test
    fun `storage index for screen row returns concrete index when backing line exists`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))
        manager.setViewportTopLineIndex(2)

        assertEquals(2, manager.storageIndexForScreenRow(0))
        assertEquals(4, manager.storageIndexForScreenRow(2))
    }

    @Test
    fun `storage index for screen row returns null when mapped row has no backing storage line`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 80,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        storage.removeFirstLine()
        storage.removeFirstLine()

        assertEquals(0, manager.storageIndexForScreenRow(0))
        assertNull(manager.storageIndexForScreenRow(1))
        assertNull(manager.storageIndexForScreenRow(2))
    }

    @Test
    fun `set cursor position updates cursor for valid coordinates`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        manager.setCursorPosition(column = 4, row = 2)

        assertEquals(CursorPosition(column = 4, row = 2), manager.cursorPosition)
    }

    @Test
    fun `set cursor position throws for out-of-bounds coordinates`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        assertFailsWith<IndexOutOfBoundsException> { manager.setCursorPosition(column = -1, row = 0) }
        assertFailsWith<IndexOutOfBoundsException> { manager.setCursorPosition(column = 5, row = 0) }
        assertFailsWith<IndexOutOfBoundsException> { manager.setCursorPosition(column = 0, row = -1) }
        assertFailsWith<IndexOutOfBoundsException> { manager.setCursorPosition(column = 0, row = 3) }
    }

    @Test
    fun `cursor move methods use default step one`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )
        manager.setCursorPosition(column = 1, row = 1)

        manager.moveCursorRight()
        assertEquals(CursorPosition(column = 2, row = 1), manager.cursorPosition)

        manager.moveCursorDown()
        assertEquals(CursorPosition(column = 2, row = 2), manager.cursorPosition)

        manager.moveCursorLeft()
        assertEquals(CursorPosition(column = 1, row = 2), manager.cursorPosition)

        manager.moveCursorUp()
        assertEquals(CursorPosition(column = 1, row = 1), manager.cursorPosition)
    }

    @Test
    fun `cursor move methods clamp within screen bounds`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )
        manager.setCursorPosition(column = 2, row = 1)

        manager.moveCursorLeft(20)
        assertEquals(CursorPosition(column = 0, row = 1), manager.cursorPosition)

        manager.moveCursorUp(20)
        assertEquals(CursorPosition(column = 0, row = 0), manager.cursorPosition)

        manager.moveCursorRight(20)
        assertEquals(CursorPosition(column = 4, row = 0), manager.cursorPosition)

        manager.moveCursorDown(20)
        assertEquals(CursorPosition(column = 4, row = 2), manager.cursorPosition)
    }

    @Test
    fun `cursor move methods reject negative cells`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )

        assertFailsWith<IllegalArgumentException> { manager.moveCursorUp(-1) }
        assertFailsWith<IllegalArgumentException> { manager.moveCursorDown(-1) }
        assertFailsWith<IllegalArgumentException> { manager.moveCursorLeft(-1) }
        assertFailsWith<IllegalArgumentException> { manager.moveCursorRight(-1) }
    }

    @Test
    fun `cursor stays within bounds across mixed set and move sequence`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 100,
            )

        manager.setCursorPosition(column = 3, row = 1)
        manager.moveCursorRight(10)
        manager.moveCursorDown(10)
        manager.moveCursorLeft(10)
        manager.moveCursorUp(10)

        val cursor = manager.cursorPosition
        assertTrue(cursor.column in 0 until manager.screenWidth)
        assertTrue(cursor.row in 0 until manager.screenHeight)
    }

    @Test
    fun `landing cursor on bottom row hard-pins viewport`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )
        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))
        manager.setViewportTopLineIndex(1)
        assertNotEquals(manager.maxViewportTopLineIndex, manager.viewportTopLineIndex)
        assertTrue(!manager.viewportPinnedToBottom)

        manager.setCursorPosition(column = 0, row = 2)

        assertEquals(manager.maxViewportTopLineIndex, manager.viewportTopLineIndex)
        assertTrue(manager.viewportPinnedToBottom)
    }

    @Test
    fun `non-bottom cursor updates do not auto-unpin or change viewport top`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )
        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))
        manager.pinViewportToBottom()
        val pinnedTop = manager.viewportTopLineIndex

        manager.setCursorPosition(column = 1, row = 1)
        manager.moveCursorRight(1)

        assertEquals(pinnedTop, manager.viewportTopLineIndex)
        assertTrue(manager.viewportPinnedToBottom)
    }

    @Test
    fun `compose render frame keeps configured dimensions and current cursor`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 100,
            )
        manager.setCursorPosition(column = 3, row = 1)

        val frame = manager.composeRenderFrame()

        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertEquals(3, frame.cursorColumn)
        assertEquals(1, frame.cursorRow)
        assertEquals(2, frame.rows.size)
        assertTrue(frame.rows.all { it.size == 4 })
    }

    @Test
    fun `compose render frame truncates pads and fills missing rows with empty cells`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 4,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )
        storage.clear()
        storage.appendLine(lineOf("ABCDE"))
        storage.appendLine(lineOf("XY"))

        val frame = manager.composeRenderFrame()

        assertEquals(listOf('A', 'B', 'C', 'D').map { it.code }, frame.rows[0].map { it.codePoint })
        assertEquals(listOf('E'.code, null, null, null), frame.rows[1].map { it.codePoint })
        assertEquals(listOf('X'.code, 'Y'.code, null, null), frame.rows[2].map { it.codePoint })
    }

    @Test
    fun `set current attributes updates state and has no side effects`() {
        val storage = InMemoryLineStorage()
        val managerWithState =
            BufferDataManager(
                storage = storage,
                screenWidth = 5,
                screenHeight = 3,
                scrollbackMaxLines = 100,
            )
        val custom =
            CellAttributes(
                foreground = TerminalColor.BRIGHT_CYAN,
                background = TerminalColor.RED,
                bold = true,
                italic = true,
                underline = true,
            )

        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("b"))
        managerWithState.setViewportTopLineIndex(1)
        managerWithState.setCursorPosition(column = 2, row = 1)
        val beforeCursor = managerWithState.cursorPosition
        val beforeTop = managerWithState.viewportTopLineIndex
        val beforePinned = managerWithState.viewportPinnedToBottom
        val beforeLineCount = storage.lineCount

        managerWithState.setCurrentAttributes(CellAttributes(foreground = TerminalColor.MAGENTA, italic = true))
        managerWithState.setCurrentAttributes(custom)
        managerWithState.moveCursorRight(1)
        managerWithState.moveCursorLeft(1)

        assertEquals(custom, managerWithState.currentAttributes)
        assertEquals(beforeCursor, managerWithState.cursorPosition)
        assertEquals(beforeTop, managerWithState.viewportTopLineIndex)
        assertEquals(beforePinned, managerWithState.viewportPinnedToBottom)
        assertEquals(beforeLineCount, storage.lineCount)
    }

    @Test
    fun `write text overwrites in line with current attributes`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        val custom = CellAttributes(foreground = TerminalColor.BRIGHT_YELLOW, bold = true)
        storage.replaceLine(0, lineOf("abcd"))
        manager.setCurrentAttributes(custom)
        manager.setCursorPosition(column = 1, row = 0)

        manager.writeText("XY")

        val frame = manager.composeRenderFrame()
        assertEquals(listOf('a'.code, 'X'.code, 'Y'.code, 'd'.code), frame.rows[0].map { it.codePoint })
        assertEquals(custom, frame.rows[0][1].attributes)
        assertEquals(custom, frame.rows[0][2].attributes)
        assertEquals(CursorPosition(column = 3, row = 0), manager.cursorPosition)
    }

    @Test
    fun `write text wraps across rows and updates cursor`() {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        manager.setCursorPosition(column = 2, row = 0)

        manager.writeText("AB")

        val frame = manager.composeRenderFrame()
        assertEquals(listOf(null, null, 'A'.code), frame.rows[0].map { it.codePoint })
        assertEquals(listOf('B'.code, null, null), frame.rows[1].map { it.codePoint })
        assertEquals(CursorPosition(column = 1, row = 1), manager.cursorPosition)
    }

    @Test
    fun `write text bottom overflow stays in the same logical line and keeps storage size`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 2,
                screenHeight = 2,
                scrollbackMaxLines = 1,
            )
        manager.setCursorPosition(column = 1, row = 1)

        manager.writeText("XYZW")

        val frame = manager.composeRenderFrame()
        assertEquals(2, storage.lineCount)
        assertEquals(listOf(null, null), frame.rows[0].map { it.codePoint })
        assertEquals(listOf(null, 'X'.code), frame.rows[1].map { it.codePoint })
        assertEquals(listOf(null, 'X'.code, 'Y'.code, 'Z'.code, 'W'.code), storage.lineSnapshot(1).map { it.codePoint })
    }

    @Test
    fun `insert text shifts content inside a line`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 5,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        storage.replaceLine(0, lineOf("abde"))
        manager.setCursorPosition(column = 2, row = 0)

        manager.insertText("X")

        val frame = manager.composeRenderFrame()
        assertEquals(listOf('a'.code, 'b'.code, 'X'.code, 'd'.code, 'e'.code), frame.rows[0].map { it.codePoint })
        assertEquals(CursorPosition(column = 3, row = 0), manager.cursorPosition)
    }

    @Test
    fun `insert text keeps overflow inside the same logical line`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 4,
            )
        storage.replaceLine(0, lineOf("abc"))
        storage.replaceLine(1, lineOf("def"))
        manager.setCursorPosition(column = 1, row = 0)

        manager.insertText("X")

        val frame = manager.composeRenderFrame()
        assertEquals(listOf('a'.code, 'X'.code, 'b'.code), frame.rows[0].map { it.codePoint })
        assertEquals(listOf('c'.code, null, null), frame.rows[1].map { it.codePoint })
        assertEquals(listOf('a'.code, 'X'.code, 'b'.code, 'c'.code), storage.lineSnapshot(0).map { it.codePoint })
        assertEquals(listOf('d'.code, 'e'.code, 'f'.code), storage.lineSnapshot(1).map { it.codePoint })
        assertEquals(CursorPosition(column = 2, row = 0), manager.cursorPosition)
    }

    @Test
    fun `insert text bottom overflow stays in the same logical line`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 2,
                screenHeight = 2,
                scrollbackMaxLines = 1,
            )
        storage.replaceLine(0, lineOf("ab"))
        storage.replaceLine(1, lineOf("cd"))
        manager.setCursorPosition(column = 1, row = 1)

        manager.insertText("X")

        val frame = manager.composeRenderFrame()
        assertEquals(2, storage.lineCount)
        assertEquals(listOf('a'.code, 'b'.code), frame.rows[0].map { it.codePoint })
        assertEquals(listOf('c'.code, 'X'.code), frame.rows[1].map { it.codePoint })
        assertEquals(listOf('c'.code, 'X'.code, 'd'.code), storage.lineSnapshot(1).map { it.codePoint })
        assertEquals(CursorPosition(column = 0, row = 1), manager.cursorPosition)
    }

    @Test
    fun `insert text pads gap when cursor is beyond current line length`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 5,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        storage.replaceLine(0, lineOf("ab"))
        manager.setCursorPosition(column = 4, row = 0)

        manager.insertText("Z")

        val frame = manager.composeRenderFrame()
        assertEquals(
            listOf('a'.code, 'b'.code, null, null, 'Z'.code),
            frame.rows[0].map { it.codePoint },
        )
        assertEquals(CursorPosition(column = 0, row = 1), manager.cursorPosition)
    }

    @Test
    fun `write and insert long wrap sequences keep final cursor deterministic`() {
        val writeManager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 3,
            )
        writeManager.setCursorPosition(column = 0, row = 0)

        writeManager.writeText("ABCDEFG")

        assertEquals(CursorPosition(column = 1, row = 1), writeManager.cursorPosition)

        val insertStorage = InMemoryLineStorage()
        val insertManager =
            BufferDataManager(
                storage = insertStorage,
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 6,
            )
        insertStorage.replaceLine(0, lineOf("abc"))
        insertStorage.replaceLine(1, lineOf("def"))
        insertManager.setCursorPosition(column = 0, row = 0)

        insertManager.insertText("WXYZ")

        assertEquals(CursorPosition(column = 1, row = 1), insertManager.cursorPosition)
    }

    @Test
    fun `editing while viewport is scrolled up auto-pins and edits bottom visible region`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 10,
            )
        storage.appendLine(lineOf("111"))
        storage.appendLine(lineOf("222"))
        storage.appendLine(lineOf("333"))
        manager.setViewportTopLineIndex(0)
        assertTrue(!manager.viewportPinnedToBottom)

        manager.setCursorPosition(column = 0, row = 0)
        manager.writeText("A")

        assertTrue(manager.viewportPinnedToBottom)
        assertEquals(manager.maxViewportTopLineIndex, manager.viewportTopLineIndex)
        val frame = manager.composeRenderFrame()
        assertEquals(listOf('A'.code, '2'.code, '2'.code), frame.rows[0].map { it.codePoint })
    }

    @Test
    fun `empty write and insert are no-op for cursor and content`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        storage.replaceLine(0, lineOf("abcd"))
        storage.replaceLine(1, lineOf("xy"))
        manager.setCursorPosition(column = 2, row = 1)

        val beforeCursor = manager.cursorPosition
        val beforeFrame = manager.composeRenderFrame()

        manager.writeText("")
        manager.insertText("")

        val afterFrame = manager.composeRenderFrame()
        assertEquals(beforeCursor, manager.cursorPosition)
        assertEquals(
            beforeFrame.rows.map { row -> row.map { it.codePoint } },
            afterFrame.rows.map { row -> row.map { it.codePoint } },
        )
    }

    @Test
    fun `fill current line with character uses current attributes and keeps cursor`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        val custom = CellAttributes(foreground = TerminalColor.CYAN, underline = true)
        manager.setCurrentAttributes(custom)
        manager.setCursorPosition(column = 2, row = 1)

        manager.fillCurrentLine('Q')

        val frame = manager.composeRenderFrame()
        assertEquals(listOf('Q'.code, 'Q'.code, 'Q'.code, 'Q'.code), frame.rows[1].map { it.codePoint })
        assertTrue(frame.rows[1].all { it.attributes == custom })
        assertEquals(CursorPosition(column = 2, row = 1), manager.cursorPosition)
    }

    @Test
    fun `fill current line with null clears to default empty cells`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 2,
            )
        storage.replaceLine(
            0,
            BufferLine.fromCells(
                List(4) {
                    TerminalCell(
                        codePoint = 'A'.code,
                        attributes = CellAttributes(foreground = TerminalColor.BRIGHT_RED, bold = true),
                    )
                },
            ),
        )
        manager.setCursorPosition(column = 1, row = 0)

        manager.fillCurrentLine(null)

        val frame = manager.composeRenderFrame()
        assertEquals(listOf(null, null, null, null), frame.rows[0].map { it.codePoint })
        assertTrue(frame.rows[0].all { it == TerminalCell() })
        assertEquals(CursorPosition(column = 1, row = 0), manager.cursorPosition)
    }

    @Test
    fun `insert empty line at bottom scrolls and enforces retention`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 0,
            )
        storage.replaceLine(0, lineOf("abc"))
        storage.replaceLine(1, lineOf("def"))
        manager.setCursorPosition(column = 1, row = 0)

        manager.insertEmptyLineAtBottom()

        val frame = manager.composeRenderFrame()
        assertEquals(2, storage.lineCount)
        assertEquals(listOf('d'.code, 'e'.code, 'f'.code), frame.rows[0].map { it.codePoint })
        assertEquals(listOf(null, null, null), frame.rows[1].map { it.codePoint })
        assertEquals(CursorPosition(column = 1, row = 0), manager.cursorPosition)
    }

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })
}
