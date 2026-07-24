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
}
