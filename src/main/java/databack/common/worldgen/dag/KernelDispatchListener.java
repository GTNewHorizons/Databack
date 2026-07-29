package databack.common.worldgen.dag;

import mcgpu.core.hwaccel.buffer.BufferDataType;

import java.util.List;

/**
 * Callback interface for observing intermediate kernel outputs during plan execution.
 * <p>
 * Implemented by the debug subsystem ({@code ChunkDebugCapture.Builder}).
 * {@link DFPlanBuilder} accepts this interface so the core kernel dispatch system has no
 * dependency on the {@code debug} package.
 * <p>
 * All callbacks fire on the server thread, inside {@code KernelScheduler.submit()}.
 */
public interface KernelDispatchListener {

    /**
     * Called once per intermediate kernel output after GPU readback completes.
     *
     * @param shape            dispatch shape (PER_VOXEL / PER_COLUMN / PER_CORNER)
     * @param chunkKey         {chunkX, chunkY, chunkZ} — chunkY=0 sentinel for FLAT_CACHE kernels
     * @param outputBarrierId  barrier ID this kernel wrote to (never null for non-terminal)
     * @param inputBarrierIds  barrier IDs this kernel read from, in declaration order
     * @param glslSource       complete GLSL source of this kernel
     * @param dataType         {@code f32} for most kernels; {@code u32} for COLUMN_REDUCE
     * @param outputValues     captured output buffer as raw floats; for u32 buffers reinterpret
     *                         each element via {@link Float#floatToRawIntBits}
     */
    void onKernelOutput(DispatchShape shape, int[] chunkKey,
                        String outputBarrierId, List<String> inputBarrierIds,
                        String glslSource, BufferDataType dataType, float[] outputValues);
}
