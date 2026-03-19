package terminalbuffer.ui

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalSizePrompterTest {
    @Test
    fun `prompt accepts valid width and height`() {
        val output = ByteArrayOutputStream()
        val prompter =
            TerminalSizePrompter(
                input = BufferedReader(StringReader("120\n40\n")),
                output = PrintStream(output),
            )

        val size = prompter.prompt(initial = TerminalSize(width = 80, height = 24))

        assertEquals(TerminalSize(width = 120, height = 40), size)
    }

    @Test
    fun `prompt uses defaults for empty lines`() {
        val output = ByteArrayOutputStream()
        val prompter =
            TerminalSizePrompter(
                input = BufferedReader(StringReader("\n\n")),
                output = PrintStream(output),
            )

        val size = prompter.prompt(initial = TerminalSize(width = 80, height = 24))

        assertEquals(TerminalSize(width = 80, height = 24), size)
    }

    @Test
    fun `prompt re-prompts on invalid values until valid input is provided`() {
        val output = ByteArrayOutputStream()
        val prompter =
            TerminalSizePrompter(
                input = BufferedReader(StringReader("wide\n999\n90\n0\n30\n")),
                output = PrintStream(output),
                limits =
                    TerminalSizeInputLimits(
                        minWidth = 10,
                        maxWidth = 200,
                        minHeight = 5,
                        maxHeight = 100,
                    ),
            )

        val size = prompter.prompt(initial = TerminalSize(width = 80, height = 24))

        assertEquals(TerminalSize(width = 90, height = 30), size)
        val text = output.toString()
        assertTrue(text.contains("Width must be an integer."))
        assertTrue(text.contains("Width must be between 10 and 200."))
        assertTrue(text.contains("Height must be between 5 and 100."))
    }

    @Test
    fun `prompt clamps out-of-range defaults before prompting`() {
        val output = ByteArrayOutputStream()
        val prompter =
            TerminalSizePrompter(
                input = BufferedReader(StringReader("\n\n")),
                output = PrintStream(output),
                limits =
                    TerminalSizeInputLimits(
                        minWidth = 20,
                        maxWidth = 30,
                        minHeight = 10,
                        maxHeight = 12,
                    ),
            )

        val size = prompter.prompt(initial = TerminalSize(width = 500, height = 1))

        assertEquals(TerminalSize(width = 30, height = 10), size)
    }
}
