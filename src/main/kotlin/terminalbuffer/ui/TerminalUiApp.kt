package terminalbuffer.ui

import terminalbuffer.contracts.TerminalBufferContract
import terminalbuffer.render.AnsiTerminalRenderer
import terminalbuffer.render.TerminalRenderer

/**
 * Fullscreen terminal UI application loop.
 *
 * @property buffer terminal buffer used for all state mutations and frame composition
 * @property renderer renderer converting composed frames to output text
 * @property keyReader key reader parsing raw input bytes
 * @property terminalMode terminal mode manager for raw input lifecycle
 * @property screen fullscreen terminal screen renderer
 * @property dispatcher key dispatcher mapping inputs to buffer operations
 */
class TerminalUiApp(
    private val buffer: TerminalBufferContract,
    private val renderer: TerminalRenderer = AnsiTerminalRenderer(),
    private val keyReader: InputKeyReader = InputKeyReader(),
    private val terminalMode: TerminalMode = PosixTerminalMode(),
    private val screen: TerminalScreen = AnsiTerminalScreen(),
    private val dispatcher: TerminalUiDispatcher = TerminalUiDispatcher(buffer = buffer),
) {
    /**
     * Starts the fullscreen UI loop.
     *
     * The loop exits on Ctrl+C or EOF.
     */
    fun run() {
        terminalMode.withConfiguredMode {
            screen.enter()
            try {
                render()
                loop()
            } finally {
                screen.exit()
            }
        }
    }

    private fun loop() {
        while (true) {
            when (dispatcher.dispatch(keyReader.readKey())) {
                DispatchOutcome.CONTINUE_WITH_RENDER -> render()
                DispatchOutcome.CONTINUE_NO_RENDER -> Unit
                DispatchOutcome.EXIT -> return
            }
        }
    }

    private fun render() {
        screen.render(renderer.render(buffer.composeRenderFrame()))
    }
}
