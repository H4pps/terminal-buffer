package terminalbuffer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CursorPositionTest {
    @Test
    fun `constructor accepts zero and positive coordinates`() {
        val origin = CursorPosition(column = 0, row = 0)
        val position = CursorPosition(column = 7, row = 3)

        assertEquals(0, origin.column)
        assertEquals(0, origin.row)
        assertEquals(7, position.column)
        assertEquals(3, position.row)
    }

    @Test
    fun `constructor rejects negative coordinates`() {
        assertFailsWith<IllegalArgumentException> { CursorPosition(column = -1, row = 0) }
        assertFailsWith<IllegalArgumentException> { CursorPosition(column = 0, row = -1) }
    }

    @Test
    fun `movement methods reject negative steps`() {
        val position = CursorPosition(column = 5, row = 5)

        assertFailsWith<IllegalArgumentException> { position.moveUp(-1) }
        assertFailsWith<IllegalArgumentException> { position.moveDown(-1) }
        assertFailsWith<IllegalArgumentException> { position.moveLeft(-1) }
        assertFailsWith<IllegalArgumentException> { position.moveRight(-1) }
    }

    @Test
    fun `move up and left floor at zero`() {
        val position = CursorPosition(column = 2, row = 1)

        val movedUp = position.moveUp(4)
        val movedLeft = position.moveLeft(9)

        assertEquals(2, movedUp.column)
        assertEquals(0, movedUp.row)
        assertEquals(0, movedLeft.column)
        assertEquals(1, movedLeft.row)
    }

    @Test
    fun `move down and right increase coordinates without clamping`() {
        val position = CursorPosition(column = 2, row = 1)

        val movedDown = position.moveDown(4)
        val movedRight = position.moveRight(9)

        assertEquals(2, movedDown.column)
        assertEquals(5, movedDown.row)
        assertEquals(11, movedRight.column)
        assertEquals(1, movedRight.row)
    }

    @Test
    fun `default movement step is one`() {
        val position = CursorPosition(column = 2, row = 2)

        assertEquals(CursorPosition(column = 2, row = 1), position.moveUp())
        assertEquals(CursorPosition(column = 2, row = 3), position.moveDown())
        assertEquals(CursorPosition(column = 1, row = 2), position.moveLeft())
        assertEquals(CursorPosition(column = 3, row = 2), position.moveRight())
    }

    @Test
    fun `clamp to validates screen dimensions`() {
        val position = CursorPosition(column = 1, row = 1)

        assertFailsWith<IllegalArgumentException> { position.clampTo(screenWidth = 0, screenHeight = 1) }
        assertFailsWith<IllegalArgumentException> { position.clampTo(screenWidth = 1, screenHeight = 0) }
    }

    @Test
    fun `clamp to keeps in-range coordinates unchanged and clamps out-of-range`() {
        val inRange = CursorPosition(column = 2, row = 1)
        val outOfRange = CursorPosition(column = 11, row = 9)

        assertEquals(inRange, inRange.clampTo(screenWidth = 5, screenHeight = 3))
        assertEquals(CursorPosition(column = 4, row = 2), outOfRange.clampTo(screenWidth = 5, screenHeight = 3))
    }

    @Test
    fun `movement and clamping return new instances and keep original unchanged`() {
        val original = CursorPosition(column = 3, row = 3)

        val moved = original.moveLeft(2)
        val clamped = original.clampTo(screenWidth = 2, screenHeight = 2)

        assertEquals(CursorPosition(column = 3, row = 3), original)
        assertEquals(CursorPosition(column = 1, row = 3), moved)
        assertEquals(CursorPosition(column = 1, row = 1), clamped)
    }
}
