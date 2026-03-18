package terminalbuffer.render

import terminalbuffer.domain.TerminalCell
import terminalbuffer.domain.TerminalColor

/**
 * ANSI renderer implementation for immutable [RenderFrame] values.
 *
 * Output guarantees:
 * - Exactly [RenderFrame.height] lines joined with `\n` (no trailing newline)
 * - Exactly [RenderFrame.width] rendered cells per line
 * - Empty cells (`codePoint == null`) rendered as spaces
 * - Cursor cell rendered with reverse-video (`SGR 7`)
 * - `SGR 0` emitted at each row end and once again at final output end
 */
class AnsiTerminalRenderer : TerminalRenderer {
    /**
     * Renders [frame] to an ANSI-styled string.
     *
     * @param frame immutable frame to render
     * @return ANSI-rendered terminal content with row separators and reset sequences
     */
    override fun render(frame: RenderFrame): String {
        val rows = frame.rows
        val builder = StringBuilder()

        rows.forEachIndexed { rowIndex, row ->
            var activeStyle = StyleState.default()
            row.forEachIndexed { columnIndex, cell ->
                val targetStyle =
                    targetStyle(
                        cell = cell,
                        cursorCell = rowIndex == frame.cursorRow && columnIndex == frame.cursorColumn,
                    )
                emitStyleTransition(builder = builder, from = activeStyle, to = targetStyle)
                builder.append(cellText(cell))
                activeStyle = targetStyle
            }

            builder.append(SGR_RESET)
            if (rowIndex < frame.height - 1) {
                builder.append('\n')
            }
        }

        builder.append(SGR_RESET)
        return builder.toString()
    }

    /**
     * Builds target style state for [cell], including cursor reverse-video flag.
     */
    private fun targetStyle(
        cell: TerminalCell,
        cursorCell: Boolean,
    ): StyleState {
        val attributes = cell.attributes
        return StyleState(
            foreground = attributes.foreground,
            background = attributes.background,
            bold = attributes.bold,
            italic = attributes.italic,
            underline = attributes.underline,
            reverse = cursorCell,
        )
    }

    /**
     * Emits the minimal ANSI transition needed from [from] to [to].
     */
    private fun emitStyleTransition(
        builder: StringBuilder,
        from: StyleState,
        to: StyleState,
    ) {
        if (from == to) {
            return
        }

        val codes = mutableListOf<String>()
        if (from.bold != to.bold) {
            codes += if (to.bold) "1" else "22"
        }
        if (from.italic != to.italic) {
            codes += if (to.italic) "3" else "23"
        }
        if (from.underline != to.underline) {
            codes += if (to.underline) "4" else "24"
        }
        if (from.reverse != to.reverse) {
            codes += if (to.reverse) "7" else "27"
        }
        if (from.foreground != to.foreground) {
            codes += foregroundCode(to.foreground).toString()
        }
        if (from.background != to.background) {
            codes += backgroundCode(to.background).toString()
        }

        appendSgr(builder, codes)
    }

    /**
     * Converts one terminal [cell] into printable text.
     */
    private fun cellText(cell: TerminalCell): String {
        val codePoint = cell.codePoint ?: SPACE_CODE_POINT
        return String(Character.toChars(codePoint))
    }

    /**
     * Returns ANSI foreground SGR code for [color].
     */
    private fun foregroundCode(color: TerminalColor): Int =
        colorSgrCode(
            color = color,
            defaultCode = 39,
            standardBase = 30,
            brightBase = 90,
        )

    /**
     * Returns ANSI background SGR code for [color].
     */
    private fun backgroundCode(color: TerminalColor): Int =
        colorSgrCode(
            color = color,
            defaultCode = 49,
            standardBase = 40,
            brightBase = 100,
        )

    /**
     * Resolves one ANSI color SGR code for [color].
     *
     * @param defaultCode SGR code representing default terminal color
     * @param standardBase SGR base for standard colors (`0..7` added)
     * @param brightBase SGR base for bright colors (`0..7` added)
     */
    private fun colorSgrCode(
        color: TerminalColor,
        defaultCode: Int,
        standardBase: Int,
        brightBase: Int,
    ): Int {
        if (color == TerminalColor.DEFAULT) {
            return defaultCode
        }

        val indexed = requireNotNull(ANSI_COLOR_INDEX[color]) { "Unsupported ANSI color: $color" }
        val base = if (indexed.bright) brightBase else standardBase
        return base + indexed.index
    }

    /**
     * Appends one ANSI SGR escape sequence for [codes].
     */
    private fun appendSgr(
        builder: StringBuilder,
        codes: List<String>,
    ) {
        if (codes.isEmpty()) {
            return
        }
        builder.append("\u001B[")
        builder.append(codes.joinToString(";"))
        builder.append('m')
    }

    /**
     * Effective render style for one cell position.
     */
    private data class StyleState(
        val foreground: TerminalColor,
        val background: TerminalColor,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val reverse: Boolean,
    ) {
        companion object {
            /**
             * Returns default terminal style without cursor highlighting.
             */
            fun default(): StyleState =
                StyleState(
                    foreground = TerminalColor.DEFAULT,
                    background = TerminalColor.DEFAULT,
                    bold = false,
                    italic = false,
                    underline = false,
                    reverse = false,
                )
        }
    }

    /**
     * ANSI palette metadata for one non-default [TerminalColor].
     */
    private data class IndexedAnsiColor(
        val index: Int,
        val bright: Boolean,
    )

    private companion object {
        private const val SPACE_CODE_POINT: Int = 32
        private const val SGR_RESET: String = "\u001B[0m"
        private val ANSI_COLOR_INDEX: Map<TerminalColor, IndexedAnsiColor> =
            mapOf(
                TerminalColor.BLACK to IndexedAnsiColor(index = 0, bright = false),
                TerminalColor.RED to IndexedAnsiColor(index = 1, bright = false),
                TerminalColor.GREEN to IndexedAnsiColor(index = 2, bright = false),
                TerminalColor.YELLOW to IndexedAnsiColor(index = 3, bright = false),
                TerminalColor.BLUE to IndexedAnsiColor(index = 4, bright = false),
                TerminalColor.MAGENTA to IndexedAnsiColor(index = 5, bright = false),
                TerminalColor.CYAN to IndexedAnsiColor(index = 6, bright = false),
                TerminalColor.WHITE to IndexedAnsiColor(index = 7, bright = false),
                TerminalColor.BRIGHT_BLACK to IndexedAnsiColor(index = 0, bright = true),
                TerminalColor.BRIGHT_RED to IndexedAnsiColor(index = 1, bright = true),
                TerminalColor.BRIGHT_GREEN to IndexedAnsiColor(index = 2, bright = true),
                TerminalColor.BRIGHT_YELLOW to IndexedAnsiColor(index = 3, bright = true),
                TerminalColor.BRIGHT_BLUE to IndexedAnsiColor(index = 4, bright = true),
                TerminalColor.BRIGHT_MAGENTA to IndexedAnsiColor(index = 5, bright = true),
                TerminalColor.BRIGHT_CYAN to IndexedAnsiColor(index = 6, bright = true),
                TerminalColor.BRIGHT_WHITE to IndexedAnsiColor(index = 7, bright = true),
            )
    }
}
