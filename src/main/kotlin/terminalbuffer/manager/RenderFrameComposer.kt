package terminalbuffer.manager

import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.render.RenderFrame

/**
 * Composes immutable [RenderFrame] values from manager-owned screen state.
 *
 * This helper is intentionally pure and storage-agnostic: callers provide row cell data through
 * [rowCellsProvider], which keeps composition separate from storage and viewport controllers.
 */
class RenderFrameComposer {
    /**
     * Composes a renderer-ready frame.
     *
     * Each visible row is truncated to [screenWidth] and padded with empty [TerminalCell] values
     * when shorter or missing.
     *
     * @param screenWidth visible frame width in cells, must be positive
     * @param screenHeight visible frame height in rows, must be positive
     * @param cursorPosition current cursor position in screen coordinates
     * @param rowCellsProvider function returning source cells for a screen row, or null when no row exists
     * @return immutable frame with exactly [screenHeight] rows of exactly [screenWidth] cells
     * @throws IllegalArgumentException when [screenWidth] or [screenHeight] is not positive
     */
    fun compose(
        screenWidth: Int,
        screenHeight: Int,
        cursorPosition: CursorPosition,
        rowCellsProvider: (screenRow: Int) -> List<TerminalCell>?,
    ): RenderFrame {
        require(screenWidth > 0) { "Screen width must be positive: $screenWidth" }
        require(screenHeight > 0) { "Screen height must be positive: $screenHeight" }

        val rows =
            (0 until screenHeight).map { screenRow ->
                composeRow(screenWidth = screenWidth, sourceCells = rowCellsProvider(screenRow))
            }

        return RenderFrame(
            width = screenWidth,
            height = screenHeight,
            rowsInput = rows,
            cursorColumn = cursorPosition.column,
            cursorRow = cursorPosition.row,
        )
    }

    private fun composeRow(
        screenWidth: Int,
        sourceCells: List<TerminalCell>?,
    ): List<TerminalCell> {
        val visibleCells = sourceCells.orEmpty().take(screenWidth)
        return if (visibleCells.size == screenWidth) {
            visibleCells
        } else {
            visibleCells + List(screenWidth - visibleCells.size) { TerminalCell() }
        }
    }
}
