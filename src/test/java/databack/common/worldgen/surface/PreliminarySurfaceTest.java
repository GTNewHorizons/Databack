package databack.common.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// The preliminary surface level the surface system uses (per 4x4 cell, at the cell's corner) against the value
/// vanilla's chunk noise state reports for every column (`*.columns.tsv`).
class PreliminarySurfaceTest {

    private static final int[][] CHUNKS = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 } };

    @ParameterizedTest
    @ValueSource(longs = { 12345L, -987654321L })
    void matchesVanilla(long seed) throws IOException {
        SurfaceSystem system = new SurfaceFixture(seed).system(SurfaceFixture.overworldRule());
        int same = 0, total = 0;
        String first = null;

        for (int[] c : CHUNKS) {
            String name = "chunk_overworld_no_aquifers_" + seed + "_" + c[0] + "_" + c[1] + "_surface.columns.tsv";
            try (InputStream in = getClass().getResourceAsStream("/vanilla_reference/surface/" + name);
                 Scanner scanner = new Scanner(in)) {
                scanner.nextLine();
                while (scanner.hasNextLine()) {
                    String[] cols = scanner.nextLine().split("\t");
                    int x = Integer.parseInt(cols[0]), z = Integer.parseInt(cols[1]);
                    int expected = Integer.parseInt(cols[2]);
                    int actual = system.preliminarySurface(c[0] * 16 + (x & ~3), c[1] * 16 + (z & ~3));
                    total++;
                    if (expected == actual) same++;
                    else if (first == null) first = c[0] + "," + c[1] + " (" + x + ", " + z + "): expected " + expected + ", got " + actual;
                }
            }
        }

        assertTrue(same == total, same + " of " + total + " columns match; first mismatch: " + first);
    }
}
