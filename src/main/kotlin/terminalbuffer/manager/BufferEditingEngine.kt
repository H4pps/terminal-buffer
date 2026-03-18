package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.storage.MutableLineStorage

/**
 * Internal editing engine that owns text-mutation algorithms for terminal content.
 *
 * This component centralizes write/insert/fill/scroll operations while keeping manager class
 * focused on runtime state orchestration.
 *
 * @param storage canonical mutable logical-line storage
 * @param screenWidth visible screen width in cells
 * @param screenHeight visible screen height in rows
 * @param scrollbackMaxLines maximum retained scrollback line count
 * @param pinViewportToBottom callback that pins viewport before/after mutations when required
 * @param storageIndexForScreenRow callback that maps visible row to storage index
 */
internal class BufferEditingEngine(
    private val storage: MutableLineStorage,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val scrollbackMaxLines: Int,
    private val pinViewportToBottom: () -> Unit,
    private val storageIndexForScreenRow: (Int) -> Int?,
) {
    /**
     * Writes [text] with overwrite semantics from [startCursor] and [attributes].
     *
     * @param text text to write
     * @param startCursor initial cursor position
     * @param attributes attributes applied to written cells
     * @return resulting cursor position, or null when [text] is empty
     */
    fun writeText(
        text: String,
        startCursor: CursorPosition,
        attributes: CellAttributes,
    ): CursorPosition? {
        if (text.isEmpty()) {
            return null
        }

        pinViewportToBottom()

        var column = startCursor.column
        var row = startCursor.row
        text.forEach { char ->
            val storageIndex = ensureStorageIndexForScreenRow(screenRow = row)
            writeCellAt(
                storageIndex = storageIndex,
                column = column,
                cell = TerminalCell.fromChar(char = char, attributes = attributes),
            )

            val (nextColumn, nextRow) = advanceCursorAfterWrite(column = column, row = row)
            column = nextColumn
            row = nextRow
        }

        return CursorPosition(column = column, row = row)
    }

    /**
     * Inserts [text] at [startCursor] with [attributes], shifting existing cells right.
     *
     * @param text text to insert
     * @param startCursor initial cursor position
     * @param attributes attributes applied to inserted cells
     * @return resulting cursor position, or null when [text] is empty
     */
    fun insertText(
        text: String,
        startCursor: CursorPosition,
        attributes: CellAttributes,
    ): CursorPosition? {
        if (text.isEmpty()) {
            return null
        }

        pinViewportToBottom()

        var column = startCursor.column
        var row = startCursor.row
        text.forEach { char ->
            val storageIndex = ensureStorageIndexForScreenRow(screenRow = row)
            insertCellAt(
                storageIndex = storageIndex,
                column = column,
                cell = TerminalCell.fromChar(char = char, attributes = attributes),
            )

            val scrolledDuringOverflow = propagateOverflowFromRow(startRow = row)
            val (nextColumn, nextRow) =
                advanceCursorAfterInsert(
                    column = column,
                    row = row,
                    scrolledDuringOverflow = scrolledDuringOverflow,
                )
            column = nextColumn
            row = nextRow
        }

        return CursorPosition(column = column, row = row)
    }

    /**
     * Fills current line at [cursor] with [character], or clears it when null.
     *
     * @param cursor cursor whose row identifies target line
     * @param character fill character, or null for default empty cells
     * @param attributes attributes applied when [character] is non-null
     */
    fun fillCurrentLine(
        cursor: CursorPosition,
        character: Char?,
        attributes: CellAttributes,
    ) {
        pinViewportToBottom()

        val storageIndex = ensureStorageIndexForScreenRow(screenRow = cursor.row)
        val fillCell =
            if (character == null) {
                TerminalCell()
            } else {
                TerminalCell.fromChar(char = character, attributes = attributes)
            }

        storage.replaceLine(
            storageIndex,
            BufferLine.fromCells(List(screenWidth) { fillCell }),
        )
    }

    /**
     * Inserts an empty line at bottom and enforces scrollback retention.
     */
    fun insertEmptyLineAtBottom() {
        pinViewportToBottom()
        appendBottomLineForScroll()
    }

    private fun ensureStorageIndexForScreenRow(screenRow: Int): Int {
        var mapped = storageIndexForScreenRow(screenRow)
        while (mapped == null) {
            storage.appendLine(BufferLine.empty())
            enforceScrollbackLimit()
            pinViewportToBottom()
            mapped = storageIndexForScreenRow(screenRow)
        }
        return mapped
    }

    private fun enforceScrollbackLimit() {
        val maxStoredLines = screenHeight + scrollbackMaxLines
        while (storage.lineCount > maxStoredLines) {
            storage.removeFirstLine()
        }
    }

    private fun appendBottomLineForScroll() {
        storage.appendLine(BufferLine.empty())
        enforceScrollbackLimit()
        pinViewportToBottom()
    }

    private fun writeCellAt(
        storageIndex: Int,
        column: Int,
        cell: TerminalCell,
    ) {
        val line = storage.mutableLine(storageIndex)
        while (line.length < column) {
            line.append(TerminalCell())
        }

        if (line.length == column) {
            line.append(cell)
        } else {
            line.set(column, cell)
        }
    }

    private fun insertCellAt(
        storageIndex: Int,
        column: Int,
        cell: TerminalCell,
    ) {
        val line = storage.mutableLine(storageIndex)
        while (line.length < column) {
            line.append(TerminalCell())
        }
        line.insertAt(column, cell)
    }

    private fun propagateOverflowFromRow(startRow: Int): Boolean {
        var row = startRow
        var carry: List<TerminalCell>? = null
        var scrolled = false

        while (true) {
            val storageIndex = ensureStorageIndexForScreenRow(screenRow = row)
            val current = storage.lineSnapshot(storageIndex)
            val combined = if (carry == null) current else carry + current

            if (combined.size <= screenWidth) {
                if (carry != null) {
                    storage.replaceLine(storageIndex, BufferLine.fromCells(combined))
                }
                return scrolled
            }

            storage.replaceLine(storageIndex, BufferLine.fromCells(combined.take(screenWidth)))
            carry = combined.drop(screenWidth)

            if (row < screenHeight - 1) {
                row += 1
            } else {
                appendBottomLineForScroll()
                scrolled = true
                row = screenHeight - 1
            }
        }
    }

    private fun advanceCursorAfterWrite(
        column: Int,
        row: Int,
    ): Pair<Int, Int> {
        if (column < screenWidth - 1) {
            return (column + 1) to row
        }
        if (row < screenHeight - 1) {
            return 0 to (row + 1)
        }

        appendBottomLineForScroll()
        return 0 to (screenHeight - 1)
    }

    private fun advanceCursorAfterInsert(
        column: Int,
        row: Int,
        scrolledDuringOverflow: Boolean,
    ): Pair<Int, Int> {
        if (column < screenWidth - 1) {
            return (column + 1) to row
        }
        if (row < screenHeight - 1) {
            return 0 to (row + 1)
        }
        if (!scrolledDuringOverflow) {
            appendBottomLineForScroll()
        }
        return 0 to (screenHeight - 1)
    }
}
