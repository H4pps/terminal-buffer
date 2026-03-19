package terminalbuffer.ui

import java.io.InputStream

/**
 * Parsed key events consumed by the fullscreen terminal UI loop.
 */
sealed interface InputKey {
    /**
     * One printable ASCII character.
     *
     * @property value typed character value
     */
    data class Printable(
        val value: Char,
    ) : InputKey

    /**
     * Arrow up key.
     */
    data object ArrowUp : InputKey

    /**
     * Arrow down key.
     */
    data object ArrowDown : InputKey

    /**
     * Arrow left key.
     */
    data object ArrowLeft : InputKey

    /**
     * Arrow right key.
     */
    data object ArrowRight : InputKey

    /**
     * Enter key.
     */
    data object Enter : InputKey

    /**
     * Ctrl+C key combination.
     */
    data object CtrlC : InputKey

    /**
     * End-of-input marker.
     */
    data object Eof : InputKey

    /**
     * Unrecognized input bytes.
     *
     * @property bytes raw bytes that could not be parsed into a known key
     */
    data class Unknown(
        val bytes: List<Int>,
    ) : InputKey
}

/**
 * Reads raw bytes from [input] and parses them into [InputKey] events.
 *
 * Supported keys:
 * - Printable ASCII (`32..126`)
 * - Enter (`\r` and `\n`)
 * - Ctrl+C (`3`)
 * - ANSI arrow sequences (`ESC [ A/B/C/D` and `ESC O A/B/C/D`)
 *
 * @property input source stream for raw key bytes
 */
class InputKeyReader(
    private val input: InputStream = System.`in`,
) {
    /**
     * Reads one key event from [input].
     *
     * @return parsed key value
     */
    fun readKey(): InputKey {
        val first = input.read()
        if (first == EOF) {
            return InputKey.Eof
        }

        return when (first) {
            CTRL_C -> InputKey.CtrlC
            CARRIAGE_RETURN, LINE_FEED -> InputKey.Enter
            ESC -> readEscapeSequence()
            in ASCII_PRINTABLE_RANGE -> InputKey.Printable(first.toChar())
            else -> InputKey.Unknown(bytes = listOf(first))
        }
    }

    private fun readEscapeSequence(): InputKey {
        val second = input.read()
        if (second == EOF) {
            return InputKey.Unknown(bytes = listOf(ESC))
        }

        if (second != BRACKET && second != O_CAPITAL) {
            return InputKey.Unknown(bytes = listOf(ESC, second))
        }

        val third = input.read()
        if (third == EOF) {
            return InputKey.Unknown(bytes = listOf(ESC, second))
        }

        return when (third) {
            ARROW_UP -> InputKey.ArrowUp
            ARROW_DOWN -> InputKey.ArrowDown
            ARROW_RIGHT -> InputKey.ArrowRight
            ARROW_LEFT -> InputKey.ArrowLeft
            else -> InputKey.Unknown(bytes = listOf(ESC, second, third))
        }
    }

    private companion object {
        private const val EOF: Int = -1
        private const val CTRL_C: Int = 3
        private const val CARRIAGE_RETURN: Int = 13
        private const val LINE_FEED: Int = 10
        private const val ESC: Int = 27
        private const val BRACKET: Int = '['.code
        private const val O_CAPITAL: Int = 'O'.code
        private const val ARROW_UP: Int = 'A'.code
        private const val ARROW_DOWN: Int = 'B'.code
        private const val ARROW_RIGHT: Int = 'C'.code
        private const val ARROW_LEFT: Int = 'D'.code
        private val ASCII_PRINTABLE_RANGE: IntRange = 32..126
    }
}
