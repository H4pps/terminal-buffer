package terminalbuffer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalColorTest {
    @Test
    fun `contains default and sixteen ansi colors`() {
        val names = TerminalColor.entries.map { it.name }.toSet()

        assertEquals(17, names.size)
        assertTrue(names.contains("DEFAULT"))
        assertTrue(names.contains("BLACK"))
        assertTrue(names.contains("RED"))
        assertTrue(names.contains("GREEN"))
        assertTrue(names.contains("YELLOW"))
        assertTrue(names.contains("BLUE"))
        assertTrue(names.contains("MAGENTA"))
        assertTrue(names.contains("CYAN"))
        assertTrue(names.contains("WHITE"))
        assertTrue(names.contains("BRIGHT_BLACK"))
        assertTrue(names.contains("BRIGHT_RED"))
        assertTrue(names.contains("BRIGHT_GREEN"))
        assertTrue(names.contains("BRIGHT_YELLOW"))
        assertTrue(names.contains("BRIGHT_BLUE"))
        assertTrue(names.contains("BRIGHT_MAGENTA"))
        assertTrue(names.contains("BRIGHT_CYAN"))
        assertTrue(names.contains("BRIGHT_WHITE"))
    }
}
