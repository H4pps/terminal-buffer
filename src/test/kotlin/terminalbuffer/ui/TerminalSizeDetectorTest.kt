package terminalbuffer.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalSizeDetectorTest {
    @Test
    fun `detect parses valid stty size output`() {
        val detector =
            TerminalSizeDetector(
                commandRunner = StaticShellCommandRunner(ShellCommandResult(exitCode = 0, output = "42 120\n")),
            )

        val size = detector.detect(defaultWidth = 80, defaultHeight = 24)

        assertEquals(TerminalSize(width = 120, height = 42), size)
    }

    @Test
    fun `detect falls back to defaults for invalid output`() {
        val detector =
            TerminalSizeDetector(
                commandRunner = StaticShellCommandRunner(ShellCommandResult(exitCode = 0, output = "not-a-size")),
            )

        val size = detector.detect(defaultWidth = 90, defaultHeight = 30)

        assertEquals(TerminalSize(width = 90, height = 30), size)
    }

    @Test
    fun `detect falls back to defaults for command failure`() {
        val detector =
            TerminalSizeDetector(
                commandRunner = StaticShellCommandRunner(ShellCommandResult(exitCode = 1, output = "failed")),
            )

        val size = detector.detect(defaultWidth = 100, defaultHeight = 40)

        assertEquals(TerminalSize(width = 100, height = 40), size)
    }

    private class StaticShellCommandRunner(
        private val result: ShellCommandResult,
    ) : ShellCommandRunner {
        override fun run(command: String): ShellCommandResult = result
    }
}
