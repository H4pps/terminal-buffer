package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })
}
