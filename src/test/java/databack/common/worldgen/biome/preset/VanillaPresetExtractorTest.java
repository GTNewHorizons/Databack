package databack.common.worldgen.biome.preset;

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

import org.junit.jupiter.api.Test;

import databack.common.worldgen.biome.ClimateEntry;
import databack.common.worldgen.vanilla.VanillaJar;

/// Runs the extractor on the real 26.1 classes and compares with the presets the reference harness dumped
/// (`misc/vanilla-reference/run.sh biome`).
class VanillaPresetExtractorTest {

    @Test
    void overworldMatchesVanilla() throws IOException {
        assertPresetMatches("preset_overworld.tsv",
            VanillaPresetExtractor.overworld(VanillaJar.classFile(VanillaPresetExtractor.OVERWORLD_CLASS)));
    }

    @Test
    void netherMatchesVanilla() throws IOException {
        assertPresetMatches("preset_nether.tsv",
            VanillaPresetExtractor.nether(VanillaJar.classFile(VanillaPresetExtractor.NETHER_CLASS)));
    }

    @Test
    void wrongClassFails() throws IOException {
        byte[] nether = VanillaJar.classFile(VanillaPresetExtractor.NETHER_CLASS);
        assertThrows(IllegalStateException.class, () -> VanillaPresetExtractor.overworld(nether));
    }

    private static void assertPresetMatches(String reference, List<ClimateEntry> actual) throws IOException {
        List<ClimateEntry> expected = readPreset(reference);
        assertEquals(expected.size(), actual.size(), "entry count");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), "entry " + i);
        }
    }

    /// Columns: index, biome, then min and max of each parameter in [ClimateEntry]'s order, then the offset.
    private static List<ClimateEntry> readPreset(String name) throws IOException {
        InputStream in = VanillaPresetExtractorTest.class.getResourceAsStream("/vanilla_reference/biome/" + name);
        assertTrue(in != null, "Missing reference data " + name + " (run misc/vanilla-reference/run.sh biome)");

        List<ClimateEntry> entries = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine();

            String line;
            while ((line = r.readLine()) != null) {
                String[] cols = line.split("\t");
                long[] min = new long[ClimateEntry.PARAMETER_COUNT];
                long[] max = new long[ClimateEntry.PARAMETER_COUNT];
                for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
                    min[p] = Long.parseLong(cols[2 + 2 * p]);
                    max[p] = Long.parseLong(cols[3 + 2 * p]);
                }
                entries.add(new ClimateEntry(cols[1], min, max, Long.parseLong(cols[14])));
            }
        }
        return entries;
    }
}
