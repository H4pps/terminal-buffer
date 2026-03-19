package terminalbuffer.ui

/**
 * POSIX terminal mode implementation using `stty`.
 *
 * This implementation captures the current terminal mode, enables raw non-echo input for the UI
 * loop, then restores the previous mode on exit.
 *
 * @property commandRunner shell command runner used for `stty` invocations
 */
class PosixTerminalMode(
    private val commandRunner: ShellCommandRunner = ProcessShellCommandRunner(),
) : TerminalMode {
    /**
     * Runs [block] with terminal configured for raw input.
     *
     * @param block operation executed in raw mode
     * @return block result
     * @throws IllegalStateException when raw mode cannot be configured or restored
     */
    override fun <T> withConfiguredMode(block: () -> T): T {
        val previousState = captureState()
        runSttyCommand("stty -icanon -echo min 1 time 0 < /dev/tty")
        try {
            return block()
        } finally {
            runSttyCommand("stty ${shellQuote(previousState)} < /dev/tty")
        }
    }

    private fun captureState(): String {
        val result = commandRunner.run("stty -g < /dev/tty")
        if (result.exitCode != 0 || result.output.isBlank()) {
            throw IllegalStateException(
                "Fullscreen UI requires an interactive POSIX terminal. " +
                    "Unable to capture terminal mode with `stty -g < /dev/tty` " +
                    "(exit=${result.exitCode}, output=${result.output.ifBlank { "no output" }}).",
            )
        }
        return result.output.trim()
    }

    private fun runSttyCommand(command: String) {
        val result = commandRunner.run(command)
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "Failed to run stty command '$command': ${result.output.ifBlank { "no output" }}",
            )
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

/**
 * Output of one shell command execution.
 *
 * @property exitCode process exit code
 * @property output merged stdout/stderr output text
 */
data class ShellCommandResult(
    val exitCode: Int,
    val output: String,
)

/**
 * Shell command runner used by terminal infrastructure.
 */
interface ShellCommandRunner {
    /**
     * Executes shell [command] and returns process result.
     *
     * @param command shell command string
     * @return execution result containing exit code and output
     */
    fun run(command: String): ShellCommandResult
}

/**
 * Process-based shell command runner.
 */
class ProcessShellCommandRunner : ShellCommandRunner {
    /**
     * Executes [command] using `sh -c`.
     */
    override fun run(command: String): ShellCommandResult {
        val process =
            ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return ShellCommandResult(exitCode = exitCode, output = output)
    }
}
