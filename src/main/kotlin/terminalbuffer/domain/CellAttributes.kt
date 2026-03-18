package terminalbuffer.domain

/**
 * Visual attributes of a terminal cell.
 *
 * @property foreground foreground color of the cell
 * @property background background color of the cell
 * @property bold whether the cell is rendered with bold style
 * @property italic whether the cell is rendered with italic style
 * @property underline whether the cell is rendered with underline style
 */
data class CellAttributes(
    val foreground: TerminalColor = TerminalColor.DEFAULT,
    val background: TerminalColor = TerminalColor.DEFAULT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
