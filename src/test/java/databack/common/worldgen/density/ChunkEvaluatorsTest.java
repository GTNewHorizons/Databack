package databack.common.worldgen.density;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import databack.common.worldgen.density.TransformNode.Kind;

/// The fast evaluator against the exact one. [StatefulChunkEvaluator] is a direct replay of the spec's evaluation
/// order, so on pure trees the two must agree everywhere; the trees here exercise what the fast path does differently:
/// on-demand corners, corner-bound skips, shared columns, per-position memos and array-at-a-time cells.
class ChunkEvaluatorsTest {

    private static final SeededNoise NOISE = DensityFunctionsTest.context(new HashMap<>()).noise("test");

    private static DensityNode noise(double xz, double y) {
        return Density.noise(NOISE, xz, y);
    }

    private static final DensityNode Y = Density.yGradient(-64, 64, -1, 1);

    /// Generates a 3x2 block of chunks (so columns get shared) with both evaluators and compares them.
    private static void assertEvaluatorsAgree(DensityNode f, int cellWidth, int cellHeight, int minY, int maxY) {
        assertTrue(ChunkAnalysis.isPure(f), ChunkAnalysis.impurity(f));
        for (int cache : new int[] { 0, 64 }) {
            ChunkDensitySampler fast = new ChunkDensitySampler(f, cellWidth, cellHeight, minY, maxY, cache);
            ChunkDensitySampler exact = new ChunkDensitySampler(
                f, cellWidth, cellHeight, minY, maxY, minY, maxY, 0, true);
            assertTrue(fast.isPure());
            assertFalse(exact.isPure());
            double[] a = fast.newBuffer(), b = exact.newBuffer();
            for (int cz = -1; cz <= 0; cz++) {
                for (int cx = 3; cx <= 5; cx++) {
                    fast.sample(cx, cz, a);
                    exact.sample(cx, cz, b);
                    for (int i = 0; i < a.length; i++) {
                        if (Double.doubleToLongBits(a[i] + 0.0) != Double.doubleToLongBits(b[i] + 0.0)) {
                            int x = i & 15, z = (i >> 4) & 15, y = fast.minY() + (i >> 8);
                            throw new AssertionError("chunk (" + cx + ", " + cz + ") cache=" + cache + " at (" + x + ", "
                                + y + ", " + z + "): fast " + a[i] + ", exact " + b[i]);
                        }
                    }
                }
            }
        }
    }

    /// Shaped like the overworld's cell level: a squeezed interpolator, min'd with a noodle-like range_choice whose
    /// branches are only needed in some cells.
    private static DensityNode noodleLike() {
        DensityNode terrain = Density.interpolated(Density.add(Density.mul(Density.constant(-2), Y), noise(0.5, 0.25)));
        DensityNode selector = Density.interpolated(noise(0.25, 0.25));
        DensityNode ridges = Density.max(
            Density.transform(Kind.ABS, Density.interpolated(noise(2, 2))),
            Density.transform(Kind.ABS, Density.interpolated(noise(3, 1))));
        DensityNode caves = Density.add(Density.interpolated(Density.mul(Density.constant(0.1), noise(1, 1))),
            Density.mul(Density.constant(1.5), ridges));
        return Density.min(
            Density.transform(Kind.SQUEEZE, Density.mul(Density.constant(0.64), terrain)),
            Density.rangeChoice(selector, -1000000, 0, Density.constant(64), caves));
    }

    @Test
    void noodleLikeTree() {
        assertEvaluatorsAgree(noodleLike(), 4, 8, -64, 64);
        assertEvaluatorsAgree(DensityOptimizer.optimize(noodleLike()), 4, 8, 0, 48);
    }

    @Test
    void otherCellSizes() {
        assertEvaluatorsAgree(noodleLike(), 8, 4, -32, 32);
        assertEvaluatorsAgree(noodleLike(), 4, 4, -16, 16);
        assertEvaluatorsAgree(noodleLike(), 16, 8, -32, 32);
    }

