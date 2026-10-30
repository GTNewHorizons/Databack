package databack.common.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import databack.common.worldgen.feature.standin.FtBiome;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtFluidState;
import databack.common.worldgen.feature.standin.FtInfoNoise;
import databack.common.worldgen.feature.standin.FtIntProvider;
import databack.common.worldgen.feature.standin.FtWeightedList;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.SourceBits;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.rng.XoroshiroRandom;
import databack.common.worldgen.vanilla.standin.VnAxis;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;
import databack.common.worldgen.vanilla.standin.VnMath;
import databack.common.worldgen.vanilla.standin.VnPlane;

/// The shared and feature stand-ins against vanilla's helpers, probed black-box (`run.sh feature`,
/// `feature/probes/*.tsv`).
class FeatureProbeTest {

    @Test
    void mathMatches() throws IOException {
        for (String[] row : rows("mth.tsv")) {
            String[] a = row[1].split(",");
            String at = row[0] + "(" + row[1] + ")";
            switch (row[0]) {
                case "ceil":
                    assertEquals(Integer.parseInt(row[2]), VnMath.ceil(Float.parseFloat(a[0])), at);
                    break;
                case "clamp":
                    assertEquals(Double.parseDouble(row[2]), VnMath.clamp(d(a[0]), d(a[1]), d(a[2])), at);
                    break;
                case "clampedMap":
                    assertEquals(Double.parseDouble(row[2]),
                        VnMath.clampedMap(d(a[0]), d(a[1]), d(a[2]), d(a[3]), d(a[4])), at);
                    break;
                default:
                    assertEquals(Double.parseDouble(row[2]), VnMath.lerp(d(a[0]), d(a[1]), d(a[2])), at);
            }
        }
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    @Test
    void betweenClosedOrderMatches() throws IOException {
        Map<String, List<String>> expected = new HashMap<>();
        for (String[] row : rows("between_closed.tsv")) {
            expected.computeIfAbsent(row[0], k -> new ArrayList<>()).add(row[2] + " " + row[3] + " " + row[4]);
        }

        for (Map.Entry<String, List<String>> e : expected.entrySet()) {
            String[] c = e.getKey().replaceAll("[\\[\\]]", "").split(",");
            VnBlockPos a = new VnBlockPos(i(c[0]), i(c[1]), i(c[2])), b = new VnBlockPos(i(c[3]), i(c[4]), i(c[5]));
            List<String> actual = new ArrayList<>();
            for (VnBlockPos p : VnBlockPos.betweenClosed(a, b)) actual.add(p.toString());
            assertEquals(e.getValue(), actual, e.getKey());
        }
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }

    @Test
    void directionsMatch() throws IOException {
        VnDirection[] values = VnDirection.values();
        List<String[]> rows = rows("direction.tsv");
        assertEquals(rows.size(), values.length);
        for (String[] row : rows) {
            VnDirection d = values[i(row[0])];
            assertEquals(row[1], d.getSerializedName());
            assertEquals(row[2] + " " + row[3] + " " + row[4], d.getStepX() + " " + d.getStepY() + " " + d.getStepZ());
        }
    }

    @Test
    void axisDrawsMatch() throws IOException {
        Map<String, RandomSource> randoms = new HashMap<>();
        for (String[] row : rows("axis_random.tsv")) {
            RandomSource r = randoms.computeIfAbsent(row[0], s -> new StandardRandom(Long.parseLong(s)));
            assertEquals(row[2], VnAxis.getRandom(r).getName(), String.join(" ", row));
        }
    }

    @Test
    void sectionsMatch() throws IOException {
        for (String[] row : rows("section.tsv")) {
            int x = i(row[1]);
            int actual = row[0].equals("sectionRelative") ? VnMath.sectionRelative(x) : VnMath.blockToSectionCoord(x);
            assertEquals(i(row[2]), actual, String.join(" ", row));
        }
    }

    @Test
    void listDrawsMatch() throws IOException {
        Map<String, RandomSource> randoms = new HashMap<>();
        for (String[] row : rows("util_random.tsv")) {
            RandomSource r = randoms.computeIfAbsent(row[0] + " " + row[1], k -> new StandardRandom(Long.parseLong(row[0])));
            List<Integer> list = new ArrayList<>();
            for (int n = 0; n < i(row[1]); n++) list.add(n);
            assertEquals(i(row[3]), (int) VnMath.getRandom(list, r), String.join(" ", row));
        }
    }

    @Test
    void weightedDrawsMatch() throws IOException {
        Map<String, RandomSource> randoms = new HashMap<>();
        for (String[] row : rows("weighted.tsv")) {
            RandomSource r = randoms.computeIfAbsent(row[0] + " " + row[1], k -> new StandardRandom(Long.parseLong(row[1])));
            FtWeightedList.Builder builder = FtWeightedList.builder();
            String[] weights = row[0].replaceAll("[\\[\\]]", "").split(",");
            for (int n = 0; n < weights.length; n++) builder.add(n, i(weights[n]));
            assertEquals(i(row[3]), builder.build().getRandomOrThrow(r), String.join(" ", row));
        }
    }

    @Test
    void trapezoidTriangleMatches() throws IOException {
        Map<String, RandomSource> randoms = new HashMap<>();
        for (String[] row : rows("trapezoid_int.tsv")) {
            RandomSource r = randoms.computeIfAbsent(row[0] + " " + row[1], k -> new StandardRandom(Long.parseLong(row[1])));
            FtIntProvider provider = FtIntProvider.triangle(i(row[0]));
            assertEquals(i(row[3]), provider.sample(r), String.join(" ", row));
        }
    }

    @Test
    void directionOperationsMatch() throws IOException {
        for (String[] row : rows("direction_ops.tsv")) {
            VnDirection d = direction(row[0]);
            String clockwise;
            try {
                clockwise = d.getClockWise().getSerializedName();
            } catch (IllegalStateException e) {
                clockwise = "throws";
            }
            assertEquals(row[1], clockwise, row[0]);
            assertEquals(row[2], d.getOpposite().getSerializedName(), row[0]);
            assertEquals(row[3], d.getAxis().getName(), row[0]);
            assertEquals(row[4], d.getAxisDirection().toString(), row[0]);
        }
    }

    private static VnDirection direction(String name) {
        for (VnDirection d : VnDirection.values()) {
            if (d.getSerializedName().equals(name)) return d;
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void horizontalPlaneMatches() throws IOException {
        List<String> order = new ArrayList<>();
        for (VnDirection d : VnPlane.HORIZONTAL) order.add(d.getSerializedName());
        Map<String, RandomSource> randoms = new HashMap<>();
        int index = 0;
        for (String[] row : rows("plane.tsv")) {
            if (row[0].equals("order")) {
                assertEquals(row[3], order.get(index++));
                continue;
            }
            RandomSource r = randoms.computeIfAbsent(row[1], seed -> new StandardRandom(Long.parseLong(seed)));
            assertEquals(row[3], VnPlane.HORIZONTAL.getRandomDirection(r).getSerializedName(), String.join(" ", row));
        }
        assertEquals(4, index);
    }

    @Test
    void shufflesMatch() throws IOException {
        for (String[] row : rows("shuffle.tsv")) {
            ObjectArrayList<Integer> list = new ObjectArrayList<>();
            for (int n = 0; n < i(row[2]); n++) list.add(n);
            RandomSource random = new StandardRandom(Long.parseLong(row[1]));
            List<Integer> result;
            if (row[0].equals("shuffle")) {
                VnMath.shuffle(list, random);
                result = list;
            } else {
                result = VnMath.shuffledCopy(list, random);
            }
            assertEquals(row[3], result.toString(), String.join(" ", row));
        }
    }

    @Test
    void uniformIntsMatch() throws IOException {
        Map<String, RandomSource> randoms = new HashMap<>();
        for (String[] row : rows("uniform_int.tsv")) {
            RandomSource r = randoms.computeIfAbsent(row[0] + " " + row[1] + " " + row[2],
                k -> new StandardRandom(Long.parseLong(row[2])));
            assertEquals(i(row[4]), FtIntProvider.of(i(row[0]), i(row[1])).sample(r), String.join(" ", row));
        }
    }

    /// Sources only: flowing fluid states aren't built ([FeatureBuildContext#fluidState]).
    @Test
    void sourceFluidStatesMatch() throws IOException {
        for (String[] row : rows("fluid_state.tsv")) {
            if (!row[4].equals("true")) continue;
            FtFluidState state = row[0].equals("water") ? FtFluidState.WATER : FtFluidState.LAVA;
            FtBlockState block = state.createLegacyBlock();
            String at = String.join(" ", row);

            assertEquals(row[2], "Block{" + block.getBlock().id + "}[level=" + block.properties().get("level") + "]", at);
            assertEquals(row[3], state.getType().id, at);
        }
    }

    @Test
    void infoNoiseMatches() throws IOException {
        Map<String, FtInfoNoise> noises = new HashMap<>();
        for (String[] row : rows("info_noise.tsv")) {
            FtInfoNoise noise = row[0].equals("biome") ? FtBiome.BIOME_INFO_NOISE
                : noises.computeIfAbsent(row[0], s -> new FtInfoNoise(Long.parseLong(s.substring("seed:".length()))));
            double value = noise.getValue(d(row[2]), d(row[3]), Boolean.parseBoolean(row[1]));
            assertEquals(d(row[4]), value, String.join(" ", row));
        }
    }

    @Test
    void decorationSeedsMatch() throws IOException {
        for (String[] row : rows("decoration_seed.tsv")) {
            RandomSource random = new SourceBits(row[0].equals("xoroshiro") ? new XoroshiroRandom(0) : new StandardRandom(0));
            long decoration = FeatureSeeds.decorationSeed(random, Long.parseLong(row[1]), i(row[2]), i(row[3]));
            int draw = i(row[6]);
            String at = String.join(" ", row);
            if (draw < 0) {
                assertEquals(Long.parseLong(row[7]), decoration, at);
                continue;
            }

            FeatureSeeds.featureSeed(random, decoration, i(row[4]), i(row[5]));
            long value = 0;
            for (int d = 0; d <= draw; d++) value = random.nextLong();
            assertEquals(Long.parseLong(row[7]), value, at);
        }
    }

    private static List<String[]> rows(String file) throws IOException {
        InputStream in = FeatureProbeTest.class.getResourceAsStream("/vanilla_reference/feature/probes/" + file);
        Assertions.assertNotNull(in, "Missing probe " + file + " (run misc/vanilla-reference/run.sh feature)");
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Iterator<String> lines = r.lines().iterator();
            lines.next();
            while (lines.hasNext()) rows.add(lines.next().split("\t"));
        }
        return rows;
    }
}
