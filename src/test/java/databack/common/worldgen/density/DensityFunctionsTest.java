package databack.common.worldgen.density;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.AbsUnary;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.ConstantFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.DensityFunctionRef;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.SquareUnary;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.YClampedGradientFunc;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.worldgen.density.TransformNode.Kind;

class DensityFunctionsTest {

    private static final DensityNode Y = Density.yGradient(-64, 320, -1.0, 1.0);

    private static double at(DensityNode f, int x, int y, int z) {
        return f.sample(x, y, z);
    }

    static DensityBuildContext context(Map<String, IDensityFunctionFactory> functions) {
        Map<String, DatapackNoise> noises = new HashMap<>();
        DatapackNoise n = new DatapackNoise();
        n.firstOctave = -4;
        n.amplitudes = new double[] { 1, 1, 0, 1 };
        noises.put("minecraft:test", n);
        return new DensityBuildContext(1234L, false, noises::get, functions::get);
    }

    static DensityNode testNoise(double xzScale, double yScale) {
        return Density.noise(context(new HashMap<>()).noise("test"), xzScale, yScale);
    }

    // <editor-fold desc="Construction and estimates">

    @Test
    void addWithConstantUsesTheConstantForm() {
        DensityNode f = Density.add(Y, Density.constant(2));
        assertInstanceOf(AffineNode.class, f);
        assertEquals(1.0, f.lo);
        assertEquals(3.0, f.hi);
        assertEquals(2.0, at(f, 0, 128, 0));
    }

    @Test
    void mulEstimateKeepsTheUnsoundCase() {
        DensityNode a = Density.yGradient(0, 10, 1, 2);
        DensityNode b = Density.yGradient(0, 10, -3, -1);
        DensityNode f = Density.mul(a, b);
        // Positive x negative intervals; the true maximum is -1.
        assertEquals(-6.0, f.lo);
        assertEquals(-2.0, f.hi);
    }

    @Test
    void invertEstimate() {
        assertEquals(Double.NEGATIVE_INFINITY, Density.transform(Kind.INVERT, Y).lo);
        DensityNode inv = Density.transform(Kind.INVERT, Density.yGradient(0, 10, 2, 4));
        assertEquals(0.25, inv.lo);
        assertEquals(0.5, inv.hi);
    }

    @Test
    void absAndSquareLowerEstimateIsClampedInputLow() {
        // [2, 3] squared: the lower estimate is max(0, 2), not 4 (spec 3.3).
        DensityNode sq = Density.transform(Kind.SQUARE, Density.yGradient(0, 10, 2, 3));
        assertEquals(2.0, sq.lo);
        assertEquals(9.0, sq.hi);
        DensityNode abs = Density.transform(Kind.ABS, Density.yGradient(0, 10, -3, 1));
        assertEquals(0.0, abs.lo);
        assertEquals(3.0, abs.hi);
    }

    @Test
    void squeezeMatchesFormula() {
        DensityNode f = Density.transform(Kind.SQUEEZE, Density.constant(0.5));
        assertEquals(0.5 / 2.0 - 0.125 / 24.0, at(f, 0, 0, 0));
        DensityNode clamped = Density.transform(Kind.SQUEEZE, Density.constant(7));
        assertEquals(1.0 / 2.0 - 1.0 / 24.0, at(clamped, 0, 0, 0));
    }

    @Test
    void markersReportTheirArgumentsEstimate() {
        DensityNode arg = Density.yGradient(0, 10, -3, 5);
        for (MarkerNode.Kind kind : MarkerNode.Kind.values()) {
            DensityNode m = Density.marker(kind, arg);
            assertEquals(-3.0, m.lo, kind.name());
            assertEquals(5.0, m.hi, kind.name());
        }
    }

    @Test
    void placeholdersAreNotConstants() {
        // mul(blend_offset, invert(0)) is the general form: the zero first argument skips the infinite second one.
        DensityNode f = Density.mul(PlaceholderNode.BLEND_OFFSET, Density.transform(Kind.INVERT, Density.constant(0)));
        assertInstanceOf(PairNode.class, f);
        assertEquals(0.0, at(f, 0, 0, 0));
        assertEquals(1.0, PlaceholderNode.BLEND_ALPHA.lo);
    }

