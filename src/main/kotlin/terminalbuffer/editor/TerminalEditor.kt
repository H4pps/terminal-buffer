package terminalbuffer.editor

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.contracts.TerminalBufferContract
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.manager.BufferDataManager
import terminalbuffer.render.AnsiTerminalRenderer
import terminalbuffer.render.RenderFrame
import terminalbuffer.render.TerminalRenderer
import terminalbuffer.storage.InMemoryLineStorage

/**
 * Single public entry point that orchestrates manager mutation/read operations and rendering.
 *
 * This editor keeps renderer decoupled from manager internals by always rendering through a
 * composed [RenderFrame].
 *
 * @property manager mutable state owner for cursor, attributes, viewport, and storage-backed frame composition
 * @property renderer pure renderer consuming composed frames only
 */
class TerminalEditor(
    private val manager: BufferDataManager,
    private val renderer: TerminalRenderer,
) : TerminalBufferContract {
    override val screenWidth: Int
        get() = manager.screenWidth

    override val screenHeight: Int
        get() = manager.screenHeight

    override val scrollbackMaxLines: Int
        get() = manager.scrollbackMaxLines

    override val cursorPosition: CursorPosition
        get() = manager.cursorPosition

    override val currentAttributes: CellAttributes
        get() = manager.currentAttributes

    override fun setCurrentAttributes(attributes: CellAttributes) {
        manager.setCurrentAttributes(attributes)
    }

    override fun setCursorPosition(
        column: Int,
        row: Int,
    ) {
        manager.setCursorPosition(column, row)
    }

    override fun moveCursorUp(cells: Int) {
        manager.moveCursorUp(cells)
    }

    override fun moveCursorDown(cells: Int) {
        manager.moveCursorDown(cells)
    }

    override fun moveCursorLeft(cells: Int) {
        manager.moveCursorLeft(cells)
    }

    override fun moveCursorRight(cells: Int) {
        manager.moveCursorRight(cells)
    }

    override fun writeText(text: String) {
        manager.writeText(text)
    }

    override fun insertText(text: String) {
        manager.insertText(text)
    }

    override fun fillCurrentLine(character: Char?) {
        manager.fillCurrentLine(character)
    }

    override fun insertEmptyLineAtBottom() {
        manager.insertEmptyLineAtBottom()
    }

    override fun clearScreen() = unsupported("clearScreen")

    override fun clearScreenAndScrollback() = unsupported("clearScreenAndScrollback")

    override fun characterAt(
        region: BufferRegion,
        row: Int,
        column: Int,
    ): Int? = unsupported("characterAt")

    override fun attributesAt(
        region: BufferRegion,
        row: Int,
        column: Int,
    ): CellAttributes = unsupported("attributesAt")

    override fun lineAsString(
        region: BufferRegion,
        row: Int,
    ): String = unsupported("lineAsString")

    override fun screenContentAsString(): String = unsupported("screenContentAsString")

    override fun screenAndScrollbackContentAsString(): String = unsupported("screenAndScrollbackContentAsString")

    override fun composeRenderFrame(): RenderFrame = manager.composeRenderFrame()

    /**
     * Renders the current manager state by composing a fresh frame and passing it to renderer.
     *
     * @return renderer output for current frame state
     */
    fun renderCurrentFrame(): String = renderer.render(composeRenderFrame())

    private fun unsupported(operation: String): Nothing = throw UnsupportedOperationException("$operation is not available in Phase 2.6")

    companion object {
        /**
         * Creates a default editor wiring with in-memory storage and ANSI renderer.
         *
         * @param screenWidth configured screen width in cells, must be positive
         * @param screenHeight configured screen height in rows, must be positive
         * @param scrollbackMaxLines maximum retained scrollback lines, must be non-negative
         * @param renderer renderer used by [renderCurrentFrame], defaults to [AnsiTerminalRenderer]
         * @return fully constructed editor instance
         */
        fun create(
            screenWidth: Int,
            screenHeight: Int,
            scrollbackMaxLines: Int,
            renderer: TerminalRenderer = AnsiTerminalRenderer(),
        ): TerminalEditor {
            val manager =
                BufferDataManager(
                    storage = InMemoryLineStorage(),
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    scrollbackMaxLines = scrollbackMaxLines,
                )
            return TerminalEditor(manager = manager, renderer = renderer)
        }
    }
}
