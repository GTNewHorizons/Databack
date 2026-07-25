package databack.test;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.density_function.IDensityFunction;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.MiscAdapters;
import databack.common.worldgen.compiler.DensityFunctionCompiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that the {@link DensityFunctionCompiler} produces functions with
 * identical semantics to their interpreted counterparts.
 *
 * <p>Each test parses a small JSON tree, compiles it, and asserts that
 * {@code compiled.compute(null, x, y, z)} equals the interpreted result.
 * Inlineable nodes never touch the WorldContext, so {@code null} is safe.
 */
class DensityFunctionCompilerTest {

    private static Gson gson;
    private static final float DELTA = 1e-5f;

    @BeforeAll
    static void initSerde() {
        DatapackSerialization.resetForTesting();
        DatapackSerialization.init();
        MiscAdapters.init();
        BuiltinDensityFunctions.init();
        DatapackSerialization.finish();
        gson = DatapackSerialization.getGson();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IDensityFunction parse(String json) {
        return gson.fromJson(json, IDensityFunction.class);
    }

    private IDensityFunction compiled(String json) {
        return DensityFunctionCompiler.compile(parse(json));
    }

    private static void assertCompute(IDensityFunction df, float expected, float x, float y, float z) {
        assertEquals(expected, df.compute(null, x, y, z), DELTA);
    }

    // ── ConstantFunc ─────────────────────────────────────────────────────────

    @Test
    void constant_positive() {
        IDensityFunction c = compiled("5.0");
        assertCompute(c, 5.0f, 0, 0, 0);
        assertCompute(c, 5.0f, 100, 200, 300);
    }

    @Test
    void constant_negative() {
        assertCompute(compiled("-3.5"), -3.5f, 0, 0, 0);
    }

    @Test
    void constant_zero() {
        assertCompute(compiled("0.0"), 0.0f, 0, 0, 0);
    }

    // ── AddBinary ────────────────────────────────────────────────────────────

    @Test
    void add_twoConstants() {
        assertCompute(compiled("{\"type\":\"minecraft:add\",\"argument1\":3.0,\"argument2\":2.0}"),
                5.0f, 0, 0, 0);
    }

    @Test
    void add_negativeResult() {
        assertCompute(compiled("{\"type\":\"minecraft:add\",\"argument1\":-10.0,\"argument2\":3.0}"),
                -7.0f, 0, 0, 0);
    }

    // ── MulBinary ────────────────────────────────────────────────────────────

    @Test
    void mul_twoConstants() {
        assertCompute(compiled("{\"type\":\"minecraft:mul\",\"argument1\":3.0,\"argument2\":4.0}"),
                12.0f, 0, 0, 0);
    }

    @Test
    void mul_byZero() {
        assertCompute(compiled("{\"type\":\"minecraft:mul\",\"argument1\":999.0,\"argument2\":0.0}"),
                0.0f, 0, 0, 0);
    }

    // ── MaxBinary ────────────────────────────────────────────────────────────

    @Test
    void max_returnsLarger() {
        assertCompute(compiled("{\"type\":\"minecraft:max\",\"argument1\":3.0,\"argument2\":7.0}"),
                7.0f, 0, 0, 0);
    }

    @Test
    void max_equalInputs() {
        assertCompute(compiled("{\"type\":\"minecraft:max\",\"argument1\":5.0,\"argument2\":5.0}"),
                5.0f, 0, 0, 0);
    }

    // ── MinBinary ────────────────────────────────────────────────────────────

    @Test
    void min_returnsSmaller() {
        assertCompute(compiled("{\"type\":\"minecraft:min\",\"argument1\":3.0,\"argument2\":7.0}"),
                3.0f, 0, 0, 0);
    }

    // ── AbsUnary ─────────────────────────────────────────────────────────────

    @Test
    void abs_negative() {
        assertCompute(compiled("{\"type\":\"minecraft:abs\",\"argument\":-4.0}"),
                4.0f, 0, 0, 0);
    }

    @Test
    void abs_positive_passthrough() {
        assertCompute(compiled("{\"type\":\"minecraft:abs\",\"argument\":3.0}"),
                3.0f, 0, 0, 0);
    }

    // ── SquareUnary ──────────────────────────────────────────────────────────

    @Test
    void square_result() {
        assertCompute(compiled("{\"type\":\"minecraft:square\",\"argument\":3.0}"),
                9.0f, 0, 0, 0);
    }

    @Test
    void square_negative_input() {
        assertCompute(compiled("{\"type\":\"minecraft:square\",\"argument\":-3.0}"),
                9.0f, 0, 0, 0);
    }

    // ── CubeUnary ────────────────────────────────────────────────────────────

    @Test
    void cube_result() {
        assertCompute(compiled("{\"type\":\"minecraft:cube\",\"argument\":2.0}"),
                8.0f, 0, 0, 0);
    }

    @Test
    void cube_negative_input() {
        assertCompute(compiled("{\"type\":\"minecraft:cube\",\"argument\":-2.0}"),
                -8.0f, 0, 0, 0);
    }

    // ── InvertUnary ──────────────────────────────────────────────────────────

    @Test
    void invert_result() {
        assertCompute(compiled("{\"type\":\"minecraft:invert\",\"argument\":4.0}"),
                0.25f, 0, 0, 0);
    }

    @Test
    void invert_one() {
        assertCompute(compiled("{\"type\":\"minecraft:invert\",\"argument\":1.0}"),
                1.0f, 0, 0, 0);
    }

    // ── BlendDensityUnary ────────────────────────────────────────────────────

    @Test
    void blendDensity_passthrough() {
        assertCompute(compiled("{\"type\":\"minecraft:blend_density\",\"argument\":7.5}"),
                7.5f, 0, 0, 0);
    }

    // ── CacheAllInCellUnary ──────────────────────────────────────────────────

    @Test
    void cacheAllInCell_passthrough() {
        assertCompute(compiled("{\"type\":\"minecraft:cache_all_in_cell\",\"argument\":2.5}"),
                2.5f, 0, 0, 0);
    }

    // ── SlideUnary ───────────────────────────────────────────────────────────

    @Test
    void slide_alwaysZero() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:slide\",\"argument\":999.0}");
        assertCompute(c, 0.0f, 0, 0, 0);
        assertCompute(c, 0.0f, 100, 200, 300);
    }

    // ── HalfNegativeUnary ────────────────────────────────────────────────────

    @Test
    void halfNegative_negative_halved() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:half_negative\",\"argument\":-4.0}");
        assertCompute(c, -2.0f, 0, 0, 0);
    }

    @Test
    void halfNegative_positive_unchanged() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:half_negative\",\"argument\":6.0}");
        assertCompute(c, 6.0f, 0, 0, 0);
    }

    @Test
    void halfNegative_zero_unchanged() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:half_negative\",\"argument\":0.0}");
        assertCompute(c, 0.0f, 0, 0, 0);
    }

    // ── QuarterNegativeUnary ─────────────────────────────────────────────────

    @Test
    void quarterNegative_negative_quartered() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:quarter_negative\",\"argument\":-8.0}");
        assertCompute(c, -2.0f, 0, 0, 0);
    }

    @Test
    void quarterNegative_positive_unchanged() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:quarter_negative\",\"argument\":6.0}");
        assertCompute(c, 6.0f, 0, 0, 0);
    }

    // ── SqueezeUnary ─────────────────────────────────────────────────────────

    @Test
    void squeeze_zero() {
        // t=0: 0/2 - 0^3/24 = 0
        assertCompute(compiled("{\"type\":\"minecraft:squeeze\",\"argument\":0.0}"),
                0.0f, 0, 0, 0);
    }

    @Test
    void squeeze_atClampBoundary_1() {
        // t=1: 1/2 - 1^3/24 = 0.5 - 1/24 ≈ 0.4583
        IDensityFunction interp = parse("{\"type\":\"minecraft:squeeze\",\"argument\":1.0}");
        IDensityFunction comp = DensityFunctionCompiler.compile(interp);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    @Test
    void squeeze_aboveClamp_clampsTo1() {
        // Input 5.0 is clamped to 1.0; result should equal squeeze(1.0)
        IDensityFunction above = compiled("{\"type\":\"minecraft:squeeze\",\"argument\":5.0}");
        IDensityFunction at1   = compiled("{\"type\":\"minecraft:squeeze\",\"argument\":1.0}");
        assertEquals(at1.compute(null, 0, 0, 0), above.compute(null, 0, 0, 0), DELTA);
    }

    @Test
    void squeeze_matchesInterpreted() {
        IDensityFunction interp = parse("{\"type\":\"minecraft:squeeze\",\"argument\":0.6}");
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    // ── ClampFunc ────────────────────────────────────────────────────────────

    @Test
    void clamp_withinRange() {
        assertCompute(compiled("{\"type\":\"minecraft:clamp\",\"input\":2.0,\"min\":0.0,\"max\":5.0}"),
                2.0f, 0, 0, 0);
    }

    @Test
    void clamp_belowMin() {
        assertCompute(compiled("{\"type\":\"minecraft:clamp\",\"input\":-1.0,\"min\":0.0,\"max\":5.0}"),
                0.0f, 0, 0, 0);
    }

    @Test
    void clamp_aboveMax() {
        assertCompute(compiled("{\"type\":\"minecraft:clamp\",\"input\":10.0,\"min\":0.0,\"max\":5.0}"),
                5.0f, 0, 0, 0);
    }

    // ── YClampedGradientFunc ─────────────────────────────────────────────────

    @Test
    void yClampedGradient_midpoint() {
        // from_y=0, to_y=256, from_value=0, to_value=1 → at y=128 → 0.5
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:y_clamped_gradient\","
                + "\"from_y\":0,\"to_y\":256,\"from_value\":0.0,\"to_value\":1.0}");
        assertCompute(c, 0.5f, 0, 128, 0);
    }

    @Test
    void yClampedGradient_atFromY() {
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:y_clamped_gradient\","
                + "\"from_y\":64,\"to_y\":256,\"from_value\":-1.0,\"to_value\":1.0}");
        assertCompute(c, -1.0f, 0, 64, 0);
    }

    @Test
    void yClampedGradient_matchesInterpreted() {
        String json = "{\"type\":\"minecraft:y_clamped_gradient\","
                + "\"from_y\":-64,\"to_y\":320,\"from_value\":-0.078125,\"to_value\":0.328125}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        for (float y : new float[]{-64, 0, 64, 128, 192, 256, 320}) {
            assertEquals(interp.compute(null, 0, y, 0), comp.compute(null, 0, y, 0), DELTA,
                    "mismatch at y=" + y);
        }
    }

    // ── Opaque delegation ────────────────────────────────────────────────────

    @Test
    void opaque_delegatesToOriginal() {
        // A lambda's class name won't match any inlineable type, so the compiler
        // treats it as opaque. The compiled wrapper must call through to it.
        IDensityFunction stub = (ctx, x, y, z) -> 42.0f;
        IDensityFunction comp = DensityFunctionCompiler.compile(stub);
        assertCompute(comp, 42.0f, 0, 0, 0);
        assertCompute(comp, 42.0f, 999, -64, 999);
    }

    // ── Nested tree ──────────────────────────────────────────────────────────

    @Test
    void nestedTree_addMulAbs() {
        // abs(add(mul(2, 3), -10)) = abs(6 - 10) = abs(-4) = 4
        String json = "{\"type\":\"minecraft:abs\",\"argument\":"
                + "{\"type\":\"minecraft:add\",\"argument1\":"
                + "{\"type\":\"minecraft:mul\",\"argument1\":2.0,\"argument2\":3.0},"
                + "\"argument2\":-10.0}}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
        assertEquals(4.0f, comp.compute(null, 0, 0, 0), DELTA);
    }

    @Test
    void deepTree_doesNotCrash() {
        // 200 nested adds: add(add(add(..., 1), 1), 1) with a constant 0 at the bottom.
        // Result should be 200.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("{\"type\":\"minecraft:add\",\"argument1\":");
        }
        sb.append("0.0");
        for (int i = 0; i < 200; i++) {
            sb.append(",\"argument2\":1.0}");
        }
        IDensityFunction interp = parse(sb.toString());
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(200.0f, comp.compute(null, 0, 0, 0), DELTA);
    }

    // ── RangeChoiceFunc ──────────────────────────────────────────────────────

    @Test
    void rangeChoice_inRange() {
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:range_choice\",\"input\":5.0," +
                "\"min_inclusive\":0.0,\"max_exclusive\":10.0," +
                "\"when_in_range\":100.0,\"when_out_of_range\":-100.0}");
        assertCompute(c, 100.0f, 0, 0, 0);
    }

    @Test
    void rangeChoice_belowMin_outOfRange() {
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:range_choice\",\"input\":-1.0," +
                "\"min_inclusive\":0.0,\"max_exclusive\":10.0," +
                "\"when_in_range\":100.0,\"when_out_of_range\":-100.0}");
        assertCompute(c, -100.0f, 0, 0, 0);
    }

    @Test
    void rangeChoice_atMaxExclusive_outOfRange() {
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:range_choice\",\"input\":10.0," +
                "\"min_inclusive\":0.0,\"max_exclusive\":10.0," +
                "\"when_in_range\":100.0,\"when_out_of_range\":-100.0}");
        assertCompute(c, -100.0f, 0, 0, 0);
    }

    @Test
    void rangeChoice_matchesInterpreted() {
        String json = "{\"type\":\"minecraft:range_choice\",\"input\":3.0," +
                "\"min_inclusive\":2.0,\"max_exclusive\":5.0," +
                "\"when_in_range\":7.0,\"when_out_of_range\":-7.0}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    // ── IntervalSelectFunc ────────────────────────────────────────────────────

    @Test
    void intervalSelect_picksMidFunction() {
        // thresholds=[0,1,2,3], functions=[f0,f1,f2,f3,f4], input=2.5 → 2.5 < 3.0 → functions[3]=30
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:interval_select\",\"input\":2.5," +
                "\"thresholds\":[0.0,1.0,2.0,3.0]," +
                "\"functions\":[0.0,10.0,20.0,30.0,40.0]}");
        assertCompute(c, 30.0f, 0, 0, 0);
    }

    @Test
    void intervalSelect_belowAllThresholds_picksFirst() {
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:interval_select\",\"input\":-5.0," +
                "\"thresholds\":[0.0,1.0,2.0]," +
                "\"functions\":[99.0,10.0,20.0,30.0]}");
        assertCompute(c, 99.0f, 0, 0, 0);
    }

    @Test
    void intervalSelect_aboveAllThresholds_picksLast() {
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:interval_select\",\"input\":100.0," +
                "\"thresholds\":[0.0,1.0,2.0]," +
                "\"functions\":[0.0,10.0,20.0,77.0]}");
        assertCompute(c, 77.0f, 0, 0, 0);
    }

    @Test
    void intervalSelect_matchesInterpreted() {
        String json = "{\"type\":\"minecraft:interval_select\",\"input\":1.5," +
                "\"thresholds\":[0.0,1.0,2.0,3.0]," +
                "\"functions\":[5.0,15.0,25.0,35.0,45.0]}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    // ── FindTopSurfaceFunc ────────────────────────────────────────────────────

    @Test
    void findTopSurface_hitsImmediately() {
        // density=1 (always positive) → hits at first y = (int)upper_bound = 10
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:find_top_surface\"," +
                "\"density\":1.0,\"upper_bound\":10.0," +
                "\"lower_bound\":0,\"cell_height\":2}");
        assertCompute(c, 10.0f, 0, 0, 0);
    }

    @Test
    void findTopSurface_neverHits_returnsLowerBound() {
        // density=-1 (never positive) → exhausts loop → returns lower_bound=0
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:find_top_surface\"," +
                "\"density\":-1.0,\"upper_bound\":10.0," +
                "\"lower_bound\":0,\"cell_height\":4}");
        assertCompute(c, 0.0f, 0, 0, 0);
    }

    @Test
    void findTopSurface_matchesInterpreted() {
        // density=1 above y=5, -1 below. Step of 2 from upper_bound=8.
        // Loop: y=8 → density(8)=1 → returns 8.
        String json = "{\"type\":\"minecraft:find_top_surface\"," +
                "\"density\":1.0,\"upper_bound\":8.0," +
                "\"lower_bound\":0,\"cell_height\":2}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    // ── InterpolatedFunc ─────────────────────────────────────────────────────

    @Test
    void interpolated_constantArg_returnsConstant() {
        // All 8 corners evaluate to 7.0, so trilinear result is also 7.0
        IDensityFunction c = compiled(
                "{\"type\":\"minecraft:interpolated\",\"argument\":7.0}");
        assertCompute(c, 7.0f, 0, 0, 0);
        assertCompute(c, 7.0f, 10, 20, 30);
    }

    @Test
    void interpolated_withGradient_matchesInterpreted() {
        // Argument is y_clamped_gradient that maps y→y (identity).
        // At (2,2,2): blockY2=0, ky=0.5, corners at y=0 and y=4 → trilinear Y-average.
        String json =
                "{\"type\":\"minecraft:interpolated\",\"argument\":" +
                "{\"type\":\"minecraft:y_clamped_gradient\"," +
                "\"from_y\":0,\"to_y\":256,\"from_value\":0.0,\"to_value\":256.0}}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        for (float y : new float[]{0, 1, 2, 3, 4, 8, 16, 100}) {
            assertEquals(interp.compute(null, 0, y, 0), comp.compute(null, 0, y, 0), DELTA,
                    "mismatch at y=" + y);
        }
    }

    // ── SplineFunc / SplineCurve ──────────────────────────────────────────────

    @Test
    void splineValue_asSplineFunc() {
        IDensityFunction c = compiled("{\"type\":\"minecraft:spline\",\"spline\":3.14}");
        assertCompute(c, 3.14f, 0, 0, 0);
        assertCompute(c, 3.14f, 100, 200, 300);
    }

    @Test
    void splineCurve_midpointHermite() {
        // 2-point spline [loc=0,val=0,deriv=0] and [loc=1,val=1,deriv=0].
        // At t=0.5: Hermite interpolation yields 0.5 (smooth S-curve midpoint).
        String json =
                "{\"type\":\"minecraft:spline\",\"spline\":{" +
                "\"coordinate\":0.5," +
                "\"points\":[" +
                "{\"location\":0.0,\"derivative\":0.0,\"value\":0.0}," +
                "{\"location\":1.0,\"derivative\":0.0,\"value\":1.0}" +
                "]}}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertEquals(0.5f, comp.compute(null, 0, 0, 0), DELTA);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    @Test
    void splineCurve_lowerClamp() {
        // t=-1 is below loc=0 → returns points[0].value = 5.0
        String json =
                "{\"type\":\"minecraft:spline\",\"spline\":{" +
                "\"coordinate\":-1.0," +
                "\"points\":[" +
                "{\"location\":0.0,\"derivative\":0.0,\"value\":5.0}," +
                "{\"location\":1.0,\"derivative\":0.0,\"value\":9.0}" +
                "]}}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertCompute(comp, 5.0f, 0, 0, 0);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    @Test
    void splineCurve_upperClamp() {
        // t=2.0 is above loc=1.0 → returns points[last].value = 9.0
        String json =
                "{\"type\":\"minecraft:spline\",\"spline\":{" +
                "\"coordinate\":2.0," +
                "\"points\":[" +
                "{\"location\":0.0,\"derivative\":0.0,\"value\":5.0}," +
                "{\"location\":1.0,\"derivative\":0.0,\"value\":9.0}" +
                "]}}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        assertCompute(comp, 9.0f, 0, 0, 0);
        assertEquals(interp.compute(null, 0, 0, 0), comp.compute(null, 0, 0, 0), DELTA);
    }

    @Test
    void splineCurve_threePoints_matchesInterpreted() {
        // 3-point spline. For many t values, compiled result must match interpreted.
        // Uses a y_clamped_gradient as the coordinate so t varies with y.
        String json =
                "{\"type\":\"minecraft:spline\",\"spline\":{" +
                "\"coordinate\":{\"type\":\"minecraft:y_clamped_gradient\"," +
                "\"from_y\":0,\"to_y\":100,\"from_value\":0.0,\"to_value\":1.0}," +
                "\"points\":[" +
                "{\"location\":0.0,\"derivative\":0.0,\"value\":0.0}," +
                "{\"location\":0.5,\"derivative\":1.0,\"value\":0.5}," +
                "{\"location\":1.0,\"derivative\":0.0,\"value\":1.0}" +
                "]}}";
        IDensityFunction interp = parse(json);
        IDensityFunction comp   = DensityFunctionCompiler.compile(interp);
        for (float y : new float[]{0, 10, 25, 50, 75, 90, 100}) {
            assertEquals(interp.compute(null, 0, y, 0), comp.compute(null, 0, y, 0), DELTA,
                    "mismatch at y=" + y);
        }
    }

    // ── Noise function smoke tests (compilation only) ─────────────────────────

    @Test
    void noiseFunc_compilesSuccessfully() {
        IDensityFunction interp = parse(
                "{\"type\":\"minecraft:noise\",\"noise\":\"minecraft:temperature\"," +
                "\"xz_scale\":1.0,\"y_scale\":0.0}");
        IDensityFunction result = DensityFunctionCompiler.compile(interp);
        assertNotNull(result);
        assertNotSame(interp, result);
    }

    @Test
    void shiftFunc_compilesSuccessfully() {
        IDensityFunction interp = parse(
                "{\"type\":\"minecraft:shift\",\"argument\":\"minecraft:shift\"}");
        IDensityFunction result = DensityFunctionCompiler.compile(interp);
        assertNotNull(result);
    }

    @Test
    void shiftedNoiseFunc_compilesSuccessfully() {
        IDensityFunction interp = parse(
                "{\"type\":\"minecraft:shifted_noise\",\"noise\":\"minecraft:temperature\"," +
                "\"xz_scale\":1.0,\"y_scale\":0.0," +
                "\"shift_x\":{\"type\":\"minecraft:shift_a\",\"argument\":\"minecraft:shift\"}," +
                "\"shift_y\":0.0," +
                "\"shift_z\":{\"type\":\"minecraft:shift_b\",\"argument\":\"minecraft:shift\"}}");
        IDensityFunction result = DensityFunctionCompiler.compile(interp);
        assertNotNull(result);
    }

    @Test
    void weirdScaledSampler_type1_compilesSuccessfully() {
        IDensityFunction interp = parse(
                "{\"type\":\"minecraft:weird_scaled_sampler\"," +
                "\"noise\":\"minecraft:erosion\"," +
                "\"rarity_value_mapper\":\"type_1\"," +
                "\"input\":0.5}");
        IDensityFunction result = DensityFunctionCompiler.compile(interp);
        assertNotNull(result);
    }

    @Test
    void weirdScaledSampler_type2_compilesSuccessfully() {
        IDensityFunction interp = parse(
                "{\"type\":\"minecraft:weird_scaled_sampler\"," +
                "\"noise\":\"minecraft:erosion\"," +
                "\"rarity_value_mapper\":\"type_2\"," +
                "\"input\":0.0}");
        IDensityFunction result = DensityFunctionCompiler.compile(interp);
        assertNotNull(result);
    }
}
