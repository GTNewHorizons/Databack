package databack.common.worldgen.aquifer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import databack.common.worldgen.surface.SurfaceDump;

/// Our aquifer fill against vanilla's, on vanilla's own density (docs/worldgen/aquifers.md). Agreement is the share of
/// open blocks (density <= 0) given the same fate (air, water, lava, stone barrier).
class AquiferParityTest {

    private static final int MIN_Y = -64, HEIGHT = 384;
    private static final int[][] CASE_CHUNKS = { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, { 6, -3 } };
    private static final int[][] REAL_CHUNKS = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 },
        { 3, 3 }, { -40, 77 }, { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };

    /// Fully open terrain with constant aquifer fields: vanilla's aquifer runs as is, so every block must match.
    @ParameterizedTest
    @CsvSource({
        "flood_m1, 1.0", "flood_0, 1.0", "flood_04, 1.0", "flood_09, 1.0", "flood_1, 1.0", "prelim_m100, 1.0",
        "flood_05, 1.0", "flood_08, 1.0", "spread05_m10, 1.0", "spread05_10, 1.0", "lava05_10, 1.0",
    })
    void syntheticCaseMatches(String name, double required) throws IOException {
        AquiferSystem system = AquiferFixture.forCase(name);
        double[] open = new double[HEIGHT * 256];
        Arrays.fill(open, -1.0);

        Agreement agreement = new Agreement();
        for (int[] c : CASE_CHUNKS) {
            SurfaceDump vanilla = SurfaceDump.read("aquifer", "case_" + name + "_" + c[0] + "_" + c[1] + ".bin.gz");
            agreement.add(system.kinds(c[0], c[1], open, MIN_Y, HEIGHT), vanilla, open);
        }

        agreement.require(name, required);
    }

    @ParameterizedTest
    @ValueSource(longs = { 12345L, -987654321L })
    void realChunksAreCloseToVanilla(long seed) throws IOException {
        AquiferSystem system = AquiferFixture.overworld(seed);

        Agreement agreement = new Agreement();
        for (int[] c : REAL_CHUNKS) {
            String base = "chunk_" + seed + "_" + c[0] + "_" + c[1];
            double[] density = AquiferFixture.density(base + "_density.bin.gz");
            SurfaceDump vanilla = SurfaceDump.read("aquifer", base + "_blocks.bin.gz");
            agreement.add(system.kinds(c[0], c[1], density, MIN_Y, HEIGHT), vanilla, density);
        }

        // Only the aquifer's positional randomness is ours (docs/worldgen/aquifers.md).
        agreement.require("seed " + seed, 0.995);
    }

    private static final class Agreement {

        long same, total;
        final Map<String, long[]> shares = new TreeMap<>();

        void add(byte[] ours, SurfaceDump vanilla, double[] density) {
            for (int i = 0; i < ours.length; i++) {
                if (density[i] > 0) continue;
                String expected = vanilla.block(i & 15, MIN_Y + (i >> 8), (i >> 4) & 15).replace("minecraft:", "");
                String actual = AquiferFixture.kindName(ours[i]);
                total++;
                if (expected.equals(actual)) same++;
                shares.computeIfAbsent(expected, k -> new long[2])[0]++;
                shares.computeIfAbsent(actual, k -> new long[2])[1]++;
            }
        }

        void require(String name, double required) {
            double share = (double) same / total;
            StringBuilder mix = new StringBuilder();
            shares.forEach((k, v) -> mix.append(String.format(" %s %.3f/%.3f", k, v[0] / (double) total,
                v[1] / (double) total)));
            System.out.printf("AQUIFER %s: %.4f agree; vanilla/ours:%s%n", name, share, mix);
            assertTrue(share >= required, String.format("%s: %.4f of %d open blocks agree (need %.2f); vanilla/ours:%s",
                name, share, total, required, mix));
        }
    }
}
