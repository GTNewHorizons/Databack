package databack.common.worldgen.dag;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;

import java.util.List;

/**
 * A DF operation compiled as a GLSL expression inside a {@link KernelGroup}.
 * Inputs may be other InlineNodes (within the same group) or BarrierNodes (buffer reads).
 */
public final class InlineNode implements DFDagNode {

    private final IDensityFunctionFactory source;
    private final DispatchShape outputShape;
    private final List<DFDagNode> inputs;

    public InlineNode(IDensityFunctionFactory source, DispatchShape outputShape, List<DFDagNode> inputs) {
        this.source = source;
        this.outputShape = outputShape;
        this.inputs = inputs;
    }

    @Override public IDensityFunctionFactory source()   { return source; }
    @Override public DispatchShape outputShape()        { return outputShape; }
    @Override public List<DFDagNode> inputs()           { return inputs; }
}