    @Test
    void leadingZeroMulShortCircuitsOnlyOnItsOwn() {
        DensityNode inf = Density.transform(Kind.INVERT, Density.constant(0));
        DensityNode leading = Density.mul(Density.constant(0), inf);
        DensityNode trailing = Density.mul(inf, Density.constant(0));
        // At a single point, a leading 0 skips the input; a trailing one doesn't.
        assertEquals(0.0, at(leading, 0, 0, 0));
        assertTrue(Double.isNaN(at(trailing, 0, 0, 0)));
        // Inside a chunk both are input * 0, and the optimizer must not fold either away.
        for (DensityNode f : new DensityNode[] { leading, DensityOptimizer.optimize(leading) }) {
            ChunkDensitySampler sampler = new ChunkDensitySampler(f, 4, 8, 0, 8, 0);
            double[] out = sampler.newBuffer();
            sampler.sample(0, 0, out);
            for (double v : out) assertTrue(Double.isNaN(v));
        }
        // Finite inputs agree either way, so those fold.
        assertInstanceOf(ConstantNode.class, DensityOptimizer.optimize(Density.mul(Density.constant(0), Density.constant(3))));
    }

    @Test
    void minAndMaxSkipOnTheEstimateEvenWhenItIsWrong() {
        // b's estimate says <= -2, but b reaches -1 at y = 0.
        DensityNode b = Density.mul(Density.yGradient(0, 10, 1, 2), Density.yGradient(0, 10, -1, -3));
        DensityNode max = Density.max(Density.constant(-1.5), b);
        assertEquals(-1.0, at(b, 0, 0, 0));
        assertEquals(-1.5, at(max, 0, 0, 0));
    }