    @Test
    void expensiveBranchesAndShortCircuits() {
        DensityNode in = Density.interpolated(noise(0.5, 0.5));
        DensityNode perBlock = noise(1, 1);
        assertEvaluatorsAgree(Density.rangeChoice(in, -0.1, 0.2, Density.interpolated(noise(2, 1)), perBlock), 4, 8, -32, 32);
        assertEvaluatorsAgree(Density.max(in, Density.add(perBlock, Density.constant(-0.3))), 4, 8, -32, 32);
        assertEvaluatorsAgree(Density.mul(Density.yGradient(-8, 8, 0, 1), Density.min(in, perBlock)), 4, 8, -32, 32);
        // The second argument's cell bounds can rule it out (max of something always below).
        assertEvaluatorsAgree(Density.max(Density.add(in, Density.constant(5)), Density.interpolated(noise(1, 1))), 4, 8, -32, 32);
    }

    @Test
    void columnMarkersAndMemos() {
        DensityNode flat3d = Density.flatCache(noise(1, 1));
        DensityNode column = Density.cache2d(Density.add(noise(0.5, 0), flat3d));
        DensityNode shared = Density.cacheOnce(Density.add(noise(0.7, 0.7), column));
        DensityNode corners = Density.interpolated(Density.min(shared, Density.mul(Density.constant(2), shared)));
        // cache_2d of a y-independent function read point by point, cache_once read as a batch.
        DensityNode f = Density.add(Density.max(Y, column), Density.add(shared, corners));
        assertEvaluatorsAgree(f, 4, 8, -32, 32);
    }

    @Test
    void nonFiniteCorners() {
        // 1 / y is infinite at y = 0, a corner: interpolations across it are NaN, and corner bounds must not be used.
        DensityNode inv = Density.interpolated(Density.transform(Kind.INVERT, Density.yGradient(-32, 32, -32, 32)));
        assertEvaluatorsAgree(Density.rangeChoice(inv, -0.5, 0.5, Density.constant(1), noise(1, 1)), 4, 8, -32, 32);
        assertEvaluatorsAgree(Density.min(Y, Density.add(inv, Density.constant(3))), 4, 8, -32, 32);
    }

    @Test
    void perPointFunctionsReadingInterpolators() {
        DensityNode in = Density.interpolated(noise(0.5, 0.5));
        Spline spline = Spline.curve(
            in, new float[] { -0.5f, 0, 0.5f }, new Spline[] { Spline.fixed(-1), Spline.fixed(0.25f), Spline.fixed(2) },
            new float[] { 1, 0, -1 });
        assertEvaluatorsAgree(Density.add(Density.spline(spline), Density.weirdScaled(in, NOISE, WeirdScaledNode.Rarity.TYPE_2)),
            4, 8, -32, 32);
        assertEvaluatorsAgree(Density.findTopSurface(Density.add(Y, in), Density.add(Density.constant(16), noise(1, 0)), -32, 8),
            4, 8, -32, 32);
    }

    /// Spec 4.3: a cache_once reached only point by point during a cell fill keeps the value of the first point that
    /// reached it, for the whole cell.
    @Test
    void cacheOnceReadPerPointKeepsTheCellsFirstValue() {
        DensityNode n = noise(1, 1);
        DensityNode f = Density.min(Density.constant(10), Density.cacheOnce(n));
        ChunkDensitySampler sampler = new ChunkDensitySampler(f, 4, 8, -32, 32, 0);
        assertFalse(sampler.isPure());
        double[] out = sampler.newBuffer();
        sampler.sample(2, -1, out);
        for (int y = -32; y < 32; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    // The first point of a cell fill is the top layer's lowest x and z.
                    int bx = 32 + (x & ~3), bz = -16 + (z & ~3), by = Math.floorDiv(y, 8) * 8 + 7;
                    assertEquals(n.sample(bx, by, bz), out[((y + 32) * 16 + z) * 16 + x]);
                }
            }
        }
    }

    /// The column cache only shares complete columns, and a chunk that finds a partial one computes the rest.
    @Test
    void sharedColumnsAreComplete() {
        DensityNode f = noodleLike();
        ChunkDensitySampler shared = new ChunkDensitySampler(f, 4, 8, -64, 64, 256);
        ChunkDensitySampler alone = new ChunkDensitySampler(f, 4, 8, -64, 64, 0);
        double[] a = shared.newBuffer(), b = alone.newBuffer();
        for (int[] c : new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, { -1, 0 } }) {
            shared.sample(c[0], c[1], a);
            alone.sample(c[0], c[1], b);
            for (int i = 0; i < a.length; i++) assertEquals(b[i], a[i], "chunk " + c[0] + ", " + c[1] + " index " + i);
        }
        assertTrue(shared.columnCache().hits() > 0);
    }
}
