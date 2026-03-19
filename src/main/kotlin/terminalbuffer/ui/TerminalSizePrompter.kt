package terminalbuffer.ui

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Limits used for startup terminal-size input validation.
 *
 * @property minWidth minimum accepted width in columns
 * @property maxWidth maximum accepted width in columns
 * @property minHeight minimum accepted height in rows
 * @property maxHeight maximum accepted height in rows
 * @throws IllegalArgumentException when limits are invalid
 */
data class TerminalSizeInputLimits(
    val minWidth: Int = 10,
    val maxWidth: Int = 300,
    val minHeight: Int = 5,
    val maxHeight: Int = 120,
) {
    init {
        require(minWidth > 0) { "Minimum width must be positive: $minWidth" }
        require(minHeight > 0) { "Minimum height must be positive: $minHeight" }
        require(maxWidth >= minWidth) {
            "Maximum width must be >= minimum width: $maxWidth < $minWidth"
        }
        require(maxHeight >= minHeight) {
            "Maximum height must be >= minimum height: $maxHeight < $minHeight"
        }
    }
}

/**
 * Interactive startup prompt for selecting terminal size before app loop starts.
 *
 * @property input reader used for line-based input
 * @property output stream used for prompt and validation messages
 * @property limits accepted numeric size limits
 */
class TerminalSizePrompter(
    private val input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val output: PrintStream = System.out,
    private val limits: TerminalSizeInputLimits = TerminalSizeInputLimits(),
) {
    /**
     * Prompts for width and height and returns selected startup size.
     *
     * Empty input keeps defaults derived from [initial], clamped to [limits].
     * End-of-input (EOF) also falls back to these defaults.
     *
     * @param initial detected size used as default suggestion
     * @return validated terminal size to use at startup
     */
    fun prompt(initial: TerminalSize): TerminalSize {
        val defaultWidth = initial.width.coerceIn(limits.minWidth, limits.maxWidth)
        val defaultHeight = initial.height.coerceIn(limits.minHeight, limits.maxHeight)

        output.println("Configure startup terminal size:")
        val width =
            promptDimension(
                name = "Width",
                defaultValue = defaultWidth,
                min = limits.minWidth,
                max = limits.maxWidth,
            )
        val height =
            promptDimension(
                name = "Height",
                defaultValue = defaultHeight,
                min = limits.minHeight,
                max = limits.maxHeight,
            )
        return TerminalSize(width = width, height = height)
    }

    /**
     * Prompts for startup scrollback maximum line count.
     *
     * @param defaultValue default scrollback maximum used for empty input/EOF
     * @param min minimum accepted value
     * @param max maximum accepted value
     * @return validated scrollback maximum line count
     * @throws IllegalArgumentException when bounds are invalid
     */
    fun promptScrollbackMaxLines(
        defaultValue: Int = 1000,
        min: Int = 0,
        max: Int = 100000,
    ): Int {
        require(min >= 0) { "Minimum scrollback must be non-negative: $min" }
        require(max >= min) { "Maximum scrollback must be >= minimum: $max < $min" }

        val normalizedDefault = defaultValue.coerceIn(min, max)
        return promptIntegerValue(
            name = "Scrollback",
            defaultValue = normalizedDefault,
            min = min,
            max = max,
        )
    }

    /**
     * Prompts for one numeric dimension and validates against [min]/[max].
     *
     * @param name display name used in prompt text
     * @param defaultValue fallback value for empty input/EOF
     * @param min minimum accepted value
     * @param max maximum accepted value
     * @return accepted value in `[min, max]`
     */
    private fun promptDimension(
        name: String,
        defaultValue: Int,
        min: Int,
        max: Int,
    ): Int =
        promptIntegerValue(
            name = name,
            defaultValue = defaultValue,
            min = min,
            max = max,
        )

    /**
     * Prompts for one integer value and validates against [min]/[max].
     *
     * @param name display name used in prompt text
     * @param defaultValue fallback value for empty input/EOF
     * @param min minimum accepted value
     * @param max maximum accepted value
     * @return accepted value in `[min, max]`
     */
    private fun promptIntegerValue(
        name: String,
        defaultValue: Int,
        min: Int,
        max: Int,
    ): Int {
        while (true) {
            output.print("$name [$min-$max] (default $defaultValue): ")
            output.flush()

            val raw = input.readLine() ?: return defaultValue
            val text = raw.trim()
            if (text.isEmpty()) {
                return defaultValue
            }

            val parsed = text.toIntOrNull()
            if (parsed == null) {
                output.println("$name must be an integer.")
                continue
            }
            if (parsed !in min..max) {
                output.println("$name must be between $min and $max.")
                continue
            }

            return parsed
        }
    }
}
