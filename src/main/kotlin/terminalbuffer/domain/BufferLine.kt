package terminalbuffer.domain

/**
 * Logical line of terminal cells.
 *
 * This model is intentionally width-agnostic. Screen width is applied later by the renderer
 * through reflow/wrapping, which keeps resizing independent from storage representation.
 */
class BufferLine private constructor(
    private val cells: MutableList<TerminalCell>,
) {
    /**
     * Number of cells in this logical line.
     */
    val length: Int
        get() = cells.size

    /**
     * Returns the cell at [index].
     *
     * @param index zero-based cell index
     * @return the cell currently stored at [index]
     * @throws IndexOutOfBoundsException when [index] is outside `0 until length`
     */
    operator fun get(index: Int): TerminalCell {
        validateExistingIndex(index)
        return cells[index]
    }

    /**
     * Replaces the cell at [index] with [cell].
     *
     * @param index zero-based cell index
     * @param cell value to store at [index]
     * @throws IndexOutOfBoundsException when [index] is outside `0 until length`
     */
    fun set(
        index: Int,
        cell: TerminalCell,
    ) {
        validateExistingIndex(index)
        cells[index] = cell
    }

    /**
     * Appends [cell] to the end of this line.
     *
     * @param cell value to append
     */
    fun append(cell: TerminalCell) {
        cells.add(cell)
    }

    /**
     * Fills all existing positions in this line with [cell].
     *
     * @param cell value used for all positions
     */
    fun fill(cell: TerminalCell) {
        for (idx in cells.indices) {
            cells[idx] = cell
        }
    }

    /**
     * Inserts [cell] at [index] and shifts subsequent cells to the right.
     *
     * @param index zero-based insertion index
     * @param cell value to insert
     * @throws IndexOutOfBoundsException when [index] is outside `0..length`
     */
    fun insertAt(
        index: Int,
        cell: TerminalCell,
    ) {
        validateInsertionIndex(index)
        cells.add(index, cell)
    }

    /**
     * Returns a detached copy of line cells to prevent external mutation.
     */
    fun snapshot(): List<TerminalCell> = cells.toList()

    private fun validateExistingIndex(index: Int) {
        if (index !in 0 until length) {
            throw IndexOutOfBoundsException("Index $index is outside valid range 0..${length - 1}")
        }
    }

    private fun validateInsertionIndex(index: Int) {
        if (index !in 0..length) {
            throw IndexOutOfBoundsException("Insertion index $index is outside valid range 0..$length")
        }
    }

    companion object {
        /**
         * Creates an empty logical line.
         *
         * @return new logical buffer line with zero cells
         */
        fun empty(): BufferLine = BufferLine(mutableListOf())

        /**
         * Creates a logical line from [cells].
         *
         * @param cells initial cells
         * @return new logical buffer line
         */
        fun fromCells(cells: List<TerminalCell>): BufferLine = BufferLine(cells.toMutableList())
    }
}
