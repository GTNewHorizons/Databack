package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.noise.PerlinSimplexNoise;
import databack.common.worldgen.rng.StandardRandom;

/// [BiomeTemperature] and [PerlinSimplexNoise] against vanilla's, probed black-box (`run.sh feature`).
class BiomeTemperatureTest {

    @Test
    void octavesMatch() throws IOException {
        for (String[] row : rows("simplex_octaves.tsv")) {
            List<Integer> octaves = new ArrayList<>();
            for (String o : row[1].replaceAll("[\\[\\]]", "").split(",")) octaves.add(Integer.parseInt(o));
            if (Collections.max(octaves) > 0) continue;

            PerlinSimplexNoise noise = new PerlinSimplexNoise(new StandardRandom(Long.parseLong(row[0])), octaves);
            assertEquals(Double.parseDouble(row[4]), noise.getValue(Double.parseDouble(row[2]),
                Double.parseDouble(row[3])), 1e-12, String.join(" ", row));
        }
    }

    @Test
    void positiveOctavesAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new PerlinSimplexNoise(new StandardRandom(0), Arrays.asList(0, 1)));
    }

    @Test
    void temperaturesMatch() throws IOException {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        Map<String, BiomeTemperature> biomes = new HashMap<>();

        for (String[] row : rows("climate.tsv")) {
            BiomeTemperature temperature = biomes.computeIfAbsent(row[0], BiomeTemperatureTest::biome);
            int x = Integer.parseInt(row[1]), y = Integer.parseInt(row[2]), z = Integer.parseInt(row[3]);
            String at = String.join(" ", row);

            assertEquals(Float.parseFloat(row[5]), temperature.at(x, y, z), 1e-6, at);
            assertEquals(Boolean.parseBoolean(row[6]), temperature.coldEnoughToSnow(x, y, z), at);
        }
    }

    @Test
    void frozenPatchesMatch() throws IOException {
        assumeTrue(VanillaWorldgen.available(), "No extracted vanilla datapack");
        BiomeTemperature frozenOcean = biome("frozen_ocean");

        for (String[] row : rows("frozen_modifier.tsv")) {
            assertEquals(Float.parseFloat(row[2]),
                frozenOcean.at(Integer.parseInt(row[0]), 63, Integer.parseInt(row[1])), String.join(" ", row));
        }
    }

    private static BiomeTemperature biome(String name) {
        DatapackBiome definition = VanillaWorldgen.definition("biome", "minecraft:" + name, DatapackBiome.class);
        assertNotNull(definition, name);
        return BiomeTemperature.of(definition);
    }

    private static List<String[]> rows(String file) throws IOException {
        InputStream in = BiomeTemperatureTest.class.getResourceAsStream("/vanilla_reference/feature/probes/" + file);
        assertNotNull(in, "Missing probe " + file + " (run misc/vanilla-reference/run.sh feature)");
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Iterator<String> lines = r.lines().iterator();
            lines.next();
            while (lines.hasNext()) rows.add(lines.next().split("\t"));
        }
        return rows;
    }
}
