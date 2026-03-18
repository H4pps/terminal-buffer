package terminalbuffer.manager

import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.render.RenderFrame
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
    private val viewportController: ViewportController
    private val cursorController: CursorController
    private val renderFrameComposer: RenderFrameComposer
    private val editingEngine: BufferEditingEngine

    /**
     * Maximum valid value for [viewportTopLineIndex] based on current storage size and screen height.
     *
     * Returns `0` when storage has at most one visible screen page of lines.
     */
    val maxViewportTopLineIndex: Int
        get() = viewportController.maxTopIndex

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

        StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = screenHeight)
        viewportController = ViewportController(storage = storage, screenHeight = screenHeight)
        cursorController =
            CursorController(
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                initial = cursorPosition,
                onBottomRowReached = { pinViewportToBottom() },
            )
        renderFrameComposer = RenderFrameComposer()
        editingEngine =
            BufferEditingEngine(
                storage = storage,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                scrollbackMaxLines = scrollbackMaxLines,
                pinViewportToBottom = ::pinViewportToBottom,
                storageIndexForScreenRow = ::storageIndexForScreenRow,
            )
        refreshPublishedState()
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
        viewportController.setTopLineIndex(topLineIndex)
        refreshPublishedState()
    }

    /**
     * Pins viewport to the newest visible page of content.
     */
    fun pinViewportToBottom() {
        viewportController.pinToBottom()
        refreshPublishedState()
    }

    /**
     * Maps [screenRow] in the current viewport to its storage line index.
     *
     * @param screenRow zero-based row index in visible screen area
     * @return mapped storage line index when backing line exists, or null otherwise
     * @throws IndexOutOfBoundsException when [screenRow] is outside `0 until screenHeight`
     */
    fun storageIndexForScreenRow(screenRow: Int): Int? = viewportController.storageIndexForScreenRow(screenRow)

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
        cursorController.setPosition(column, row)
        refreshPublishedState()
    }

    /**
     * Moves cursor up by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorUp(cells: Int = 1) {
        cursorController.moveUp(cells)
        refreshPublishedState()
    }

    /**
     * Moves cursor down by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorDown(cells: Int = 1) {
        cursorController.moveDown(cells)
        refreshPublishedState()
    }

    /**
     * Moves cursor left by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorLeft(cells: Int = 1) {
        cursorController.moveLeft(cells)
        refreshPublishedState()
    }

    /**
     * Moves cursor right by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     */
    fun moveCursorRight(cells: Int = 1) {
        cursorController.moveRight(cells)
        refreshPublishedState()
    }

    /**
     * Composes an immutable visible frame for rendering.
     *
     * Rows are built from viewport-mapped storage lines, truncated to [screenWidth] and padded
     * with empty cells when shorter or missing.
     *
     * @return renderer-ready immutable frame for the current visible screen
     */
    fun composeRenderFrame(): RenderFrame =
        renderFrameComposer.compose(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            cursorPosition = cursorPosition,
            rowCellsProvider = { screenRow -> storageIndexForScreenRow(screenRow)?.let(storage::lineSnapshot) },
        )

    /**
     * Writes [text] using overwrite semantics from current cursor and [currentAttributes].
     *
     * Writing advances cursor left-to-right and wraps at [screenWidth]. If wrapping happens on the
     * bottom row, screen scrolls by appending a new bottom line and moving top content to
     * scrollback. Retention is enforced by [scrollbackMaxLines].
     *
     * @param text text to overwrite into the buffer
     */
    fun writeText(text: String) {
        val target =
            editingEngine.writeText(
                text = text,
                startCursor = cursorPosition,
                attributes = currentAttributes,
            )
                ?: return

        setCursorPosition(column = target.column, row = target.row)
    }

    /**
     * Inserts [text] from current cursor and [currentAttributes].
     *
     * Insertion shifts existing cells to the right. Overflow beyond [screenWidth] is propagated to
     * subsequent rows. If propagation reaches beyond bottom row, screen scrolls and retention is
     * enforced by [scrollbackMaxLines].
     *
     * @param text text to insert into the buffer
     */
    fun insertText(text: String) {
        val target =
            editingEngine.insertText(
                text = text,
                startCursor = cursorPosition,
                attributes = currentAttributes,
            )
                ?: return

        setCursorPosition(column = target.column, row = target.row)
    }

    /**
     * Fills the current cursor row with [character], or clears it when null.
     *
     * The target line is always exactly [screenWidth] cells. Non-null fill uses
     * [currentAttributes]. Null fill uses default empty cells (`TerminalCell()`).
     *
     * @param character fill character, or null for empty default cells
     */
    fun fillCurrentLine(character: Char?) {
        editingEngine.fillCurrentLine(
            cursor = cursorPosition,
            character = character,
            attributes = currentAttributes,
        )
    }

    /**
     * Inserts one empty line at the bottom of the visible screen.
     *
     * This causes screen scroll by one row and enforces retention using [scrollbackMaxLines].
     * Cursor position is preserved.
     */
    fun insertEmptyLineAtBottom() {
        editingEngine.insertEmptyLineAtBottom()
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

    private fun refreshPublishedState() {
        cursorPosition = cursorController.position
        viewportTopLineIndex = viewportController.topLineIndex
        viewportPinnedToBottom = viewportController.pinnedToBottom
    }
}
