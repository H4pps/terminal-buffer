package terminalbuffer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BufferLineTest {
    @Test
    fun `empty creates logical empty line`() {
        val line = BufferLine.empty()

        assertEquals(0, line.length)
        assertTrue(line.snapshot().isEmpty())
    }

    @Test
    fun `from cells preserves length and cell order`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                ),
            )

        assertEquals(2, line.length)
        assertEquals('A'.code, line[0].codePoint)
        assertEquals('B'.code, line[1].codePoint)
    }

    @Test
    fun `from cells accepts empty input`() {
        val line = BufferLine.fromCells(emptyList())

        assertEquals(0, line.length)
        assertTrue(line.snapshot().isEmpty())
    }

    @Test
    fun `get and set validate bounds`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                ),
            )

        line.set(1, TerminalCell.fromChar('Z'))
        assertEquals('Z'.code, line[1].codePoint)

        assertFailsWith<IndexOutOfBoundsException> { line[-1] }
        assertFailsWith<IndexOutOfBoundsException> { line[2] }
        assertFailsWith<IndexOutOfBoundsException> { line.set(-1, TerminalCell()) }
        assertFailsWith<IndexOutOfBoundsException> { line.set(2, TerminalCell()) }
    }

    @Test
    fun `fill updates every existing cell`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                    TerminalCell.fromChar('C'),
                ),
            )

        val fillCell = TerminalCell.fromChar('X')
        line.fill(fillCell)

        assertTrue(line.snapshot().all { it == fillCell })
    }

    @Test
    fun `insert at shifts right and grows length`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                    TerminalCell.fromChar('C'),
                ),
            )

        line.insertAt(1, TerminalCell.fromChar('X'))

        assertEquals(4, line.length)
        assertEquals(
            listOf('A', 'X', 'B', 'C').map { it.code },
            line.snapshot().map { it.codePoint },
        )
    }

    @Test
    fun `insert at validates bounds`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                ),
            )

        assertFailsWith<IndexOutOfBoundsException> { line.insertAt(-1, TerminalCell()) }
        assertFailsWith<IndexOutOfBoundsException> { line.insertAt(3, TerminalCell()) }
    }

    @Test
    fun `insert at allows append at end`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                ),
            )

        line.insertAt(2, TerminalCell.fromChar('C'))

        assertEquals(3, line.length)
        assertEquals(listOf('A', 'B', 'C').map { it.code }, line.snapshot().map { it.codePoint })
    }

    @Test
    fun `append adds cell to the end`() {
        val line = BufferLine.empty()

        line.append(TerminalCell.fromChar('Z'))

        assertEquals(1, line.length)
        assertEquals('Z'.code, line[0].codePoint)
    }

    @Test
    fun `snapshot returns copy and does not expose internal mutable list`() {
        val line =
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('A'),
                    TerminalCell.fromChar('B'),
                ),
            )

        val copy = line.snapshot().toMutableList()
        copy[0] = TerminalCell.fromChar('Z')

        assertNotEquals(copy[0], line[0])
        assertEquals('A'.code, line[0].codePoint)
    }
}
