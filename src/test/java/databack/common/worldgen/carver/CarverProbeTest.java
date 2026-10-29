package databack.common.worldgen.carver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import databack.common.dto.worldgen.float_provider.IFloatProvider;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.worldgen.carver.standin.CvMath;
import databack.common.worldgen.carver.standin.CvRandom;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.RandomSourceAdapter;
import databack.common.worldgen.rng.SourceBits;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.rng.XoroshiroRandom;

/// The carver stand-ins against vanilla's helpers, probed black-box (`run.sh carver`, `carver/probes/*.tsv`).
class CarverProbeTest {

    private static final int MIN_Y = -64, HEIGHT = 384;

    @Test
    void mathMatches() throws IOException {
        for (String[] row : rows("mth.tsv")) {
            double x = Double.parseDouble(row[1]);
            String at = row[0] + "(" + row[1] + ")";
            switch (row[0]) {
                case "sin":
                    assertEquals(Float.parseFloat(row[2]), CvMath.sin(x), at);
                    break;
                case "cos":
                    assertEquals(Float.parseFloat(row[2]), CvMath.cos(x), at);
                    break;
                default:
                    assertEquals(Integer.parseInt(row[2]), CvMath.floor(x), at);
            }
        }
    }

    @Test
    void randomSourcesMatch() throws IOException {
        for (String[] row : rows("random.tsv")) {
            long seed = Long.parseLong(row[1]);
            int index = Integer.parseInt(row[3]);
            RandomSource random = row[0].equals("xoroshiro") ? new XoroshiroRandom(seed)
                : row[0].equals("thread_local") ? CvRandom.createThreadLocalInstance(seed).source()
                : new StandardRandom(seed);

            String value = null;
            for (int i = 0; i <= index; i++) value = draw(random, row[2]);
            assertEquals(row[4], value, String.join(" ", row));
        }
    }

    private static String draw(RandomSource random, String op) {
        switch (op) {
            case "long":
                return Long.toString(random.nextLong());
            case "int37":
                return Integer.toString(random.nextInt(37));
            case "float":
                return Float.toString(random.nextFloat());
            default:
                return Double.toString(random.nextDouble());
        }
    }

    @Test
    void largeFeatureSeedMatches() throws IOException {
        for (String[] row : rows("large_feature_seed.tsv")) {
            RandomSource random = new SourceBits(row[0].equals("xoroshiro") ? new XoroshiroRandom(0) : new StandardRandom(0));
            CarverSeeds.largeFeatureSeed(random, Long.parseLong(row[1]), Integer.parseInt(row[2]),
                Integer.parseInt(row[3]));

            int index = Integer.parseInt(row[4]);
            long value = 0;
            for (int i = 0; i <= index; i++) value = random.nextLong();
            assertEquals(Long.parseLong(row[5]), value, String.join(" ", row));
        }
    }

    @Test
    void providersMatch() throws IOException {
        for (String[] row : rows("providers.tsv")) {
            StandardRandom source = new StandardRandom(Long.parseLong(row[1]));
            Random random = new RandomSourceAdapter(source);
            String at = String.join(" ", row);

            String value = sample(row[0], random, source);
            assertEquals(row[2], value, at);
            assertEquals(Float.parseFloat(row[3]), source.nextFloat(), "draws of " + at);
        }
    }

    private static String sample(String provider, Random random, RandomSource source) {
        switch (provider) {
            case "uniform_float(0.1,0.9)":
                return Float.toString(floatProvider("{type: 'minecraft:uniform', min_inclusive: 0.1, max_exclusive: 0.9}")
                    .get(random));
            case "uniform_float(-1,-0.4)":
                return Float.toString(floatProvider("{type: 'minecraft:uniform', min_inclusive: -1, max_exclusive: -0.4}")
                    .get(random));
            case "trapezoid(0,6,2)":
                return Float.toString(floatProvider("{type: 'minecraft:trapezoid', min: 0, max: 6, plateau: 2}")
                    .get(random));
            case "constant(3)":
                return Float.toString(floatProvider("3.0").get(random));
            case "uniform_height(above_bottom 8,absolute 180)":
                return Integer.toString(heightProvider(
                    "{type: 'minecraft:uniform', min_inclusive: {above_bottom: 8}, max_inclusive: {absolute: 180}}")
                    .get(random, MIN_Y, HEIGHT));
            case "uniform_height(absolute 10,absolute 67)":
                return Integer.toString(heightProvider(
                    "{type: 'minecraft:uniform', min_inclusive: {absolute: 10}, max_inclusive: {absolute: 67}}")
                    .get(random, MIN_Y, HEIGHT));
            case "random_between(0.5,4)":
                return Float.toString(CvMath.randomBetween(new CvRandom(source), 0.5f, 4f));
            default:
                throw new AssertionError("Unknown provider " + provider);
        }
    }

    private static IFloatProvider floatProvider(String json) {
        return VanillaWorldgen.parse(new JsonParser().parse(json), IFloatProvider.class);
    }

    private static IHeightProvider heightProvider(String json) {
        return VanillaWorldgen.parse(new JsonParser().parse(json), IHeightProvider.class);
    }

    private static List<String[]> rows(String name) throws IOException {
        InputStream in = CarverProbeTest.class.getResourceAsStream("/vanilla_reference/carver/probes/" + name);
        Assertions.assertNotNull(in, "Missing probe " + name + " (run misc/vanilla-reference/run.sh carver)");

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine();
            for (String line = r.readLine(); line != null; line = r.readLine()) rows.add(line.split("\t"));
        }
        return rows;
    }
}
