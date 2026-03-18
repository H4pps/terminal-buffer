package terminalbuffer.contracts

import terminalbuffer.render.RenderFrame
import terminalbuffer.render.TerminalRenderer
import terminalbuffer.storage.InMemoryLineStorage
import terminalbuffer.storage.MutableLineStorage
import terminalbuffer.storage.ReadOnlyLineStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureInvariantsTest {
    @Test
    fun `storage contracts remain width height and viewport free`() {
        val readOnlyMethodNames =
            ReadOnlyLineStorage::class.java.declaredMethods
                .map { it.name }
                .toSet()
        val mutableMethodNames =
            MutableLineStorage::class.java.declaredMethods
                .map { it.name }
                .toSet()

        assertTrue("getLineCount" in readOnlyMethodNames)
        assertTrue("lineSnapshot" in readOnlyMethodNames)

        assertFalse("setCursorPosition" in mutableMethodNames)
        assertFalse("moveCursorUp" in mutableMethodNames)
        assertFalse("moveCursorDown" in mutableMethodNames)
        assertFalse("moveCursorLeft" in mutableMethodNames)
        assertFalse("moveCursorRight" in mutableMethodNames)
    }

    @Test
    fun `renderer contract accepts render frame only`() {
        val renderMethod = TerminalRenderer::class.java.declaredMethods.single { it.name == "render" }
        val parameterTypes = renderMethod.parameterTypes.toList()

        assertEquals(1, parameterTypes.size)
        assertEquals(RenderFrame::class.java, parameterTypes.single())
    }

    @Test
    fun `storage implementation remains reflow agnostic by API shape`() {
        val functionNames =
            InMemoryLineStorage::class.java.declaredMethods
                .map { it.name }
                .toSet()

        assertTrue("lineSnapshot" in functionNames)
        assertTrue("appendLine" in functionNames)
        assertTrue("insertLine" in functionNames)
        assertTrue("replaceLine" in functionNames)
        assertTrue("mutableLine" in functionNames)
        assertTrue("removeFirstLine" in functionNames)
        assertTrue("clear" in functionNames)

        assertFalse("setScreenSize" in functionNames)
        assertFalse("wrapToWidth" in functionNames)
        assertFalse("reflow" in functionNames)
        assertFalse("setViewportOffset" in functionNames)
    }
}
