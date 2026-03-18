package terminalbuffer.domain

/**
 * Immutable cursor position in screen-space coordinates.
 *
 * @property column zero-based cursor column
 * @property row zero-based cursor row
 * @throws IllegalArgumentException when [column] or [row] is negative
 */
data class CursorPosition(
    val column: Int,
    val row: Int,
) {
    init {
        require(column >= 0) { "Cursor column must be non-negative: $column" }
        require(row >= 0) { "Cursor row must be non-negative: $row" }
    }

    /**
     * Returns a new position moved up by [steps].
     *
     * Movement is floored at row `0`.
     *
     * @param steps number of rows to move
     * @throws IllegalArgumentException when [steps] is negative
     */
    fun moveUp(steps: Int = 1): CursorPosition {
        validateSteps(steps)
        return copy(row = (row - steps).coerceAtLeast(0))
    }

    /**
     * Returns a new position moved down by [steps].
     *
     * This operation does not clamp to screen height.
     *
     * @param steps number of rows to move
     * @throws IllegalArgumentException when [steps] is negative
     */
    fun moveDown(steps: Int = 1): CursorPosition {
        validateSteps(steps)
        return copy(row = row + steps)
    }

    /**
     * Returns a new position moved left by [steps].
     *
     * Movement is floored at column `0`.
     *
     * @param steps number of columns to move
     * @throws IllegalArgumentException when [steps] is negative
     */
    fun moveLeft(steps: Int = 1): CursorPosition {
        validateSteps(steps)
        return copy(column = (column - steps).coerceAtLeast(0))
    }

    /**
     * Returns a new position moved right by [steps].
     *
     * This operation does not clamp to screen width.
     *
     * @param steps number of columns to move
     * @throws IllegalArgumentException when [steps] is negative
     */
    fun moveRight(steps: Int = 1): CursorPosition {
        validateSteps(steps)
        return copy(column = column + steps)
    }

    /**
     * Returns a new position clamped to the given screen bounds.
     *
     * The resulting column is within `0..screenWidth-1` and the resulting row is within
     * `0..screenHeight-1`.
     *
     * @param screenWidth screen width in cells
     * @param screenHeight screen height in rows
     * @throws IllegalArgumentException when [screenWidth] or [screenHeight] is not positive
     */
    fun clampTo(
        screenWidth: Int,
        screenHeight: Int,
    ): CursorPosition {
        require(screenWidth > 0) { "Screen width must be positive: $screenWidth" }
        require(screenHeight > 0) { "Screen height must be positive: $screenHeight" }

        return copy(
            column = column.coerceIn(0, screenWidth - 1),
            row = row.coerceIn(0, screenHeight - 1),
        )
    }

    private fun validateSteps(steps: Int) {
        require(steps >= 0) { "Movement steps must be non-negative: $steps" }
    }
}
