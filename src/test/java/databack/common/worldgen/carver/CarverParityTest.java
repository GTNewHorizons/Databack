package databack.common.worldgen.carver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import databack.common.worldgen.aquifer.AquiferSystem;
import databack.common.worldgen.aquifer.VanillaAquifer;
import databack.common.worldgen.carver.standin.CvAquifer;
import databack.common.worldgen.carver.standin.CvBlockPos;
import databack.common.worldgen.carver.standin.CvChunk;
import databack.common.worldgen.carver.standin.CvContext;
import databack.common.worldgen.carver.standin.CvStatePool;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceDump;
import databack.common.worldgen.vanilla.VanillaJar;

/// Our carver step, running vanilla's carvers, against vanilla's own (docs/worldgen/carvers.md).
class CarverParityTest {

    private static final int MIN_Y = CarverFixture.MIN_Y, HEIGHT = CarverFixture.HEIGHT;

    private static final int[][] REAL_CHUNKS = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 },
        { 3, 3 }, { -40, 77 }, { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };

    /// Real chunks with vanilla's aquifer, whose randomness is ours: where it puts barriers differs a little, so
    /// carved blocks are compared by agreement (carved by both, to the same block).
    @ParameterizedTest
    @CsvSource({ "12345", "-987654321" })
    void realChunksAreCloseToVanilla(long seed) throws IOException {
        CvStatePool pool = CarverFixture.pool();
        List<ConfiguredCarver> carvers = CarverFixture.configured(pool, CarverFixture.OVERWORLD_CARVERS);
        CarverSystem system = new CarverSystem(seed, (x, z) -> carvers);
        AquiferSystem aquifers = AquiferSystem.enabled(CarverFixture.SEA_LEVEL,
            VanillaAquifer.load(VanillaJar.classFile(VanillaAquifer.CLASS)),
            VanillaWorldgen.router("overworld", seed, true), VanillaWorldgen.context(seed, false).random());

        long both = 0, vanillaOnly = 0, oursOnly = 0;
        CvBlockPos at = new CvBlockPos();
        for (int[] c : REAL_CHUNKS) {
            String base = "chunk_" + seed + "_" + c[0] + "_" + c[1];
            SurfaceDump before = SurfaceDump.read("carver", base + "_surface.bin.gz");
            SurfaceDump expected = SurfaceDump.read("carver", base + "_carved.bin.gz");
            CvChunk chunk = CarverFixture.chunk(pool, before, c[0], c[1]);
            system.carve(chunk, new CvContext(MIN_Y, HEIGHT, (ch, pos, fluid) -> null),
                new CvAquifer(aquifers.chunkAquifer(c[0], c[1], MIN_Y, HEIGHT)));

            for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        String old = before.block(x, y, z), want = expected.block(x, y, z);
                        String got = (String) pool.payload(chunk.getBlockState(at.set(c[0] * 16 + x, y, c[1] * 16 + z)));
                        boolean vanillaCarved = !want.equals(old), oursCarved = !got.equals(old);
                        if (vanillaCarved && oursCarved && want.equals(got)) both++;
                        else if (vanillaCarved) vanillaOnly++;
                        else if (oursCarved) oursOnly++;
                    }
                }
            }
        }

        double agreement = (double) both / (both + vanillaOnly + oursOnly);
        System.out.println("seed " + seed + ": both " + both + ", vanilla only " + vanillaOnly + ", ours only "
            + oursOnly + ", agreement " + agreement);
        assertTrue(agreement > 0.97, "seed " + seed + ": agreement " + agreement);
    }

    /// All-solid terrain, aquifers off, one biome everywhere: every block must match.
    @ParameterizedTest
    @CsvSource({ "0, 0", "3, 3", "-7, 13", "25, -40", "120, -15", "-300, 180", "800, 64", "-512, -512" })
    void solidCaseMatches(int chunkX, int chunkZ) throws IOException {
        String base = "case_solid_" + chunkX + "_" + chunkZ;
        CvStatePool pool = CarverFixture.pool();
        List<ConfiguredCarver> carvers = CarverFixture.configured(pool, CarverFixture.OVERWORLD_CARVERS);
        CarverSystem system = new CarverSystem(12345L, (x, z) -> carvers);

        CvChunk chunk = CarverFixture.chunk(pool, SurfaceDump.read("carver", base + "_surface.bin.gz"), chunkX,
            chunkZ);
        system.carve(chunk, new CvContext(MIN_Y, HEIGHT, (c, pos, fluid) -> null),
            CarverFixture.disabledAquifer(chunkX, chunkZ));

        SurfaceDump expected = SurfaceDump.read("carver", base + "_carved.bin.gz");
        Map<String, Integer> mismatches = new TreeMap<>();
        CvBlockPos at = new CvBlockPos();
        String first = null;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    String want = expected.block(x, y, z);
                    String got = (String) pool.payload(chunk.getBlockState(at.set(chunkX * 16 + x, y, chunkZ * 16 + z)));
                    if (want.equals(got)) continue;
                    mismatches.merge(want + " -> " + got, 1, Integer::sum);
                    if (first == null) first = x + " " + y + " " + z + ": " + want + " vs " + got;
                }
            }
        }

        assertEquals("{}", mismatches.toString(), base + ", first " + first);
    }
}
