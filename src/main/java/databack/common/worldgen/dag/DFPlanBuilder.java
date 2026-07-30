package databack.common.worldgen.dag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.BufferDescriptor;
import mcgpu.core.hwaccel.scheduling.ComputePlan;

import databack.common.worldgen.dag.codegen.DFKernelCodegen;
import databack.common.worldgen.dag.codegen.GeneratedKernel;

/**
 * Builds {@link ComputePlan}s from a compiled {@link DFKernelPlan}.
 * <p>
 * Create one {@code DFPlanBuilder} per density function tree (typically one per dimension), then
 * call {@link #createPlan} once per chunk column.
 *
 * <h3>Dispatch deduplication</h3>
 * {@link BarrierKind#FLAT_CACHE} kernels are Y-independent — their shaders only use the XZ plane.
 * They are dispatched <em>once per chunk column</em> and their output buffers shared as inputs to
 * all Y-level dispatches downstream. Every other kernel stage dispatches once per Y-level.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Build: {@code DFPlanBuilder builder = DFPlanBuilder.create(kernelPlan);}</li>
 *   <li>Compile: {@code builder.getExecutors().forEach(scheduler::compileExecutor);}</li>
 *   <li>Per chunk column:
 *       <pre>
 *       Map&lt;Integer, Consumer&lt;FloatBuffer&gt;&gt; consumers = new HashMap&lt;&gt;();
 *       for (int chunkY : sectionsToGenerate) {
 *           consumers.put(chunkY, density -&gt; fillSection(chunkY, density));
 *       }
 *       scheduler.submit(Collections.singletonList(builder.createPlan(chunkX, chunkZ, consumers)));
 *       </pre>
 *   </li>
 * </ol>
 */
public class DFPlanBuilder {

    private final List<DensityFunctionExecutor> executors;
    private final List<GeneratedKernel> kernels;
    private final Map<String, String[]> kernelGroupLabels;

    DFPlanBuilder(List<DensityFunctionExecutor> executors, List<GeneratedKernel> kernels,
                  Map<String, String[]> kernelGroupLabels) {
        this.executors          = executors;
        this.kernels            = kernels;
        this.kernelGroupLabels  = kernelGroupLabels;
    }

    /**
     * Creates a {@code DFPlanBuilder} from a compiled kernel plan.
     * One {@link DensityFunctionExecutor} is created per kernel group.
     */
    public static DFPlanBuilder create(DFKernelPlan plan) {
        List<GeneratedKernel> kernels = DFKernelCodegen.compile(plan);
        List<DensityFunctionExecutor> executors = new ArrayList<>(plan.groups().size());
        for (KernelGroup group : plan.groups()) {
            executors.add(new DensityFunctionExecutor(group));
        }
        return new DFPlanBuilder(executors, kernels, plan.buildKernelGroupLabels());
    }

    /**
     * Returns the executors backing each kernel stage, in topological order.
     * Pass each to
     * {@link mcgpu.core.hwaccel.scheduling.KernelScheduler#compileExecutor KernelScheduler.compileExecutor}
     * before calling {@link #createPlan}.
     */
    public List<DensityFunctionExecutor> getExecutors() {
        return executors;
    }

    /**
     * Returns all unique noise slot IDs referenced across every kernel in this plan.
     * Call this on the server thread to pre-fetch noise data before handing it off to
     * {@link DensityFunctionExecutor#setNoiseProvider}.
     */
    /**
     * Returns the kernel group label map built from the plan at construction time.
     * Keyed by outputBarrierId (or {@code "(terminal)"}); values are
     * {@code String[]{kind, sourceDFType, inlinedDFTypes}}.
     */
    public Map<String, String[]> getKernelGroupLabels() { return kernelGroupLabels; }

