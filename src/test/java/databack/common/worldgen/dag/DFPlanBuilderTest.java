package databack.common.worldgen.dag;

import static org.junit.jupiter.api.Assertions.*;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.*;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.dag.codegen.DFKernelCodegen;
import databack.common.worldgen.dag.codegen.GeneratedKernel;
import databack.common.worldgen.dag.codegen.KernelBodyEmitter;
import com.gtnewhorizon.gtnhlib.space.ImmutableXYZ;
import mcgpu.core.hwaccel.buffer.BufferDescriptor;
import mcgpu.core.hwaccel.scheduling.ComputePlan;
import mcgpu.core.hwaccel.scheduling.KernelJob;
import mcgpu.core.hwaccel.shader.KernelBuilder;

import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unit tests for {@link DFPlanBuilder}.
 * <p>
 * All tests are pure Java; no Vulkan runtime is required.  The tests are placed in the same
 * package as the production class to access its package-private constructor and to call
 * {@link DensityFunctionExecutor#initForTesting}.
 */
public class DFPlanBuilderTest {

    // ---- Factory helpers -------------------------------------------------------------------

    private static ConstantFunc constant(float v) {
        return new ConstantFunc(v);
    }
    private static FlatCacheUnary flatCache(IDensityFunctionFactory arg) {
        return new FlatCacheUnary(arg);
    }
    private static CacheOnceUnary cacheOnce(IDensityFunctionFactory arg) {
        return new CacheOnceUnary(arg);
    }
    private static MulBinary mul(IDensityFunctionFactory a, IDensityFunctionFactory b) {
        return new MulBinary(a, b);
    }

    // ---- Helper: build a test-ready DFPlanBuilder without Vulkan --------------------------

    /**
     * Builds a {@link DFPlanBuilder} from the given root, initialising each executor with
     * {@link DensityFunctionExecutor#initForTesting} so that {@link DensityFunctionExecutor#getOutputs}
     * can be called without a compiled Vulkan pipeline.
     */
    private static DFPlanBuilder buildForTesting(IDensityFunctionFactory root) {
        DFKernelPlan kernelPlan = DFDagBuilder.build(root);
        List<GeneratedKernel> kernels = DFKernelCodegen.compile(kernelPlan);
        List<DensityFunctionExecutor> executors = new ArrayList<>(kernelPlan.groups().size());
        for (KernelGroup group : kernelPlan.groups()) {
            DensityFunctionExecutor exec = new DensityFunctionExecutor(group);
            KernelBuilder b = new KernelBuilder(null);
            KernelBodyEmitter.emit(group, b);
            exec.initForTesting(b.inputs, b.outputs);
            executors.add(exec);
        }
        return new DFPlanBuilder(executors, kernels, kernelPlan.buildKernelGroupLabels());
    }

    /** Returns all jobs in the plan whose executor is {@code exec}. */
    private static List<KernelJob> jobsFor(ComputePlan plan, DensityFunctionExecutor exec) {
        List<KernelJob> result = new ArrayList<>();
        for (KernelJob job : (Collection<KernelJob>) plan.submits.values()) {
            if (job.submission().executor() == exec) result.add(job);
        }
        return result;
    }

    // ---- Tests -----------------------------------------------------------------------------

    /**
     * A FLAT_CACHE kernel must be submitted exactly once per chunk column, regardless of how many
     * Y-levels are requested.
     */
    @Test
    public void testFlatCacheDispatchedOnce() {
        // 2-group plan: FLAT_CACHE (group 0) + terminal (group 1).
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor flatCacheExec = builder.getExecutors().get(0);
        assertTrue(flatCacheExec.isYIndependent(), "Precondition: executor 0 must be Y-independent");

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(0, buf -> {}); consumers.put(1, buf -> {}); consumers.put(2, buf -> {});

        ComputePlan plan = builder.createPlan(5, 7, consumers);

        assertEquals(1, jobsFor(plan, flatCacheExec).size(),
            "FLAT_CACHE kernel should be submitted exactly once for 3 Y-levels");
    }

    /**
     * Y-dependent (non-FLAT_CACHE) kernels must be submitted once per requested Y-level.
     */
    @Test
    public void testYDependentDispatchedPerLevel() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor terminalExec = builder.getExecutors().get(1);

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(0, buf -> {}); consumers.put(4, buf -> {}); consumers.put(8, buf -> {});

        ComputePlan plan = builder.createPlan(5, 7, consumers);

        assertEquals(3, jobsFor(plan, terminalExec).size(),
            "Terminal kernel should be submitted once per Y-level (3)");
    }

    /**
     * Total submission count for a 2-group plan (1 FLAT_CACHE + N Y-levels) is N+1.
     */
    @Test
    public void testTotalSubmissions() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        for (int y = 0; y < 5; y++) consumers.put(y, buf -> {});

        ComputePlan plan = builder.createPlan(0, 0, consumers);

        assertEquals(6, plan.submits.size(),
            "Total submits = 1 (FLAT_CACHE) + 5 (Y-levels)");
    }

    /**
     * The number of registered terminal tasks must equal the number of Y-levels.
     */
    @Test
    public void testTerminalCountMatchesYLevels() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(0, buf -> {}); consumers.put(1, buf -> {});

        ComputePlan plan = builder.createPlan(0, 0, consumers);

        assertEquals(2, plan.terminals.size(),
            "Terminal task count should equal the number of Y-levels (2)");
    }

    /**
     * The FLAT_CACHE output {@link BufferDescriptor} must be the exact same object instance
     * used as an input in every Y-level submission that depends on it.
     */
    @Test
    public void testSharedBufferDescriptorForFlatCache() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor flatCacheExec = builder.getExecutors().get(0);
        DensityFunctionExecutor terminalExec  = builder.getExecutors().get(1);

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(0, buf -> {}); consumers.put(1, buf -> {});

        ComputePlan plan = builder.createPlan(3, 7, consumers);

        // Obtain the single FLAT_CACHE output descriptor.
        List<KernelJob> flatCacheJobs = jobsFor(plan, flatCacheExec);
        assertEquals(1, flatCacheJobs.size(), "Precondition: one FLAT_CACHE job");
        BufferDescriptor sharedDesc = flatCacheJobs.get(0).outputs().get("output");
        assertNotNull(sharedDesc, "Precondition: FLAT_CACHE job must have an 'output' descriptor");

        // Every terminal job's single input must be the identical descriptor object.
        List<KernelJob> terminalJobs = jobsFor(plan, terminalExec);
        assertEquals(2, terminalJobs.size(), "Precondition: two terminal jobs");
        for (KernelJob tJob : terminalJobs) {
            assertEquals(1, tJob.inputs().size(), "Terminal job should have exactly one input");
            BufferDescriptor inputDesc = tJob.inputs().values().iterator().next();
            assertSame(sharedDesc, inputDesc,
                "All Y-level submissions must share the same FLAT_CACHE BufferDescriptor instance");
        }
    }

    /**
     * The FLAT_CACHE submission must use {@code chunkY = 0} as the Y sentinel regardless of the
     * actual Y-levels requested.
     */
    @Test
    public void testFlatCacheUsesChunkYZero() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor flatCacheExec = builder.getExecutors().get(0);

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(5, buf -> {});

        ComputePlan plan = builder.createPlan(3, 7, consumers);

        List<KernelJob> flatCacheJobs = jobsFor(plan, flatCacheExec);
        assertEquals(1, flatCacheJobs.size());
        ImmutableXYZ key = (ImmutableXYZ) flatCacheJobs.get(0).submission().key();
        assertEquals(3, key.getX(), "chunkX should be 3");
        assertEquals(0, key.getY(), "FLAT_CACHE chunkY sentinel should be 0");
        assertEquals(7, key.getZ(), "chunkZ should be 7");
    }

    /**
     * Y-dependent submissions must carry the correct chunk coordinates.
     */
    @Test
    public void testYDependentKeyValues() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor terminalExec = builder.getExecutors().get(1);

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(4, buf -> {});

        ComputePlan plan = builder.createPlan(2, 6, consumers);

        List<KernelJob> terminalJobs = jobsFor(plan, terminalExec);
        assertEquals(1, terminalJobs.size());
        ImmutableXYZ key = (ImmutableXYZ) terminalJobs.get(0).submission().key();
        assertEquals(2, key.getX(), "chunkX should be 2");
        assertEquals(4, key.getY(), "chunkY should be the requested Y-level (4)");
        assertEquals(6, key.getZ(), "chunkZ should be 6");
    }

    /**
     * When no FLAT_CACHE group is present every kernel is dispatched once per Y-level.
     */
    @Test
    public void testNoFlatCache_allYDependent() {
        // cacheOnce → CACHE_ONCE (Y-dependent), 2 groups: CACHE_ONCE + terminal.
        DFPlanBuilder builder = buildForTesting(mul(cacheOnce(constant(1.0f)), constant(2.0f)));

        Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
        consumers.put(0, buf -> {}); consumers.put(1, buf -> {});

        ComputePlan plan = builder.createPlan(0, 0, consumers);

        // 2 groups × 2 Y-levels = 4 submissions; 2 terminal tasks.
        assertEquals(4, plan.submits.size(),
            "All Y-dependent: 2 groups × 2 Y-levels = 4 submits");
        assertEquals(2, plan.terminals.size(),
            "2 terminal tasks for 2 Y-levels");
    }

    /**
     * An empty Y-level map produces a plan with only the FLAT_CACHE submission and no terminals.
     */
    @Test
    public void testEmptyYLevelMap() {
        DFPlanBuilder builder = buildForTesting(mul(flatCache(constant(1.0f)), constant(2.0f)));

        ComputePlan plan = builder.createPlan(0, 0, new HashMap<>());

        assertEquals(1, plan.submits.size(),
            "Only the FLAT_CACHE submission should be present when no Y-levels requested");
        assertEquals(0, plan.terminals.size(), "No terminals when no Y-levels requested");
    }
}
