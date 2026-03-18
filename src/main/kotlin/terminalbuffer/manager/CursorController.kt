package terminalbuffer.manager

import terminalbuffer.domain.CursorPosition

/**
 * Owns cursor state and movement rules inside screen bounds.
 *
 * @param screenWidth number of screen columns
 * @param screenHeight number of screen rows
 * @param initial initial cursor position
 * @param onBottomRowReached callback invoked when cursor lands on bottom screen row
 */
class CursorController(
    private val screenWidth: Int,
    private val screenHeight: Int,
    initial: CursorPosition = CursorPosition(column = 0, row = 0),
    private val onBottomRowReached: () -> Unit = {},
) {
    /**
     * Current cursor position.
     */
    var position: CursorPosition = initial
        private set

    /**
     * Sets cursor to exact [column]/[row] position.
     *
     * @param column zero-based screen column
     * @param row zero-based screen row
     * @throws IndexOutOfBoundsException when target is outside screen bounds
     */
    fun setPosition(
        column: Int,
        row: Int,
    ) {
        validateTarget(column, row)
        position = CursorPosition(column = column, row = row)
        notifyBottomRowIfNeeded()
    }

    /**
     * Moves cursor up by [cells] and clamps to screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveUp(cells: Int = 1) {
        validateCells(cells)
        position = position.moveUp(cells).clampTo(screenWidth, screenHeight)
        notifyBottomRowIfNeeded()
    }

    /**
     * Moves cursor down by [cells] and clamps to screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveDown(cells: Int = 1) {
        validateCells(cells)
        position = position.moveDown(cells).clampTo(screenWidth, screenHeight)
        notifyBottomRowIfNeeded()
    }

    /**
     * Moves cursor left by [cells] and clamps to screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveLeft(cells: Int = 1) {
        validateCells(cells)
        position = position.moveLeft(cells).clampTo(screenWidth, screenHeight)
        notifyBottomRowIfNeeded()
    }

    /**
     * Moves cursor right by [cells] and clamps to screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveRight(cells: Int = 1) {
        validateCells(cells)
        position = position.moveRight(cells).clampTo(screenWidth, screenHeight)
        notifyBottomRowIfNeeded()
    }

    private fun validateTarget(
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

    private fun validateCells(cells: Int) {
        require(cells >= 0) { "Movement cells must be non-negative: $cells" }
    }

    private fun notifyBottomRowIfNeeded() {
        if (position.row == screenHeight - 1) {
            onBottomRowReached()
        }
    }
}
