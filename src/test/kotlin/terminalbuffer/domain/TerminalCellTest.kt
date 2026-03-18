package terminalbuffer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalCellTest {
    @Test
    fun `accepts empty cell with null code point`() {
        val cell = TerminalCell()

        assertEquals(null, cell.codePoint)
        assertEquals(CellAttributes(), cell.attributes)
        assertTrue(cell.isEmpty())
    }

    @Test
    fun `accepts valid code point`() {
        val cell = TerminalCell("A".codePointAt(0), CellAttributes(foreground = TerminalColor.RED))

        assertEquals("A".codePointAt(0), cell.codePoint)
        assertEquals(TerminalColor.RED, cell.attributes.foreground)
        assertFalse(cell.isEmpty())
    }

    @Test
    fun `rejects invalid code point`() {
        assertFailsWith<IllegalArgumentException> {
            TerminalCell(Int.MAX_VALUE)
        }
    }

    @Test
    fun `from char helper maps code point and attributes`() {
        val attributes = CellAttributes(bold = true)
        val cell = TerminalCell.fromChar('Z', attributes)

        assertEquals('Z'.code, cell.codePoint)
        assertEquals(attributes, cell.attributes)
        assertFalse(cell.isEmpty())
    }
}
