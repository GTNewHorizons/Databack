package databack.common.worldgen.dag;

import java.util.List;

import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.Expr;
import databack.common.worldgen.dag.DFDagBuilder2.BarrierDAGNode;
import databack.common.worldgen.dag.DFDagBuilder2.DAGNode;
import databack.common.worldgen.dag.DFDagBuilder2.PartitionedDAGNode;
import databack.common.worldgen.dag.codegen.CodeGenContext;
import mcgpu.core.hwaccel.buffer.BufferLayout;

/// A [IDensityFunctionFactory] that changes the execution shape of the density function tree by forcing a kernel
/// boundary. Its output is materialized into a GPU buffer that consumers read in subsequent kernels.
///
/// The interface is split into two sides:
///
/// - **Producing side**: called by the plan builder when building this barrier's own kernel.
/// - **Consuming side**: called by [DFDagBuilder2.DagContext] when building a kernel that reads this barrier.
public interface BarrierDFCodeGenerator<State> extends DFCodeGenerator<State> {

    // ---- Producing side ----

    /// Returns the [CellSize] of the output buffer given the shape hint from the parent context.
    /// Return {@code null} to indicate this barrier must be inlined at the given shape
    /// (e.g. CacheOnce is only a barrier in BLOCKS context; inside a flat context it inlines).
    CellSize outputShape(CellSize inputShapeHint);

    /// Returns the layout of the GPU buffer this barrier writes.
    BufferLayout getBufferLayout();

    /// Emits the {@code void main()} body of this barrier's kernel into {@code context.getKernel()}.
    /// Recursively calls {@code context.compute()} for child nodes.
    /// Called once per barrier by the plan builder.
    void emitMain(CodeGenContext context, BarrierDAGNode barrier);

    // ---- Consuming side ----

    /// Called in a consuming kernel to add this barrier's buffer as an input binding.
    /// Returns the State token (typically the GLSL binding name) cached in DagContext.kernelState.
    State configureConsumer(CodeGenContext consumingContext, BarrierDAGNode barrierNode);

    /// Returns a GLSL Expr that reads the barrier buffer at (x,y,z) from the consuming kernel.
    Expr<Float> emitGetter(CodeGenContext consumingContext, State state,
                           Expr<Integer> x, Expr<Integer> y, Expr<Integer> z);

    // ---- Optional overrides for special barriers ----

    /// Returns the shape hint to pass to a specific child during partitionTree.
    /// Default: same as the output shape hint (most barriers evaluate children at their own shape).
    /// Override to pass different shapes to different children (e.g. FindTopSurface passes BLOCKS
    /// to the density child and COLUMNS to upper_bound).
    ///
    /// @param parentFactory the factory object of the barrier node (for casting to the concrete type)
    /// @param outputHint    the output shape hint for this barrier
    /// @param child         the specific child factory whose hint is being queried
    default CellSize childShapeHint(IDensityFunctionFactory parentFactory, CellSize outputHint,
                                    IDensityFunctionFactory child) {
        return outputHint;
    }

    /// Compound barriers (InterpolatedFunc, SplineCurve) can expand into a chain of multiple
    /// BarrierDAGNodes. Return a non-null list (ordered: dependencies before consumers) to use
    /// chain expansion; the last element is what external consumers reference.
    /// Return {@code null} to use the normal single-barrier path.
    default List<PartitionedDAGNode> expandToChain(DAGNode node, DFDagBuilder2 partitioner,
                                                    CellSize shapeHint) {
        return null;
    }
}
