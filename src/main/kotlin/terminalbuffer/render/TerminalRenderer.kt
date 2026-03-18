package terminalbuffer.render

/**
 * Rendering contract that converts a precomposed [RenderFrame] into renderer output.
 *
 * This contract intentionally accepts frame data instead of storage access.
 */
interface TerminalRenderer {
    /**
     * Renders a complete frame.
     *
     * @param frame immutable frame to render
     * @return rendered output format chosen by implementation
     */
    fun render(frame: RenderFrame): String
}
