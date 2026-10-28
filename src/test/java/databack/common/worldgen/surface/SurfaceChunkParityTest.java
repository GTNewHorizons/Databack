package databack.common.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/// Vanilla's overworld surface rule, applied by our system to vanilla's real chunks (terrain without aquifers, after
/// the noise step), against vanilla's result. Statistical, since parts of the system are approximations
/// (docs/worldgen/surface-rules.md): most blocks must agree, and the same blocks must be used.
class SurfaceChunkParityTest {

    private static final int[][] CHUNKS = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 } };

    @ParameterizedTest
    @ValueSource(longs = { 12345L, -987654321L })
    void overworldSurfaceIsCloseToVanilla(long seed) throws IOException {
        SurfaceFixture fixture = new SurfaceFixture(seed);
        SurfaceSystem system = fixture.system(SurfaceFixture.overworldRule());

        long same = 0, total = 0, nearSurfaceSame = 0, nearSurface = 0, fixedSame = 0, fixed = 0;
        Set<String> expectedBlocks = new TreeSet<>(), actualBlocks = new TreeSet<>();

        for (int[] c : CHUNKS) {
            String base = "chunk_overworld_no_aquifers_" + seed + "_" + c[0] + "_" + c[1];
            SurfaceDump terrain = SurfaceDump.read(base + "_noise.bin.gz");
            SurfaceDump surface = SurfaceDump.read(base + "_surface.bin.gz");
            byte[] kinds = terrain.kinds();
            ImmutableBlockMeta[] placed = system.apply(c[0], c[1], kinds, terrain.minY, terrain.height,
                fixture.biomes());

            for (int i = 0; i < kinds.length; i++) {
                if (kinds[i] != SurfaceSystem.SOLID) continue;
                int x = i & 15, z = (i >> 4) & 15, y = terrain.minY + (i >> 8);
                String expected = surface.block(x, y, z), actual = SurfaceFixture.decode(placed[i], "minecraft:stone");
                expectedBlocks.add(expected);
                actualBlocks.add(actual);

                total++;
                if (expected.equals(actual)) same++;

                // Outside the rule's two random fades (bedrock at the bottom, deepslate up to y 8).
                if (y >= 8 || (y >= terrain.minY + 5 && y < 0)) {
                    fixed++;
                    if (expected.equals(actual)) fixedSame++;
                }

                // The layers the surface rule decides most, right under the terrain's open blocks.
                if (y + 1 < terrain.minY + terrain.height && kinds[i + 256] != SurfaceSystem.SOLID) {
                    nearSurface++;
                    if (expected.equals(actual)) nearSurfaceSame++;
                }
            }
        }

        double share = (double) same / total, surfaceShare = (double) nearSurfaceSame / nearSurface;
        double fixedShare = (double) fixedSame / fixed;
        assertEquals(expectedBlocks, actualBlocks);
        assertTrue(share >= 0.95, String.format("%.4f of solid blocks agree", share));
        assertTrue(fixedShare >= 0.995, String.format("%.4f of blocks outside the random fades agree", fixedShare));
        assertTrue(surfaceShare >= 0.95, String.format("%.4f of top blocks agree", surfaceShare));
    }
}
