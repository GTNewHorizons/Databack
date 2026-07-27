package databack.common.worldgen.dag;

import static org.junit.jupiter.api.Assertions.*;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.*;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.dag.codegen.KernelBodyEmitter;
import mcgpu.core.hwaccel.buffer.BufferDescriptor;
import mcgpu.core.hwaccel.buffer.BufferLayout;
import mcgpu.core.hwaccel.scheduling.ComputePlan;
import mcgpu.core.hwaccel.scheduling.KernelSubmissionToken;
import mcgpu.core.hwaccel.shader.KernelBuilder;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link DensityFunctionExecutor}.
 * <p>
 * Placed in the same package as the production class to access package-private members
 * ({@code initForTesting}, {@code isYIndependent}).  No Vulkan runtime is required.
 */
public class DensityFunctionExecutorTest {

    // ---- Factory helpers -------------------------------------------------------------------

    private static ConstantFunc constant(float v) {
        ConstantFunc c = new ConstantFunc(); c.argument = v; return c;
    }
    private static FlatCacheUnary flatCache(IDensityFunctionFactory arg) {
        FlatCacheUnary f = new FlatCacheUnary(); f.argument = arg; return f;
    }
    private static CacheOnceUnary cacheOnce(IDensityFunctionFactory arg) {
        CacheOnceUnary c = new CacheOnceUnary(); c.argument = arg; return c;
    }
    private static InterpolatedFunc interpolated(IDensityFunctionFactory arg) {
        InterpolatedFunc i = new InterpolatedFunc(); i.argument = arg; return i;
    }
    private static MulBinary mul(IDensityFunctionFactory a, IDensityFunctionFactory b) {
        MulBinary m = new MulBinary(); m.argument1 = a; m.argument2 = b; return m;
    }
    private static FindTopSurfaceFunc findTopSurface(IDensityFunctionFactory density,
            IDensityFunctionFactory upperBound, int lowerBound, int cellHeight) {
        FindTopSurfaceFunc f = new FindTopSurfaceFunc();
        f.density = density; f.upper_bound = upperBound;
        f.lower_bound = lowerBound; f.cell_height = cellHeight;
        return f;
    }

    // ---- Helper: create and init executor for a specific group ----------------------------

    private static DensityFunctionExecutor initExec(KernelGroup group) {
        DensityFunctionExecutor exec = new DensityFunctionExecutor(group);
        KernelBuilder b = new KernelBuilder(null);
        KernelBodyEmitter.emit(group, b);
        exec.initForTesting(b.inputs, b.outputs);
        return exec;
    }

    // ---- isYIndependent tests --------------------------------------------------------------

