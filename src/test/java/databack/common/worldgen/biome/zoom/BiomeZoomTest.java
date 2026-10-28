package databack.common.worldgen.biome.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import databack.common.worldgen.vanilla.VanillaJar;

/// Runs the zoom on the real 26.1 classes and compares with the harness dumps (`misc/vanilla-reference/run.sh biome`).
class BiomeZoomTest {

    private static BiomeZoom zoom;

    @BeforeAll
    static void load() throws IOException {
        zoom = BiomeZoom.load(VanillaJar.classFile(BiomeZoom.MANAGER_CLASS), VanillaJar.classFile(BiomeZoom.LCG_CLASS));
    }

    @Test
    void zoomSeedsMatchVanilla() throws IOException {
        List<String[]> rows = read("zoom_seeds.tsv");
        assertTrue(rows.size() > 50);

        for (String[] row : rows) {
            assertEquals(Long.parseLong(row[1]), zoom.zoomSeed(Long.parseLong(row[0])), "seed " + row[0]);
        }
    }

    /// The source answers with the quart cell it was asked for, in the dump's `qx,qy,qz` form, and records how many
    /// times it was asked.
    @ParameterizedTest
    @ValueSource(longs = { 12345L, -987654321L })
    void blocksReadTheSameCellAsVanilla(long seed) throws IOException {
        int[] calls = new int[1];
        ZoomedBiomes<String> biomes = zoom.zoom((qx, qy, qz) -> {
            calls[0]++;
            return qx + "," + qy + "," + qz;
        }, zoom.zoomSeed(seed));

        List<String[]> rows = read("zoom_" + seed + ".tsv");
        assertTrue(rows.size() > 10_000);

        List<String> mismatches = new ArrayList<>();
        for (String[] row : rows) {
            int x = Integer.parseInt(row[0]), y = Integer.parseInt(row[1]), z = Integer.parseInt(row[2]);
            calls[0] = 0;
            String cell = biomes.biome(x, y, z);
            if (!cell.equals(row[3]) || calls[0] != 1) {
                mismatches.add("(" + x + ", " + y + ", " + z + "): expected " + row[3] + ", got " + cell + " after "
                    + calls[0] + " lookups");
            }
        }

        assertTrue(mismatches.isEmpty(), mismatches.size() + "/" + rows.size() + " differ, first few:\n  "
            + String.join("\n  ", mismatches.subList(0, Math.min(10, mismatches.size()))));
    }

    @Test
    void wrongClassesFail() throws IOException {
        byte[] lcg = VanillaJar.classFile(BiomeZoom.LCG_CLASS);
        assertThrows(IllegalStateException.class, () -> BiomeZoom.load(lcg, lcg));
    }

    private static List<String[]> read(String name) throws IOException {
        InputStream in = BiomeZoomTest.class.getResourceAsStream("/vanilla_reference/biome/" + name);
        assertTrue(in != null, "Missing reference data " + name + " (run misc/vanilla-reference/run.sh biome)");

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) rows.add(line.split("\t"));
        }
        return rows;
    }
}