    @Test
    void structuralEqualityIncludesEstimates() {
        DensityNode a = Density.transform(Kind.ABS, Y), b = Density.transform(Kind.ABS, Y);
        assertNotSame(a, b);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(a.withBounds(-5, 5)));
        assertFalse(a.equals(Density.transform(Kind.SQUARE, Y)));
    }

    // </editor-fold>

    // <editor-fold desc="Chunk evaluation of marker-free trees">

    /// A marker-free tree is a pure function of the position, so both evaluators must give exactly what
    /// [DensityNode#sample] gives. Terrain is `final_density + beardifier`, so only the sign of a zero may differ.
    /// Two chunks, each twice, so later fills run on dirty buffers.
    static void assertChunkMatchesSample(DensityNode f) {
        for (boolean stateful : new boolean[] { false, true }) {
            ChunkDensitySampler sampler = new ChunkDensitySampler(f, 4, 8, -64, 64, -64, 64, 0, stateful);
            assertEquals(!stateful, sampler.isPure());
            double[] out = sampler.newBuffer();
            for (int[] chunk : new int[][] { { 0, 0 }, { -3, 7 }, { 0, 0 }, { -3, 7 } }) {
                Arrays.fill(out, Double.NaN);
                sampler.sample(chunk[0], chunk[1], out);
                for (int y = -64; y < 64; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int bx = (chunk[0] << 4) + x, bz = (chunk[1] << 4) + z;
                            double expected = f.sample(bx, y, bz);
                            double got = out[((y + 64) * 16 + z) * 16 + x];
                            assertEquals(Double.doubleToLongBits(expected + 0.0), Double.doubleToLongBits(got + 0.0),
                                "stateful=" + stateful + " at (" + bx + ", " + y + ", " + bz + "): expected " + expected
                                    + ", got " + got);
                        }
                    }
                }
            }
        }
    }

    /// -0.0 everywhere, without being a [ConstantNode] (which would turn a `mul` into the constant form).
    private static final DensityNode NEGATIVE_ZERO = Density.transform(Kind.QUARTER_NEGATIVE, Density.constant(-0.0));

    @Test
    void cheapnessFollowsTheTree() {
        assertTrue(ChunkAnalysis.isCheap(Y));
        assertTrue(ChunkAnalysis.isCheap(Density.constant(3)));
        assertTrue(ChunkAnalysis.isCheap(
            Density.rangeChoice(Y, 0, 1, Density.transform(Kind.ABS, Y), Density.clamp(Y, 0, 1))));

        DensityNode noise = testNoise(1, 1);
        assertFalse(ChunkAnalysis.isCheap(noise));
        assertFalse(ChunkAnalysis.isCheap(Density.add(Y, noise)));
        assertFalse(ChunkAnalysis.isCheap(Density.cacheOnce(noise)));
        assertTrue(ChunkAnalysis.isCheap(Density.interpolated(noise)));
    }

    @Test
    void chunkTransformsMatchSample() {
        DensityNode in = Density.add(Y, Density.mul(Density.constant(0.25), testNoise(1, 1)));
        for (Kind kind : Kind.values()) assertChunkMatchesSample(Density.transform(kind, in));
        assertChunkMatchesSample(Density.clamp(in, -0.2, 0.3));
        assertChunkMatchesSample(Density.blendDensity(in));
    }

    @Test
    void chunkMinMaxMatchSample() {
        DensityNode low = Density.yGradient(-64, 64, -5, -4);
        DensityNode high = Density.yGradient(-64, 64, 4, 5);
        DensityNode square = Density.transform(Kind.SQUARE, Y);
        // Mixed short circuiting, never short circuiting, and always short circuiting.
        assertChunkMatchesSample(Density.min(Y, square));
        assertChunkMatchesSample(Density.max(Y, square));
        assertChunkMatchesSample(Density.min(low, high));
        assertChunkMatchesSample(Density.min(high, low));
        assertChunkMatchesSample(Density.max(low, high));
        assertChunkMatchesSample(Density.max(high, low));
    }

    @Test
    void chunkMulMatchesSample() {
        DensityNode zeroBelow0 = Density.yGradient(0, 64, 0, 1);
        assertChunkMatchesSample(Density.mul(zeroBelow0, Y));
        assertChunkMatchesSample(Density.mul(Y, zeroBelow0));
        // a is -0.0 everywhere: the short circuit returns +0.0, not a.
        DensityNode f = Density.mul(NEGATIVE_ZERO, Y);
        assertChunkMatchesSample(f);
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(at(f, 0, 0, 0)));
    }

    @Test
    void chunkRangeChoiceMatchesSample() {
        DensityNode a = Density.transform(Kind.CUBE, Y), b = Density.clamp(Y, -0.3, 0.3);
        assertChunkMatchesSample(Density.rangeChoice(Y, -0.5, 0.25, a, b));
        assertChunkMatchesSample(Density.rangeChoice(Y, -2, 2, a, b));
        assertChunkMatchesSample(Density.rangeChoice(Y, 2, 3, a, b));
        // Boundaries: min is inclusive, max exclusive.
        assertChunkMatchesSample(Density.rangeChoice(Y, -1, 1, NEGATIVE_ZERO, a));
    }

    @Test
    void chunkKeepsShortCircuitsForExpensiveArguments() {
        DensityNode noise = testNoise(0.5, 0.5);
        DensityNode yRange = Density.yGradient(-64, 64, -1, 1);
        assertChunkMatchesSample(Density.min(yRange, noise));
        assertChunkMatchesSample(Density.max(yRange, noise));
        assertChunkMatchesSample(Density.mul(Density.yGradient(0, 64, 0, 1), noise));
        assertChunkMatchesSample(Density.rangeChoice(yRange, 0, 1, noise, yRange));
        assertChunkMatchesSample(Density.rangeChoice(yRange, 0, 1, yRange, noise));
        assertChunkMatchesSample(Density.add(noise, yRange));
        // Expensive on both sides of every short circuit, nested.
        DensityNode n2 = testNoise(2, 0.25);
        assertChunkMatchesSample(Density.min(noise, Density.max(n2, Density.mul(noise, n2))));
    }

    // </editor-fold>

    // <editor-fold desc="Spline">

    @Test
    void splineExtendsLinearlyOutsideItsPoints() {
        // value 1 at -1 (slope 2), value 3 at 1 (slope 0)
        Spline spline = Spline.curve(
            Y, new float[] { -1, 1 }, new Spline[] { Spline.fixed(1), Spline.fixed(3) }, new float[] { 2, 0 });
        DensityNode f = Density.spline(spline);
        // Y maps -64..320 to -1..1, so y = -64 sits exactly on the first point and y = 320 on the last.
        assertEquals(1.0, at(f, 0, -64, 0), 1e-6);
        assertEquals(3.0, at(f, 0, 320, 0), 1e-6);
        // Halfway: lerp(0.5, 1, 3) + 0.25 * lerp(0.5, a, b) with a = 2*2 - 2 = 2, b = 0 + 2 = 2 -> 2 + 0.5
        assertEquals(2.5, at(f, 0, 128, 0), 1e-6);
        assertTrue(f.lo <= 1.0 && f.hi >= 3.0);
    }

    @Test
    void splineSegmentSearch() {
        Spline.Curve c = (Spline.Curve) Spline.curve(
            Y, new float[] { -1, 0, 1 }, new Spline[] { Spline.fixed(0), Spline.fixed(0), Spline.fixed(0) },
            new float[] { 0, 0, 0 });
        assertEquals(-1, c.segment(-2));
        assertEquals(0, c.segment(-1));
        assertEquals(0, c.segment(-0.5f));
        assertEquals(1, c.segment(0));
        assertEquals(2, c.segment(5));
        // NaN lands on the last point (spec 3.9).
        assertEquals(2, c.segment(Float.NaN));
    }

    @Test
    void splineEstimateFollowsTheSpec() {
        // Coordinate in [-1, 1]; points at -0.5 (value 0, slope 1) and 0.5 (value 1, slope 0).
        Spline s = Spline.curve(
            Y, new float[] { -0.5f, 0.5f }, new Spline[] { Spline.fixed(0), Spline.fixed(1) }, new float[] { 1, 0 });
        // Below the first point: 0 + 1 * (-1 - -0.5) = -0.5. The pair term: p1 = 1, p2 = 0;
        // minA = 1 - 1 + 0 = 0, minB = 0 + 0 - 0 = 0 -> 0 + 0.25 * 0 = 0; maxA = 1 - 1 + 0 = 0, maxB = 0 + 1 - 0 = 1
        // -> 1 + 0.25 * 1 = 1.25.
        assertEquals(-0.5f, s.lo);
        assertEquals(1.25f, s.hi);
    }

    @Test
    void splineChunkEvaluationMatchesSample() {
        DensityNode noise = testNoise(1, 1);
        Spline inner = Spline.curve(
            noise, new float[] { -0.3f, 0.2f, 0.6f }, new Spline[] { Spline.fixed(-1), Spline.fixed(0.5f), Spline.fixed(2) },
            new float[] { 0.5f, -1, 3 });
        Spline outer = Spline.curve(
            Y, new float[] { -0.5f, 0.5f }, new Spline[] { inner, Spline.fixed(4) }, new float[] { 0, 2 });
        assertChunkMatchesSample(Density.spline(outer));
    }

    // </editor-fold>

    // <editor-fold desc="Optimizer">

    @Test
    void optimizerFoldsConstants() {
        DensityNode f = Density.add(
            Density.transform(Kind.SQUARE, Density.constant(3)), Density.clamp(Density.constant(5), 0, 1));
        DensityNode o = DensityOptimizer.optimize(f);
        assertInstanceOf(ConstantNode.class, o);
        assertEquals(10.0, ((ConstantNode) o).value);
    }

    @Test
    void optimizerKeepsEstimatesOfWhatItReplaces() {
        // square(3) folds to 9, but its estimate stays [3, 9] (spec 3.3), which a min/max above may rely on.
        DensityNode square = Density.transform(Kind.SQUARE, Density.constant(3));
        DensityNode o = DensityOptimizer.optimize(Density.rangeChoice(Y, 0, 1, square, Y));
        DensityNode folded = ((RangeChoiceNode) o).whenIn;
        assertInstanceOf(ConstantNode.class, folded);
        assertEquals(3.0, folded.lo);
        assertEquals(9.0, folded.hi);
    }

    @Test
    void optimizerDropsRedundantClampAndIdentities() {
        DensityNode f = Density.clamp(Density.add(Density.mul(Y, Density.constant(1)), Density.ZERO), -2, 2);
        DensityNode o = DensityOptimizer.optimize(f);
        // Y, with the clamp's estimate.
        assertInstanceOf(GradientNode.class, o);
        assertEquals(-2.0, o.lo);
        for (int y = -100; y < 400; y += 7) assertEquals(at(f, 0, y, 0), at(o, 0, y, 0));

        // This clamp can trigger, so it has to stay.
        DensityNode tight = Density.clamp(Y, -0.5, 0.5);
        assertInstanceOf(ClampNode.class, DensityOptimizer.optimize(tight));
    }

    @Test
    void optimizerPicksTheOnlyRangeChoiceBranch() {
        DensityNode a = Density.yGradient(0, 10, 5, 6);
        DensityNode b = Density.yGradient(0, 10, 7, 8);
        // Bounds are padded slightly for rounding, so leave some room around [-1, 1].
        assertEquals(a, DensityOptimizer.optimize(Density.rangeChoice(Y, -1.001, 1.001, a, b)).withBounds(5, 6));
        assertInstanceOf(GradientNode.class, DensityOptimizer.optimize(Density.rangeChoice(Y, 2, 3, a, b)));
        assertEquals(7.0, at(DensityOptimizer.optimize(Density.rangeChoice(Y, 2, 3, a, b)), 0, 0, 0));
        assertInstanceOf(RangeChoiceNode.class, DensityOptimizer.optimize(Density.rangeChoice(Y, 0, 3, a, b)));
    }

    @Test
    void optimizerResolvesMinAndMaxWithDisjointRanges() {
        DensityNode low = Density.yGradient(0, 10, -5, -4);
        DensityNode high = Density.yGradient(0, 10, 4, 5);
        for (DensityNode f : new DensityNode[] { Density.min(low, high), Density.min(high, low) }) {
            DensityNode o = DensityOptimizer.optimize(f);
            assertInstanceOf(GradientNode.class, o);
            assertEquals(-5.0, at(o, 0, 0, 0));
        }
        for (DensityNode f : new DensityNode[] { Density.max(low, high), Density.max(high, low) }) {
            DensityNode o = DensityOptimizer.optimize(f);
            assertInstanceOf(GradientNode.class, o);
            assertEquals(4.0, at(o, 0, 0, 0));
        }
    }

    @Test
    void optimizerDoesNotTrustUnsoundEstimates() {
        // The estimate says this product is at most -2, but it reaches -1 (at y = 0: 1 * -1).
        DensityNode a = Density.yGradient(0, 10, 1, 2);
        DensityNode b = Density.yGradient(0, 10, -1, -3);
        DensityNode clamp = Density.clamp(Density.mul(a, b), -10, -1.5);
        DensityNode o = DensityOptimizer.optimize(clamp);
        assertEquals(at(clamp, 0, 0, 0), at(o, 0, 0, 0));
        assertEquals(-1.5, at(o, 0, 0, 0));
    }

    @Test
    void optimizerKeepsResultsIdentical() {
        Random r = new Random(5);
        DensityNode f = Density.rangeChoice(
            Y, -0.25, 0.25,
            Density.max(Density.transform(Kind.HALF_NEGATIVE, Y), Density.clamp(Y, -0.1, 0.1)),
            Density.add(Density.interpolated(Density.constant(0.5)), Density.transform(Kind.CUBE, Y)));
        DensityNode o = DensityOptimizer.optimize(f);
        for (int i = 0; i < 1000; i++) {
            int y = r.nextInt(500) - 100;
            assertEquals(at(f, 0, y, 0), at(o, 0, y, 0), "y = " + y);
        }
    }

    @Test
    void optimizerDeduplicatesIdenticalSubtrees() {
        DensityNode a = Density.transform(Kind.SQUARE, Density.yGradient(0, 10, 1, 2));
        DensityNode b = Density.transform(Kind.SQUARE, Density.yGradient(0, 10, 1, 2));
        assertNotSame(a, b);
        PairNode sum = (PairNode) DensityOptimizer.optimize(Density.add(a, b));
        assertSame(sum.a, sum.b);
    }

    @Test
    void optimizerLeavesStatefulTreesAlone() {
        // A cache_once read point by point at cell level: its state is observable, so nothing may be dropped.
        DensityNode f = Density.min(Density.yGradient(0, 10, -5, -4), Density.cacheOnce(Density.yGradient(0, 10, 4, 5)));
        assertSame(f, DensityOptimizer.optimize(f));
    }

    // </editor-fold>

    // <editor-fold desc="Chunk analysis">

    @Test
    void vanillaLikeTreesArePure() {
        DensityNode noise = testNoise(1, 1);
        DensityNode flat = Density.flatCache(Density.cache2d(testNoise(1, 0)));
        DensityNode shared = Density.cacheOnce(Density.add(noise, flat));
        DensityNode f = Density.min(
            Density.interpolated(Density.min(shared, Density.mul(Density.constant(5), shared))),
            Density.rangeChoice(Density.interpolated(noise), -1, 0, Density.constant(64), Density.interpolated(shared)));
        assertTrue(ChunkAnalysis.isPure(f), ChunkAnalysis.impurity(f));
    }

    @Test
    void observableStateIsDetected() {
        DensityNode noise = testNoise(1, 1);
        DensityNode noise2d = testNoise(1, 0);
        // Nesting.
        assertFalse(ChunkAnalysis.isPure(Density.interpolated(Density.add(Density.interpolated(noise), noise))));
        assertFalse(ChunkAnalysis.isPure(Density.interpolated(Density.cacheAllInCell(noise))));
        // cache_once read point by point at cell level, but fine when batch-read or inside an interpolator.
        assertFalse(ChunkAnalysis.isPure(Density.min(Y, Density.cacheOnce(noise))));
        assertTrue(ChunkAnalysis.isPure(Density.add(Y, Density.cacheOnce(noise))));
        assertTrue(ChunkAnalysis.isPure(Density.interpolated(Density.min(Y, Density.cacheOnce(noise)))));
        // cache_2d read point by point: only a problem for functions of y.
        assertFalse(ChunkAnalysis.isPure(Density.max(Y, Density.cache2d(noise))));
        assertTrue(ChunkAnalysis.isPure(Density.max(Y, Density.cache2d(noise2d))));
        assertTrue(ChunkAnalysis.isPure(Density.add(Y, Density.cache2d(noise))));
        // Single-point contexts are transparent.
        assertTrue(ChunkAnalysis.isPure(Density.flatCache(Density.min(Y, Density.cacheOnce(noise)))));
        assertTrue(ChunkAnalysis.isPure(
            Density.findTopSurface(Density.interpolated(Density.interpolated(noise)), noise2d, -64, 8)));
        // A cell cache at cell level, even around an interpolator.
        assertTrue(ChunkAnalysis.isPure(Density.cacheAllInCell(Density.mul(Density.constant(2), Density.interpolated(noise)))));
    }

    @Test
    void yIndependence() {
        assertTrue(ChunkAnalysis.isYIndependent(testNoise(1, 0)));
        assertFalse(ChunkAnalysis.isYIndependent(testNoise(1, 0.5)));
        assertFalse(ChunkAnalysis.isYIndependent(Density.add(Y, testNoise(1, 0))));
        SeededNoise n = context(new HashMap<>()).noise("test");
        assertTrue(ChunkAnalysis.isYIndependent(
            Density.shiftedNoise(Density.shiftA(n), Density.ZERO, Density.shiftB(n), 0.25, 0, n)));
        assertFalse(ChunkAnalysis.isYIndependent(Density.shiftedNoise(Density.shift(n), Density.ZERO, Density.ZERO, 1, 0, n)));
        assertTrue(ChunkAnalysis.isYIndependent(Density.flatCache(Y)));
        assertFalse(ChunkAnalysis.isYIndependent(Density.interpolated(testNoise(1, 0))));
    }

    @Test
    void planOrdersMarkersChildrenFirst() {
        DensityNode inner = Density.interpolated(testNoise(1, 1));
        DensityNode outer = Density.interpolated(Density.add(inner, testNoise(2, 2)));
        DensityNode cell = Density.cacheAllInCell(Density.mul(Density.constant(2), outer));
        DensityNode other = Density.interpolated(Y);
        ChunkPlan plan = new ChunkPlan(Density.add(cell, other), 4, 8, 0, 64, false);
        assertEquals(3, plan.interpolators.size());
        assertEquals(inner, plan.interpolators.get(0));
        assertEquals(outer, plan.interpolators.get(1));
        assertEquals(other, plan.interpolators.get(2));
        assertEquals(1, plan.cellCaches.size());
        assertFalse(plan.pure);
        assertEquals(8, plan.cellsY);
    }

    @Test
    void planInternsEqualSubtrees() {
        SeededNoise noise = context(new HashMap<>()).noise("test");
        DensityNode a = Density.interpolated(Density.noise(noise, 1, 1)), b = Density.interpolated(Density.noise(noise, 1, 1));
        assertNotSame(a, b);
        ChunkPlan plan = new ChunkPlan(Density.add(a, b), 4, 8, 0, 64, false);
        assertEquals(1, plan.interpolators.size());
        PairNode root = (PairNode) plan.root;
        assertSame(root.a, root.b);
    }

    // </editor-fold>

    // <editor-fold desc="Shared subexpressions">

    @Test
    void sharedSubtreesInsideInterpolatorsGetMemos() {
        DensityNode inner = testNoise(0.5, 0.5);
        // `shared` is used twice (like sloped_cheese), and `inner` is used both inside it and on its own.
        DensityNode shared = Density.add(inner, Y);
        DensityNode contents = ChunkPlan.intern(
            Density.rangeChoice(shared, -1000000, 0.5, Density.min(shared, inner), Y));
        Set<DensityNode> targets = SharedSubexpressions.memoTargets(java.util.Collections.singletonList(contents));
        assertEquals(2, targets.size());
        assertTrue(targets.contains(((RangeChoiceNode) contents).input));
        assertTrue(targets.contains(((PairNode) ((RangeChoiceNode) contents).input).a));
    }

    @Test
    void trivialAndMarkedSubtreesAreNotMemoized() {
        DensityNode trivial = Density.transform(Kind.SQUARE, Y);
        DensityNode cached = Density.flatCache(testNoise(1, 1));
        DensityNode contents = ChunkPlan.intern(
            Density.add(Density.add(trivial, cached), Density.max(trivial, cached)));
        assertTrue(SharedSubexpressions.memoTargets(java.util.Collections.singletonList(contents)).isEmpty());
    }

    @Test
    void memoizedCornersMatchSample() {
        // The memos must not change values: an interpolated tree with heavy sharing, checked against the
        // interpolation of sampled corners.
        DensityNode inner = testNoise(0.5, 0.5);
        DensityNode shared = Density.add(inner, Y);
        DensityNode arg = Density.rangeChoice(shared, -1000000, 0.5, Density.min(shared, inner), Density.max(inner, shared));
        DensityNode f = Density.interpolated(arg);
        ChunkDensitySampler sampler = new ChunkDensitySampler(f, 4, 8, -64, 64, 0);
        double[] out = sampler.newBuffer();
        sampler.sample(2, -5, out);
        for (int y = -64; y < 64; y += 3) {
            for (int z = 0; z < 16; z += 5) {
                for (int x = 0; x < 16; x += 3) {
                    int bx = 32 + x, bz = -80 + z;
                    int x0 = bx & ~3, y0 = Math.floorDiv(y, 8) * 8, z0 = bz & ~3;
                    double expected = DensityMath.trilinearXYZ(
                        (bx - x0) / 4.0, (y - y0) / 8.0, (bz - z0) / 4.0, arg.sample(x0, y0, z0),
                        arg.sample(x0 + 4, y0, z0), arg.sample(x0, y0 + 8, z0), arg.sample(x0 + 4, y0 + 8, z0),
                        arg.sample(x0, y0, z0 + 4), arg.sample(x0 + 4, y0, z0 + 4), arg.sample(x0, y0 + 8, z0 + 4),
                        arg.sample(x0 + 4, y0 + 8, z0 + 4));
                    assertEquals(expected, out[((y + 64) * 16 + z) * 16 + x]);
                }
            }
        }
    }

    // </editor-fold>

    // <editor-fold desc="Build context">

    @Test
    void buildContextSharesIdenticalFunctionsAndNoises() {
        Map<String, IDensityFunctionFactory> functions = new HashMap<>();
        functions.put("minecraft:grad", new YClampedGradientFunc(0, 10, 0, 1));
        DensityBuildContext ctx = context(functions);

        assertSame(ctx.build(new DensityFunctionRef("minecraft:grad")), ctx.build(new YClampedGradientFunc(0, 10, 0, 1)));
        assertSame(ctx.noise("test"), ctx.noise("minecraft:test"));
    }

    @Test
    void buildContextDistinguishesUnaryTypes() {
        DensityBuildContext ctx = context(new HashMap<>());
        DensityNode abs = ctx.build(new AbsUnary(new ConstantFunc(-2)));
        DensityNode square = ctx.build(new SquareUnary(new ConstantFunc(-2)));
        assertEquals(2.0, at(abs, 0, 0, 0));
        assertEquals(4.0, at(square, 0, 0, 0));
    }

    @Test
    void unknownReferenceIsZero() {
        DensityBuildContext ctx = context(new HashMap<>());
        assertEquals(0.0, at(ctx.build(new DensityFunctionRef("minecraft:missing")), 1, 2, 3));
    }

    @Test
    void noiseIsDeterministicPerSeedAndId() {
        DensityBuildContext a = context(new HashMap<>()), b = context(new HashMap<>());
        assertEquals(a.noise("test").getValue(1.5, 2.5, 3.5), b.noise("test").getValue(1.5, 2.5, 3.5));
    }

    // </editor-fold>
}
