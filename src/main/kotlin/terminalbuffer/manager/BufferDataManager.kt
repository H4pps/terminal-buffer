package terminalbuffer.manager

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
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
    screenWidth: Int,
    screenHeight: Int,
    val scrollbackMaxLines: Int,
) {
    private var viewportController: ViewportController
    private var cursorController: CursorController
    private val renderFrameComposer: RenderFrameComposer
    private var editingEngine: BufferEditingEngine

    /**
     * Current screen width in cells.
     *
     * This value can change through [resize].
     */
    var screenWidth: Int = screenWidth
        private set

    /**
     * Current screen height in rows.
     *
     * This value can change through [resize].
     */
    var screenHeight: Int = screenHeight
        private set

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
        editingEngine = createEditingEngine()
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
     * Resolves the logical storage line index backing one visible screen row.
     *
     * This uses wrapped viewport mapping and returns the underlying logical line index for the
     * row slice currently visible at [screenRow]. Multiple adjacent screen rows can map to the
     * same logical index when one long logical line wraps on screen.
     *
     * @param screenRow zero-based row index in visible screen area
     * @return backing logical storage line index, or null when row has no backing line
     * @throws IndexOutOfBoundsException when [screenRow] is outside `0 until screenHeight`
     */
    fun actualLineIndexForScreenRow(screenRow: Int): Int? = rowReferenceForScreenRow(screenRow).lineIndex

    /**
     * Sets cursor to the exact [column]/[row] position.
     *
     * @param column zero-based screen column
     * @param row zero-based screen row
     * @throws IndexOutOfBoundsException when target is outside screen bounds
     * @throws IndexOutOfBoundsException when target cell is empty while visible content exists
     */
    fun setCursorPosition(
        column: Int,
        row: Int,
    ) {
        setCursorPositionInternal(
            column = column,
            row = row,
            requireNonEmptyTarget = true,
        )
    }

    /**
     * Moves cursor up by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     * @throws IndexOutOfBoundsException when destination cell is empty while visible content exists
     */
    fun moveCursorUp(cells: Int = 1) {
        require(cells >= 0) { "Movement cells must be non-negative: $cells" }
        if (cells == 0) {
            return
        }
        val target = cursorPosition.moveUp(cells).clampTo(screenWidth, screenHeight)
        ensureNavigableCursorTarget(target.column, target.row)
        cursorController.moveUp(cells)
        refreshPublishedState()
    }

    /**
     * Moves cursor down by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     * @throws IndexOutOfBoundsException when destination cell is empty while visible content exists
     */
    fun moveCursorDown(cells: Int = 1) {
        require(cells >= 0) { "Movement cells must be non-negative: $cells" }
        if (cells == 0) {
            return
        }
        val target = cursorPosition.moveDown(cells).clampTo(screenWidth, screenHeight)
        ensureNavigableCursorTarget(target.column, target.row)
        cursorController.moveDown(cells)
        refreshPublishedState()
    }

    /**
     * Moves cursor left by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     * @throws IndexOutOfBoundsException when destination cell is empty while visible content exists
     */
    fun moveCursorLeft(cells: Int = 1) {
        require(cells >= 0) { "Movement cells must be non-negative: $cells" }
        if (cells == 0) {
            return
        }
        val target = cursorPosition.moveLeft(cells).clampTo(screenWidth, screenHeight)
        ensureNavigableCursorTarget(target.column, target.row)
        cursorController.moveLeft(cells)
        refreshPublishedState()
    }

    /**
     * Moves cursor right by [cells] within screen bounds.
     *
     * @param cells number of cells to move
     * @throws IllegalArgumentException when [cells] is negative
     * @throws IndexOutOfBoundsException when destination cell is empty while visible content exists
     */
    fun moveCursorRight(cells: Int = 1) {
        require(cells >= 0) { "Movement cells must be non-negative: $cells" }
        if (cells == 0) {
            return
        }
        val target = cursorPosition.moveRight(cells).clampTo(screenWidth, screenHeight)
        ensureNavigableCursorTarget(target.column, target.row)
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
            rowCellsProvider = { screenRow ->
                val reference = rowReferenceForScreenRow(screenRow)
                reference.lineIndex?.let { lineIndex ->
                    storage.lineSnapshot(lineIndex).drop(reference.startColumnIndex)
                }
            },
        )

    /**
     * Returns character code point at [region]/[row]/[column].
     *
     * @param region selected region
     * @param row row inside selected region
     * @param column column inside selected region
     * @return code point at target cell, or null when target screen cell is empty
     * @throws IndexOutOfBoundsException when any index is invalid for selected [region]
     */
    fun characterAt(
        region: BufferRegion,
        row: Int,
        column: Int,
    ): Int? =
        when (region) {
            BufferRegion.SCREEN -> screenCellAt(row = row, column = column).codePoint
            BufferRegion.SCROLLBACK -> scrollbackCellAt(row = row, column = column).codePoint
        }

    /**
     * Returns attributes at [region]/[row]/[column].
     *
     * Missing screen cells return default [CellAttributes].
     *
     * @param region selected region
     * @param row row inside selected region
     * @param column column inside selected region
     * @return attributes at target position
     * @throws IndexOutOfBoundsException when any index is invalid for selected [region]
     */
    fun attributesAt(
        region: BufferRegion,
        row: Int,
        column: Int,
    ): CellAttributes =
        when (region) {
            BufferRegion.SCREEN -> screenCellAt(row = row, column = column).attributes
            BufferRegion.SCROLLBACK -> scrollbackCellAt(row = row, column = column).attributes
        }

    /**
     * Returns one line as plain text from [region].
     *
     * Screen lines are rendered with fixed width [screenWidth], filling empty cells as spaces.
     * Scrollback lines are rendered with logical line width and empty cells as spaces.
     *
     * @param region selected region
     * @param row row index inside selected region
     * @return plain-text representation of selected line
     * @throws IndexOutOfBoundsException when [row] is invalid for selected [region]
     */
    fun lineAsString(
        region: BufferRegion,
        row: Int,
    ): String =
        when (region) {
            BufferRegion.SCREEN -> screenLineAsString(row)
            BufferRegion.SCROLLBACK -> scrollbackLineAsString(row)
        }

    /**
     * Returns all visible screen rows as plain text.
     *
     * Rows are joined with `\n` and there is no trailing newline.
     *
     * @return visible screen content in top-to-bottom order
     */
    fun screenContentAsString(): String =
        (0 until screenHeight)
            .joinToString(separator = "\n") { row ->
                lineAsString(region = BufferRegion.SCREEN, row = row)
            }

    /**
     * Returns scrollback and visible screen content as plain text.
     *
     * Output order is oldest scrollback first, followed by visible screen rows. Lines are joined
     * with `\n` and there is no trailing newline.
     *
     * @return scrollback and screen content in top-to-bottom order
     */
    fun screenAndScrollbackContentAsString(): String {
        val lines = mutableListOf<String>()
        for (row in 0 until viewportTopLineIndex) {
            lines += lineAsString(region = BufferRegion.SCROLLBACK, row = row)
        }
        for (row in 0 until screenHeight) {
            lines += lineAsString(region = BufferRegion.SCREEN, row = row)
        }
        return lines.joinToString(separator = "\n")
    }

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

        setCursorPositionInternal(
            column = target.column,
            row = target.row,
            requireNonEmptyTarget = false,
        )
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

        setCursorPositionInternal(
            column = target.column,
            row = target.row,
            requireNonEmptyTarget = false,
        )
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
     * Clears visible screen content while preserving scrollback history.
     *
     * This operation appends one full empty screen page, applies retention, pins viewport to
     * bottom, and resets cursor to `(0, 0)`.
     */
    fun clearScreen() {
        pinViewportToBottom()
        repeat(screenHeight) {
            storage.appendLine(BufferLine.empty())
        }
        enforceRetentionLimit()
        pinViewportToBottom()
        setCursorPositionInternal(column = 0, row = 0, requireNonEmptyTarget = false)
    }

    /**
     * Clears both visible screen and scrollback content.
     *
     * This operation resets storage to exactly [screenHeight] empty lines, pins viewport to bottom,
     * and resets cursor to `(0, 0)`.
     */
    fun clearScreenAndScrollback() {
        StorageBootstrapper.bootstrapEmptyScreen(storage = storage, screenHeight = screenHeight)
        pinViewportToBottom()
        setCursorPositionInternal(column = 0, row = 0, requireNonEmptyTarget = false)
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

    /**
     * Resizes screen dimensions in place while preserving canonical storage content.
     *
     * Cursor is clamped to new bounds. Viewport pin state is preserved: pinned viewports stay
     * pinned to bottom, and non-pinned viewports keep their top line index when still valid.
     *
     * @param screenWidth new screen width in cells
     * @param screenHeight new screen height in rows
     * @throws IllegalArgumentException when new dimensions are not positive
     */
    fun resize(
        screenWidth: Int,
        screenHeight: Int,
    ) {
        require(screenWidth > 0) { "Screen width must be positive: $screenWidth" }
        require(screenHeight > 0) { "Screen height must be positive: $screenHeight" }

        val previousCursor = cursorPosition
        val previousTopLineIndex = viewportTopLineIndex
        val wasPinnedToBottom = viewportPinnedToBottom

        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        ensureAtLeastOneStorageLine()
        enforceRetentionLimit()

        viewportController = ViewportController(storage = storage, screenHeight = this.screenHeight)
        if (wasPinnedToBottom) {
            viewportController.pinToBottom()
        } else {
            viewportController.setTopLineIndex(previousTopLineIndex.coerceIn(0, viewportController.maxTopIndex))
        }

        cursorController =
            CursorController(
                screenWidth = this.screenWidth,
                screenHeight = this.screenHeight,
                initial = previousCursor.clampTo(this.screenWidth, this.screenHeight),
                onBottomRowReached = { pinViewportToBottom() },
            )

        editingEngine = createEditingEngine()
        refreshPublishedState()
    }

    private fun screenCellAt(
        row: Int,
        column: Int,
    ): TerminalCell {
        validateScreenRow(row)
        validateScreenColumn(column)

        val reference = rowReferenceForScreenRow(row)
        val lineIndex = reference.lineIndex ?: return TerminalCell()
        val absoluteColumn = reference.startColumnIndex + column
        return storage.lineSnapshot(lineIndex).getOrNull(absoluteColumn) ?: TerminalCell()
    }

    private fun scrollbackCellAt(
        row: Int,
        column: Int,
    ): TerminalCell {
        validateScrollbackRow(row)
        val line = storage.lineSnapshot(row)
        if (column !in line.indices) {
            throw IndexOutOfBoundsException("Scrollback column $column is outside valid range 0..${line.size - 1}")
        }

        return line[column]
    }

    private fun screenLineAsString(row: Int): String {
        validateScreenRow(row)
        val builder = StringBuilder()
        for (column in 0 until screenWidth) {
            appendCodePointOrSpace(
                builder = builder,
                codePoint = characterAt(region = BufferRegion.SCREEN, row = row, column = column),
            )
        }
        return builder.toString()
    }

    private fun scrollbackLineAsString(row: Int): String {
        validateScrollbackRow(row)
        val line = storage.lineSnapshot(row)
        val builder = StringBuilder()
        line.forEach { cell ->
            appendCodePointOrSpace(builder = builder, codePoint = cell.codePoint)
        }
        return builder.toString()
    }

    private fun validateScreenRow(row: Int) {
        if (row !in 0 until screenHeight) {
            throw IndexOutOfBoundsException("Screen row $row is outside valid range 0..${screenHeight - 1}")
        }
    }

    private fun validateScreenColumn(column: Int) {
        if (column !in 0 until screenWidth) {
            throw IndexOutOfBoundsException("Screen column $column is outside valid range 0..${screenWidth - 1}")
        }
    }

    private fun validateScrollbackRow(row: Int) {
        if (row !in 0 until viewportTopLineIndex) {
            throw IndexOutOfBoundsException("Scrollback row $row is outside valid range 0..${viewportTopLineIndex - 1}")
        }
    }

    private fun appendCodePointOrSpace(
        builder: StringBuilder,
        codePoint: Int?,
    ) {
        if (codePoint == null) {
            builder.append(' ')
            return
        }
        builder.append(Character.toChars(codePoint))
    }

    private fun rowReferenceForScreenRow(screenRow: Int): WrappedViewportMapper.RowReference {
        if (screenRow !in 0 until screenHeight) {
            throw IndexOutOfBoundsException(
                "Screen row $screenRow is outside valid range 0..${screenHeight - 1}",
            )
        }

        return WrappedViewportMapper
            .rowReferences(
                storage = storage,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                topLineIndex = viewportTopLineIndex,
            )[screenRow]
    }

    /**
     * Sets cursor position with optional empty-target validation.
     *
     * @param column destination column
     * @param row destination row
     * @param requireNonEmptyTarget when true, empty targets are rejected if screen has content
     */
    private fun setCursorPositionInternal(
        column: Int,
        row: Int,
        requireNonEmptyTarget: Boolean,
    ) {
        if (requireNonEmptyTarget) {
            ensureNavigableCursorTarget(column, row)
        }
        cursorController.setPosition(column, row)
        refreshPublishedState()
    }

    /**
     * Validates that cursor target is navigable under current content constraints.
     *
     * Empty targets are allowed only when:
     * - visible screen has no content at all, or
     * - target is the insertion frontier (the cell immediately after current logical-line content
     *   in the corresponding wrapped row slice).
     *
     * @param column candidate column
     * @param row candidate row
     * @throws IndexOutOfBoundsException when target is empty while visible content exists
     */
    private fun ensureNavigableCursorTarget(
        column: Int,
        row: Int,
    ) {
        val targetCell = screenCellAt(row = row, column = column)
        if (targetCell.codePoint != null) {
            return
        }
        if (!hasAnyVisibleContent()) {
            return
        }
        if (isInsertionFrontierTarget(column = column, row = row)) {
            return
        }

        throw IndexOutOfBoundsException(
            "Cursor target ($column,$row) is empty and cannot be selected while content exists on screen.",
        )
    }

    /**
     * Returns whether [column]/[row] is the insertion frontier for its wrapped logical-line slice.
     *
     * The insertion frontier is the first cell immediately after existing logical-line content in
     * that row slice. Allowing this position enables moving back to "after last character"
     * locations after stepping left.
     */
    private fun isInsertionFrontierTarget(
        column: Int,
        row: Int,
    ): Boolean {
        val reference = rowReferenceForScreenRow(row)
        val lineIndex = reference.lineIndex ?: return false
        val lineLength = storage.lineSnapshot(lineIndex).size
        val frontierColumnInRow = lineLength - reference.startColumnIndex
        return frontierColumnInRow == column && frontierColumnInRow in 0 until screenWidth
    }

    /**
     * Returns whether any visible screen cell currently contains a code point.
     *
     * @return true when at least one visible cell is non-empty
     */
    private fun hasAnyVisibleContent(): Boolean =
        (0 until screenHeight).any { row ->
            (0 until screenWidth).any { column ->
                screenCellAt(row = row, column = column).codePoint != null
            }
        }

    private fun enforceRetentionLimit() {
        val maxStoredLines = screenHeight + scrollbackMaxLines
        while (storage.lineCount > maxStoredLines) {
            storage.removeFirstLine()
        }
    }

    /**
     * Ensures canonical storage has at least one logical line.
     *
     * Backing lines for additional visible rows are created lazily by edit operations.
     */
    private fun ensureAtLeastOneStorageLine() {
        while (storage.lineCount < 1) {
            storage.appendLine(BufferLine.empty())
        }
    }

    private fun createEditingEngine(): BufferEditingEngine =
        BufferEditingEngine(
            storage = storage,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            scrollbackMaxLines = scrollbackMaxLines,
            pinViewportToBottom = ::pinViewportToBottom,
            rowReferenceForScreenRow = ::rowReferenceForScreenRow,
        )

    private fun refreshPublishedState() {
        cursorPosition = cursorController.position
        viewportTopLineIndex = viewportController.topLineIndex
        viewportPinnedToBottom = viewportController.pinnedToBottom
    }
}
