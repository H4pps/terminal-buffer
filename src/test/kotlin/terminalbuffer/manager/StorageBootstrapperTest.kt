package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageBootstrapperTest {
    @Test
    fun `bootstrap clears existing storage and creates one empty line`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("abc"))
        storage.appendLine(lineOf("def"))

        StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = 3)

        assertEquals(1, storage.lineCount)
        assertTrue(storage.lineSnapshot(0).isEmpty())
    }

    @Test
    fun `bootstrap with zero screen height still keeps one canonical empty line`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("abc"))

        StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = 0)

        assertEquals(1, storage.lineCount)
        assertTrue(storage.lineSnapshot(0).isEmpty())
    }

    @Test
    fun `bootstrap rejects negative screen height`() {
        val storage = InMemoryLineStorage()

        val error =
            assertFailsWith<IllegalArgumentException> {
                StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = -1)
            }

        assertTrue(error.message?.contains("non-negative") == true)
    }

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })
}
