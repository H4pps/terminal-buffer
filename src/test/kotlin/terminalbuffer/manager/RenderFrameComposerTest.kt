package terminalbuffer.manager

import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderFrameComposerTest {
    private val composer = RenderFrameComposer()

    @Test
    fun `compose keeps frame dimensions and cursor coordinates`() {
        val frame =
            composer.compose(
                screenWidth = 4,
                screenHeight = 2,
                cursorPosition = CursorPosition(column = 3, row = 1),
            ) { null }

        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertEquals(3, frame.cursorColumn)
        assertEquals(1, frame.cursorRow)
        assertEquals(2, frame.rows.size)
        assertTrue(frame.rows.all { it.size == 4 })
    }

    @Test
    fun `compose truncates long rows and pads short or missing rows`() {
        val frame =
            composer.compose(
                screenWidth = 4,
                screenHeight = 3,
                cursorPosition = CursorPosition(column = 0, row = 0),
            ) { row ->
                when (row) {
                    0 -> "ABCDE".map { TerminalCell.fromChar(it) }
                    1 -> "XY".map { TerminalCell.fromChar(it) }
                    else -> null
                }
            }

        assertEquals(listOf('A', 'B', 'C', 'D').map { it.code }, frame.rows[0].map { it.codePoint })
        assertEquals(listOf('X'.code, 'Y'.code, null, null), frame.rows[1].map { it.codePoint })
        assertEquals(listOf(null, null, null, null), frame.rows[2].map { it.codePoint })
    }
}
