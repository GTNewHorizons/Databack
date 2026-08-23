package databack.common.worldgen.dag;

import java.util.List;

/**
 * A set of {@link InlineNode}s compiled into a single compute dispatch.
 * <p>
 * {@code reads} are BarrierNodes whose GPU buffers must exist before this group dispatches.
 * {@code nodes} are in dependency order (inputs before the nodes that use them).
 * {@code output} is null for the terminal kernel (the final_density root group).
 */
public final class KernelGroup {

    private final CellSize shape;
    private final List<BarrierNode> reads;
    private final List<InlineNode> nodes;
    /** Null for the terminal kernel. */
    private final BarrierNode output;

    public KernelGroup(CellSize shape, List<BarrierNode> reads, List<InlineNode> nodes, BarrierNode output) {
        this.shape = shape;
        this.reads = reads;
        this.nodes = nodes;
        this.output = output;
    }

    public CellSize shape()        { return shape; }
    public List<BarrierNode> reads()    { return reads; }
    public List<InlineNode> nodes()     { return nodes; }
    /** Returns the BarrierNode this kernel materializes, or null if this is the terminal kernel. */
    public BarrierNode output()         { return output; }
    public boolean isTerminal()         { return output == null; }
}