    /**
     * A FLAT_CACHE group (FlatCacheUnary) should report Y-independent = true.
     */
    @Test
    public void testIsYIndependent_flatCache() {
        // Group 0 of this plan is the FLAT_CACHE kernel.
        DFKernelPlan plan = DFDagBuilder.build(mul(flatCache(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor exec = new DensityFunctionExecutor(plan.groups().get(0));
        assertTrue(exec.isYIndependent(), "FLAT_CACHE group should be Y-independent");
    }

    /**
     * A CACHE_ONCE group should not be Y-independent.
     */
    @Test
    public void testIsYIndependent_cacheOnce() {
        DFKernelPlan plan = DFDagBuilder.build(mul(cacheOnce(constant(1.0f)), constant(2.0f)));
        DensityFunctionExecutor exec = new DensityFunctionExecutor(plan.groups().get(0));
        assertFalse(exec.isYIndependent(), "CACHE_ONCE group should not be Y-independent");
    }

    /**
     * The INTERPOLATED_SAMPLE (PER_CORNER) group should not be Y-independent.
     */
    @Test
    public void testIsYIndependent_interpolatedSample() {
        // InterpolatedFunc produces INTERPOLATED_SAMPLE as its first group.
        DFKernelPlan plan = DFDagBuilder.build(interpolated(constant(0.5f)));
        DensityFunctionExecutor exec = new DensityFunctionExecutor(plan.groups().get(0));
        assertFalse(exec.isYIndependent(), "INTERPOLATED_SAMPLE group should not be Y-independent");
    }

    /**
     * The terminal group (null output barrier) is never Y-independent.
     */
    @Test
    public void testIsYIndependent_terminal() {
        DFKernelPlan plan = DFDagBuilder.build(constant(1.0f));
        DensityFunctionExecutor exec = new DensityFunctionExecutor(plan.groups().get(0));
        assertFalse(exec.isYIndependent(), "Terminal group should not be Y-independent");
    }

    /**
     * A COLUMN_REDUCE group uses a Y-scan internally and is therefore Y-dependent,
     * even though its output buffer is PER_COLUMN.
     */
    @Test
    public void testIsYIndependent_columnReduce() {
        DFKernelPlan plan = DFDagBuilder.build(
            findTopSurface(cacheOnce(constant(1.0f)), flatCache(constant(64.0f)), 0, 8));
        DensityFunctionExecutor columnReduceExec = null;
        for (KernelGroup g : plan.groups()) {
            if (!g.isTerminal() && g.output().kind() == BarrierKind.COLUMN_REDUCE) {
                columnReduceExec = new DensityFunctionExecutor(g);
            }
        }
        assertNotNull(columnReduceExec, "Precondition: expected a COLUMN_REDUCE group");
        assertFalse(columnReduceExec.isYIndependent(), "COLUMN_REDUCE group should not be Y-independent");
    }

    // ---- getOutputs tests ------------------------------------------------------------------

    /**
     * {@code getOutputs} should describe the "output" buffer when all required inputs are present
     * and match the declared layout.
     */
    @Test
    public void testGetOutputs_validInputs() {
        DFKernelPlan plan = DFDagBuilder.build(mul(flatCache(constant(1.0f)), constant(2.0f)));
        List<KernelGroup> groups = plan.groups();
        KernelGroup terminalGroup = groups.get(groups.size() - 1);

        // Collect the layouts the terminal kernel expects.
        KernelBuilder layoutBuilder = new KernelBuilder(null);
        KernelBodyEmitter.emit(terminalGroup, layoutBuilder);

        DensityFunctionExecutor exec = new DensityFunctionExecutor(terminalGroup);
        exec.initForTesting(layoutBuilder.inputs, layoutBuilder.outputs);

        // Build a fake descriptor whose layout exactly matches the declared input layout.
        String inputId = terminalGroup.reads().get(0).id();
        BufferLayout inputLayout = layoutBuilder.inputs.get(inputId);
        KernelSubmissionToken dummyToken = new KernelSubmissionToken(null, null, 0, 0);
        BufferDescriptor inputDesc = new BufferDescriptor(
            dummyToken, 0,
            inputLayout.dataType(), inputLayout.lenX(), inputLayout.lenY(), inputLayout.lenZ());

        Map<String, BufferDescriptor> inputs = new HashMap<>();
        inputs.put(inputId, inputDesc);

        ComputePlan computePlan = new ComputePlan();
        KernelSubmissionToken token = new KernelSubmissionToken(exec, new int[]{0, 0, 0}, 1, 0);

        Map<String, BufferDescriptor> outputs =
            exec.getOutputs(computePlan, token, new int[]{0, 0, 0}, inputs);

        assertNotNull(outputs.get("output"), "getOutputs should return a descriptor for 'output'");
    }

    /**
     * {@code getOutputs} must throw {@link IllegalStateException} when a required input buffer
     * is absent from the provided map.
     */
    @Test
    public void testGetOutputs_missingInput_throws() {
        DFKernelPlan plan = DFDagBuilder.build(mul(flatCache(constant(1.0f)), constant(2.0f)));
        List<KernelGroup> groups = plan.groups();
        KernelGroup terminalGroup = groups.get(groups.size() - 1);
        DensityFunctionExecutor exec = initExec(terminalGroup);

        ComputePlan computePlan = new ComputePlan();
        KernelSubmissionToken token = new KernelSubmissionToken(exec, new int[]{0, 0, 0}, 1, 0);

        // Pass an empty input map — the required flat-cache buffer is absent.
        assertThrows(IllegalStateException.class,
            () -> exec.getOutputs(computePlan, token, new int[]{0, 0, 0}, new HashMap<>()),
            "Missing input buffer should throw IllegalStateException");
    }
}
