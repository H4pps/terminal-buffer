package terminalbuffer.ui

/**
 * One terminal size snapshot.
 *
 * @property width terminal width in columns
 * @property height terminal height in rows
 */
data class TerminalSize(
    val width: Int,
    val height: Int,
)

/**
 * Detects terminal dimensions using `stty size` and falls back to defaults on failures.
 *
 * @property commandRunner shell runner used to execute `stty size`
 */
class TerminalSizeDetector(
    private val commandRunner: ShellCommandRunner = ProcessShellCommandRunner(),
) {
    /**
     * Attempts to detect current terminal width/height.
     *
     * Expected command output format: `<rows> <columns>`.
     *
     * @param defaultWidth fallback width in columns
     * @param defaultHeight fallback height in rows
     * @return detected size when parsing succeeds, otherwise fallback size
     * @throws IllegalArgumentException when defaults are not positive
     */
    fun detect(
        defaultWidth: Int = 80,
        defaultHeight: Int = 24,
    ): TerminalSize {
        require(defaultWidth > 0) { "Default width must be positive: $defaultWidth" }
        require(defaultHeight > 0) { "Default height must be positive: $defaultHeight" }

        val result = commandRunner.run("stty size < /dev/tty")
        if (result.exitCode != 0) {
            return TerminalSize(width = defaultWidth, height = defaultHeight)
        }

        val tokens = result.output.trim().split(Regex("\\s+"))
        if (tokens.size != 2) {
            return TerminalSize(width = defaultWidth, height = defaultHeight)
        }

        val rows = tokens[0].toIntOrNull()
        val columns = tokens[1].toIntOrNull()
        if (rows == null || columns == null || rows <= 0 || columns <= 0) {
            return TerminalSize(width = defaultWidth, height = defaultHeight)
        }

        return TerminalSize(width = columns, height = rows)
    }
}
