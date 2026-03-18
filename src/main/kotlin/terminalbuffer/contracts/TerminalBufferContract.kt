package terminalbuffer.contracts

import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.render.RenderFrame

/**
 * Buffer region selector for content access APIs.
 */
enum class BufferRegion {
    /**
     * Visible editable screen region.
     */
    SCREEN,

    /**
     * Historical scrollback region.
     */
    SCROLLBACK,
}

/**
 * Public terminal-buffer contract covering required assignment operations.
 */
interface TerminalBufferContract {
    /**
     * Current screen width in cells.
     */
    val screenWidth: Int

    /**
     * Current screen height in rows.
     */
    val screenHeight: Int

    /**
     * Maximum allowed scrollback line count.
     */
    val scrollbackMaxLines: Int

    /**
     * Current cursor position.
     */
    val cursorPosition: CursorPosition

    /**
     * Current cursor column.
     *
     * This is a convenience view derived from [cursorPosition].
     */
    val cursorColumn: Int
        get() = cursorPosition.column

    /**
     * Current cursor row.
     *
     * This is a convenience view derived from [cursorPosition].
     */
    val cursorRow: Int
        get() = cursorPosition.row

    /**
     * Current attributes used by subsequent edits.
     */
    val currentAttributes: CellAttributes

    /**
     * Updates current attributes used by subsequent edits.
     *
     * @param attributes attributes to set as active
     */
    fun setCurrentAttributes(attributes: CellAttributes)

    /**
     * Sets cursor position.
     *
     * @param column destination column
     * @param row destination row
     * @throws IndexOutOfBoundsException when the target is outside screen bounds
     */
    fun setCursorPosition(
        column: Int,
        row: Int,
    )

    /**
     * Moves cursor up by [cells].
     *
     * @param cells number of cells to move
     */
    fun moveCursorUp(cells: Int = 1)

    /**
     * Moves cursor down by [cells].
     *
     * @param cells number of cells to move
     */
    fun moveCursorDown(cells: Int = 1)

    /**
     * Moves cursor left by [cells].
     *
     * @param cells number of cells to move
     */
    fun moveCursorLeft(cells: Int = 1)

    /**
     * Moves cursor right by [cells].
     *
     * @param cells number of cells to move
     */
    fun moveCursorRight(cells: Int = 1)

    /**
     * Writes [text] with overwrite semantics starting at current cursor and active attributes.
     *
     * @param text text to write
     */
    fun writeText(text: String)

    /**
     * Inserts [text] with insert semantics starting at current cursor and active attributes.
     *
     * @param text text to insert
     */
    fun insertText(text: String)

    /**
     * Fills the current line with [character], or clears it when null.
     *
     * @param character fill character or null for empty cells
     */
    fun fillCurrentLine(character: Char?)

    /**
     * Inserts an empty line at the bottom of the screen.
     */
    fun insertEmptyLineAtBottom()

    /**
     * Clears screen content and keeps scrollback.
     */
    fun clearScreen()

    /**
     * Clears both screen and scrollback content.
     */
    fun clearScreenAndScrollback()

    /**
     * Returns character code point at the given [region]/position.
     *
     * @param region selected region
     * @param row row index inside selected region
     * @param column column index
     * @return code point or null for empty cell
     * @throws IndexOutOfBoundsException when position is invalid
     */
    fun characterAt(
        region: BufferRegion,
        row: Int,
        column: Int,
    ): Int?

    /**
     * Returns cell attributes at the given [region]/position.
     *
     * @param region selected region
     * @param row row index inside selected region
     * @param column column index
     * @return cell attributes
     * @throws IndexOutOfBoundsException when position is invalid
     */
    fun attributesAt(
        region: BufferRegion,
        row: Int,
        column: Int,
    ): CellAttributes

    /**
     * Returns one line as plain string from selected [region].
     *
     * @param region selected region
     * @param row row index inside selected region
     * @return line text representation
     * @throws IndexOutOfBoundsException when row is invalid
     */
    fun lineAsString(
        region: BufferRegion,
        row: Int,
    ): String

    /**
     * Returns complete visible screen content as string.
     */
    fun screenContentAsString(): String

    /**
     * Returns complete screen and scrollback content as string.
     */
    fun screenAndScrollbackContentAsString(): String

    /**
     * Composes a renderer-ready immutable frame for the current viewport.
     *
     * Rendering code should consume this frame instead of reading storage directly.
     */
    fun composeRenderFrame(): RenderFrame
}
