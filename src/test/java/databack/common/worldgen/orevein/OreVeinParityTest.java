package databack.common.worldgen.orevein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceDump;
import databack.common.worldgen.surface.SurfaceSystem;

/// Our ore veins against vanilla's (docs/worldgen/ore-veins.md). Where veins are is exact given the fields; what they
/// are made of uses our own randomness, so it is compared by shares.
class OreVeinParityTest {

    private static final int MIN_Y = OreVeinFixture.MIN_Y, HEIGHT = OreVeinFixture.HEIGHT;

    /// Below this many candidates, a layer may hold no vein blocks by chance (0.3^30 for all of them to stay stone).
    private static final int LAYER_CANDIDATES = 30;

    /// Constant fields: every layer must hold veins exactly when vanilla's does, at the same shares.
    @ParameterizedTest
    @ValueSource(strings = {
        "toggle_02", "toggle_03", "toggle_04", "toggle_041", "toggle_043", "toggle_045", "toggle_047", "toggle_05",
        "toggle_052", "toggle_055", "toggle_058", "toggle_06", "toggle_07", "toggle_08", "toggle_10", "toggle_15",
        "toggle_m02", "toggle_m03", "toggle_m04", "toggle_m05", "toggle_m06", "toggle_m07", "toggle_m08", "toggle_m10",
        "toggle_m15", "gap_0.5", "gap_0", "gap_m0.25", "gap_m0.29", "gap_m0.3", "gap_m0.31", "gap_m0.35", "gap_m0.4",
        "gap_m0.5", "gap_m1", "ridged_0.01", "ridged_0", "ridged_m0.01", "aquifer_barriers",
    })
    void constantCasesMatchPerLayer(String name) throws IOException {
        JsonObject json = OreVeinFixture.caseJson(name);
        OreVeinSystem system = OreVeinFixture.system(json.get("seed").getAsLong());
        VeinFields fields = OreVeinFixture.constantFields(json.getAsJsonObject("router"));

        Shares ours = new Shares(), vanilla = new Shares();
        for (JsonElement chunk : json.getAsJsonArray("chunks")) {
            int cx = chunk.getAsJsonArray().get(0).getAsInt(), cz = chunk.getAsJsonArray().get(1).getAsInt();
            SurfaceDump dump = OreVeinFixture.blocks("case_" + name + "_" + cx + "_" + cz);
            byte[] kinds = dump.kinds();
            VeinBlock[] placed = system.place(cx, cz, kinds, MIN_Y, HEIGHT, fields);

            for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
                boolean predicted = OreVeinSystem.inVein(OreVeinSystem.Kind.of(fields.toggle[0]), y,
                    fields.toggle[0], fields.ridged[0]);
                boolean vanillaVein = false;
                int candidates = 0;
                for (int column = 0; column < 256; column++) {
                    int index = (y - MIN_Y) * 256 + column;
                    if (kinds[index] != SurfaceSystem.SOLID) continue;

                    String block = dump.block(column & 15, y, column >> 4);
                    vanillaVein |= OreVeinFixture.isVeinBlock(block);
                    candidates++;
                    if (!predicted) continue;
                    vanilla.add(block);
                    ours.add(placed[index] == null ? "minecraft:stone" : placed[index].id);
                }
                if (vanillaVein || candidates >= LAYER_CANDIDATES) {
                    assertEquals(predicted, vanillaVein, name + ": veins at y " + y);
                }
            }
        }

