package terminalbuffer.domain

/**
 * A single terminal grid cell.
 *
 * @property codePoint Unicode code point stored in the cell or null when the cell is empty
 * @property attributes visual attributes associated with the cell
 * @throws IllegalArgumentException when [codePoint] is not a valid Unicode code point
 */
data class TerminalCell(
    val codePoint: Int? = null,
    val attributes: CellAttributes = CellAttributes(),
) {
    init {
        require(codePoint == null || Character.isValidCodePoint(codePoint)) {
            "Invalid Unicode code point: $codePoint"
        }
    }

    /**
     * Returns true when this cell does not contain a character.
     */
    fun isEmpty(): Boolean = codePoint == null

    companion object {
        /**
         * Creates a terminal cell from a single UTF-16 character.
         *
         * @param char source character
         * @param attributes visual attributes applied to the created cell
         * @return a new terminal cell containing [char]
         */
        fun fromChar(
            char: Char,
            attributes: CellAttributes = CellAttributes(),
        ): TerminalCell = TerminalCell(char.code, attributes)
    }
}
