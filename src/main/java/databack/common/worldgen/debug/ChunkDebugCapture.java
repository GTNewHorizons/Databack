package databack.common.worldgen.debug;

import databack.common.worldgen.dag.DispatchShape;
import databack.common.worldgen.dag.KernelDispatchListener;
import mcgpu.core.hwaccel.buffer.BufferDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all GPU kernel dispatches for a single chunk column evaluation.
 * Constructed via {@link Builder}, which implements {@link KernelDispatchListener} so it can
 * be passed directly to {@code DFPlanBuilder.createPlan}.
 */
public final class ChunkDebugCapture {

    public final int chunkX;
    public final int chunkZ;
    public final long timestampMs;

    /** All kernel records in topological dispatch order, including the terminal kernel. */
    public final List<KernelRecord> kernelRecords;

    /**
     * Final density values — the same {@code float[16][4096]} instance written by the
     * production pipeline. Valid only after {@code KernelScheduler.submit()} returns.
     * Indexed as {@code densities[chunkY][z*256 + y*16 + x]}.
     */
    public final float[][] densities;

    /**
     * Ordered map (topological plan order) from barrier ID → label strings.
     * Key = outputBarrierId, or {@code "(terminal)"} for the terminal group.
     * Value = {@code String[]{kind, sourceDFType, inlinedDFTypes}}.
     */
    public final Map<String, String[]> kernelGroupLabels;

    private ChunkDebugCapture(int chunkX, int chunkZ, long timestampMs,
                              List<KernelRecord> kernelRecords, float[][] densities,
                              Map<String, String[]> kernelGroupLabels) {
        this.chunkX            = chunkX;
        this.chunkZ            = chunkZ;
        this.timestampMs       = timestampMs;
        this.kernelRecords     = kernelRecords;
        this.densities         = densities;
        this.kernelGroupLabels = kernelGroupLabels;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Accumulates {@link KernelRecord}s during a single chunk column's GPU plan execution.
     * <p>
     * Implements {@link KernelDispatchListener} so it can be passed directly to
     * {@code DFPlanBuilder.createPlan} without the planner knowing about the debug package.
     * <p>
     * {@link #addRecord} is called from within terminal callbacks during
     * {@code KernelScheduler.submit()} (server thread). Call {@link #build} after
     * {@code submit()} returns, once the {@code densities} array is fully populated.
     */
    public static final class Builder implements KernelDispatchListener {

        public final int chunkX;
        public final int chunkZ;
        private final List<KernelRecord> records = new ArrayList<>();
        private final Map<String, String[]> kernelGroupLabels;

        public Builder(int chunkX, int chunkZ, Map<String, String[]> kernelGroupLabels) {
            this.chunkX             = chunkX;
            this.chunkZ             = chunkZ;
            this.kernelGroupLabels  = kernelGroupLabels;
        }

        @Override
        public synchronized void onKernelOutput(DispatchShape shape, int[] chunkKey,
                                                String outputBarrierId, List<String> inputBarrierIds,
                                                String glslSource, BufferDataType dataType,
                                                float[] outputValues) {
            records.add(new KernelRecord(shape, chunkKey, outputBarrierId,
                inputBarrierIds, glslSource, dataType, outputValues));
        }

        /**
         * Finalises the capture. Must be called after {@code KernelScheduler.submit()} returns
         * so that the {@code densities} array has been fully populated by the normal terminal callbacks.
         *
         * @param densities the {@code float[16][4096]} stored in {@code GpuChunkCache.computed}
         */
        public ChunkDebugCapture build(float[][] densities) {
            return new ChunkDebugCapture(
                chunkX, chunkZ, System.currentTimeMillis(),
                Collections.unmodifiableList(new ArrayList<>(records)),
                densities,
                kernelGroupLabels);
        }
    }
}
