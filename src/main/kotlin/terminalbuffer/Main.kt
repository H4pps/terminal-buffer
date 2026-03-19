package terminalbuffer

import terminalbuffer.editor.TerminalEditor
import terminalbuffer.render.AnsiTerminalRenderer
import terminalbuffer.ui.CommandTerminalUiApp
import terminalbuffer.ui.TerminalSize
import terminalbuffer.ui.TerminalSizeDetector
import terminalbuffer.ui.TerminalSizePrompter
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Application entry point.
 *
 * Starts non-fullscreen command-driven terminal UI.
 */
fun main() {
    val input = BufferedReader(InputStreamReader(System.`in`))
    val output = System.out
    val detectedSize = detectTerminalSize()
    val size = TerminalSizePrompter(input = input, output = output).prompt(initial = detectedSize)
    val renderer = AnsiTerminalRenderer()
    val buffer =
        TerminalEditor.create(
            screenWidth = size.width,
            screenHeight = size.height,
            scrollbackMaxLines = 1000,
            renderer = renderer,
        )

    val app =
        CommandTerminalUiApp(
            initialBuffer = buffer,
            renderer = renderer,
            input = input,
            output = output,
        )
    app.run()
}

/**
 * Detects terminal dimensions using `stty size` and falls back to `80x24` when detection fails.
 *
 * @return detected terminal size or fallback size
 */
private fun detectTerminalSize(): TerminalSize =
    try {
        TerminalSizeDetector().detect(defaultWidth = 10, defaultHeight = 10)
    } catch (_: Throwable) {
        TerminalSize(width = 80, height = 24)
    }
