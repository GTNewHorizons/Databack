package databack.common.worldgen.dag.codegen;

import databack.common.worldgen.dag.CellSize;
import mcgpu.core.hwaccel.buffer.BufferLayout;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The compiled GLSL source and metadata for a single compute kernel.
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

    /**
     * True if this kernel is Y-independent (FLAT_CACHE): dispatched once per chunk column,
     * shared by all Y-level dispatches downstream.
     */
    public final boolean isYIndependent;

    /**
     * True if this kernel is a column-reduce (FindTopSurface): output is u32 rather than f32.
     */
    public final boolean isColumnReduce;

    /**
     * Buffer layouts this kernel reads, keyed by barrier buffer name.
     * Used by {@link databack.common.worldgen.dag.DensityFunctionExecutor#getOutputs} to validate inputs.
     */
    public final Map<String, BufferLayout> inputLayouts;

    /**
     * Buffer layouts this kernel writes, keyed by buffer name (usually {@code "output"}).
     * Used by {@link databack.common.worldgen.dag.DensityFunctionExecutor#getOutputs} to describe outputs.
     */
    public final Map<String, BufferLayout> outputLayouts;

    public GeneratedKernel(
            CellSize shape, String glslSource,
            List<String> inputBarrierIds, String outputBarrierId,
            List<String> noiseSlotIds,
            boolean isYIndependent, boolean isColumnReduce,
            Map<String, BufferLayout> inputLayouts, Map<String, BufferLayout> outputLayouts) {
        this.shape = shape;
        this.glslSource = glslSource;
        this.inputBarrierIds = inputBarrierIds;
        this.outputBarrierId = outputBarrierId;
        this.noiseSlotIds = noiseSlotIds;
        this.isYIndependent = isYIndependent;
        this.isColumnReduce = isColumnReduce;
        this.inputLayouts = Collections.unmodifiableMap(inputLayouts);
        this.outputLayouts = Collections.unmodifiableMap(outputLayouts);
    }

    /** Legacy constructor for Builder1 path (no layout metadata). */
    public GeneratedKernel(
            CellSize shape, String glslSource,
            List<String> inputBarrierIds, String outputBarrierId,
            List<String> noiseSlotIds) {
        this(shape, glslSource, inputBarrierIds, outputBarrierId, noiseSlotIds,
            false, false, Collections.emptyMap(), Collections.emptyMap());
    }
}
