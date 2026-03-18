package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewportControllerTest {
    @Test
    fun `max top index is zero when storage does not overflow screen`() {
        val storage = InMemoryLineStorage()
        repeat(3) { storage.appendLine(BufferLine.empty()) }
        val controller = ViewportController(storage = storage, screenHeight = 3)

        assertEquals(0, controller.maxTopIndex)
    }

    @Test
    fun `max top index equals overflow amount`() {
        val storage = InMemoryLineStorage()
        repeat(5) { storage.appendLine(BufferLine.empty()) }
        val controller = ViewportController(storage = storage, screenHeight = 3)

        assertEquals(2, controller.maxTopIndex)
    }

    @Test
    fun `set top index validates bounds and updates pin flag`() {
        val storage = InMemoryLineStorage()
        repeat(5) { storage.appendLine(BufferLine.empty()) }
        val controller = ViewportController(storage = storage, screenHeight = 3)

        assertFailsWith<IndexOutOfBoundsException> { controller.setTopLineIndex(-1) }
        assertFailsWith<IndexOutOfBoundsException> { controller.setTopLineIndex(3) }

        controller.setTopLineIndex(1)
        assertEquals(1, controller.topLineIndex)
        assertTrue(!controller.pinnedToBottom)

        controller.setTopLineIndex(2)
        assertEquals(2, controller.topLineIndex)
        assertTrue(controller.pinnedToBottom)
    }

    @Test
    fun `pin to bottom sets max top index and pinned true`() {
        val storage = InMemoryLineStorage()
        repeat(5) { storage.appendLine(BufferLine.empty()) }
        val controller = ViewportController(storage = storage, screenHeight = 3)
        controller.setTopLineIndex(0)

        controller.pinToBottom()

        assertEquals(2, controller.topLineIndex)
        assertTrue(controller.pinnedToBottom)
    }

    @Test
    fun `storage index for screen row validates bounds and maps existing rows`() {
        val storage = InMemoryLineStorage()
        repeat(5) { storage.appendLine(BufferLine.empty()) }
        val controller = ViewportController(storage = storage, screenHeight = 3)
        controller.setTopLineIndex(2)

        assertFailsWith<IndexOutOfBoundsException> { controller.storageIndexForScreenRow(-1) }
        assertFailsWith<IndexOutOfBoundsException> { controller.storageIndexForScreenRow(3) }

        assertEquals(2, controller.storageIndexForScreenRow(0))
        assertEquals(4, controller.storageIndexForScreenRow(2))
    }

    @Test
    fun `storage index for screen row returns null when backing line missing`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("a"))
        val controller = ViewportController(storage = storage, screenHeight = 3)

        assertEquals(0, controller.storageIndexForScreenRow(0))
        assertNull(controller.storageIndexForScreenRow(1))
        assertNull(controller.storageIndexForScreenRow(2))
    }

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })
}
