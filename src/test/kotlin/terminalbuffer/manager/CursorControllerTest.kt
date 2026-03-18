package terminalbuffer.manager

import terminalbuffer.domain.CursorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorControllerTest {
    @Test
    fun `set position updates cursor within bounds`() {
        var callbackCount = 0
        val controller =
            CursorController(
                screenWidth = 5,
                screenHeight = 3,
                initial = CursorPosition(column = 0, row = 0),
                onBottomRowReached = { callbackCount++ },
            )

        controller.setPosition(column = 4, row = 1)

        assertEquals(CursorPosition(column = 4, row = 1), controller.position)
        assertEquals(0, callbackCount)
    }

    @Test
    fun `set position validates bounds`() {
        val controller = CursorController(screenWidth = 5, screenHeight = 3)

        assertFailsWith<IndexOutOfBoundsException> { controller.setPosition(-1, 0) }
        assertFailsWith<IndexOutOfBoundsException> { controller.setPosition(5, 0) }
        assertFailsWith<IndexOutOfBoundsException> { controller.setPosition(0, -1) }
        assertFailsWith<IndexOutOfBoundsException> { controller.setPosition(0, 3) }
    }

    @Test
    fun `move methods clamp inside bounds and reject negative steps`() {
        val controller =
            CursorController(
                screenWidth = 5,
                screenHeight = 3,
                initial = CursorPosition(column = 2, row = 1),
            )

        controller.moveLeft(20)
        assertEquals(CursorPosition(column = 0, row = 1), controller.position)
        controller.moveUp(20)
        assertEquals(CursorPosition(column = 0, row = 0), controller.position)
        controller.moveRight(20)
        assertEquals(CursorPosition(column = 4, row = 0), controller.position)
        controller.moveDown(20)
        assertEquals(CursorPosition(column = 4, row = 2), controller.position)

        assertFailsWith<IllegalArgumentException> { controller.moveUp(-1) }
        assertFailsWith<IllegalArgumentException> { controller.moveDown(-1) }
        assertFailsWith<IllegalArgumentException> { controller.moveLeft(-1) }
        assertFailsWith<IllegalArgumentException> { controller.moveRight(-1) }
    }

    @Test
    fun `bottom row callback triggers only when landing on bottom row`() {
        var callbackCount = 0
        val controller =
            CursorController(
                screenWidth = 5,
                screenHeight = 3,
                initial = CursorPosition(column = 0, row = 0),
                onBottomRowReached = { callbackCount++ },
            )

        controller.moveDown(1)
        assertFalse(callbackCount > 0)

        controller.moveDown(1)
        assertTrue(callbackCount > 0)

        controller.moveLeft(1)
        assertTrue(callbackCount > 0)
    }
}
