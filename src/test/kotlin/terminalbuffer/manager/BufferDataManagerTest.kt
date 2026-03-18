package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })
}
