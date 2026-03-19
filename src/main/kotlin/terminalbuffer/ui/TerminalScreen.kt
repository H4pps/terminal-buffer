package terminalbuffer.ui

import java.io.PrintStream

/**
 * Terminal screen output abstraction used by the fullscreen UI runtime.
 */
interface TerminalScreen {
    /**
     * Enters fullscreen drawing mode.
     */
    fun enter()

    /**
     * Renders [content] into current screen view.
     *
     * @param content ANSI-rendered frame text
     */
    fun render(content: String)

    /**
     * Leaves fullscreen drawing mode.
     */
    fun exit()
}

/**
 * ANSI fullscreen implementation writing to [output].
 *
 * This implementation uses alternate screen buffer and cursor visibility toggles.
 *
 * @property output target stream receiving ANSI escape sequences
 */
class AnsiTerminalScreen(
    private val output: PrintStream = System.out,
) : TerminalScreen {
    /**
     * Switches to alternate screen and hides cursor.
     */
    override fun enter() {
        output.print(ALT_SCREEN_ENTER)
        output.print(CURSOR_HIDE)
        output.flush()
    }

    /**
     * Repositions cursor to top-left, prints frame, and clears remaining content below.
     *
     * @param content ANSI-rendered frame text
     */
    override fun render(content: String) {
        output.print(CURSOR_HOME)
        output.print(content)
        output.print(CLEAR_REMAINDER)
        output.flush()
    }

    /**
     * Restores cursor visibility and returns to primary screen buffer.
     */
    override fun exit() {
        output.print(CURSOR_SHOW)
        output.print(ALT_SCREEN_EXIT)
        output.flush()
    }

    private companion object {
        private const val ALT_SCREEN_ENTER: String = "\u001B[?1049h"
        private const val ALT_SCREEN_EXIT: String = "\u001B[?1049l"
        private const val CURSOR_HIDE: String = "\u001B[?25l"
        private const val CURSOR_SHOW: String = "\u001B[?25h"
        private const val CURSOR_HOME: String = "\u001B[H"
        private const val CLEAR_REMAINDER: String = "\u001B[J"
    }
}
