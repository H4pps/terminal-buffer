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
 * @param rowReferenceForScreenRow callback that maps visible row to wrapped logical-line segment
 */
internal class BufferEditingEngine(
    private val storage: MutableLineStorage,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val scrollbackMaxLines: Int,
    private val pinViewportToBottom: () -> Unit,
    private val rowReferenceForScreenRow: (Int) -> WrappedViewportMapper.RowReference,
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

        val reference = rowReferenceForScreenRow(startCursor.row)
        val storageIndex = ensureStorageIndexForRowReference(reference)
        val startAbsoluteColumn = reference.startColumnIndex + startCursor.column
        var absoluteColumn = startAbsoluteColumn

        text.forEach { char ->
            writeCellAt(
                storageIndex = storageIndex,
                column = absoluteColumn,
                cell = TerminalCell.fromChar(char = char, attributes = attributes),
            )
            absoluteColumn += 1
        }

        return cursorFromAbsoluteColumn(
            startCursor = startCursor,
            startAbsoluteColumn = startAbsoluteColumn,
            endAbsoluteColumn = absoluteColumn,
        )
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

        val reference = rowReferenceForScreenRow(startCursor.row)
        val storageIndex = ensureStorageIndexForRowReference(reference)
        val startAbsoluteColumn = reference.startColumnIndex + startCursor.column
        var absoluteColumn = startAbsoluteColumn

        text.forEach { char ->
            insertCellAt(
                storageIndex = storageIndex,
                column = absoluteColumn,
                cell = TerminalCell.fromChar(char = char, attributes = attributes),
            )
            absoluteColumn += 1
        }

        return cursorFromAbsoluteColumn(
            startCursor = startCursor,
            startAbsoluteColumn = startAbsoluteColumn,
            endAbsoluteColumn = absoluteColumn,
        )
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

        val reference = rowReferenceForScreenRow(cursor.row)
        val storageIndex = ensureStorageIndexForRowReference(reference)
        val startColumnIndex = reference.startColumnIndex
        val fillCell =
            if (character == null) {
                TerminalCell()
            } else {
                TerminalCell.fromChar(char = character, attributes = attributes)
            }
        val line = storage.mutableLine(storageIndex)
        repeat(screenWidth) { offset ->
            val column = startColumnIndex + offset
            while (line.length < column) {
                line.append(TerminalCell())
            }
            if (line.length == column) {
                line.append(fillCell)
            } else {
                line.set(column, fillCell)
            }
        }
    }

    /**
     * Inserts an empty line at bottom and enforces scrollback retention.
     */
    fun insertEmptyLineAtBottom() {
        pinViewportToBottom()
        appendBottomLineForScroll()
    }

    private fun ensureStorageIndexForRowReference(reference: WrappedViewportMapper.RowReference): Int {
        reference.lineIndex?.let { return it }

        storage.appendLine(BufferLine.empty())
        enforceScrollbackLimit()
        pinViewportToBottom()

        return rowReferenceForScreenRow(screenHeight - 1).lineIndex
            ?: throw IllegalStateException("Unable to resolve storage line for editing row")
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

    private fun cursorFromAbsoluteColumn(
        startCursor: CursorPosition,
        startAbsoluteColumn: Int,
        endAbsoluteColumn: Int,
    ): CursorPosition {
        val startWrappedRow = startAbsoluteColumn / screenWidth
        val endWrappedRow = endAbsoluteColumn / screenWidth
        val rowDelta = endWrappedRow - startWrappedRow
        val targetRow = (startCursor.row + rowDelta).coerceAtMost(screenHeight - 1)
        val targetColumn = endAbsoluteColumn % screenWidth
        return CursorPosition(column = targetColumn, row = targetRow)
    }
}
