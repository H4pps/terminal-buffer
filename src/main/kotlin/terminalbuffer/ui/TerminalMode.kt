package terminalbuffer.ui

/**
 * Runtime terminal mode controller.
 *
 * Implementations can switch stdin to raw mode while the UI loop is running and restore previous
 * settings afterwards.
 */
interface TerminalMode {
    /**
     * Executes [block] while the terminal is configured for UI input handling.
     *
     * Implementations must always restore terminal mode in a `finally` block.
     *
     * @param block operation executed in configured mode
     * @return block result
     */
    fun <T> withConfiguredMode(block: () -> T): T
}
