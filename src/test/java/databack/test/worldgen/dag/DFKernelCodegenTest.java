package databack.test.worldgen.dag;

import static org.junit.jupiter.api.Assertions.*;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.*;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.dag.DFDagBuilder;
import databack.common.worldgen.dag.DFKernelPlan;
import databack.common.worldgen.dag.DispatchShape;
import databack.common.worldgen.dag.codegen.DFKernelCodegen;
import databack.common.worldgen.dag.codegen.GeneratedKernel;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for the GLSL density function kernel code generator.
 * <p>
 * All tests are pure Java; no Minecraft/FML runtime code is used.
 */
public class DFKernelCodegenTest {

    // ---- Factory helpers -------------------------------------------------------------------

    private static ConstantFunc constant(float v) {
        ConstantFunc c = new ConstantFunc();
        c.argument = v;
        return c;
    }

    private static CacheOnceUnary cacheOnce(IDensityFunctionFactory arg) {
        CacheOnceUnary c = new CacheOnceUnary();
        c.argument = arg;
        return c;
    }

    private static FlatCacheUnary flatCache(IDensityFunctionFactory arg) {
        FlatCacheUnary f = new FlatCacheUnary();
        f.argument = arg;
        return f;
    }

    private static InterpolatedFunc interpolated(IDensityFunctionFactory arg) {
        InterpolatedFunc i = new InterpolatedFunc();
        i.argument = arg;
        return i;
    }

    private static AbsUnary abs(IDensityFunctionFactory arg) {
        AbsUnary a = new AbsUnary();
        a.argument = arg;
        return a;
    }

    private static CubeUnary cube(IDensityFunctionFactory arg) {
        CubeUnary c = new CubeUnary();
        c.argument = arg;
        return c;
    }

    private static SquareUnary square(IDensityFunctionFactory arg) {
        SquareUnary s = new SquareUnary();
        s.argument = arg;
        return s;
    }

    private static HalfNegativeUnary halfNeg(IDensityFunctionFactory arg) {
        HalfNegativeUnary h = new HalfNegativeUnary();
        h.argument = arg;
        return h;
    }

    private static QuarterNegativeUnary quarterNeg(IDensityFunctionFactory arg) {
        QuarterNegativeUnary q = new QuarterNegativeUnary();
        q.argument = arg;
        return q;
    }

    private static InvertUnary invert(IDensityFunctionFactory arg) {
        InvertUnary i = new InvertUnary();
        i.argument = arg;
        return i;
    }

    private static SqueezeUnary squeeze(IDensityFunctionFactory arg) {
        SqueezeUnary s = new SqueezeUnary();
        s.argument = arg;
        return s;
    }

    private static BlendDensityUnary blendDensity(IDensityFunctionFactory arg) {
        BlendDensityUnary b = new BlendDensityUnary();
        b.argument = arg;
        return b;
    }

    private static AddBinary add(IDensityFunctionFactory a, IDensityFunctionFactory b) {
        AddBinary ab = new AddBinary();
        ab.argument1 = a;
        ab.argument2 = b;
        return ab;
    }

    private static MulBinary mul(IDensityFunctionFactory a, IDensityFunctionFactory b) {
        MulBinary mb = new MulBinary();
        mb.argument1 = a;
        mb.argument2 = b;
        return mb;
    }

    private static MaxBinary max(IDensityFunctionFactory a, IDensityFunctionFactory b) {
        MaxBinary mb = new MaxBinary();
        mb.argument1 = a;
        mb.argument2 = b;
        return mb;
    }

    private static MinBinary min(IDensityFunctionFactory a, IDensityFunctionFactory b) {
        MinBinary mb = new MinBinary();
        mb.argument1 = a;
        mb.argument2 = b;
        return mb;
    }

    private static ClampFunc clamp(IDensityFunctionFactory input, float min, float max) {
        ClampFunc c = new ClampFunc();
        c.input = input;
        c.min = min;
        c.max = max;
        return c;
    }

    private static YClampedGradientFunc yGradient(int fromY, int toY, float fromVal, float toVal) {
        YClampedGradientFunc y = new YClampedGradientFunc();
        y.from_y = fromY;
        y.to_y = toY;
        y.from_value = fromVal;
        y.to_value = toVal;
        return y;
    }

    private static NoiseFunc noise(String id, float xzScale, float yScale) {
        NoiseFunc n = new NoiseFunc();
        n.noise = id;
        n.xz_scale = xzScale;
        n.y_scale = yScale;
        return n;
    }

