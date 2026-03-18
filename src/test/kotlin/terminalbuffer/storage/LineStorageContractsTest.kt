package terminalbuffer.storage

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LineStorageContractsTest {
    @Test
    fun `indexed methods throw on invalid bounds`() {
        val storage = InMemoryLineStorage()

        assertFailsWith<IndexOutOfBoundsException> { storage.lineSnapshot(0) }
        assertFailsWith<IndexOutOfBoundsException> { storage.mutableLine(0) }
        assertFailsWith<IndexOutOfBoundsException> { storage.replaceLine(0, lineOf("x")) }
        assertFailsWith<IndexOutOfBoundsException> { storage.removeFirstLine() }
        assertFailsWith<IndexOutOfBoundsException> { storage.insertLine(-1, lineOf("x")) }
        assertFailsWith<IndexOutOfBoundsException> { storage.insertLine(1, lineOf("x")) }
    }

    @Test
    fun `line snapshot is detached from storage`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("ab"))

        val snapshot = storage.lineSnapshot(0).toMutableList()
        snapshot[0] = TerminalCell.fromChar('Z')

        assertEquals("ab", storage.lineSnapshot(0).toText())
    }

    @Test
    fun `append insert and replace defensively copy inputs`() {
        val storage = InMemoryLineStorage()

        val appendSource = lineOf("ab")
        storage.appendLine(appendSource)
        appendSource.insertAt(0, TerminalCell.fromChar('X'))
        assertEquals("ab", storage.lineSnapshot(0).toText())

        val insertSource = lineOf("cd")
        storage.insertLine(1, insertSource)
        insertSource.append(TerminalCell.fromChar('Y'))
        assertEquals("cd", storage.lineSnapshot(1).toText())

        val replaceSource = lineOf("ef")
        storage.replaceLine(0, replaceSource)
        replaceSource.append(TerminalCell.fromChar('Z'))
        assertEquals("ef", storage.lineSnapshot(0).toText())
    }

    @Test
    fun `mutable line returns live stored line`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("ab"))

        val liveLine = storage.mutableLine(0)
        liveLine.insertAt(1, TerminalCell.fromChar('X'))

        assertEquals("aXb", storage.lineSnapshot(0).toText())
    }

    @Test
    fun `structural operations keep line count consistent`() {
        val storage = InMemoryLineStorage()
        assertEquals(0, storage.lineCount)

        storage.appendLine(lineOf("a"))
        assertEquals(1, storage.lineCount)

        storage.insertLine(1, lineOf("b"))
        assertEquals(2, storage.lineCount)

        storage.replaceLine(0, lineOf("c"))
        assertEquals(2, storage.lineCount)

        val removedFirst = storage.removeFirstLine()
        assertEquals("c", removedFirst.snapshot().toText())
        assertEquals(1, storage.lineCount)

        val removedFirstAgain = storage.removeFirstLine()
        assertEquals("b", removedFirstAgain.snapshot().toText())
        assertEquals(0, storage.lineCount)

        storage.appendLine(lineOf("x"))
        storage.appendLine(lineOf("y"))
        assertEquals(2, storage.lineCount)

        storage.clear()
        assertEquals(0, storage.lineCount)
    }

    @Test
    fun `insert line at lineCount appends using array deque backing`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("a"))

        storage.insertLine(storage.lineCount, lineOf("b"))

        assertEquals(2, storage.lineCount)
        assertEquals("a", storage.lineSnapshot(0).toText())
        assertEquals("b", storage.lineSnapshot(1).toText())
    }

    @Test
    fun `stored lines preserve variable lengths without normalization`() {
        val storage = InMemoryLineStorage()

        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("longer-line"))
        storage.appendLine(lineOf(""))

        assertEquals(1, storage.lineSnapshot(0).size)
        assertEquals(11, storage.lineSnapshot(1).size)
        assertTrue(storage.lineSnapshot(2).isEmpty())
    }

    @Test
    fun `mutable line edits stay local to selected line`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("ab"))
        storage.appendLine(lineOf("cd"))

        storage.mutableLine(0).insertAt(1, TerminalCell.fromChar('X'))

        assertEquals("aXb", storage.lineSnapshot(0).toText())
        assertEquals("cd", storage.lineSnapshot(1).toText())
    }

    @Test
    fun `storage remains structurally valid across mixed operation sequence`() {
        val storage = InMemoryLineStorage()
        storage.appendLine(lineOf("a"))
        storage.appendLine(lineOf("bb"))
        storage.insertLine(1, lineOf("ccc"))
        storage.replaceLine(0, lineOf("dddd"))
        storage.mutableLine(2).append(TerminalCell.fromChar('E'))
        storage.removeFirstLine()

        assertEquals(2, storage.lineCount)
        assertEquals("ccc", storage.lineSnapshot(0).toText())
        assertEquals("bbE", storage.lineSnapshot(1).toText())
    }

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })

    private fun List<TerminalCell>.toText(): String =
        buildString {
            this@toText.forEach { cell ->
                val codePoint = cell.codePoint ?: return@forEach
                append(String(Character.toChars(codePoint)))
            }
        }
}
