package terminalbuffer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CellAttributesTest {
    @Test
    fun `uses expected defaults`() {
        val attributes = CellAttributes()

        assertEquals(TerminalColor.DEFAULT, attributes.foreground)
        assertEquals(TerminalColor.DEFAULT, attributes.background)
        assertFalse(attributes.bold)
        assertFalse(attributes.italic)
        assertFalse(attributes.underline)
    }

    @Test
    fun `stores explicit custom values`() {
        val attributes =
            CellAttributes(
                foreground = TerminalColor.BRIGHT_GREEN,
                background = TerminalColor.BLUE,
                bold = true,
                italic = true,
                underline = true,
            )

        assertEquals(TerminalColor.BRIGHT_GREEN, attributes.foreground)
        assertEquals(TerminalColor.BLUE, attributes.background)
        assertTrue(attributes.bold)
        assertTrue(attributes.italic)
        assertTrue(attributes.underline)
    }
}
