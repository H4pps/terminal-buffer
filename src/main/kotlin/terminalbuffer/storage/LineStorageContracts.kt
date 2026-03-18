package terminalbuffer.storage

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell

/**
 * Read-only storage contract for logical lines.
 *
 * Implementations are width-agnostic and must not perform wrapping/reflow.
 */
interface ReadOnlyLineStorage {
    /**
     * Number of currently stored logical lines.
     */
    val lineCount: Int

    /**
     * Returns a detached snapshot of cells for the line at [index].
     *
     * The returned list must not expose mutable internal storage.
     *
     * @param index zero-based line index
     * @return immutable snapshot of line cells
     * @throws IndexOutOfBoundsException when [index] is outside `0 until lineCount`
     */
    fun lineSnapshot(index: Int): List<TerminalCell>
}

/**
 * Mutable storage contract for logical lines.
 *
 * Retention policy (for example scrollback trimming) is owned by higher-level manager code and
 * is intentionally outside of this contract.
 */
interface MutableLineStorage : ReadOnlyLineStorage {
    /**
     * Appends [line] to the end of storage.
     *
     * Implementations must defensively copy [line] before storing it.
     *
     * @param line source line to append
     */
    fun appendLine(line: BufferLine)

    /**
     * Inserts [line] at [index].
     *
     * Implementations must defensively copy [line] before storing it.
     *
     * @param index insertion index in range `0..lineCount`
     * @param line source line to insert
     * @throws IndexOutOfBoundsException when [index] is outside `0..lineCount`
     */
    fun insertLine(
        index: Int,
        line: BufferLine,
    )

    /**
     * Replaces stored line at [index] with [line].
     *
     * Implementations must defensively copy [line] before storing it.
     *
     * @param index replacement index in range `0 until lineCount`
     * @param line source line used as replacement
     * @throws IndexOutOfBoundsException when [index] is outside `0 until lineCount`
     */
    fun replaceLine(
        index: Int,
        line: BufferLine,
    )

    /**
     * Returns a live mutable stored line at [index].
     *
     * This API exists for manager-only in-place edit paths.
     *
     * @param index zero-based line index
     * @return storage-owned mutable line reference
     * @throws IndexOutOfBoundsException when [index] is outside `0 until lineCount`
     */
    fun mutableLine(index: Int): BufferLine

    /**
     * Removes and returns the first stored line.
     *
     * @return removed first line
     * @throws IndexOutOfBoundsException when no lines are stored
     */
    fun removeFirstLine(): BufferLine

    /**
     * Removes all stored lines.
     */
    fun clear()
}
