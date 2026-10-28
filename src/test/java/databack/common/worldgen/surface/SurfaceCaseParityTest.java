package databack.common.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/// Each synthetic case (`vanilla_reference/surface/cases`) on vanilla's own terrain: our surface system must place
/// what vanilla placed. Cases that depend only on exact inputs must match every block; cases involving an
/// approximation (docs/worldgen/surface-rules.md) must agree on at least the given share of solid blocks.
class SurfaceCaseParityTest {

    private static final int[][] CHUNKS = { { -218, -70095 }, { -348, 6 }, { 10, 455 }, { 138, 107 }, { 157, -188 },
        { 379, 337 }, { -64, -172 }, { -266, -248 }, { 22, 156 }, { -32, -216 } };

    private static SurfaceFixture fixture;

    @BeforeAll
    static void load() throws IOException {
        fixture = new SurfaceFixture(12345L);
    }

    @ParameterizedTest
    @CsvSource({
        "stone_depth_floor, 1.0", "stone_depth_ceiling, 1.0", "stone_depth_floor_secondary, 1.0",
        "water_offset, 1.0", "water_stone_depth, 1.0", "y_above, 1.0", "y_above_stone_depth, 1.0",
        "noise_threshold_surface, 1.0", "biome_lookup, 1.0", "steep, 1.0",
        // Approximations: surface depth's random part, vertical gradients, temperature, the preliminary surface
        // threshold and bandlands.
        "stone_depth_floor_surface, 0.99", "water_surface_multiplier, 0.95", "y_above_surface_multiplier, 0.95",
        "hole, 0.99", "temperature, 0.97", "above_preliminary_surface, 0.95", "vertical_gradient, 0.95",
        "vertical_gradient_bottom, 0.98",
    })
    void caseMatchesVanilla(String name, double required) throws IOException {
        SurfaceSystem system = fixture.system(rule(name));
        long same = 0, total = 0;
        String firstMismatch = null;

        for (int[] c : CHUNKS) {
            SurfaceDump dump = SurfaceDump.read("case_" + name + "_" + c[0] + "_" + c[1] + ".bin.gz");
            byte[] kinds = dump.kinds();
            ImmutableBlockMeta[] placed = system.apply(c[0], c[1], kinds, dump.minY, dump.height, fixture.biomes());

            for (int i = 0; i < kinds.length; i++) {
                if (kinds[i] != SurfaceSystem.SOLID) continue;
                int x = i & 15, z = (i >> 4) & 15, y = dump.minY + (i >> 8);
                String expected = dump.block(x, y, z), actual = SurfaceFixture.decode(placed[i], "minecraft:stone");
                total++;
                if (expected.equals(actual)) {
                    same++;
                } else if (firstMismatch == null) {
                    firstMismatch = String.format("chunk %d,%d at (%d, %d, %d): expected %s, got %s", c[0], c[1], x, y,
                        z, expected, actual);
                }
            }
        }

        double share = (double) same / total;
        assertTrue(share >= required, String.format("%s: %.4f of %d solid blocks agree (need %.2f); first mismatch: %s",
            name, share, total, required, firstMismatch));
    }

    /// **Approximation:** our stripe pattern is our own, so only its character is compared: each colour's share of
    /// the terracotta must be within 0.1 of vanilla's, and the same colours must appear.
    @Test
    void bandlandsHaveVanillasColours() throws IOException {
        SurfaceSystem system = fixture.system(rule("bandlands"));
        Map<String, Integer> expected = new TreeMap<>(), actual = new TreeMap<>();

        for (int[] c : CHUNKS) {
            SurfaceDump dump = SurfaceDump.read("case_bandlands_" + c[0] + "_" + c[1] + ".bin.gz");
            byte[] kinds = dump.kinds();
            ImmutableBlockMeta[] placed = system.apply(c[0], c[1], kinds, dump.minY, dump.height, fixture.biomes());

            for (int i = 0; i < kinds.length; i++) {
                if (kinds[i] != SurfaceSystem.SOLID) continue;
                expected.merge(dump.block(i & 15, dump.minY + (i >> 8), (i >> 4) & 15), 1, Integer::sum);
                actual.merge(SurfaceFixture.decode(placed[i], "minecraft:stone"), 1, Integer::sum);
            }
        }

        assertEquals(expected.keySet(), actual.keySet());
        double expectedTotal = total(expected), actualTotal = total(actual);
        for (String colour : expected.keySet()) {
            double e = expected.get(colour) / expectedTotal, a = actual.get(colour) / actualTotal;
            assertTrue(Math.abs(e - a) <= 0.1, String.format("%s: vanilla %.3f, ours %.3f", colour, e, a));
        }
    }

    private static double total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static JsonElement rule(String name) throws IOException {
        InputStream in = SurfaceCaseParityTest.class.getResourceAsStream(
            "/vanilla_reference/surface/cases/" + name + ".json");
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = new JsonParser().parse(r).getAsJsonObject();
            return json.get("surface_rule");
        }
    }
}
