package databack.common.worldgen.density;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/// Compares our density functions against values produced by the real 26.1 jar (see `misc/vanilla-reference`).
///
/// The goal is bit-for-bit equality (the sign of a zero may differ).
class VanillaParityTest {

    @BeforeAll
    static void requirePack() {
        assumeTrue(VanillaWorldgen.available(), "Vanilla datapack not extracted (run the downloadVanillaDatapack task)");
    }

    private static InputStream resource(String name) {
        InputStream in = VanillaParityTest.class.getResourceAsStream("/vanilla_reference/" + name);
        if (in == null) throw new IllegalStateException("Missing reference data " + name);
        return in;
    }

    // <editor-fold desc="Single points">

    @ParameterizedTest(name = "{0} seed={1} optimize={2}")
    @CsvSource({
        "overworld, 12345, false", "overworld, 12345, true", "overworld, -987654321, true",
        "amplified, 12345, true", "amplified, -987654321, false",
        "nether, 12345, false", "nether, 12345, true", "nether, -987654321, true",
        "end, 12345, false", "end, 12345, true", "end, -987654321, true",
    })
    void routerPointsMatchVanilla(String dimension, long seed, boolean optimize) throws IOException {
        DensityRouter router = VanillaWorldgen.router(dimension, seed, optimize);
        DensityNode[] fns = router.all();

        List<String> mismatches = new ArrayList<>();
        int checked = 0;

        try (BufferedReader r = new BufferedReader(new InputStreamReader(
            resource("points_" + dimension + "_" + seed + ".tsv"), StandardCharsets.UTF_8))) {
            String[] header = r.readLine().split("\t");
            assertEquals(3 + fns.length, header.length);
            for (int i = 0; i < fns.length; i++) assertEquals(DensityRouter.FIELD_NAMES[i], header[3 + i]);

            String line;
            while ((line = r.readLine()) != null) {
                String[] cols = line.split("\t");
                int x = Integer.parseInt(cols[0]), y = Integer.parseInt(cols[1]), z = Integer.parseInt(cols[2]);
                for (int i = 0; i < fns.length; i++) {
                    double expected = Double.parseDouble(cols[3 + i]);
                    double actual = fns[i].sample(x, y, z);
                    checked++;
                    // == so that 0.0 and -0.0 compare equal (the optimizer may flip the sign of a zero).
                    if (!(expected == actual || (Double.isNaN(expected) && Double.isNaN(actual)))) {
                        mismatches.add(String.format(
                            "%s at (%d, %d, %d): expected %s, got %s (diff %.3g)", header[3 + i], x, y, z, expected,
                            actual, actual - expected));
                    }
                }
            }
        }

        assertTrue(checked > 1000);
        assertTrue(
            mismatches.isEmpty(),
            mismatches.size() + "/" + checked + " values differ, first few:\n  "
                + String.join("\n  ", mismatches.subList(0, Math.min(15, mismatches.size()))));
    }

    // </editor-fold>

    // <editor-fold desc="Interpolated chunks">

    static final class ReferenceChunk {

        final int minY, height;
        final double[] values;

        ReferenceChunk(int minY, int height, double[] values) {
            this.minY = minY;
            this.height = height;
            this.values = values;
        }

