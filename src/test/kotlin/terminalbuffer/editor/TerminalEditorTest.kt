package terminalbuffer.editor

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.domain.TerminalColor
import terminalbuffer.manager.BufferDataManager
import terminalbuffer.render.RenderFrame
import terminalbuffer.render.TerminalRenderer
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalEditorTest {
    @Test
    fun `editor delegates phase two properties and commands to manager`() {
        val (editor, manager, _) = createEditor(screenWidth = 6, screenHeight = 4)
        val custom = CellAttributes(foreground = TerminalColor.BRIGHT_GREEN, bold = true)

        assertEquals(6, editor.screenWidth)
        assertEquals(4, editor.screenHeight)
        assertEquals(100, editor.scrollbackMaxLines)

        editor.setCurrentAttributes(custom)
        editor.setCursorPosition(column = 2, row = 1)
        editor.moveCursorRight(1)
        editor.moveCursorDown(1)

        assertEquals(custom, manager.currentAttributes)
        assertEquals(CursorPosition(column = 3, row = 2), editor.cursorPosition)
        assertEquals(3, editor.cursorColumn)
        assertEquals(2, editor.cursorRow)
        assertEquals(manager.cursorPosition.column, editor.cursorColumn)
        assertEquals(manager.cursorPosition.row, editor.cursorRow)

        val editorFrame = editor.composeRenderFrame()
        val managerFrame = manager.composeRenderFrame()
        assertFrameEquals(managerFrame, editorFrame)
    }

    @Test
    fun `render current frame delegates to renderer with freshly composed frame`() {
        val (editor, _, renderer) = createEditor(screenWidth = 5, screenHeight = 3, renderOutput = "rendered")
        editor.setCursorPosition(column = 1, row = 1)

        val rendered = editor.renderCurrentFrame()

        assertEquals("rendered", rendered)
        assertEquals(1, renderer.renderCalls)
        val renderedFrame = renderer.lastFrame ?: error("Renderer should receive frame")
        val expected = editor.composeRenderFrame()
        assertFrameEquals(expected, renderedFrame)
    }

    @Test
    fun `editor delegates clear operations to manager`() {
        val (editor, manager, _) = createEditor(screenWidth = 3, screenHeight = 2)
        editor.writeText("abc")
        manager.insertEmptyLineAtBottom()

        editor.clearScreen()
        val afterClear = editor.screenContentAsString()

        editor.writeText("xy")
        editor.clearScreenAndScrollback()
        val afterClearAll = editor.screenAndScrollbackContentAsString()

        assertEquals("   \n   ", afterClear)
        assertEquals("   \n   ", afterClearAll)
    }

    @Test
    fun `editor delegates read APIs to manager`() {
        val storage = InMemoryLineStorage()
        val manager =
            BufferDataManager(
                storage = storage,
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 100,
            )
        val editor = TerminalEditor(manager = manager, renderer = CapturingRenderer(output = "ok"))
        storage.replaceLine(
            0,
            BufferLine.fromCells(
                listOf(
                    TerminalCell.fromChar('a'),
                    TerminalCell(),
                    TerminalCell.fromChar('b'),
                ),
            ),
        )
        storage.appendLine(lineOf("CD"))
        storage.appendLine(lineOf("QRS"))
        manager.setViewportTopLineIndex(1)

        assertEquals(
            manager.characterAt(BufferRegion.SCREEN, row = 0, column = 0),
            editor.characterAt(BufferRegion.SCREEN, row = 0, column = 0),
        )
        assertEquals(
            manager.attributesAt(BufferRegion.SCREEN, row = 0, column = 2),
            editor.attributesAt(BufferRegion.SCREEN, row = 0, column = 2),
        )
        assertEquals(
            manager.lineAsString(BufferRegion.SCROLLBACK, row = 0),
            editor.lineAsString(BufferRegion.SCROLLBACK, row = 0),
        )
        assertEquals(manager.screenContentAsString(), editor.screenContentAsString())
        assertEquals(manager.screenAndScrollbackContentAsString(), editor.screenAndScrollbackContentAsString())
    }

    @Test
    fun `editor delegates first phase three editing operations`() {
        val (editor, manager, _) = createEditor(screenWidth = 3, screenHeight = 2)

        editor.writeText("ab")
        editor.insertText("X")
        editor.fillCurrentLine('Q')
        editor.insertEmptyLineAtBottom()

        val editorFrame = editor.composeRenderFrame()
        val managerFrame = manager.composeRenderFrame()
        assertFrameEquals(managerFrame, editorFrame)
    }

    @Test
    fun `create factory builds editor with expected dimensions and scrollback`() {
        val editor =
            TerminalEditor.create(
                screenWidth = 7,
                screenHeight = 4,
                scrollbackMaxLines = 11,
            )

        assertEquals(7, editor.screenWidth)
        assertEquals(4, editor.screenHeight)
        assertEquals(11, editor.scrollbackMaxLines)
        assertEquals(CursorPosition(column = 0, row = 0), editor.cursorPosition)
    }

    @Test
    fun `create factory uses default ansi renderer when omitted`() {
        val editor = TerminalEditor.create(screenWidth = 3, screenHeight = 1, scrollbackMaxLines = 5)
        editor.writeText("A")

        val rendered = editor.renderCurrentFrame()

        assertTrue(rendered.contains("\u001B["))
        assertTrue(rendered.contains("A"))
    }

    @Test
    fun `create factory uses provided renderer override`() {
        val customRenderer = CapturingRenderer(output = "custom-render")
        val editor =
            TerminalEditor.create(
                screenWidth = 3,
                screenHeight = 2,
                scrollbackMaxLines = 9,
                renderer = customRenderer,
            )
        editor.writeText("Hi")

        val rendered = editor.renderCurrentFrame()

        assertEquals("custom-render", rendered)
        assertEquals(1, customRenderer.renderCalls)
    }

    @Test
    fun `resize preserves content attributes and cursor using core manager state`() {
        val customRenderer = CapturingRenderer(output = "rendered")
        val editor =
            TerminalEditor.create(
                screenWidth = 4,
                screenHeight = 2,
                scrollbackMaxLines = 5,
                renderer = customRenderer,
            )
        editor.writeText("AB  CDE")
        val customAttributes = CellAttributes(background = TerminalColor.BRIGHT_GREEN, underline = true)
        editor.setCurrentAttributes(customAttributes)
        editor.setCursorPosition(column = 2, row = 1)

        editor.resize(screenWidth = 2, screenHeight = 3)

        assertEquals(2, editor.screenWidth)
        assertEquals(3, editor.screenHeight)
        assertEquals(5, editor.scrollbackMaxLines)
        assertEquals(customAttributes, editor.currentAttributes)
        assertEquals(CursorPosition(column = 1, row = 1), editor.cursorPosition)
        assertEquals('A'.code, editor.characterAt(BufferRegion.SCREEN, row = 0, column = 0))
        assertEquals('B'.code, editor.characterAt(BufferRegion.SCREEN, row = 0, column = 1))
        assertEquals(' '.code, editor.characterAt(BufferRegion.SCREEN, row = 1, column = 0))
        assertEquals(' '.code, editor.characterAt(BufferRegion.SCREEN, row = 1, column = 1))
        assertEquals('C'.code, editor.characterAt(BufferRegion.SCREEN, row = 2, column = 0))
        assertEquals('D'.code, editor.characterAt(BufferRegion.SCREEN, row = 2, column = 1))
    }

    private fun createEditor(
        screenWidth: Int = 5,
        screenHeight: Int = 3,
        renderOutput: String = "ok",
    ): Triple<TerminalEditor, BufferDataManager, CapturingRenderer> {
        val manager =
            BufferDataManager(
                storage = InMemoryLineStorage(),
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                scrollbackMaxLines = 100,
            )
        val renderer = CapturingRenderer(renderOutput)
        val editor = TerminalEditor(manager = manager, renderer = renderer)
        return Triple(editor, manager, renderer)
    }

    private fun assertFrameEquals(
        expected: RenderFrame,
        actual: RenderFrame,
    ) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        assertEquals(expected.cursorColumn, actual.cursorColumn)
        assertEquals(expected.cursorRow, actual.cursorRow)
        assertEquals(
            expected.rows.map { row -> row.map { it.codePoint } },
            actual.rows.map { row -> row.map { it.codePoint } },
        )
    }

    private fun lineOf(text: String): BufferLine = BufferLine.fromCells(text.map { TerminalCell.fromChar(it) })

    private class CapturingRenderer(
        private val output: String,
    ) : TerminalRenderer {
        var renderCalls: Int = 0
            private set
        var lastFrame: RenderFrame? = null
            private set

        override fun render(frame: RenderFrame): String {
            renderCalls += 1
            lastFrame = frame
            return output
        }
    }
}
