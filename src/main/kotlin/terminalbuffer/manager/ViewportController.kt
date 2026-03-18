package terminalbuffer.manager

import terminalbuffer.storage.ReadOnlyLineStorage

/**
 * Owns viewport state and mapping rules for a screen over canonical storage lines.
 *
 * @param storage read-only storage used for visible-line calculations
 * @param screenHeight number of visible rows on screen
 */
class ViewportController(
    private val storage: ReadOnlyLineStorage,
    private val screenHeight: Int,
) {
    /**
     * Current top visible logical-line index.
     */
    var topLineIndex: Int = 0
        private set

    /**
     * Whether viewport is pinned to the newest content at bottom.
     */
    var pinnedToBottom: Boolean = true
        private set

    /**
     * Maximum valid top-line index for current storage size.
     */
    val maxTopIndex: Int
        get() = (storage.lineCount - screenHeight).coerceAtLeast(0)

    /**
     * Sets viewport top line to [topLineIndex].
     *
     * @param topLineIndex target top visible logical-line index
     * @throws IndexOutOfBoundsException when [topLineIndex] is outside `0..maxTopIndex`
     */
    fun setTopLineIndex(topLineIndex: Int) {
        if (topLineIndex !in 0..maxTopIndex) {
            throw IndexOutOfBoundsException(
                "Viewport top index $topLineIndex is outside valid range 0..$maxTopIndex",
            )
        }

        this.topLineIndex = topLineIndex
        pinnedToBottom = topLineIndex == maxTopIndex
    }

    /**
     * Pins viewport to newest visible page.
     */
    fun pinToBottom() {
        topLineIndex = maxTopIndex
        pinnedToBottom = true
    }

    /**
     * Maps [screenRow] to storage index for current viewport.
     *
     * @param screenRow zero-based row within visible screen area
     * @return backing storage index or null when no backing line exists
     * @throws IndexOutOfBoundsException when [screenRow] is outside `0 until screenHeight`
     */
    fun storageIndexForScreenRow(screenRow: Int): Int? {
        if (screenRow !in 0 until screenHeight) {
            throw IndexOutOfBoundsException(
                "Screen row $screenRow is outside valid range 0..${screenHeight - 1}",
            )
        }

        val mappedIndex = topLineIndex + screenRow
        return if (mappedIndex in 0 until storage.lineCount) mappedIndex else null
    }
}