    private static RangeChoiceFunc rangeChoice(IDensityFunctionFactory input,
                                               float min, float max,
                                               IDensityFunctionFactory inRange,
                                               IDensityFunctionFactory outOfRange) {
        RangeChoiceFunc r = new RangeChoiceFunc();
        r.input = input;
        r.min_inclusive = min;
        r.max_exclusive = max;
        r.when_in_range = inRange;
        r.when_out_of_range = outOfRange;
        return r;
    }

    private static IntervalSelectFunc intervalSelect(IDensityFunctionFactory input,
                                                     float[] thresholds,
                                                     IDensityFunctionFactory... functions) {
        IntervalSelectFunc is = new IntervalSelectFunc();
        is.input = input;
        is.thresholds = thresholds;
        is.functions = functions;
        return is;
    }

    private static FindTopSurfaceFunc findTopSurface(IDensityFunctionFactory density,
                                                      IDensityFunctionFactory upperBound,
                                                      int lowerBound, int cellHeight) {
        FindTopSurfaceFunc f = new FindTopSurfaceFunc();
        f.density = density;
        f.upper_bound = upperBound;
        f.lower_bound = lowerBound;
        f.cell_height = cellHeight;
        return f;
    }

    private static SplineFunc spline(SplineCurve curve) {
        SplineFunc sf = new SplineFunc();
        sf.spline = curve;
        return sf;
    }

    private static SplineCurve splineCurve(IDensityFunctionFactory coord, SplinePoint... points) {
        SplineCurve sc = new SplineCurve();
        sc.coordinate = coord;
        sc.points = points;
        return sc;
    }

    private static SplinePoint splinePoint(float location, float derivative, ISpline value) {
        SplinePoint sp = new SplinePoint();
        sp.location = location;
        sp.derivative = derivative;
        sp.value = value;
        return sp;
    }

    private static SplineValue splineValue(float v) {
        return new SplineValue(v);
    }

    // ---- Helper: compile a root factory to kernels -----------------------------------------

    private static List<GeneratedKernel> compile(IDensityFunctionFactory root) {
        DFKernelPlan plan = DFDagBuilder.build(root);
        return DFKernelCodegen.compile(plan);
    }

    // ---- Tests -----------------------------------------------------------------------------

    /**
     * Test 1: ConstantFunc{3.14f} at root → single terminal kernel.
     * Shape must be PER_VOXEL, outputBarrierId must be null, and GLSL must contain "3.14f".
     */
    @Test
    public void testConstantTerminal() {
        ConstantFunc root = constant(3.14f);
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "Expected exactly one kernel for a constant root");

