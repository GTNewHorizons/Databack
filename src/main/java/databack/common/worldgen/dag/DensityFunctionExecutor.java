package databack.common.worldgen.dag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import com.google.common.collect.ImmutableMap;

import com.gtnewhorizon.gtnhlib.space.ImmutableXYZ;
import databack.common.worldgen.dag.codegen.GeneratedKernel;
import databack.common.worldgen.dag.codegen.KernelBodyEmitter;
import mcgpu.core.hwaccel.KernelContext;
import mcgpu.core.hwaccel.buffer.BufferAllocator;
import mcgpu.core.hwaccel.buffer.BufferDescriptor;
import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.BufferLayout;
import mcgpu.core.hwaccel.buffer.ConstantBuffer;
import mcgpu.core.hwaccel.buffer.GPUBuffer;
import mcgpu.core.hwaccel.executor.KernelExecutor;
import mcgpu.core.hwaccel.scheduling.ComputePlan;
import mcgpu.core.hwaccel.scheduling.KernelSubmission;
import mcgpu.core.hwaccel.scheduling.KernelSubmissionResult;
import mcgpu.core.hwaccel.scheduling.KernelSubmissionToken;
import mcgpu.core.hwaccel.shader.Kernel;
import mcgpu.core.hwaccel.shader.KernelBuilder;
import mcgpu.core.hwaccel.shader.PushConstantLayout;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * GPU kernel executor for one kernel produced by the density function DAG.
 * <p>
 * Supports two construction paths:
 * <ul>
 *   <li><b>Builder1</b>: {@link #DensityFunctionExecutor(KernelGroup)} — {@link #compile} re-emits GLSL
 *       via {@link KernelBodyEmitter}.</li>
 *   <li><b>Builder2</b>: {@link #DensityFunctionExecutor(GeneratedKernel)} — GLSL is pre-built;
 *       {@link #compile} only replays push-constant registration and uploads noise data.</li>
 * </ul>
 * Must be compiled via
 * {@link mcgpu.core.hwaccel.scheduling.KernelScheduler#compileExecutor KernelScheduler.compileExecutor}
 * before the first plan that uses it is submitted.
 */
@Lwjgl3Aware
public class DensityFunctionExecutor implements KernelExecutor<ImmutableXYZ> {

    // Exactly one of these is non-null depending on the construction path.
    private final KernelGroup group;           // Builder1 path
    private final GeneratedKernel prebuilt;    // Builder2 path

    /** Set before compile() to enable real noise-data upload during kernel compilation. */
    private Function<String, int[]> noiseDataProvider = null;

    // Populated during compile() on the worker thread.
    private Kernel pipeline;
    private PushConstantLayout pushConstants;
    private Map<String, BufferLayout> inputLayouts  = Collections.emptyMap();
    private Map<String, BufferLayout> outputLayouts = Collections.emptyMap();

    /** Builder1 path: compile() re-emits GLSL from the KernelGroup. */
    public DensityFunctionExecutor(KernelGroup group) {
        this.group    = group;
        this.prebuilt = null;
    }

    /** Builder2 path: GLSL is pre-built; compile() only replays push-constant setup. */
    public DensityFunctionExecutor(GeneratedKernel prebuilt) {
        this.prebuilt = prebuilt;
        this.group    = null;
    }

    /**
     * Sets the provider used during {@link #compile} to serialize named noise entries into
     * GPU data. The function receives a noise slot ID (e.g. {@code "minecraft:temperature"})
     * and returns the {@code int[]} produced by
     * {@link databack.common.worldgen.noise.NormalNoiseGpuSerializer#toGpuData}.
     * Must be called before {@code compile()} for noise uploads to take effect.
     */
    public void setNoiseProvider(Function<String, int[]> provider) {
        this.noiseDataProvider = provider;
    }

    /**
     * Bypasses GPU compilation for testing: directly installs the input and output layout maps
     * that {@link #compile} would normally derive from a real {@link ConstantBuffer}.
     * <p>
     * Only {@link #getOutputs} is usable after this call; {@link #submit} still requires a compiled pipeline.
     */
    void initForTesting(Map<String, BufferLayout> inputs, Map<String, BufferLayout> outputs) {
        this.inputLayouts  = new HashMap<>(inputs);
        this.outputLayouts = new HashMap<>(outputs);
    }

    /**
     * Returns true if this kernel's output does not depend on the chunk Y coordinate
     * (i.e., it is a FLAT_CACHE kernel dispatched once per chunk column).
     */
    boolean isYIndependent() {
        if (prebuilt != null) return prebuilt.isYIndependent;
        return !group.isTerminal() && group.output().kind() == BarrierKind.FLAT_CACHE;
    }

    /**
     * Returns true if this kernel's output buffer contains {@code u32} values
     * (i.e., it is a COLUMN_REDUCE / FindTopSurface kernel).
     */
    boolean isColumnReduce() {
        if (prebuilt != null) return prebuilt.isColumnReduce;
        return !group.isTerminal() && group.output().kind() == BarrierKind.COLUMN_REDUCE;
    }

    // -------------------------------------------------------------------------
    // KernelExecutor — worker-thread methods
    // -------------------------------------------------------------------------

    @Override
    public boolean isCompiled() {
        return pipeline != null;
    }

    /**
     * Compiles this kernel for GPU execution.
     * <p>
     * Builder1 path: re-emits GLSL via {@link KernelBodyEmitter}, uploads noise constants.
     * Builder2 path: uses pre-built GLSL, replays push-constant registration, uploads noise constants.
     */
    @Override
    public void compile(ConstantBuffer constants) {
        if (prebuilt != null) {
            compilePrebuilt(constants);
        } else {
            compileFromGroup(constants);
        }
    }

    private void compileFromGroup(ConstantBuffer constants) {
        KernelBuilder builder = new KernelBuilder(constants);
        GeneratedKernel gen = KernelBodyEmitter.emit(group, builder, this.noiseDataProvider);

        this.pipeline      = new Kernel("DFKernel/" + group.shape(), gen.glslSource);
        this.pushConstants = builder.pushConstants;
        this.inputLayouts  = new HashMap<>(builder.inputs);
        this.outputLayouts = new HashMap<>(builder.outputs);
    }

    /**
     * Builder2 compile path: GLSL is pre-built.
     * Replays the push-constant layout (chunk coords + noise slot offsets) so that
     * the layout matches the pre-baked shader source, and uploads noise tables to constants.
     */
    private void compilePrebuilt(ConstantBuffer constants) {
        CellSize shape = prebuilt.shape;
        KernelBuilder builder = new KernelBuilder(constants);

        builder.addParameter(BufferDataType.i32, "chunkX");
        if (!shape.isYInvariant()) {
            builder.addParameter(BufferDataType.i32, "chunkY");
        }
        builder.addParameter(BufferDataType.i32, "chunkZ");

        for (String noiseId : prebuilt.noiseSlotIds) {
            int offset = 0;
            if (noiseDataProvider != null && constants != null) {
                int[] gpuData = noiseDataProvider.apply(noiseId);
                if (gpuData != null) {
                    GPUBuffer gpuBuf = constants.addConstant(gpuData);
                    offset = gpuBuf.getBufferOffset();
                }
            }
            builder.pushConstants.addConstantOffset(BufferDataType.u32, offset, noiseId);
        }

        this.pipeline      = new Kernel("DFKernel/" + shape, prebuilt.glslSource);
        this.pushConstants = builder.pushConstants;
        this.inputLayouts  = new HashMap<>(prebuilt.inputLayouts);
        this.outputLayouts = new HashMap<>(prebuilt.outputLayouts);
    }

    @Override
    public KernelSubmissionResult[] submit(VkCommandBuffer commands, BufferAllocator alloc,
            KernelSubmission<ImmutableXYZ>[] submissions) {
        pipeline.bind(commands);

        CellSize shape = prebuilt != null ? prebuilt.shape : group.shape();
        KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

        for (int i = 0; i < submissions.length; i++) {
            // Allocate output buffers for this dispatch.
            ImmutableMap.Builder<String, GPUBuffer> outBuilder = ImmutableMap.builder();
            for (Map.Entry<String, BufferLayout> e : outputLayouts.entrySet()) {
                outBuilder.put(e.getKey(), alloc.alloc(e.getValue()));
            }
            Map<String, GPUBuffer> outputMap = outBuilder.build();
            results[i] = new KernelSubmissionResult(outputMap);

            // Merge inputs + outputs for push-constant upload.
            Map<String, GPUBuffer> allBuffers = new HashMap<>(submissions[i].inputs());
            allBuffers.putAll(outputMap);

            ImmutableXYZ key = submissions[i].key();
            Map<String, Number> params = new HashMap<>();
            params.put("chunkX", key.getX());
            params.put("chunkY", key.getY());
            params.put("chunkZ", key.getZ());

            pushConstants.upload(
                commands,
                KernelContext.getScheduler().getPipelineLayout(),
                allBuffers,
                params);

            // PER_VOXEL uses local_size(16,4,16)=1024 and four Y workgroups to reach 16×16×16.
            // PER_COLUMN (256) and PER_CORNER (125) fit within one workgroup.
            if (shape == CellSize.BLOCKS) {
                VK10.vkCmdDispatch(commands, 1, 4, 1);
            } else {
                VK10.vkCmdDispatch(commands, 1, 1, 1);
            }
        }

        return results;
    }

    @Override
    public void close() {
        if (pipeline != null) {
            pipeline.destroy();
        }
    }

    // -------------------------------------------------------------------------
    // KernelExecutor — server-thread method
    // -------------------------------------------------------------------------

    /**
     * Validates incoming buffer layouts against the declared inputs and describes output buffers.
     * Must only be called after {@link #compile}.
     */
    @Override
    public Map<String, BufferDescriptor> getOutputs(
            ComputePlan plan, KernelSubmissionToken submission, ImmutableXYZ key,
            Map<String, BufferDescriptor> inputs) {
        for (Map.Entry<String, BufferLayout> e : inputLayouts.entrySet()) {
            BufferDescriptor desc = inputs.get(e.getKey());
            if (desc == null) throw new IllegalStateException("Input buffer was not passed to kernel: " + e.getKey());
            desc.assertLayout(e.getValue());
        }

        ImmutableMap.Builder<String, BufferDescriptor> out = ImmutableMap.builder();
        for (Map.Entry<String, BufferLayout> e : outputLayouts.entrySet()) {
            out.put(e.getKey(), plan.describeBuffer(submission, e.getValue()));
        }
        return out.build();
    }
}
