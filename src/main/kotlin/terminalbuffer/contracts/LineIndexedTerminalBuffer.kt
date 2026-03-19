package terminalbuffer.contracts

/**
 * Provides mapping from one visible screen row to its backing logical storage line index.
 *
 * Implementations return `null` when the visible row has no backing logical line.
 */
interface VisibleLineIndexProvider {
    /**
     * Resolves backing logical storage line index for one visible screen row.
     *
     * @param screenRow zero-based visible row index
     * @return backing logical line index, or null when no line exists for that row
     * @throws IndexOutOfBoundsException when [screenRow] is outside screen bounds
     */
    fun actualLineIndexForScreenRow(screenRow: Int): Int?
}

/**
 * Composite terminal buffer contract used by command UI.
 *
 * This combines standard terminal buffer operations with visible-row to logical-line mapping
 * needed for line-number labels.
 */
interface LineIndexedTerminalBuffer :
    TerminalBufferContract,
    VisibleLineIndexProvider

/**
 * Terminal buffer that can be resized in place while preserving content and state.
 */
interface ResizableTerminalBuffer : LineIndexedTerminalBuffer {
    /**
     * Resizes the buffer in place preserving content, attributes, and cursor as much as possible.
     *
     * @param screenWidth target screen width in cells, must be positive
     * @param screenHeight target screen height in rows, must be positive
     * @throws IllegalArgumentException when target dimensions are invalid
     */
    fun resize(
        screenWidth: Int,
        screenHeight: Int,
    )
}
