package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.storage.MutableLineStorage

/**
 * Owner of mutable terminal-buffer runtime state.
 *
 * This manager keeps screen configuration, cursor position, current attributes, and viewport
 * scaffold state. It uses [MutableLineStorage] only as canonical logical-line persistence.
 *
 * Startup is deterministic: constructor always clears injected storage and bootstraps exactly
 * [screenHeight] empty logical lines.
 *
 * @property screenWidth configured screen width in cells, always positive
 * @property screenHeight configured screen height in rows, always positive
 * @property scrollbackMaxLines maximum retained scrollback lines, zero or greater
 * @throws IllegalArgumentException when [screenWidth] or [screenHeight] is not positive,
 * or when [scrollbackMaxLines] is negative
 */
class BufferDataManager(
    private val storage: MutableLineStorage,
    val screenWidth: Int,
    val screenHeight: Int,
    val scrollbackMaxLines: Int,
) {
    /**
     * Maximum valid value for [viewportTopLineIndex] based on current storage size and screen height.
     *
     * Returns `0` when storage has at most one visible screen page of lines.
     */
    val maxViewportTopLineIndex: Int
        get() = (storage.lineCount - screenHeight).coerceAtLeast(0)

    /**
     * Current cursor position in screen coordinates.
     */
    var cursorPosition: CursorPosition = CursorPosition(column = 0, row = 0)
        private set

    /**
     * Attributes used by subsequent edit operations.
     */
    var currentAttributes: CellAttributes = CellAttributes()
        private set

    /**
     * Zero-based top logical-line index of current viewport.
     *
     * This field is a scaffold for future scrolling behavior.
     */
    var viewportTopLineIndex: Int = 0
        private set

    /**
     * Whether viewport is pinned to the newest content at the bottom.
     *
     * This field is a scaffold for future scrolling behavior.
     */
    var viewportPinnedToBottom: Boolean = true
        private set

    init {
        require(screenWidth > 0) { "Screen width must be positive: $screenWidth" }
        require(screenHeight > 0) { "Screen height must be positive: $screenHeight" }
        require(scrollbackMaxLines >= 0) {
            "Scrollback maximum lines must be non-negative: $scrollbackMaxLines"
        }

        bootstrapEmptyScreen()
    }

    /**
     * Sets [viewportTopLineIndex] to [topLineIndex].
     *
     * The value must be inside `0..maxViewportTopLineIndex` for current storage state.
     * This method also updates [viewportPinnedToBottom].
     *
     * @param topLineIndex desired top visible logical-line index
     * @throws IndexOutOfBoundsException when [topLineIndex] is outside `0..maxViewportTopLineIndex`
     */
    fun setViewportTopLineIndex(topLineIndex: Int) {
        if (topLineIndex !in 0..maxViewportTopLineIndex) {
            throw IndexOutOfBoundsException(
                "Viewport top index $topLineIndex is outside valid range 0..$maxViewportTopLineIndex",
            )
        }

        viewportTopLineIndex = topLineIndex
        viewportPinnedToBottom = topLineIndex == maxViewportTopLineIndex
    }

    /**
     * Pins viewport to the newest visible page of content.
     */
    fun pinViewportToBottom() {
        viewportTopLineIndex = maxViewportTopLineIndex
        viewportPinnedToBottom = true
    }

    /**
     * Maps [screenRow] in the current viewport to its storage line index.
     *
     * @param screenRow zero-based row index in visible screen area
     * @return mapped storage line index when backing line exists, or null otherwise
     * @throws IndexOutOfBoundsException when [screenRow] is outside `0 until screenHeight`
     */
    fun storageIndexForScreenRow(screenRow: Int): Int? {
        if (screenRow !in 0 until screenHeight) {
            throw IndexOutOfBoundsException(
                "Screen row $screenRow is outside valid range 0..${screenHeight - 1}",
            )
        }

        val mappedIndex = viewportTopLineIndex + screenRow
        return if (mappedIndex in 0 until storage.lineCount) mappedIndex else null
    }

    /**
     * Sets cursor to the exact [column]/[row] position.
     *
     * @param column zero-based screen column
     * @param row zero-based screen row
     * @throws IndexOutOfBoundsException when target is outside screen bounds
     */
    fun setCursorPosition(
        column: Int,
        row: Int,
    ) {
        validateCursorTarget(column, row)
        cursorPosition = CursorPosition(column = column, row = row)
        updateViewportForCursorRow()
    }

    /**
     * Moves cursor up by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorUp(cells: Int = 1) {
        validateMovementCells(cells)
        cursorPosition = cursorPosition.moveUp(cells).clampTo(screenWidth, screenHeight)
        updateViewportForCursorRow()
    }

    /**
     * Moves cursor down by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorDown(cells: Int = 1) {
        validateMovementCells(cells)
        cursorPosition = cursorPosition.moveDown(cells).clampTo(screenWidth, screenHeight)
        updateViewportForCursorRow()
    }

    /**
     * Moves cursor left by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorLeft(cells: Int = 1) {
        validateMovementCells(cells)
        cursorPosition = cursorPosition.moveLeft(cells).clampTo(screenWidth, screenHeight)
        updateViewportForCursorRow()
    }

    /**
     * Moves cursor right by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorRight(cells: Int = 1) {
        validateMovementCells(cells)
        cursorPosition = cursorPosition.moveRight(cells).clampTo(screenWidth, screenHeight)
        updateViewportForCursorRow()
    }

    /**
     * Sets currently active cell [attributes] for subsequent edit operations.
     *
     * This updates only attribute state. Cursor position, viewport state, and storage content remain
     * unchanged.
     *
     * @param attributes new current attributes
     */
    fun setCurrentAttributes(attributes: CellAttributes) {
        currentAttributes = attributes
    }

    private fun bootstrapEmptyScreen() {
        storage.clear()
        repeat(screenHeight) {
            storage.appendLine(BufferLine.empty())
        }
    }

    private fun validateCursorTarget(
        column: Int,
        row: Int,
    ) {
        if (column !in 0 until screenWidth) {
            throw IndexOutOfBoundsException(
                "Cursor column $column is outside valid range 0..${screenWidth - 1}",
            )
        }
        if (row !in 0 until screenHeight) {
            throw IndexOutOfBoundsException(
                "Cursor row $row is outside valid range 0..${screenHeight - 1}",
            )
        }
    }

    private fun validateMovementCells(cells: Int) {
        require(cells >= 0) { "Movement cells must be non-negative: $cells" }
    }

    private fun updateViewportForCursorRow() {
        if (cursorPosition.row == screenHeight - 1) {
            pinViewportToBottom()
        }
    }
}
