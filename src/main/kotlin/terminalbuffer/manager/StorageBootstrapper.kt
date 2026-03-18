package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.storage.MutableLineStorage

/**
 * Initializes canonical storage to an empty screen state.
 */
object StorageBootstrapper {
    /**
     * Clears [storage] and appends one empty logical line.
     *
     * Startup uses lazy backing-line creation, so only one canonical line is pre-created.
     *
     * @param storage mutable line storage to initialize
     * @param screenHeight number of visible screen rows (validated for non-negative values)
     * @throws IllegalArgumentException when [screenHeight] is negative
     */
    fun bootstrapEmptyScreen(
        storage: MutableLineStorage,
        screenHeight: Int,
    ) {
        require(screenHeight >= 0) { "Screen height must be non-negative: $screenHeight" }
        storage.clear()
        storage.appendLine(BufferLine.empty())
    }
}
