package terminalbuffer.ui

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.contracts.TerminalBufferContract
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.CursorPosition
import terminalbuffer.domain.TerminalCell
import terminalbuffer.render.RenderFrame
import terminalbuffer.render.TerminalRenderer
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminalUiLifecycleTest {
    @Test
    fun `run configures mode and screen in expected order on normal exit`() {
        val events = mutableListOf<String>()
        val app =
            TerminalUiApp(
                buffer = FakeBuffer(),
                renderer = RecordingRenderer(),
                keyReader = InputKeyReader(ByteArrayInputStream(byteArrayOf(3))),
                terminalMode = RecordingTerminalMode(events),
                screen = RecordingScreen(events),
            )

        app.run()

        assertEquals(
            listOf("mode_enter", "screen_enter", "screen_render", "screen_exit", "mode_exit"),
            events,
        )
    }

    @Test
    fun `run restores screen and mode when render throws`() {
        val events = mutableListOf<String>()
        val app =
            TerminalUiApp(
                buffer = FakeBuffer(),
                renderer = RecordingRenderer(),
                keyReader = InputKeyReader(ByteArrayInputStream(byteArrayOf())),
                terminalMode = RecordingTerminalMode(events),
                screen = RecordingScreen(events, throwOnRender = true),
            )

        assertFailsWith<IllegalStateException> { app.run() }
        assertEquals(
            listOf("mode_enter", "screen_enter", "screen_render", "screen_exit", "mode_exit"),
            events,
        )
    }

    private class RecordingTerminalMode(
        private val events: MutableList<String>,
    ) : TerminalMode {
        override fun <T> withConfiguredMode(block: () -> T): T {
            events += "mode_enter"
            return try {
                block()
            } finally {
                events += "mode_exit"
            }
        }
    }

    private class RecordingScreen(
        private val events: MutableList<String>,
        private val throwOnRender: Boolean = false,
    ) : TerminalScreen {
        override fun enter() {
            events += "screen_enter"
        }

        override fun render(content: String) {
            events += "screen_render"
            if (throwOnRender) {
                throw IllegalStateException("render failure")
            }
        }

        override fun exit() {
            events += "screen_exit"
        }
    }

    private class RecordingRenderer : TerminalRenderer {
        override fun render(frame: RenderFrame): String = "frame"
    }

    private class FakeBuffer : TerminalBufferContract {
        override val screenWidth: Int = 5
        override val screenHeight: Int = 3
        override val scrollbackMaxLines: Int = 100
        override val cursorPosition: CursorPosition = CursorPosition(column = 0, row = 0)
        override val currentAttributes: CellAttributes = CellAttributes()

        override fun setCurrentAttributes(attributes: CellAttributes) = Unit

        override fun setCursorPosition(
            column: Int,
            row: Int,
        ) = Unit

        override fun moveCursorUp(cells: Int) = Unit

        override fun moveCursorDown(cells: Int) = Unit

        override fun moveCursorLeft(cells: Int) = Unit

        override fun moveCursorRight(cells: Int) = Unit

        override fun writeText(text: String) = Unit

        override fun insertText(text: String) = Unit

        override fun fillCurrentLine(character: Char?) = Unit

        override fun insertEmptyLineAtBottom() = Unit

        override fun clearScreen() = Unit

        override fun clearScreenAndScrollback() = Unit

        override fun characterAt(
            region: BufferRegion,
            row: Int,
            column: Int,
        ): Int? = null

        override fun attributesAt(
            region: BufferRegion,
            row: Int,
            column: Int,
        ): CellAttributes = CellAttributes()

        override fun lineAsString(
            region: BufferRegion,
            row: Int,
        ): String = ""

        override fun screenContentAsString(): String = ""

        override fun screenAndScrollbackContentAsString(): String = ""

        override fun composeRenderFrame(): RenderFrame {
            val rows = List(screenHeight) { List(screenWidth) { TerminalCell() } }
            return RenderFrame(
                width = screenWidth,
                height = screenHeight,
                rowsInput = rows,
                cursorColumn = cursorColumn,
                cursorRow = cursorRow,
            )
        }
    }
}
