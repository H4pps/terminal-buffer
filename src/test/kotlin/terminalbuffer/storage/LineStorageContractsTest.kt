package terminalbuffer.storage

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LineStorageContractsTest {
    @Test
    fun `indexed methods throw on invalid bounds`() {
        val storage = TestMutableLineStorage()

        assertFailsWith<IndexOutOfBoundsException> { storage.lineSnapshot(0) }
        assertFailsWith<IndexOutOfBoundsException> { storage.mutableLine(0) }
        assertFailsWith<IndexOutOfBoundsException> { storage.replaceLine(0, lineOf("x")) }
        assertFailsWith<IndexOutOfBoundsException> { storage.removeFirstLine() }
        assertFailsWith<IndexOutOfBoundsException> { storage.insertLine(-1, lineOf("x")) }
        assertFailsWith<IndexOutOfBoundsException> { storage.insertLine(1, lineOf("x")) }
    }

    @Test
    fun `line snapshot is detached from storage`() {
        val storage = TestMutableLineStorage()
        storage.appendLine(lineOf("ab"))

        val snapshot = storage.lineSnapshot(0).toMutableList()
        snapshot[0] = TerminalCell.fromChar('Z')

        assertEquals("ab", storage.lineSnapshot(0).toText())
    }

    @Test
    fun `append insert and replace defensively copy inputs`() {
        val storage = TestMutableLineStorage()

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
        val storage = TestMutableLineStorage()
        storage.appendLine(lineOf("ab"))

        val liveLine = storage.mutableLine(0)
        liveLine.insertAt(1, TerminalCell.fromChar('X'))

        assertEquals("aXb", storage.lineSnapshot(0).toText())
    }

    @Test
    fun `structural operations keep line count consistent`() {
        val storage = TestMutableLineStorage()
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

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })

    private fun List<TerminalCell>.toText(): String =
        buildString {
            this@toText.forEach { cell ->
                val codePoint = cell.codePoint ?: return@forEach
                append(String(Character.toChars(codePoint)))
            }
        }

    /**
     * Minimal mutable storage test double that follows the storage contract semantics.
     */
    private class TestMutableLineStorage : MutableLineStorage {
        private val lines = mutableListOf<BufferLine>()

        override val lineCount: Int
            get() = lines.size

        override fun lineSnapshot(index: Int): List<TerminalCell> {
            validateExistingIndex(index)
            return lines[index].snapshot()
        }

        override fun appendLine(line: BufferLine) {
            lines.add(copyOf(line))
        }

        override fun insertLine(
            index: Int,
            line: BufferLine,
        ) {
            validateInsertionIndex(index)
            lines.add(index, copyOf(line))
        }

        override fun replaceLine(
            index: Int,
            line: BufferLine,
        ) {
            validateExistingIndex(index)
            lines[index] = copyOf(line)
        }

        override fun mutableLine(index: Int): BufferLine {
            validateExistingIndex(index)
            return lines[index]
        }

        override fun removeFirstLine(): BufferLine {
            if (lines.isEmpty()) {
                throw IndexOutOfBoundsException("Cannot remove first line from empty storage")
            }
            return lines.removeAt(0)
        }

        override fun clear() {
            lines.clear()
        }

        private fun validateExistingIndex(index: Int) {
            if (index !in 0 until lineCount) {
                throw IndexOutOfBoundsException("Index $index is outside valid range 0..${lineCount - 1}")
            }
        }

        private fun validateInsertionIndex(index: Int) {
            if (index !in 0..lineCount) {
                throw IndexOutOfBoundsException("Insertion index $index is outside valid range 0..$lineCount")
            }
        }

        private fun copyOf(source: BufferLine): BufferLine = BufferLine.fromCells(source.snapshot())
    }
}
