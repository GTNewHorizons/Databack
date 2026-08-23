package databack.common.worldgen.debug;

import databack.common.worldgen.dag.CellSize;
import mcgpu.core.hwaccel.buffer.BufferDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a single GPU kernel dispatch during density function evaluation.
 * <p>
 * For FLAT_CACHE kernels, one record is produced for the whole column (chunkY = 0 sentinel).
 * For all other kernels, one record is produced per Y-level dispatch.
 * The terminal kernel has no record; its output is in {@link ChunkDebugCapture#densities}.
 */
public final class KernelRecord {

    /** Dispatch shape: PER_VOXEL (4096 elements), PER_COLUMN (256), or PER_CORNER (125). */
    public final CellSize shape;

    /** {chunkX, chunkY, chunkZ} — for FLAT_CACHE kernels, chunkY is the sentinel 0. */
    public final int[] chunkKey;

    /** Barrier ID this kernel wrote to. */
    public final String outputBarrierId;

    /** Barrier IDs this kernel read from. */
    public final List<String> inputBarrierIds;

    /** Complete GLSL source of this kernel. */
    public final String glslSource;

    /**
     * Output buffer data type: {@code f32} for most kernels, {@code u32} for COLUMN_REDUCE.
     * For u32 buffers, reinterpret each element of {@link #outputValues} via
     * {@link Float#floatToRawIntBits(float)}.
     */
    public final BufferDataType dataType;

    /**
     * Captured output values. Array length equals {@code shape.elementCount}.
     * For u32 buffers ({@link #dataType} == u32), the bit pattern of each float encodes
     * the raw unsigned integer — use {@link Float#floatToRawIntBits} to recover it.
     */
    public final float[] outputValues;

    public KernelRecord(
        CellSize shape, int[] chunkKey,
                        String outputBarrierId, List<String> inputBarrierIds,
                        String glslSource, BufferDataType dataType, float[] outputValues) {
        this.shape            = shape;
        this.chunkKey         = chunkKey;
        this.outputBarrierId  = outputBarrierId;
        this.inputBarrierIds  = Collections.unmodifiableList(new ArrayList<>(inputBarrierIds));
        this.glslSource       = glslSource;
        this.dataType         = dataType;
        this.outputValues     = outputValues;
    }
}
