package terminalbuffer.ui

import terminalbuffer.contracts.TerminalBufferContract

/**
 * Result of one key dispatch step.
 */
enum class DispatchOutcome {
    /**
     * Continue loop and redraw screen.
     */
    CONTINUE_WITH_RENDER,

    /**
     * Continue loop without redraw.
     */
    CONTINUE_NO_RENDER,

    /**
     * Stop UI loop.
     */
    EXIT,
}

/**
 * Maps parsed keys to buffer operations.
 *
 * @property buffer terminal buffer contract used for mutations and cursor access
 */
class TerminalUiDispatcher(
    private val buffer: TerminalBufferContract,
) {
    /**
     * Dispatches [key] to one buffer action.
     *
     * @param key parsed input key
     * @return dispatch outcome controlling loop behavior
     */
    fun dispatch(key: InputKey): DispatchOutcome =
        when (key) {
            is InputKey.Printable -> {
                buffer.writeText(key.value.toString())
                DispatchOutcome.CONTINUE_WITH_RENDER
            }

            InputKey.ArrowUp -> {
                buffer.moveCursorUp(cells = 1)
                DispatchOutcome.CONTINUE_WITH_RENDER
            }

            InputKey.ArrowDown -> {
                buffer.moveCursorDown(cells = 1)
                DispatchOutcome.CONTINUE_WITH_RENDER
            }

            InputKey.ArrowLeft -> {
                buffer.moveCursorLeft(cells = 1)
                DispatchOutcome.CONTINUE_WITH_RENDER
            }

            InputKey.ArrowRight -> {
                buffer.moveCursorRight(cells = 1)
                DispatchOutcome.CONTINUE_WITH_RENDER
            }

            InputKey.Enter -> {
                if (buffer.cursorRow < buffer.screenHeight - 1) {
                    buffer.setCursorPosition(column = 0, row = buffer.cursorRow + 1)
                } else {
                    buffer.insertEmptyLineAtBottom()
                    buffer.setCursorPosition(column = 0, row = buffer.screenHeight - 1)
                }
                DispatchOutcome.CONTINUE_WITH_RENDER
            }

            InputKey.CtrlC,
            InputKey.Eof,
            -> DispatchOutcome.EXIT

            is InputKey.Unknown -> DispatchOutcome.CONTINUE_NO_RENDER
        }
}