        GeneratedKernel k = kernels.get(0);
        assertEquals(DispatchShape.PER_VOXEL, k.shape,
            "Terminal kernel shape should be PER_VOXEL");
        assertNull(k.outputBarrierId,
            "Terminal kernel should have null outputBarrierId");
        assertTrue(k.glslSource.contains("3.14f"),
            "GLSL source should contain the constant value '3.14f'");
        assertTrue(k.glslSource.contains("void main()"),
            "GLSL source should contain 'void main()'");
    }

    /**
     * Test 2: AddBinary(ConstantFunc{1.0f}, ConstantFunc{2.0f}) → 1 kernel.
     * GLSL must contain an expression with "(v_" variables and "+" operator.
     */
    @Test
    public void testAddBinary() {
        AddBinary root = add(constant(1.0f), constant(2.0f));
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "Expected exactly one kernel for AddBinary(const, const)");

        GeneratedKernel k = kernels.get(0);
        String glsl = k.glslSource;

        // The two constants are v_0 and v_1, the add result is v_2.
        assertTrue(glsl.contains("v_0 = 1.0f"), "GLSL should contain constant assignment for 1.0f");
        assertTrue(glsl.contains("v_1 = 2.0f"), "GLSL should contain constant assignment for 2.0f");
        // The addition expression should reference the two variables with +.
        assertTrue(glsl.contains("(v_0 + v_1)"),
            "GLSL should contain '(v_0 + v_1)' as the add expression");
    }

    /**
     * Test 3: Individual unary operator expressions.
     */
    @Test
    public void testUnaryOps() {
        // AbsUnary
        {
            AbsUnary root = abs(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("abs(v_0)"), "AbsUnary should emit abs(v_0)");
        }
        // CubeUnary
        {
            CubeUnary root = cube(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("v_0 * v_0 * v_0"), "CubeUnary should emit triple product");
        }
        // SquareUnary
        {
            SquareUnary root = square(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("v_0 * v_0"), "SquareUnary should emit square product");
        }
        // HalfNegativeUnary
        {
            HalfNegativeUnary root = halfNeg(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("0.5f"), "HalfNegativeUnary should contain 0.5f multiplier");
            assertTrue(glsl.contains("< 0.0f"), "HalfNegativeUnary should contain comparison < 0.0f");
        }
        // QuarterNegativeUnary
        {
            QuarterNegativeUnary root = quarterNeg(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("0.25f"), "QuarterNegativeUnary should contain 0.25f multiplier");
            assertTrue(glsl.contains("< 0.0f"), "QuarterNegativeUnary should contain comparison < 0.0f");
        }
        // InvertUnary
        {
            InvertUnary root = invert(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("1.0f / v_0"), "InvertUnary should emit 1.0f / v_0");
        }
        // SqueezeUnary
        {
            SqueezeUnary root = squeeze(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            assertTrue(glsl.contains("clamp(v_0, -1.0f, 1.0f)"),
                "SqueezeUnary should contain clamp expression");
            assertTrue(glsl.contains("24.0f"),
                "SqueezeUnary should contain /24.0f divisor");
        }
        // BlendDensityUnary (pass-through)
        {
            BlendDensityUnary root = blendDensity(constant(1.0f));
            String glsl = compile(root).get(0).glslSource;
            // Result of blend = result of the inner constant, just passed through as v_0.
            assertTrue(glsl.contains("v_0"), "BlendDensityUnary should pass through its argument");
        }
    }

    /**
     * Test 4: Cross-shape read.
     * FlatCacheUnary(ConstantFunc) → PER_COLUMN barrier.
     * MulBinary(flatCache, ConstantFunc) at root → PER_VOXEL terminal kernel that reads the PER_COLUMN buffer.
     * Assert the PER_VOXEL terminal kernel GLSL contains "relZ * 16 + relX" (column index expression).
     */
    @Test
    public void testCrossShapeRead() {
        ConstantFunc inner = constant(1.0f);
        FlatCacheUnary flatCacheNode = flatCache(inner);
        MulBinary root = mul(flatCacheNode, constant(2.0f));

        List<GeneratedKernel> kernels = compile(root);

        // There should be at least 2 kernels: the FLAT_CACHE kernel + the terminal.
        assertTrue(kernels.size() >= 2, "Expected at least 2 kernels for flatCache -> mul tree");

        // The terminal kernel is always last.
        GeneratedKernel terminal = kernels.get(kernels.size() - 1);
        assertNull(terminal.outputBarrierId, "Last kernel should be terminal (null outputBarrierId)");
        assertEquals(DispatchShape.PER_VOXEL, terminal.shape,
            "Terminal kernel should be PER_VOXEL");

        // The terminal reads from the PER_COLUMN flat-cache buffer, so it must use the column index.
        assertTrue(terminal.glslSource.contains("relZ * 16 + relX"),
            "Terminal PER_VOXEL kernel reading a PER_COLUMN buffer should use 'relZ * 16 + relX' index");
    }

    /**
     * Test 5: Interpolated kernel pair.
     * InterpolatedFunc(ConstantFunc{0.5f}) produces two barrier groups (SAMPLE + INTERP) plus
     * the terminal (which reads the INTERP PER_VOXEL buffer).
     * Check shapes and that the INTERP kernel contains trilinear coefficients.
     */
    @Test
    public void testInterpolatedKernel() {
        InterpolatedFunc root = interpolated(constant(0.5f));
        List<GeneratedKernel> kernels = compile(root);

        // Expected: INTERPOLATED_SAMPLE (PER_CORNER), INTERPOLATED_INTERP (PER_VOXEL), terminal (PER_VOXEL).
        assertEquals(3, kernels.size(),
            "InterpolatedFunc should produce 3 kernels: SAMPLE + INTERP + terminal");

        // Verify PER_CORNER kernel exists.
        boolean hasCorner = false;
        for (GeneratedKernel k : kernels) {
            if (k.shape == DispatchShape.PER_CORNER) hasCorner = true;
        }
        assertTrue(hasCorner, "Expected a PER_CORNER kernel for InterpolatedFunc SAMPLE phase");

        // Find the INTERP kernel (PER_VOXEL that has a non-null outputBarrierId).
        GeneratedKernel interpKernel = null;
        for (GeneratedKernel k : kernels) {
            if (k.shape == DispatchShape.PER_VOXEL && k.outputBarrierId != null
                && k.outputBarrierId.startsWith("InterpolatedInterp")) {
                interpKernel = k;
            }
        }
        assertNotNull(interpKernel, "Expected an INTERPOLATED_INTERP kernel");

        String interpGlsl = interpKernel.glslSource;
        assertTrue(interpGlsl.contains("0.25f"),
            "INTERP kernel should contain 0.25f interpolation factors");
        assertTrue(interpGlsl.contains("gz*25"),
            "INTERP kernel should contain 'gz*25' for corner indexing");
        assertTrue(interpGlsl.contains("void main()"),
            "INTERP kernel should contain void main()");
    }

    /**
     * Test 6: COLUMN_REDUCE (FindTopSurfaceFunc).
     * Check that the COLUMN_REDUCE kernel contains the Y-scan loop and integer output.
     * cell_height is baked as a literal (8), not as a variable named "cellHeight".
     */
    @Test
    public void testColumnReduce() {
        // density must be a CacheOnce (PER_VOXEL barrier), upper_bound a FlatCache (PER_COLUMN barrier).
        FindTopSurfaceFunc root = findTopSurface(
            cacheOnce(constant(1.0f)),
            flatCache(constant(64.0f)),
            0,
            8
        );

        List<GeneratedKernel> kernels = compile(root);

        // Find the COLUMN_REDUCE kernel.
        GeneratedKernel columnReduceKernel = null;
        for (GeneratedKernel k : kernels) {
            if (k.outputBarrierId != null && k.outputBarrierId.startsWith("FindTopSurfaceFunc")) {
                columnReduceKernel = k;
            }
        }
        assertNotNull(columnReduceKernel, "Expected a COLUMN_REDUCE kernel for FindTopSurfaceFunc");

        String glsl = columnReduceKernel.glslSource;
        assertTrue(glsl.contains("for (int y"),
            "COLUMN_REDUCE kernel should contain a Y-scan for loop");
        // cell_height=8 is baked as literal "8", not a named variable "cellHeight"
        assertFalse(glsl.contains("cellHeight"),
            "cell_height should be baked as a literal, not a variable named 'cellHeight'");
        assertTrue(glsl.contains("8"),
            "cell_height literal '8' should appear in COLUMN_REDUCE kernel");
        assertTrue(glsl.contains("uint(result)"),
            "COLUMN_REDUCE kernel should write 'uint(result)' to output");
    }

    /**
     * Test 7: NoiseFunc → push constant block and sampleNoise call.
     * Assert glsl contains "constantOffset" (push constant field) and "sampleNoise(".
     */
    @Test
    public void testPushConstantBlock() {
        NoiseFunc root = noise("minecraft:test", 1.0f, 1.0f);
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "Single NoiseFunc should produce one kernel");
        String glsl = kernels.get(0).glslSource;

        assertTrue(glsl.contains("constantOffset"),
            "GLSL should contain 'constantOffset' push constant field for noise table");
        assertTrue(glsl.contains("sampleNoise("),
            "GLSL should contain 'sampleNoise(' call");
    }

    /**
     * Test 8: SplineFunc emission.
     * A 3-point SplineCurve should emit if/else if chain and Hermite coefficient 2.0f.
     */
    @Test
    public void testSplineEmission() {
        // Build a 3-point spline with a constant coordinate.
        SplineCurve curve = splineCurve(
            constant(0.0f),
            splinePoint(-1.0f, 0.0f, splineValue(-0.5f)),
            splinePoint(0.0f,  1.0f, splineValue(0.0f)),
            splinePoint(1.0f,  0.0f, splineValue(0.5f))
        );
        SplineFunc root = spline(curve);
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "SplineFunc should produce a single kernel");
        String glsl = kernels.get(0).glslSource;

        assertTrue(glsl.contains("if ("),
            "Spline GLSL should contain an if block for edge/segment selection");
        // The Hermite basis contains coefficient 2.0f (from "2.0f * u³")
        assertTrue(glsl.contains("2.0f"),
            "Spline GLSL should contain Hermite coefficient 2.0f");
    }

    /**
     * Test 9: Multi-kernel plan with CacheOnce wrapping a NoiseFunc, multiplied by a constant.
     * Expect at least 2 kernels: the CACHE_ONCE kernel + terminal.
     * Terminal should declare the CACHE_ONCE buffer as an input.
     */
    @Test
    public void testMultiKernelPlan() {
        NoiseFunc nf = noise("minecraft:multi_test", 1.0f, 1.0f);
        CacheOnceUnary cached = cacheOnce(nf);
        MulBinary root = mul(cached, constant(0.5f));

        List<GeneratedKernel> kernels = compile(root);

        assertTrue(kernels.size() >= 2,
            "CacheOnce(noise) wrapped in mul should produce at least 2 kernels");

        // The first kernel should be the CACHE_ONCE kernel (non-terminal, writes to barrier buffer).
        GeneratedKernel cacheKernel = kernels.get(0);
        assertNotNull(cacheKernel.outputBarrierId,
            "First kernel should be non-terminal (writes to CACHE_ONCE barrier buffer)");
        assertTrue(cacheKernel.outputBarrierId.startsWith("CacheOnceUnary"),
            "First kernel output should be the CacheOnceUnary barrier");

        // The last kernel is the terminal.
        GeneratedKernel terminal = kernels.get(kernels.size() - 1);
        assertNull(terminal.outputBarrierId, "Last kernel should be terminal");

        // Terminal should read from the CACHE_ONCE barrier.
        assertFalse(terminal.inputBarrierIds.isEmpty(),
            "Terminal kernel should have at least one input (the CACHE_ONCE buffer)");
        assertTrue(terminal.inputBarrierIds.get(0).startsWith("CacheOnceUnary"),
            "Terminal kernel's first input should be the CacheOnceUnary barrier");
    }

    /**
     * Test 10: YClampedGradientFunc — verify mix() and worldBlockY appear in PER_VOXEL kernel.
     */
    @Test
    public void testYClampedGradient() {
        YClampedGradientFunc root = yGradient(-64, 256, 1.0f, 0.0f);
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "YClampedGradientFunc should produce one kernel");
        String glsl = kernels.get(0).glslSource;

        assertTrue(glsl.contains("mix("),
            "YClampedGradient GLSL should use GLSL mix() function");
        assertTrue(glsl.contains("worldBlockY"),
            "YClampedGradient GLSL should reference worldBlockY local variable");
    }

    /**
     * Test 11: RangeChoiceFunc — verify ternary operator in GLSL.
     */
    @Test
    public void testRangeChoice() {
        RangeChoiceFunc root = rangeChoice(
            constant(0.0f),
            -0.5f, 0.5f,
            constant(1.0f),
            constant(-1.0f)
        );
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "RangeChoiceFunc should produce one kernel");
        String glsl = kernels.get(0).glslSource;

        assertTrue(glsl.contains("?"),
            "RangeChoiceFunc GLSL should use ternary '?'");
        assertTrue(glsl.contains(":"),
            "RangeChoiceFunc GLSL should use ternary ':'");
        assertTrue(glsl.contains(">="),
            "RangeChoiceFunc GLSL should contain '>=' for min_inclusive check");
        assertTrue(glsl.contains("<"),
            "RangeChoiceFunc GLSL should contain '<' for max_exclusive check");
    }

    /**
     * Test 12: IntervalSelectFunc with 2 thresholds — verify nested ternary.
     */
    @Test
    public void testIntervalSelect() {
        // 2 thresholds → 3 functions → nested ternary of depth 2
        IntervalSelectFunc root = intervalSelect(
            constant(0.0f),
            new float[]{-0.5f, 0.5f},
            constant(-1.0f), constant(0.0f), constant(1.0f)
        );
        List<GeneratedKernel> kernels = compile(root);

        assertEquals(1, kernels.size(), "IntervalSelectFunc should produce one kernel");
        String glsl = kernels.get(0).glslSource;

        // Expect at least two levels of ternary (two '?' operators).
        long ternaryCount = glsl.chars().filter(c -> c == '?').count();
        assertTrue(ternaryCount >= 2,
            "IntervalSelectFunc with 2 thresholds should have at least 2 ternary operators, found: " + ternaryCount);
        // Each threshold comparison uses '<'.
        assertTrue(glsl.contains("-0.5f"),
            "IntervalSelectFunc should contain first threshold -0.5f");
        assertTrue(glsl.contains("0.5f"),
            "IntervalSelectFunc should contain second threshold 0.5f");
    }

    /**
     * Test 13: All kernels in a moderately complex plan have a void main() entry point.
     * Plan: CacheOnce(Add(NoiseFunc, NoiseFunc)) → Interpolated reads that, terminal reads INTERP.
     */
    @Test
    public void testAllKernelsHaveMain() {
        NoiseFunc n1 = noise("minecraft:noise1", 1.0f, 1.0f);
        NoiseFunc n2 = noise("minecraft:noise2", 0.5f, 0.5f);
        AddBinary sum = add(n1, n2);
        CacheOnceUnary cached = cacheOnce(sum);
        InterpolatedFunc interp = interpolated(cached);

        List<GeneratedKernel> kernels = compile(interp);

        assertTrue(kernels.size() >= 2,
            "Complex tree should produce multiple kernels");

        for (int i = 0; i < kernels.size(); i++) {
            GeneratedKernel k = kernels.get(i);
            assertTrue(k.glslSource.contains("void main()"),
                "Kernel " + i + " (shape=" + k.shape + ", output=" + k.outputBarrierId
                    + ") should contain 'void main()'");
        }
    }
}
