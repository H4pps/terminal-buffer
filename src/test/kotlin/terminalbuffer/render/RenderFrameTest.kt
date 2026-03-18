package terminalbuffer.render

import terminalbuffer.domain.TerminalCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderFrameTest {
    @Test
    fun `rejects non positive width`() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                width = 0,
                height = 1,
                rows = listOf(listOf(TerminalCell.fromChar('a'))),
                cursorColumn = 0,
                cursorRow = 0,
            )
        }
    }

    @Test
    fun `rejects non positive height`() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                width = 1,
                height = 0,
                rows = emptyList(),
                cursorColumn = 0,
                cursorRow = 0,
            )
        }
    }

    @Test
    fun `rejects row count mismatch`() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                width = 1,
                height = 2,
                rows = listOf(listOf(TerminalCell.fromChar('a'))),
                cursorColumn = 0,
                cursorRow = 0,
            )
        }
    }

    @Test
    fun `rejects row width mismatch`() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                width = 2,
                height = 1,
                rows = listOf(listOf(TerminalCell.fromChar('a'))),
                cursorColumn = 0,
                cursorRow = 0,
            )
        }
    }

    @Test
    fun `rejects invalid cursor column`() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                width = 2,
                height = 1,
                rows = listOf(listOf(TerminalCell.fromChar('a'), TerminalCell.fromChar('b'))),
                cursorColumn = 2,
                cursorRow = 0,
            )
        }
    }

    @Test
    fun `rejects invalid cursor row`() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                width = 2,
                height = 1,
                rows = listOf(listOf(TerminalCell.fromChar('a'), TerminalCell.fromChar('b'))),
                cursorColumn = 0,
                cursorRow = 1,
            )
        }
    }

    @Test
    fun `defensively copies rows to prevent caller list mutation`() {
        val mutableRows =
            mutableListOf(
                mutableListOf(TerminalCell.fromChar('a'), TerminalCell.fromChar('b')),
                mutableListOf(TerminalCell.fromChar('c'), TerminalCell.fromChar('d')),
            )

        val frame =
            frame(
                width = 2,
                height = 2,
                rows = mutableRows,
                cursorColumn = 0,
                cursorRow = 0,
            )

        mutableRows[0][0] = TerminalCell.fromChar('z')
        mutableRows[1].add(TerminalCell.fromChar('x'))
        mutableRows.clear()

        assertEquals(2, frame.rows.size)
        assertEquals('a'.code, frame.rows[0][0].codePoint)
        assertEquals('d'.code, frame.rows[1][1].codePoint)
    }

    private fun frame(
        width: Int,
        height: Int,
        rows: List<List<TerminalCell>>,
        cursorColumn: Int,
        cursorRow: Int,
    ): RenderFrame =
        RenderFrame(
            width,
            height,
            rows,
            cursorColumn,
            cursorRow,
        )
}
