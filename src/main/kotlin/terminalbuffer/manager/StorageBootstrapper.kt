package terminalbuffer.manager

import terminalbuffer.domain.BufferLine
import terminalbuffer.storage.MutableLineStorage

/**
 * Initializes canonical storage to an empty screen state.
 */
object StorageBootstrapper {
    /**
     * Clears [storage] and appends exactly [screenHeight] empty logical lines.
     *
     * @param storage mutable line storage to initialize
     * @param screenHeight number of visible screen rows
     */
    fun bootstrapEmptyScreen(
        storage: MutableLineStorage,
        screenHeight: Int,
    ) {
        storage.clear()
        repeat(screenHeight) {
            storage.appendLine(BufferLine.empty())
        }
    }
}
