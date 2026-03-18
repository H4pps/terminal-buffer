package terminalbuffer.manager

import terminalbuffer.storage.ReadOnlyLineStorage

/**
 * Visual-row mapping for render-time wrapping.
 *
 * This mapper converts canonical logical lines into visible wrapped rows without mutating
 * storage. Each visible row references one logical line and the starting column inside that line.
 */
internal object WrappedViewportMapper {
    /**
     * One visible wrapped row reference.
     *
     * @property lineIndex logical line index, or null when no backing line exists
     * @property startColumnIndex zero-based start column index inside the logical line for this wrapped slice
     */
    data class RowReference(
        val lineIndex: Int?,
        val startColumnIndex: Int,
    )

    /**
     * Builds wrapped visible-row references for the current viewport.
     *
     * @param storage logical-line storage
     * @param screenWidth visible width in cells
     * @param screenHeight visible height in rows
     * @param topLineIndex top logical-line index of viewport
     * @return exactly [screenHeight] row references
     */
    fun rowReferences(
        storage: ReadOnlyLineStorage,
        screenWidth: Int,
        screenHeight: Int,
        topLineIndex: Int,
    ): List<RowReference> {
        val rows = mutableListOf<RowReference>()
        var logicalIndex = topLineIndex

        while (rows.size < screenHeight) {
            if (logicalIndex !in 0 until storage.lineCount) {
                rows += RowReference(lineIndex = null, startColumnIndex = 0)
                logicalIndex += 1
                continue
            }

            val lineLength = storage.lineSnapshot(logicalIndex).size
            val wrappedRowsForLine = maxOf(1, (lineLength + screenWidth - 1) / screenWidth)
            repeat(wrappedRowsForLine) { segment ->
                rows += RowReference(lineIndex = logicalIndex, startColumnIndex = segment * screenWidth)
                if (rows.size == screenHeight) {
                    return rows
                }
            }

            logicalIndex += 1
        }

        return rows
    }
}
