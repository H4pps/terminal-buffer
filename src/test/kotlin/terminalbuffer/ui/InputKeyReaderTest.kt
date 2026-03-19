package terminalbuffer.ui

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputKeyReaderTest {
    @Test
    fun `parses printable character`() {
        val reader = readerOf('A'.code)

        assertEquals(InputKey.Printable('A'), reader.readKey())
    }

    @Test
    fun `parses enter for carriage return and line feed`() {
        val carriageReturnReader = readerOf(13)
        val lineFeedReader = readerOf(10)

        assertEquals(InputKey.Enter, carriageReturnReader.readKey())
        assertEquals(InputKey.Enter, lineFeedReader.readKey())
    }

    @Test
    fun `parses ctrl c`() {
        val reader = readerOf(3)

        assertEquals(InputKey.CtrlC, reader.readKey())
    }

    @Test
    fun `parses arrow keys from CSI and SS3 sequences`() {
        val up = readerOf(27, '['.code, 'A'.code)
        val down = readerOf(27, '['.code, 'B'.code)
        val right = readerOf(27, 'O'.code, 'C'.code)
        val left = readerOf(27, 'O'.code, 'D'.code)

        assertEquals(InputKey.ArrowUp, up.readKey())
        assertEquals(InputKey.ArrowDown, down.readKey())
        assertEquals(InputKey.ArrowRight, right.readKey())
        assertEquals(InputKey.ArrowLeft, left.readKey())
    }

    @Test
    fun `parses unknown for unsupported escape sequence`() {
        val reader = readerOf(27, '['.code, 'Z'.code)

        val key = reader.readKey()

        assertTrue(key is InputKey.Unknown)
        assertEquals(listOf(27, '['.code, 'Z'.code), key.bytes)
    }

    @Test
    fun `parses eof when stream is exhausted`() {
        val reader = InputKeyReader(ByteArrayInputStream(byteArrayOf()))

        assertEquals(InputKey.Eof, reader.readKey())
    }

    private fun readerOf(vararg values: Int): InputKeyReader {
        val bytes = values.map { it.toByte() }.toByteArray()
        return InputKeyReader(ByteArrayInputStream(bytes))
    }
}
