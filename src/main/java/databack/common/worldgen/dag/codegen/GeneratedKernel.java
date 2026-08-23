package databack.common.worldgen.dag.codegen;

import databack.common.worldgen.dag.CellSize;

import java.util.List;

/**
 * The compiled GLSL source and metadata for a single compute kernel derived from a {@link databack.common.worldgen.dag.KernelGroup}.
 */
public final class GeneratedKernel {

    /** The dispatch shape this kernel runs at (PER_VOXEL, PER_COLUMN, or PER_CORNER). */
    public final CellSize shape;

    /** Complete GLSL source text ready for shader compilation. */
    public final String glslSource;

    /**
     * IDs of the barrier buffers this kernel reads, in the same order as {@code KernelGroup.reads()}.
     * Each entry matches the {@code id()} of a {@link databack.common.worldgen.dag.BarrierNode}.
     */
    public final List<String> inputBarrierIds;

    /**
     * ID of the barrier buffer this kernel writes its result to, or {@code null} for the terminal kernel
     * (which has no downstream consumer in the DAG).
     */
    public final String outputBarrierId;

    /**
     * Noise IDs encountered in expression-order within this kernel, in first-encounter order.
     * Each entry corresponds to one {@code constantOffset} push constant whose GPU-side data is
     * the base uint offset into the constants buffer for that noise table.
     */
    public final List<String> noiseSlotIds;

    public GeneratedKernel(
        CellSize shape, String glslSource,
                           List<String> inputBarrierIds, String outputBarrierId,
                           List<String> noiseSlotIds) {
        this.shape = shape;
        this.glslSource = glslSource;
        this.inputBarrierIds = inputBarrierIds;
        this.outputBarrierId = outputBarrierId;
        this.noiseSlotIds = noiseSlotIds;
    }
}
