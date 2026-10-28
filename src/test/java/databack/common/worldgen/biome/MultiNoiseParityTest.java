package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import databack.common.worldgen.biome.preset.VanillaPresetExtractor;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.vanilla.VanillaJar;

/// Climate sampling and biome selection against the harness dumps (`climate_<dim>_<seed>.tsv`): our router, sampled
/// per spec section 3, must give vanilla's target points, and the preset searched per section 4 vanilla's biomes.
///
/// Where several entries are equally close, vanilla's answer depends on its earlier queries (spec section 4), so the
/// dump may hold any of them; there the test only requires the dumped biome to be one of the tied entries.
class MultiNoiseParityTest {

    private static List<ClimateEntry> overworld;
    private static List<ClimateEntry> nether;

    @BeforeAll
    static void loadPresets() throws IOException {
        overworld = VanillaPresetExtractor.overworld(VanillaJar.classFile(VanillaPresetExtractor.OVERWORLD_CLASS));
        nether = VanillaPresetExtractor.nether(VanillaJar.classFile(VanillaPresetExtractor.NETHER_CLASS));
    }

    @ParameterizedTest
    @CsvSource({
        "overworld, 12345, true", "overworld, -987654321, false",
        "large_biomes, 12345, true", "large_biomes, -987654321, true",
        "amplified, 12345, false", "amplified, -987654321, true",
        "nether, 12345, true", "nether, -987654321, false",
    })
    void climateAndBiomesMatchVanilla(String dimension, long seed, boolean optimize) throws IOException {
        DensityRouter router = VanillaWorldgen.router(dimension, seed, optimize);
        ClimateSampler sampler = ClimateSampler.of(router);
        List<ClimateEntry> preset = dimension.equals("nether") ? nether : overworld;
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.create(preset, sampler);
        BruteForceBiomeSearch brute = new BruteForceBiomeSearch(preset);
        int ties = 0;

        List<String> mismatches = new ArrayList<>();
        List<String[]> rows = read("climate_" + dimension + "_" + seed + ".tsv");
        long[] target = new long[ClimateEntry.PARAMETER_COUNT];

        for (String[] row : rows) {
            int qx = Integer.parseInt(row[0]), qy = Integer.parseInt(row[1]), qz = Integer.parseInt(row[2]);

            sampler.sample(qx, qy, qz, target);
            for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
                long expected = Long.parseLong(row[3 + p]);
                if (target[p] != expected) {
                    mismatches.add(String.format("(%d, %d, %d) parameter %d: expected %d, got %d", qx, qy, qz, p,
                        expected, target[p]));
                }
            }

            String biome = source.getNoiseBiome(qx, qy, qz);
            if (!row[9].equals(biome)) {
                if (isTiedClosest(preset, preset.get(brute.closest(target)).distance(target), target, row[9])) {
                    ties++;
                } else {
                    mismatches.add(String.format("(%d, %d, %d): expected %s, got %s", qx, qy, qz, row[9], biome));
                }
            }
        }

        assertTrue(rows.size() > 3000);
        assertTrue(ties * 500 < rows.size(), ties + " tied cells resolved differently by vanilla");
        assertTrue(mismatches.isEmpty(), mismatches.size() + " mismatches over " + rows.size() + " cells, first few:\n  "
            + String.join("\n  ", mismatches.subList(0, Math.min(15, mismatches.size()))));
    }

    private static boolean isTiedClosest(List<ClimateEntry> entries, long closest, long[] target, String biome) {
        for (ClimateEntry e : entries) {
            if (e.biome().equals(biome) && e.distance(target) == closest) return true;
        }
        return false;
    }

    static List<String[]> read(String name) throws IOException {
        InputStream in = MultiNoiseParityTest.class.getResourceAsStream("/vanilla_reference/biome/" + name);
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
