package terminalbuffer.ui

import terminalbuffer.contracts.BufferRegion
import terminalbuffer.contracts.ResizableTerminalBuffer
import terminalbuffer.domain.CellAttributes
import terminalbuffer.domain.TerminalColor
import terminalbuffer.render.AnsiTerminalRenderer
import terminalbuffer.render.TerminalRenderer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Non-fullscreen command-driven terminal UI.
 *
 * This runtime does not require raw mode or alternate-screen handling. Users enter one command
 * per line and the app prints a fresh rendered frame after mutating commands.
 *
 * @property renderer renderer converting composed frames into output text
 * @property input command input reader
 * @property output output stream used for UI text
 */
class CommandTerminalUiApp(
    initialBuffer: ResizableTerminalBuffer,
    private val renderer: TerminalRenderer = AnsiTerminalRenderer(),
    private val input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val output: PrintStream = System.out,
) {
    private val emptyCellPlaceholder: Char = '.'
    private val buffer: ResizableTerminalBuffer = initialBuffer

    /**
     * Runs command loop until EOF or explicit exit command.
     */
    fun run() {
        printHelp()
        renderFrame()

        while (true) {
            output.print("cmd> ")
            output.flush()
            val line = input.readLine() ?: break

            when (handleCommand(line.trim())) {
                CommandOutcome.RENDER -> renderFrame()
                CommandOutcome.IGNORE -> Unit
                CommandOutcome.EXIT -> break
            }
        }
    }

    /**
     * Parses one textual command and dispatches it to buffer operations.
     *
     * @param commandLine full user-entered command line
     * @return outcome controlling loop continuation and redraw behavior
     */
    private fun handleCommand(commandLine: String): CommandOutcome {
        if (commandLine.isBlank()) {
            return CommandOutcome.IGNORE
        }

        val headAndTail = commandLine.split(" ", limit = 2)
        val command = headAndTail.first().lowercase()
        val tail = headAndTail.getOrNull(1).orEmpty()

        return when (command) {
            "help", "h" -> {
                printHelp()
                CommandOutcome.IGNORE
            }
            "attr" -> handleAttributesCommand(tail)
            "cursor" -> handleCursorCommand(tail)
            "up" -> moveWithAmount(tail, buffer::moveCursorUp)
            "down" -> moveWithAmount(tail, buffer::moveCursorDown)
            "left" -> moveWithAmount(tail, buffer::moveCursorLeft)
            "right" -> moveWithAmount(tail, buffer::moveCursorRight)
            "write", "w" -> {
                buffer.writeText(tail)
                CommandOutcome.RENDER
            }
            "insert", "i" -> {
                buffer.insertText(tail)
                CommandOutcome.RENDER
            }
            "fill" -> fillCurrentLine(tail)
            "enter" -> handleEnter()
            "bottom", "insert-empty-line" -> {
                buffer.insertEmptyLineAtBottom()
                CommandOutcome.RENDER
            }
            "clear" -> {
                buffer.clearScreen()
                CommandOutcome.RENDER
            }
            "clearall" -> {
                buffer.clearScreenAndScrollback()
                CommandOutcome.RENDER
            }
            "char" -> printCharacterAt(tail)
            "cellattr" -> printAttributesAt(tail)
            "line" -> printLine(tail)
            "screen" -> {
                output.println(buffer.screenContentAsString())
                CommandOutcome.IGNORE
            }
            "all" -> {
                output.println(buffer.screenAndScrollbackContentAsString())
                CommandOutcome.IGNORE
            }
            "resize" -> handleResizeCommand(tail)
            "q", "quit", "exit" -> CommandOutcome.EXIT
            else -> {
                output.println("Unknown command: '$command'. Type 'help'.")
                CommandOutcome.IGNORE
            }
        }
    }

    /**
     * Applies a cursor-move command with optional numeric amount parsing.
     *
     * @param amountText optional amount token
     * @param move buffer move operation
     * @return outcome telling caller whether to redraw
     */
    private fun moveWithAmount(
        amountText: String,
        move: (Int) -> Unit,
    ): CommandOutcome {
        val amount = amountText.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
        if (amount < 0) {
            output.println("Amount must be non-negative.")
            return CommandOutcome.IGNORE
        }

        return try {
            move(amount)
            CommandOutcome.RENDER
        } catch (error: IndexOutOfBoundsException) {
            output.println(error.message ?: "Cursor cannot move to target position.")
            CommandOutcome.IGNORE
        }
    }

    /**
     * Handles `fill` command for replacing current line with one character or empty cells.
     *
     * @param tail command tail containing `empty` or a single character
     * @return outcome telling caller whether to redraw
     */
    private fun fillCurrentLine(tail: String): CommandOutcome {
        val value = tail.trim()
        if (value.equals("empty", ignoreCase = true) || value.equals("null", ignoreCase = true)) {
            buffer.fillCurrentLine(null)
            return CommandOutcome.RENDER
        }

        if (value.isEmpty()) {
            output.println("Usage: fill <character|empty>")
            return CommandOutcome.IGNORE
        }

        val codePointCount = value.codePointCount(0, value.length)
        if (codePointCount != 1) {
            output.println("Fill accepts exactly one character or 'empty'.")
            return CommandOutcome.IGNORE
        }

        buffer.fillCurrentLine(value.first())
        return CommandOutcome.RENDER
    }

    /**
     * Handles `cursor` subcommands.
     *
     * Supported forms:
     * - `cursor get`
     * - `cursor set <column> <row>`
     *
     * @param tail command tail
     * @return outcome controlling redraw behavior
     */
    private fun handleCursorCommand(tail: String): CommandOutcome {
        val tokens = tail.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens[0] == "get") {
            output.println(
                "cursor=(${buffer.cursorColumn},${buffer.cursorRow}) " +
                    "size=${buffer.screenWidth}x${buffer.screenHeight}",
            )
            return CommandOutcome.IGNORE
        }

        if (tokens[0] != "set" || tokens.size != 3) {
            output.println("Usage: cursor get | cursor set <column> <row>")
            return CommandOutcome.IGNORE
        }

        val column = tokens[1].toIntOrNull()
        val row = tokens[2].toIntOrNull()
        if (column == null || row == null) {
            output.println("Cursor values must be integers.")
            return CommandOutcome.IGNORE
        }

        return try {
            buffer.setCursorPosition(column = column, row = row)
            CommandOutcome.RENDER
        } catch (error: IndexOutOfBoundsException) {
            output.println(error.message ?: "Cursor position is out of bounds.")
            CommandOutcome.IGNORE
        }
    }

    /**
     * Handles newline behavior used by `enter` command.
     *
     * @return outcome instructing caller to redraw
     */
    private fun handleEnter(): CommandOutcome {
        val targetRow =
            if (buffer.cursorRow < buffer.screenHeight - 1) {
                buffer.cursorRow + 1
            } else {
                buffer.screenHeight - 1
            }

        return try {
            if (buffer.cursorRow < buffer.screenHeight - 1) {
                try {
                    buffer.setCursorPosition(column = 0, row = targetRow)
                } catch (_: IndexOutOfBoundsException) {
                    buffer.insertEmptyLineAtBottom()
                    buffer.setCursorPosition(column = 0, row = targetRow)
                }
            } else {
                buffer.insertEmptyLineAtBottom()
                buffer.setCursorPosition(column = 0, row = targetRow)
            }
            CommandOutcome.RENDER
        } catch (error: IndexOutOfBoundsException) {
            output.println(error.message ?: "Enter cannot move cursor to target row.")
            CommandOutcome.IGNORE
        }
    }

    /**
     * Handles `attr` subcommands.
     *
     * Supported forms:
     * - `attr get`
     * - `attr set <foreground> <background> <bold> <italic> <underline>`
     *
     * @param tail command tail
     * @return outcome controlling redraw behavior
     */
    private fun handleAttributesCommand(tail: String): CommandOutcome {
        val tokens = tail.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens[0] == "get") {
            output.println("attributes=${buffer.currentAttributes}")
            return CommandOutcome.IGNORE
        }

        if (tokens[0] != "set" || tokens.size != 6) {
            output.println("Usage: attr get | attr set <fg> <bg> <bold> <italic> <underline>")
            output.println("Example: attr set bright_green default true false false")
            return CommandOutcome.IGNORE
        }

        val foreground = parseColor(tokens[1])
        val background = parseColor(tokens[2])
        val bold = parseBoolean(tokens[3])
        val italic = parseBoolean(tokens[4])
        val underline = parseBoolean(tokens[5])

        if (foreground == null || background == null || bold == null || italic == null || underline == null) {
            output.println("Invalid attr values. Colors must match TerminalColor names.")
            return CommandOutcome.IGNORE
        }

        buffer.setCurrentAttributes(
            CellAttributes(
                foreground = foreground,
                background = background,
                bold = bold,
                italic = italic,
                underline = underline,
            ),
        )
        return CommandOutcome.IGNORE
    }

    /**
     * Prints one character lookup result for `char <region> <row> <column>`.
     *
     * @param tail command tail
     * @return ignore outcome after printing result or error
     */
    private fun printCharacterAt(tail: String): CommandOutcome {
        val parsed = parseRegionRowColumn(tail) ?: return CommandOutcome.IGNORE
        val (region, row, column) = parsed
        return try {
            val codePoint = buffer.characterAt(region = region, row = row, column = column)
            output.println("char=$codePoint")
            CommandOutcome.IGNORE
        } catch (error: IndexOutOfBoundsException) {
            output.println(error.message ?: "Index is out of bounds.")
            CommandOutcome.IGNORE
        }
    }

    /**
     * Prints one cell-attributes lookup result for `cellattr <region> <row> <column>`.
     *
     * @param tail command tail
     * @return ignore outcome after printing result or error
     */
    private fun printAttributesAt(tail: String): CommandOutcome {
        val parsed = parseRegionRowColumn(tail) ?: return CommandOutcome.IGNORE
        val (region, row, column) = parsed
        return try {
            val attributes = buffer.attributesAt(region = region, row = row, column = column)
            output.println("attributes=$attributes")
            CommandOutcome.IGNORE
        } catch (error: IndexOutOfBoundsException) {
            output.println(error.message ?: "Index is out of bounds.")
            CommandOutcome.IGNORE
        }
    }

    /**
     * Prints one line lookup result for `line <region> <row>`.
     *
     * @param tail command tail
     * @return ignore outcome after printing result or error
     */
    private fun printLine(tail: String): CommandOutcome {
        val tokens = tail.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size != 2) {
            output.println("Usage: line <screen|scrollback> <row>")
            return CommandOutcome.IGNORE
        }

        val region = parseRegion(tokens[0])
        val row = tokens[1].toIntOrNull()
        if (region == null || row == null) {
            output.println("Usage: line <screen|scrollback> <row>")
            return CommandOutcome.IGNORE
        }

        return try {
            output.println(buffer.lineAsString(region = region, row = row))
            CommandOutcome.IGNORE
        } catch (error: IndexOutOfBoundsException) {
            output.println(error.message ?: "Index is out of bounds.")
            CommandOutcome.IGNORE
        }
    }

    /**
     * Handles `resize <width> <height>`.
     *
     * Resize applies in-place core buffer resize.
     *
     * @param tail command tail
     * @return render outcome when resize succeeds
     */
    private fun handleResizeCommand(tail: String): CommandOutcome {
        val tokens = tail.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size != 2) {
            output.println("Usage: resize <width> <height>")
            return CommandOutcome.IGNORE
        }

        val width = tokens[0].toIntOrNull()
        val height = tokens[1].toIntOrNull()
        if (width == null || height == null || width <= 0 || height <= 0) {
            output.println("Resize values must be positive integers.")
            return CommandOutcome.IGNORE
        }

        buffer.resize(screenWidth = width, screenHeight = height)
        return CommandOutcome.RENDER
    }

    /**
     * Parses `region row column` tuple for read commands.
     *
     * @param tail command tail
     * @return parsed tuple, or null when input is invalid
     */
    private fun parseRegionRowColumn(tail: String): Triple<BufferRegion, Int, Int>? {
        val tokens = tail.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size != 3) {
            output.println("Usage: <command> <screen|scrollback> <row> <column>")
            return null
        }

        val region = parseRegion(tokens[0])
        val row = tokens[1].toIntOrNull()
        val column = tokens[2].toIntOrNull()
        if (region == null || row == null || column == null) {
            output.println("Usage: <command> <screen|scrollback> <row> <column>")
            return null
        }

        return Triple(region, row, column)
    }

    /**
     * Parses buffer region token.
     *
     * @param value textual region value
     * @return parsed region or null when unsupported
     */
    private fun parseRegion(value: String): BufferRegion? =
        when (value.lowercase()) {
            "screen" -> BufferRegion.SCREEN
            "scrollback" -> BufferRegion.SCROLLBACK
            else -> null
        }

    /**
     * Parses one terminal color token.
     *
     * @param value textual color name
     * @return parsed color or null when unsupported
     */
    private fun parseColor(value: String): TerminalColor? =
        TerminalColor.entries.firstOrNull { color -> color.name.equals(value, ignoreCase = true) }

    /**
     * Parses one boolean token.
     *
     * @param value textual boolean value
     * @return parsed boolean or null when unsupported
     */
    private fun parseBoolean(value: String): Boolean? =
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

    /**
     * Prints the latest rendered frame inside a full ASCII border plus cursor metadata.
     */
    private fun renderFrame() {
        val frameOutput = renderer.render(buffer.composeRenderFrame())
        output.println()
        printBorderedFrame(frameOutput)
        output.println(
            "cursor=(${buffer.cursorColumn},${buffer.cursorRow}) " +
                "size=${buffer.screenWidth}x${buffer.screenHeight}",
        )
    }

    /**
     * Renders [frameOutput] inside a full border using current screen width.
     *
     * @param frameOutput multi-line frame output to render
     */
    private fun printBorderedFrame(frameOutput: String) {
        val lines = frameOutput.split('\n')
        val width = buffer.screenWidth
        val labels = buildLineLabels(lines.size)
        val numberWidth = maxOf(1, labels.maxOfOrNull { it.length } ?: 1)
        val horizontal = "+${"-".repeat(numberWidth + 1)}+${"-".repeat(width)}+"

        output.println(horizontal)
        lines.forEachIndexed { row, line ->
            output.print("|")
            output.print(labels[row].padStart(numberWidth))
            output.print(" ")
            output.print("|")
            output.print(displayLine(line))
            output.println("|")
        }
        output.println(horizontal)
    }

    /**
     * Converts renderer output into command-UI row text.
     *
     * Spaces are shown as dots to make empty cells visible inside bordered output.
     *
     * @param line one rendered row
     * @return row with visible empty-cell placeholders
     */
    private fun displayLine(line: String): String = line.replace(' ', emptyCellPlaceholder)

    /**
     * Builds visible row labels using actual logical line indices.
     *
     * A logical line number is printed only on the first visible row of that logical line.
     * Continuation rows (wrapping) are rendered with an empty label cell.
     *
     * @param rowCount number of visible rows to label
     * @return row labels in visual order
     */
    private fun buildLineLabels(rowCount: Int): List<String> {
        val labels = mutableListOf<String>()
        var previousIndex: Int? = null
        repeat(rowCount) { row ->
            val index = buffer.actualLineIndexForScreenRow(row)
            val label =
                when {
                    index == null -> "-"
                    index == previousIndex -> ""
                    else -> index.toString()
                }
            labels += label
            previousIndex = index
        }
        return labels
    }

    /**
     * Prints command reference for interactive users.
     */
    private fun printHelp() {
        output.println("Commands:")
        output.println("  attr get | attr set <fg> <bg> <bold> <italic> <underline> # get/set current attributes")
        output.println("  cursor get | cursor set <column> <row>    # print or set cursor position")
        output.println("  up [n], down [n], left [n], right [n]     # move cursor by n (default 1)")
        output.println("  write <text>              | w <text>      # overwrite text at cursor")
        output.println("  insert <text>             | i <text>      # insert text at cursor and shift right")
        output.println("  fill <character|empty>                    # replace current line with one char or empty")
        output.println("  enter                                      # newline; scroll when on bottom row")
        output.println("  bottom                    | insert-empty-line # append empty line at bottom")
        output.println("  clear                                      # clear visible screen only")
        output.println("  clearall                                   # clear screen and scrollback")
        output.println("  char <screen|scrollback> <row> <column>   # print code point at cell")
        output.println("  cellattr <screen|scrollback> <row> <column> # print attributes at cell")
        output.println("  line <screen|scrollback> <row>            # print one line as text")
        output.println("  screen                                     # print visible screen content")
        output.println("  all                                        # print screen + scrollback content")
        output.println("  resize <width> <height>                    # resize in place")
        output.println("  help | h                                   # show this command reference")
        output.println("  quit | q | exit                            # stop the UI loop")
        output.println()
    }

    /**
     * Command dispatch outcome.
     */
    private enum class CommandOutcome {
        /**
         * Redraw frame after command.
         */
        RENDER,

        /**
         * Do not redraw frame.
         */
        IGNORE,

        /**
         * Exit UI loop.
         */
        EXIT,
    }
}
