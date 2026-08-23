package databack.common.worldgen.dag;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;

import java.util.List;

/**
 * A DF operation that forces a kernel boundary.
 * Its inputs are compiled into its own kernel(s); its output is a GPU buffer.
 * <p>
 * Two references to the same source factory instance are guaranteed by {@link DFDagBuilder}
 * to resolve to the same BarrierNode object, so identity comparison is valid for deduplication.
 */
public final class BarrierNode implements DFDagNode {

    private final IDensityFunctionFactory source;
    private final BarrierKind kind;
    private final CellSize outputShape;
    private final List<DFDagNode> inputs;
    /** Unique identifier used for GPU buffer naming. */
    private final String id;

    public BarrierNode(IDensityFunctionFactory source, BarrierKind kind, CellSize outputShape,
                       List<DFDagNode> inputs, String id) {
        this.source = source;
        this.kind = kind;
        this.outputShape = outputShape;
        this.inputs = inputs;
        this.id = id;
    }

    @Override public IDensityFunctionFactory getFactory() { return source; }
    @Override public CellSize getOutputShape()      { return outputShape; }
    @Override public List<DFDagNode> getInputs()         { return inputs; }
    public BarrierKind kind()                         { return kind; }
    public String id()                                { return id; }
}
