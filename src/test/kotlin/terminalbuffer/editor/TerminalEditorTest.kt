package terminalbuffer.editor

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalColor
import terminalbuffer.manager.BufferDataManager
import terminalbuffer.render.RenderFrame
import terminalbuffer.render.TerminalRenderer
import terminalbuffer.storage.InMemoryLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `deferred contract methods throw unsupported operation`() {
        val (editor, _, _) = createEditor()

        assertFailsWith<UnsupportedOperationException> { editor.clearScreen() }
        assertFailsWith<UnsupportedOperationException> { editor.clearScreenAndScrollback() }
        assertFailsWith<UnsupportedOperationException> { editor.characterAt(BufferRegion.SCREEN, 0, 0) }
        assertFailsWith<UnsupportedOperationException> { editor.attributesAt(BufferRegion.SCREEN, 0, 0) }
        assertFailsWith<UnsupportedOperationException> { editor.lineAsString(BufferRegion.SCREEN, 0) }
        assertFailsWith<UnsupportedOperationException> { editor.screenContentAsString() }
        assertFailsWith<UnsupportedOperationException> { editor.screenAndScrollbackContentAsString() }
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