        static ReferenceChunk load(String name) throws IOException {
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(resource(name)))) {
                int minY = in.readInt();
                int height = in.readInt();
                double[] values = new double[height * 256];
                for (int i = 0; i < values.length; i++) values[i] = in.readDouble();
                return new ReferenceChunk(minY, height, values);
            }
        }

        double get(int x, int y, int z) {
            return values[((y - minY) * 16 + z) * 16 + x];
        }
    }

    private static String compare(ReferenceChunk ref, ChunkDensitySampler sampler, double[] actual) {
        int mismatches = 0;
        String first = null;
        for (int y = sampler.minY(); y < sampler.minY() + sampler.height(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    double expected = ref.get(x, y, z);
                    double got = actual[((y - sampler.minY()) * 16 + z) * 16 + x];
                    // == so that 0.0 and -0.0 compare equal; NaN matches NaN.
                    if (!(expected == got || Double.isNaN(expected) && Double.isNaN(got))) {
                        if (first == null) first = String.format("(%d, %d, %d): expected %s, got %s", x, y, z, expected, got);
                        mismatches++;
                    }
                }
            }
        }
        return mismatches == 0 ? null : mismatches + " blocks differ, first at " + first;
    }

    @ParameterizedTest(name = "{0} chunk ({2}, {3}) optimize={4}")
    @CsvSource({
        "overworld, 12345, 0, 0, false",
        "overworld, 12345, 0, 0, true",
        "overworld, 12345, -7, 13, true",
        "nether, 12345, 2, -3, false",
        "nether, 12345, 2, -3, true",
        "amplified, 12345, 3, -2, false",
        "amplified, 12345, 3, -2, true",
        "end, 12345, 0, 0, true",
        "end, 12345, 200, -150, true",
        "overworld, -987654321, 5, 5, true",
        // Far out, where noise coordinates wrap.
        "overworld, 12345, 62500, -62500, true",
        "overworld, 12345, -1874999, 1874999, false",
        "overworld, 12345, -1874999, 1874999, true",
    })
    void fullChunkMatchesVanilla(String dimension, long seed, int chunkX, int chunkZ, boolean optimize)
        throws IOException {
        ReferenceChunk ref = ReferenceChunk.load("chunk_" + dimension + "_" + seed + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        VanillaWorldgen.Settings s = VanillaWorldgen.settings(dimension);
        DensityRouter router = VanillaWorldgen.router(dimension, seed, optimize);

        ChunkDensitySampler sampler = new ChunkDensitySampler(
            router.finalDensity, s.cellWidth(), s.cellHeight(), ref.minY, ref.minY + ref.height, 0);
        assertEquals(ref.height, sampler.height());

        double[] out = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, out);

        String error = compare(ref, sampler, out);
        assertTrue(error == null, error);
    }

    /// Restricting the Y range (as the 1.7 world does) and sharing corner columns between chunks must not change
    /// anything. Generates a 3x3 block of chunks around the reference chunk, in an order where the reference chunk
    /// reuses columns from all four neighbours.
    @ParameterizedTest(name = "{0} chunk ({2}, {3}) y=[{4}, {5})")
    @CsvSource({
        "overworld, 12345, 0, 0, 0, 256",
        "overworld, 12345, -7, 13, 0, 256",
        "overworld, 12345, -7, 13, -64, 320",
        "nether, 12345, 2, -3, 0, 128",
        "nether, 12345, 2, -3, 32, 96",
    })
    void partialRangeWithColumnCacheMatchesVanilla(String dimension, long seed, int chunkX, int chunkZ, int minY, int maxY)
        throws IOException {
        ReferenceChunk ref = ReferenceChunk.load("chunk_" + dimension + "_" + seed + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        VanillaWorldgen.Settings s = VanillaWorldgen.settings(dimension);
        DensityRouter router = VanillaWorldgen.router(dimension, seed, true);

        ChunkDensitySampler sampler = new ChunkDensitySampler(
            router.finalDensity, s.cellWidth(), s.cellHeight(), minY, maxY, 1024);
        double[] out = sampler.newBuffer();

        int[][] order = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { -1, -1 }, { 1, 1 }, { 0, 0 } };
        for (int[] o : order) {
            Arrays.fill(out, Double.NaN);
            sampler.sample(chunkX + o[0], chunkZ + o[1], out);
        }

        String error = compare(ref, sampler, out);
        assertTrue(error == null, error);
        // 7 chunks x 25 columns, the last chunk's 25 columns all come from its neighbours.
        assertTrue(sampler.columnCache().hits() >= 25, "column cache hits: " + sampler.columnCache().hits());
    }

    /// The exact evaluator (used for trees whose marker state is observable) must agree with the fast path on real
    /// terrain too.
    @ParameterizedTest(name = "{0} chunk ({2}, {3})")
    @CsvSource({ "overworld, 12345, 0, 0", "nether, 12345, 2, -3" })
    void statefulEvaluatorMatchesVanilla(String dimension, long seed, int chunkX, int chunkZ) throws IOException {
        ReferenceChunk ref = ReferenceChunk.load("chunk_" + dimension + "_" + seed + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        VanillaWorldgen.Settings s = VanillaWorldgen.settings(dimension);
        DensityRouter router = VanillaWorldgen.router(dimension, seed, false);
        int maxY = ref.minY + ref.height;

        ChunkDensitySampler fast = new ChunkDensitySampler(
            router.finalDensity, s.cellWidth(), s.cellHeight(), ref.minY, maxY, 0);
        assertTrue(fast.isPure(), "vanilla's own trees should take the fast path");

        ChunkDensitySampler exact = new ChunkDensitySampler(
            router.finalDensity, s.cellWidth(), s.cellHeight(), ref.minY, maxY, ref.minY, maxY, 0, true);
        assertTrue(!exact.isPure());
        double[] out = exact.newBuffer();
        exact.sample(chunkX, chunkZ, out);

        String error = compare(ref, exact, out);
        assertTrue(error == null, error);
    }

    // </editor-fold>

    // <editor-fold desc="Synthetic cases">

    /// A tree from `vanilla_reference/cases`, used by the harness as the final density of an otherwise empty router.
    static final class Case {

        final String name;
        final long seed;
        final int minY, height, cellWidth, cellHeight;
        final JsonElement density;

        Case(String name) throws IOException {
            this.name = name;
            try (Reader r = new InputStreamReader(resource("cases/" + name + ".json"), StandardCharsets.UTF_8)) {
                JsonObject json = new JsonParser().parse(r).getAsJsonObject();
                JsonObject noise = json.getAsJsonObject("noise");
                this.seed = json.get("seed").getAsLong();
                this.minY = noise.get("min_y").getAsInt();
                this.height = noise.get("height").getAsInt();
                this.cellWidth = noise.get("size_horizontal").getAsInt() * 4;
                this.cellHeight = noise.get("size_vertical").getAsInt() * 4;
                this.density = json.get("density");
            }
        }

        DensityNode build(boolean optimize) {
            DensityNode f = VanillaWorldgen.context(seed, false).build(VanillaWorldgen.parseFunction(density));
            return optimize ? DensityOptimizer.optimize(f) : f;
        }
    }

    static Stream<Arguments> caseChunks() throws IOException {
        List<Arguments> args = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(resource("cases_index.tsv"), StandardCharsets.UTF_8))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] cols = line.split("\t");
                for (boolean optimize : new boolean[] { false, true }) {
                    args.add(Arguments.of(cols[0], Integer.parseInt(cols[1]), Integer.parseInt(cols[2]), optimize));
                }
            }
        }
        return args.stream();
    }

    static Stream<Arguments> caseNames() throws IOException {
        return caseChunks().map(a -> (String) a.get()[0]).distinct()
            .flatMap(name -> Stream.of(Arguments.of(name, false), Arguments.of(name, true)));
    }

    @ParameterizedTest(name = "{0} chunk ({1}, {2}) optimize={3}")
    @MethodSource("caseChunks")
    void caseChunkMatchesVanilla(String name, int chunkX, int chunkZ, boolean optimize) throws IOException {
        Case c = new Case(name);
        ReferenceChunk ref = ReferenceChunk.load("case_" + name + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        assertEquals(c.minY, ref.minY);
        assertEquals(c.height, ref.height);

        ChunkDensitySampler sampler = new ChunkDensitySampler(
            c.build(optimize), c.cellWidth, c.cellHeight, c.minY, c.minY + c.height, 0);
        double[] out = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, out);

        String error = compare(ref, sampler, out);
        assertTrue(error == null, error);
    }

    /// Every chunk must behave as if generated from scratch, but the sampler reuses its per-thread state: generating
    /// another chunk first must not change the result. The west neighbour shares a corner column with the chunk, so position-keyed
    /// state (like `cache_2d`) carries over too.
    @ParameterizedTest(name = "{0} chunk ({1}, {2}) optimize={3}")
    @MethodSource("caseChunks")
    void caseChunkMatchesVanillaAfterAnotherChunk(String name, int chunkX, int chunkZ, boolean optimize)
        throws IOException {
        Case c = new Case(name);
        ReferenceChunk ref = ReferenceChunk.load("case_" + name + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        ChunkDensitySampler sampler = new ChunkDensitySampler(
            c.build(optimize), c.cellWidth, c.cellHeight, c.minY, c.minY + c.height, 0);
        double[] out = sampler.newBuffer();
        sampler.sample(chunkX - 1, chunkZ, out);
        sampler.sample(chunkX, chunkZ, out);

        String error = compare(ref, sampler, out);
        assertTrue(error == null, error);
    }

    /// In the stateful regime a block's value can depend on cells outside a clipped Y range (a nested cell cache read
    /// at a corner returns the most recently filled cell's value), so the sampler evaluates the dimension's full range
    /// and copies out the requested part.
    @ParameterizedTest(name = "{0} chunk ({1}, {2}) optimize={3}")
    @MethodSource("caseChunks")
    void caseChunkMatchesVanillaInAClippedRange(String name, int chunkX, int chunkZ, boolean optimize) throws IOException {
        Case c = new Case(name);
        ReferenceChunk ref = ReferenceChunk.load("case_" + name + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        int minY = c.minY + c.cellHeight, maxY = c.minY + c.height - c.cellHeight;
        ChunkDensitySampler sampler = new ChunkDensitySampler(
            c.build(optimize), c.cellWidth, c.cellHeight, minY, maxY, c.minY, c.minY + c.height, 0, false);
        assertEquals(minY, sampler.minY());
        double[] out = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, out);

        String error = compare(ref, sampler, out);
        assertTrue(error == null, error);
    }

    /// With Cubic Chunks the generated range can extend past the dimension's: blocks inside the dimension's range
    /// must still match.
    @ParameterizedTest(name = "{0} chunk ({1}, {2}) optimize={3}")
    @MethodSource("caseChunks")
    void caseChunkMatchesVanillaInAWiderRange(String name, int chunkX, int chunkZ, boolean optimize) throws IOException {
        Case c = new Case(name);
        ReferenceChunk ref = ReferenceChunk.load("case_" + name + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        int minY = c.minY - 2 * c.cellHeight, maxY = c.minY + c.height + 3 * c.cellHeight;
        ChunkDensitySampler sampler = new ChunkDensitySampler(
            c.build(optimize), c.cellWidth, c.cellHeight, minY, maxY, c.minY, c.minY + c.height, 0, false);
        assertEquals(minY, sampler.minY());
        double[] out = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, out);

        for (int y = c.minY; y < c.minY + c.height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    double got = out[((y - minY) * 16 + z) * 16 + x];
                    assertEquals(ref.get(x, y, z) + 0.0, got + 0.0, "at (" + x + ", " + y + ", " + z + ")");
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} optimize={1}")
    @MethodSource("caseNames")
    void casePointsMatchVanilla(String name, boolean optimize) throws IOException {
        DensityNode f = new Case(name).build(optimize);
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
            resource("case_" + name + "_points.tsv"), StandardCharsets.UTF_8))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] cols = line.split("\t");
                int x = Integer.parseInt(cols[0]), y = Integer.parseInt(cols[1]), z = Integer.parseInt(cols[2]);
                double expected = Double.parseDouble(cols[3]);
                double actual = f.sample(x, y, z);
                checked++;
                if (!(expected == actual || (Double.isNaN(expected) && Double.isNaN(actual)))) {
                    mismatches.add(String.format("(%d, %d, %d): expected %s, got %s", x, y, z, expected, actual));
                }
            }
        }
        assertTrue(checked >= 300);
        assertTrue(mismatches.isEmpty(), mismatches.size() + "/" + checked + " differ, first few:\n  "
            + String.join("\n  ", mismatches.subList(0, Math.min(10, mismatches.size()))));
    }

    /// The nested-marker cases exist to pin down the stateful regime; make sure they still reach it.
    @ParameterizedTest(name = "{0}")
    @CsvSource({ "nested_interpolated", "nested_cell_caches" })
    void nestedCasesUseStatefulEvaluator(String name) throws IOException {
        Case c = new Case(name);
        ChunkDensitySampler sampler = new ChunkDensitySampler(
            c.build(false), c.cellWidth, c.cellHeight, c.minY, c.minY + c.height, 0);
        assertTrue(!sampler.isPure());
    }

    /// Guards against a case matching trivially: with another seed, most of the chunk must change.
    @ParameterizedTest(name = "{0} chunk ({1}, {2})")
    @MethodSource("caseChunks")
    void caseDependsOnSeed(String name, int chunkX, int chunkZ, boolean optimize) throws IOException {
        if (optimize) return;
        // The main end island only depends on the distance to the origin; the seeded islands start much further out.
        if (name.equals("end_islands") && Math.abs(chunkX) < 64 && Math.abs(chunkZ) < 64) return;
        Case c = new Case(name);
        ReferenceChunk ref = ReferenceChunk.load("case_" + name + "_" + chunkX + "_" + chunkZ + ".bin.gz");
        DensityNode f = VanillaWorldgen.context(c.seed + 1, false).build(VanillaWorldgen.parseFunction(c.density));
        ChunkDensitySampler sampler = new ChunkDensitySampler(f, c.cellWidth, c.cellHeight, c.minY, c.minY + c.height, 0);
        double[] out = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, out);

        int differing = 0;
        for (int i = 0; i < out.length; i++) if (out[i] != ref.values[i]) differing++;
        assertTrue(differing > out.length / 2, "only " + differing + "/" + out.length + " values differ");
    }

    // </editor-fold>

    // <editor-fold desc="Sanity">

    /// Guards against the comparisons above passing vacuously.
    @ParameterizedTest(name = "{0}")
    @CsvSource({ "overworld", "nether" })
    void wrongSeedDoesNotMatch(String dimension) throws IOException {
        ReferenceChunk ref = ReferenceChunk.load(
            "chunk_" + dimension + "_12345_" + (dimension.equals("nether") ? "2_-3" : "0_0") + ".bin.gz");
        VanillaWorldgen.Settings s = VanillaWorldgen.settings(dimension);
        DensityRouter router = VanillaWorldgen.router(dimension, 12346, true);

        ChunkDensitySampler sampler = new ChunkDensitySampler(
            router.finalDensity, s.cellWidth(), s.cellHeight(), ref.minY, ref.minY + ref.height, 0);
        double[] out = sampler.newBuffer();
        sampler.sample(dimension.equals("nether") ? 2 : 0, dimension.equals("nether") ? -3 : 0, out);

        int differing = 0;
        for (int i = 0; i < out.length; i++) if (out[i] != ref.values[i]) differing++;
        // Large parts of the overworld are constant (the top and bottom slides), so not everything changes.
        assertTrue(differing > out.length / 5, "only " + differing + " values differ");
    }

    // </editor-fold>
}
