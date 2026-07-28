package databack.common.worldgen.dag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import com.google.common.collect.ImmutableMap;

import databack.common.worldgen.dag.codegen.GeneratedKernel;
import databack.common.worldgen.dag.codegen.KernelBodyEmitter;
import mcgpu.core.hwaccel.KernelContext;
import mcgpu.core.hwaccel.buffer.BufferAllocator;
import mcgpu.core.hwaccel.buffer.BufferDescriptor;
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
 * GPU kernel executor for a single {@link KernelGroup} produced by the density function DAG.
 * <p>
 * The key is a 3-element {@code int[]} {@code {chunkX, chunkY, chunkZ}}.  One executor instance
 * is created per kernel group; the scheduler batches multiple Y-levels into a single
 * {@link #submit} call automatically.
 * <p>
 * Must be compiled via
 * {@link mcgpu.core.hwaccel.scheduling.KernelScheduler#compileExecutor KernelScheduler.compileExecutor}
 * before the first plan that uses it is submitted.
 */
@Lwjgl3Aware
public class DensityFunctionExecutor implements KernelExecutor<int[]> {

    private final KernelGroup group;

    /** Set before compile() to enable real noise-data upload during kernel compilation. */
    private Function<String, int[]> noiseDataProvider = null;

    // Populated during compile() on the worker thread.
    private Kernel pipeline;
    private PushConstantLayout pushConstants;
    private Map<String, BufferLayout> inputLayouts  = Collections.emptyMap();
    private Map<String, BufferLayout> outputLayouts = Collections.emptyMap();

    public DensityFunctionExecutor(KernelGroup group) {
        this.group = group;
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
     * that {@link #compile} would normally derive from a real {@link mcgpu.core.hwaccel.buffer.ConstantBuffer}.
     * <p>
     * Only {@link #getOutputs} is usable after this call; {@link #submit} still requires a compiled pipeline.
     */
    void initForTesting(Map<String, BufferLayout> inputs, Map<String, BufferLayout> outputs) {
        this.inputLayouts  = new HashMap<>(inputs);
        this.outputLayouts = new HashMap<>(outputs);
    }

    /**
     * Returns true if this kernel's output does not depend on the chunk Y coordinate.
     * Only {@link BarrierKind#FLAT_CACHE} kernels qualify — their shaders operate on the
     * XZ plane and never read {@code wy} or {@code worldBlockY}.
     */
    boolean isYIndependent() {
        return !group.isTerminal() && group.output().kind() == BarrierKind.FLAT_CACHE;
    }

    // -------------------------------------------------------------------------
    // KernelExecutor — worker-thread methods
    // -------------------------------------------------------------------------

    @Override
    public boolean isCompiled() {
        return pipeline != null;
    }

    /**
     * Emits the GLSL kernel, uploads any constants, compiles to a Vulkan pipeline, and captures
     * the push-constant layout and buffer layouts for later use in {@link #submit}.
     */
    @Override
    public void compile(ConstantBuffer constants) {
        KernelBuilder builder = new KernelBuilder(constants);
        GeneratedKernel gen = KernelBodyEmitter.emit(group, builder, this.noiseDataProvider);

        this.pipeline      = new Kernel("DFKernel/" + group.shape(), gen.glslSource);
        this.pushConstants = builder.pushConstants;
        this.inputLayouts  = new HashMap<>(builder.inputs);
        this.outputLayouts = new HashMap<>(builder.outputs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public KernelSubmissionResult[] submit(VkCommandBuffer commands, BufferAllocator alloc,
            KernelSubmission<int[]>[] submissions) {
        pipeline.bind(commands);

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

            int[] key = submissions[i].key();
            Map<String, Number> params = new HashMap<>();
            params.put("chunkX", key[0]);
            params.put("chunkY", key[1]);
            params.put("chunkZ", key[2]);

            pushConstants.upload(
                commands,
                KernelContext.getScheduler().getPipelineLayout(),
                allBuffers,
                params);

            // PER_VOXEL uses local_size(16,4,16)=1024 and four Y workgroups to reach 16×16×16.
            // PER_COLUMN (256) and PER_CORNER (125) fit within one workgroup.
            if (group.shape() == DispatchShape.PER_VOXEL) {
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
            ComputePlan plan, KernelSubmissionToken submission, int[] key,
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
