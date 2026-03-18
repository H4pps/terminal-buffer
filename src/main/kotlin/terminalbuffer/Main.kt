package terminalbuffer

import terminalbuffer.editor.TerminalEditor
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.TerminalColor

/**
 * Application entry point for local execution.
 */
fun main() {
    val editor =
        TerminalEditor.create(
            screenWidth = 20,
            screenHeight = 3,
            scrollbackMaxLines = 100,
        )
    editor.setCurrentAttributes(CellAttributes(TerminalColor.CYAN))
    editor.writeText("Hello ANSI renderer")

    println(editor.renderCurrentFrame())
}