        ours.requireCloseTo(vanilla, name);
    }

    /// Vanilla's own field values: every vein block vanilla placed must be in a vein, and the rest of the veins must
    /// stay stone at vanilla's rate.
    @ParameterizedTest
    @ValueSource(strings = {
        "case_solid_real_0_0", "case_solid_real_3_3", "case_solid_real_-512_-512", "case_solid_real_120_-15",
        "case_real_ridged_3_3", "case_real_toggle_3_3", "chunk_12345_0_0", "chunk_12345_-7_13",
        "chunk_-987654321_0_0", "chunk_-987654321_-7_13",
    })
    void vanillaFieldsGiveTheVeins(String base) throws IOException {
        SurfaceDump dump = OreVeinFixture.blocks(base);
        VeinFields fields = OreVeinFixture.fields(base);
        byte[] kinds = dump.kinds();

        long predicted = 0, stone = 0;
        for (int y = fields.minY; y < fields.minY + fields.layers; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (kinds[((y - MIN_Y) * 16 + z) * 16 + x] != SurfaceSystem.SOLID) continue;

                    int field = fields.index(x, y, z);
                    double toggle = fields.toggle[field];
                    boolean inVein = OreVeinSystem.inVein(OreVeinSystem.Kind.of(toggle), y, toggle,
                        fields.ridged[field]);
                    String block = dump.block(x, y, z);
                    assertTrue(inVein || !OreVeinFixture.isVeinBlock(block),
                        base + ": " + block + " outside a vein at " + x + " " + y + " " + z);

                    if (!inVein) continue;
                    predicted++;
                    if (block.equals("minecraft:stone")) stone++;
                }
            }
        }

        if (predicted > 0) {
            double share = (double) stone / predicted, sigma = Math.sqrt(0.3 * 0.7 / predicted);
            assertTrue(Math.abs(share - 0.3) < 4 * sigma, base + ": stone share in veins " + share + " of " + predicted);
        }
    }

    /// Our evaluator's vein fields against vanilla's interpolated values.
    @ParameterizedTest
    @CsvSource({ "12345, 0, 0", "12345, -7, 13", "-987654321, 0, 0", "-987654321, -7, 13" })
    void ourFieldsMatchVanilla(long seed, int chunkX, int chunkZ) throws IOException {
        VeinFields expected = OreVeinFixture.fields("chunk_" + seed + "_" + chunkX + "_" + chunkZ);
        VeinFieldSampler sampler = VeinFieldSampler.create(VanillaWorldgen.router("overworld", seed, true), 4, 8,
            MIN_Y, MIN_Y + HEIGHT, MIN_Y, MIN_Y + HEIGHT);
        assertNotNull(sampler);
        VeinFields actual = sampler.sample(chunkX, chunkZ);

        int compared = 0;
        for (int y = actual.minY; y < actual.minY + actual.layers; y++) {
            if (!expected.covers(y)) continue;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int a = actual.index(x, y, z), e = expected.index(x, y, z);
                    String at = " at " + x + " " + y + " " + z;
                    assertEquals(expected.toggle[e], actual.toggle[a], 1e-9, "vein_toggle" + at);
                    assertEquals(expected.ridged[e], actual.ridged[a], 1e-9, "vein_ridged" + at);
                    assertEquals(expected.gap[e], actual.gap[a], 1e-9, "vein_gap" + at);
                    compared++;
                }
            }
        }
        assertTrue(compared >= 100 * 256, "compared " + compared);
    }

    /// Our whole placement, on our fields, against vanilla's real chunks: the same shares of filler, ore and raw
    /// blocks.
    @ParameterizedTest
    @ValueSource(longs = { 12345L, -987654321L })
    void realChunksAreCloseToVanilla(long seed) throws IOException {
        int[][] chunks = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 }, { 3, 3 }, { -40, 77 },
            { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };
        OreVeinSystem system = OreVeinFixture.system(seed);
        VeinFieldSampler sampler = VeinFieldSampler.create(VanillaWorldgen.router("overworld", seed, true), 4, 8,
            MIN_Y, MIN_Y + HEIGHT, MIN_Y, MIN_Y + HEIGHT);
        assertNotNull(sampler);

        long[] ours = new long[2], vanilla = new long[2];
        for (int[] c : chunks) {
            SurfaceDump dump = OreVeinFixture.blocks("chunk_" + seed + "_" + c[0] + "_" + c[1]);
            byte[] kinds = dump.kinds();
            VeinBlock[] placed = system.place(c[0], c[1], kinds, MIN_Y, HEIGHT, sampler.sample(c[0], c[1]));

            for (int i = 0; i < kinds.length; i++) {
                if (placed[i] != null) ours[category(placed[i].id)]++;
                String block = dump.block(i & 15, MIN_Y + (i >> 8), (i >> 4) & 15);
                if (OreVeinFixture.isVeinBlock(block)) vanilla[category(block)]++;
            }
        }

        long oursTotal = ours[0] + ours[1], vanillaTotal = vanilla[0] + vanilla[1];
        assertTrue(vanillaTotal > 0, "seed " + seed + " has no veins");
        assertEquals(1.0, (double) oursTotal / vanillaTotal, 0.1, "vein blocks, seed " + seed);
        assertEquals((double) vanilla[1] / vanillaTotal, (double) ours[1] / oursTotal, 0.05, "ore share, seed " + seed);
    }

    /// 0 filler, 1 ore (raw blocks included).
    private static int category(String block) {
        return OreVeinFixture.isOre(block) ? 1 : 0;
    }

    /// Shares of stone, filler, ore and raw blocks among the blocks of veins.
    private static final class Shares {

        long stone, filler, ore, raw;

        void add(String block) {
            if (block.equals("minecraft:stone")) stone++;
            else if (block.startsWith("minecraft:raw_")) raw++;
            else if (OreVeinFixture.isOre(block)) ore++;
            else filler++;
        }

        long total() {
            return stone + filler + ore + raw;
        }

        void requireCloseTo(Shares vanilla, String name) {
            if (vanilla.total() == 0) {
                assertEquals(0, total(), name);
                return;
            }
            require(name + " stone", stone, vanilla.stone);
            require(name + " filler", filler, vanilla.filler);
            require(name + " ore", ore + raw, vanilla.ore + vanilla.raw);
        }

        private void require(String what, long ours, long theirs) {
            double share = (double) ours / total(), expected = (double) theirs / total();
            assertEquals(expected, share, 0.03, what);
        }
    }
}
