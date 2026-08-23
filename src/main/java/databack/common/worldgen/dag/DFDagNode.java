package databack.common.worldgen.dag;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;

import java.util.List;

/** A node in the density function execution DAG. Implemented by {@link InlineNode} and {@link BarrierNode}. */
public interface DFDagNode {
    IDensityFunctionFactory getFactory();
    CellSize getOutputShape();
    List<DFDagNode> getInputs();
}
