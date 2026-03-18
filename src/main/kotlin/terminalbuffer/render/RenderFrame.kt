package terminalbuffer.render

import terminalbuffer.domain.TerminalCell

/**
 * Immutable renderer input frame representing the current visible screen state.
 *
 * @property width visible screen width in cells
 * @property height visible screen height in rows
 * @property rows screen rows in top-to-bottom order, each containing cell data for that row
 * @property cursorColumn cursor column relative to the visible frame
 * @property cursorRow cursor row relative to the visible frame
 */
class RenderFrame(
    val width: Int,
    val height: Int,
    rowsInput: List<List<TerminalCell>>,
    val cursorColumn: Int,
    val cursorRow: Int,
) {
    private val rowsSnapshot: List<List<TerminalCell>> = rowsInput.map { it.toList() }.toList()

    init {
        require(width > 0) { "Frame width must be positive, got $width" }
        require(height > 0) { "Frame height must be positive, got $height" }
        require(rowsSnapshot.size == height) { "Frame row count ${rowsSnapshot.size} must match height $height" }
        require(rowsSnapshot.all { it.size == width }) { "Each frame row must contain exactly $width cells" }
        require(cursorColumn in 0 until width) { "Cursor column $cursorColumn is outside range 0..${width - 1}" }
        require(cursorRow in 0 until height) { "Cursor row $cursorRow is outside range 0..${height - 1}" }
    }

    /**
     * Detached immutable snapshot of visible rows.
     *
     * Caller-owned collections used at construction are copied, preventing external list mutation
     * from affecting this frame.
     */
    val rows: List<List<TerminalCell>>
        get() = rowsSnapshot.map { it.toList() }
}
