package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageBootstrapperTest {
    @Test
    fun `bootstrap clears existing storage and creates exact empty screen lines`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("abc"))
        storage.appendLine(lineOf("def"))

        StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = 3)

        assertEquals(3, storage.lineCount)
        for (index in 0 until storage.lineCount) {
            assertTrue(storage.lineSnapshot(index).isEmpty())
        }
    }

    @Test
    fun `bootstrap supports zero screen height by clearing storage only`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("abc"))

        StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = 0)

        assertEquals(0, storage.lineCount)
    }

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })
}