    public Set<String> getNoiseSlotIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (GeneratedKernel k : kernels) {
            ids.addAll(k.noiseSlotIds);
        }
        return ids;
    }

    /**
     * Wires density-function kernel submissions for every entry in {@code yLevelConsumers} into a
     * new {@link ComputePlan}, deduplicating Y-independent ({@link BarrierKind#FLAT_CACHE}) kernel
     * stages so they dispatch only once per chunk column. Terminal readback tasks are registered
     * automatically; the returned plan is ready to submit to the scheduler.
     * <p>
     * Each consumer is called with a 4096-element {@link FloatBuffer} (16×16×16, indexed
     * {@code relZ*256 + relY*16 + relX}) after GPU readback completes.
     *
     * @param chunkX          chunk X section coordinate
     * @param chunkZ          chunk Z section coordinate
     * @param yLevelConsumers map from chunk Y section index → consumer invoked with density data
     * @return fully wired plan including terminal tasks, ready for scheduler submission
     */
    public ComputePlan createPlan(int chunkX, int chunkZ,
            Map<Integer, Consumer<FloatBuffer>> yLevelConsumers) {
        return createPlan(chunkX, chunkZ, yLevelConsumers, null);
    }

    /**
     * Like {@link #createPlan(int, int, Map)} but also notifies a {@link KernelDispatchListener}
     * with the captured output of every intermediate (non-terminal) kernel stage.
     * <p>
     * When {@code listener} is non-null, an extra {@code plan.terminal()} call is registered for
     * each intermediate buffer so its GPU readback data is delivered to the listener inside
     * {@code KernelScheduler.submit()}. The terminal kernel's output is never sent to the
     * listener — it is delivered to the normal consumer and available via the density arrays.
     *
     * @param listener optional capture listener; {@code null} disables capture with no overhead
     */
    public ComputePlan createPlan(int chunkX, int chunkZ,
            Map<Integer, Consumer<FloatBuffer>> yLevelConsumers,
            KernelDispatchListener listener) {
        ComputePlan plan = new ComputePlan();

        // Y-independent buffer descriptors (FLAT_CACHE outputs) — dispatched once, shared by all Y.
        Map<String, BufferDescriptor> sharedBuffers = new HashMap<>();

        // Y-dependent buffer descriptors — one set per Y-level.
        Map<Integer, Map<String, BufferDescriptor>> perYBuffers = new HashMap<>();
        for (Integer y : yLevelConsumers.keySet()) {
            perYBuffers.put(y, new HashMap<>());
        }

        for (int gi = 0; gi < executors.size(); gi++) {
            DensityFunctionExecutor executor = executors.get(gi);
            GeneratedKernel kernel = kernels.get(gi);

            if (executor.isYIndependent()) {
                // FLAT_CACHE: dispatch once — chunkY is registered in the PC struct but never read
                // by PER_COLUMN shaders, so any value is correct; use 0 as a sentinel.
                Map<String, BufferDescriptor> inputs =
                    resolveInputs(kernel, gi, sharedBuffers, null);
                Map<String, BufferDescriptor> outputs =
                    plan.submit(executor, new int[]{chunkX, 0, chunkZ}, inputs);
                BufferDescriptor outDesc = outputs.get("output");
                sharedBuffers.put(kernel.outputBarrierId, outDesc);

                // FLAT_CACHE kernels always write f32.
                if (listener != null) {
                    final GeneratedKernel gk = kernel;
                    final int[] key = {chunkX, 0, chunkZ};
                    plan.terminal(
                        Collections.singletonMap("output", outDesc),
                        buffers -> listener.onKernelOutput(
                            gk.shape, key, gk.outputBarrierId, gk.inputBarrierIds,
                            gk.glslSource, BufferDataType.f32,
                            extractFloats(buffers.get("output"))));
                }

            } else {
                // Y-dependent: dispatch once per Y-level.
                for (Map.Entry<Integer, Consumer<FloatBuffer>> yEntry : yLevelConsumers.entrySet()) {
                    int chunkY = yEntry.getKey();
                    Map<String, BufferDescriptor> inputs =
                        resolveInputs(kernel, gi, sharedBuffers, perYBuffers.get(chunkY));
                    Map<String, BufferDescriptor> outputs =
                        plan.submit(executor, new int[]{chunkX, chunkY, chunkZ}, inputs);

                    if (kernel.outputBarrierId != null) {
                        BufferDescriptor outDesc = outputs.get("output");
                        perYBuffers.get(chunkY).put(kernel.outputBarrierId, outDesc);

                        if (listener != null) {
                            final GeneratedKernel gk = kernel;
                            final int[] key = {chunkX, chunkY, chunkZ};
                            final BufferDataType dtype =
                                executor.isColumnReduce() ? BufferDataType.u32 : BufferDataType.f32;
                            plan.terminal(
                                Collections.singletonMap("output", outDesc),
                                buffers -> listener.onKernelOutput(
                                    gk.shape, key, gk.outputBarrierId, gk.inputBarrierIds,
                                    gk.glslSource, dtype,
                                    extractFloats(buffers.get("output"))));
                        }
                    } else {
                        // Terminal group: wire readback → consumer (and listener if present).
                        Consumer<FloatBuffer> consumer = yEntry.getValue();
                        if (listener != null) {
                            final GeneratedKernel gk = kernel;
                            final int[] key = {chunkX, chunkY, chunkZ};
                            plan.terminal(
                                Collections.singletonMap("output", outputs.get("output")),
                                buffers -> {
                                    // extractFloats reads via a FloatBuffer view; the ByteBuffer
                                    // position is unaffected, so the consumer gets a clean view.
                                    ByteBuffer raw =
                                        buffers.get("output").order(ByteOrder.nativeOrder());
                                    float[] vals = extractFloats(raw);
                                    listener.onKernelOutput(
                                        gk.shape, key, null, gk.inputBarrierIds,
                                        gk.glslSource, BufferDataType.f32, vals);
                                    consumer.accept(raw.asFloatBuffer());
                                });
                        } else {
                            plan.terminal(
                                Collections.singletonMap("output", outputs.get("output")),
                                buffers -> consumer.accept(
                                    buffers.get("output").order(ByteOrder.nativeOrder()).asFloatBuffer()));
                        }
                    }
                }
            }
        }

        return plan;
    }

    /** Copies a ByteBuffer's content into a new float[] using native byte order. */
    private static float[] extractFloats(ByteBuffer buf) {
        buf.order(ByteOrder.nativeOrder());
        FloatBuffer fb = buf.asFloatBuffer();
        float[] arr = new float[fb.remaining()];
        fb.get(arr);
        return arr;
    }

    /**
     * Builds the input buffer map for a kernel group, checking shared (Y-independent) buffers
     * first then per-Y buffers.
     */
    private static Map<String, BufferDescriptor> resolveInputs(
            GeneratedKernel kernel, int groupIndex,
            Map<String, BufferDescriptor> sharedBuffers,
            Map<String, BufferDescriptor> perYBuffers) {
        Map<String, BufferDescriptor> inputs = new HashMap<>();
        for (String inputId : kernel.inputBarrierIds) {
            BufferDescriptor desc = sharedBuffers.get(inputId);
            if (desc == null && perYBuffers != null) {
                desc = perYBuffers.get(inputId);
            }
            if (desc == null) {
                throw new IllegalStateException(
                    "Missing barrier buffer '" + inputId + "' (kernel group " + groupIndex + ")");
            }
            inputs.put(inputId, desc);
        }
        return inputs;
    }
}
