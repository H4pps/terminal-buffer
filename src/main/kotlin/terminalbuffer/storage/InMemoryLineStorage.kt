package terminalbuffer.storage

import terminalbuffer.domain.BufferLine
import terminalbuffer.domain.TerminalCell

/**
 * In-memory implementation of [MutableLineStorage] for canonical logical lines.
 *
 * This storage is width-agnostic and performs no wrapping/reflow or retention trimming.
 * Retention policy is intentionally owned by higher-level manager code.
 */
class InMemoryLineStorage : MutableLineStorage {
    private val lines: ArrayDeque<BufferLine> = ArrayDeque()

    /**
     * Number of currently stored logical lines.
     */
    override val lineCount: Int
        get() = lines.size

    /**
     * Returns a detached snapshot of the stored line at [index].
     *
     * @param index zero-based line index
     * @return immutable list snapshot of line cells
     * @throws IndexOutOfBoundsException when [index] is outside `0 until lineCount`
     */
    override fun lineSnapshot(index: Int): List<TerminalCell> {
        validateExistingIndex(index)
        return lines[index].snapshot()
    }

    /**
     * Appends [line] to storage.
     *
     * @param line source line
     */
    override fun appendLine(line: BufferLine) {
        lines.addLast(copyOf(line))
    }

    /**
     * Inserts [line] at [index].
     *
     * @param index insertion index in `0..lineCount`
     * @param line source line
     * @throws IndexOutOfBoundsException when [index] is outside `0..lineCount`
     */
    override fun insertLine(
        index: Int,
        line: BufferLine,
    ) {
        validateInsertionIndex(index)
        lines.add(index, copyOf(line))
    }

    /**
     * Replaces line at [index] with [line].
     *
     * @param index replacement index in `0 until lineCount`
     * @param line source line
     * @throws IndexOutOfBoundsException when [index] is outside `0 until lineCount`
     */
    override fun replaceLine(
        index: Int,
        line: BufferLine,
    ) {
        validateExistingIndex(index)
        lines[index] = copyOf(line)
    }

    /**
     * Returns live mutable storage-owned line at [index].
     *
     * @param index zero-based line index
     * @return mutable line reference stored in this storage
     * @throws IndexOutOfBoundsException when [index] is outside `0 until lineCount`
     */
    override fun mutableLine(index: Int): BufferLine {
        validateExistingIndex(index)
        return lines[index]
    }

    /**
     * Removes and returns the first line.
     *
     * @return first stored line
     * @throws IndexOutOfBoundsException when storage is empty
     */
    override fun removeFirstLine(): BufferLine {
        if (lines.isEmpty()) {
            throw IndexOutOfBoundsException("Cannot remove first line from empty storage")
        }
        return lines.removeFirst()
    }

    /**
     * Removes all stored lines.
     */
    override fun clear() {
        lines.clear()
    }

    private fun validateExistingIndex(index: Int) {
        if (index !in 0 until lineCount) {
            throw IndexOutOfBoundsException("Index $index is outside valid range 0..${lineCount - 1}")
        }
    }

    private fun validateInsertionIndex(index: Int) {
        if (index !in 0..lineCount) {
            throw IndexOutOfBoundsException("Insertion index $index is outside valid range 0..$lineCount")
        }
    }

    private fun copyOf(source: BufferLine): BufferLine = BufferLine.fromCells(source.snapshot())
}
