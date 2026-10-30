package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import databack.common.worldgen.biome.preset.VanillaPresetExtractor;
import databack.common.worldgen.vanilla.VanillaJar;

/// [ClimateTree] must answer exactly like [BruteForceBiomeSearch].
class ClimateTreeTest {

    private static final long[] GRID = { -10000, -5000, 0, 5000, 10000 };

    @Test
    void presetsMatchBruteForce() throws IOException {
        assertMatchesBruteForce(VanillaPresetExtractor.overworld(VanillaJar.classFile(VanillaPresetExtractor.OVERWORLD_CLASS)),
            50_000, 25_000, 1);
        assertMatchesBruteForce(VanillaPresetExtractor.nether(VanillaJar.classFile(VanillaPresetExtractor.NETHER_CLASS)),
            50_000, 25_000, 2);
    }

    /// Coarse lists where many targets are equally close to several entries.
    @Test
    void coarseListsWithTiesMatchBruteForce() {
        Random random = new Random(5);
        for (int list = 0; list < 300; list++) {
            List<ClimateEntry> entries = new ArrayList<>();
            int size = 1 + random.nextInt(list < 200 ? 40 : 600);
            for (int i = 0; i < size; i++) entries.add(coarseEntry(random, "e" + i));

            ClimateTree tree = new ClimateTree(entries);
            BruteForceBiomeSearch brute = new BruteForceBiomeSearch(entries);
            for (int q = 0; q < 200; q++) {
                long[] target = coarseTarget(random);
                assertEquals(brute.closest(target), tree.closest(target), "list " + list + " query " + q);
            }
        }
    }

    /// A query reused across targets starts from its previous answer; the answers must not depend on it.
    @Test
    void reusedQueriesMatchBruteForce() {
        Random random = new Random(9);
        for (int list = 0; list < 200; list++) {
            List<ClimateEntry> entries = new ArrayList<>();
            int size = 1 + random.nextInt(list < 100 ? 40 : 600);
            for (int i = 0; i < size; i++) entries.add(coarseEntry(random, "e" + i));

            ClimateTree tree = new ClimateTree(entries);
            BruteForceBiomeSearch brute = new BruteForceBiomeSearch(entries);
            ClimateTree.Query query = new ClimateTree.Query();
            for (int q = 0; q < 200; q++) {
                long[] target = coarseTarget(random);
                assertEquals(brute.closest(target), tree.closest(target, query), "list " + list + " query " + q);
            }
        }
    }

    /// Vanilla's brute-force answers on the harness's synthetic tie lists (`ties_*.tsv`, column `brute_force`).
    @Test
    void tieListsMatchVanillaBruteForce() throws IOException {
        Map<String, List<ClimateEntry>> lists = new HashMap<>();
        for (String[] row : MultiNoiseParityTest.read("ties_lists.tsv")) {
            long[] min = new long[ClimateEntry.PARAMETER_COUNT], max = new long[ClimateEntry.PARAMETER_COUNT];
            for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
                min[p] = Long.parseLong(row[2 + 2 * p]);
                max[p] = Long.parseLong(row[3 + 2 * p]);
            }
            lists.computeIfAbsent(row[0], k -> new ArrayList<>())
                .add(new ClimateEntry("e" + row[1], min, max, Long.parseLong(row[14])));
        }

        Map<String, ClimateTree> trees = new HashMap<>();
        for (String[] row : MultiNoiseParityTest.read("ties_queries.tsv")) {
            long[] target = new long[ClimateEntry.PARAMETER_COUNT];
            for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) target[p] = Long.parseLong(row[2 + p]);

            ClimateTree tree = trees.computeIfAbsent(row[0], k -> new ClimateTree(lists.get(k)));
            assertEquals(Integer.parseInt(row[10]), tree.closest(target), "list " + row[0] + " query " + row[1]);
        }
    }

    @Test
    void edgeCases() {
        assertEquals(-1, new ClimateTree(Collections.emptyList()).closest(new long[6]));

        List<ClimateEntry> one = Collections.singletonList(coarseEntry(new Random(1), "only"));
        assertEquals(0, new ClimateTree(one).closest(new long[] { 1, 2, 3, 4, 5, 6 }));

        // Values beyond the tree's safe range fall back to brute force.
        Random random = new Random(9);
        List<ClimateEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) entries.add(coarseEntry(random, "e" + i));
        entries.add(new ClimateEntry("huge", new long[] { 0, 0, 0, 0, 0, Long.MIN_VALUE / 2 },
            new long[] { 0, 0, 0, 0, 0, Long.MAX_VALUE / 2 }, 0));
        ClimateTree tree = new ClimateTree(entries);
        BruteForceBiomeSearch brute = new BruteForceBiomeSearch(entries);
        long[] far = { Long.MAX_VALUE, 0, 0, 0, 0, 0 };
        assertEquals(brute.closest(far), tree.closest(far));
        long[] near = coarseTarget(random);
        assertEquals(brute.closest(near), tree.closest(near));
    }

    private static void assertMatchesBruteForce(List<ClimateEntry> entries, int queries, long spread, long seed) {
        ClimateTree tree = new ClimateTree(entries);
        BruteForceBiomeSearch brute = new BruteForceBiomeSearch(entries);
        Random random = new Random(seed);

        for (int q = 0; q < queries; q++) {
            long[] target = new long[ClimateEntry.PARAMETER_COUNT];
            for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
                // Mostly realistic values, sometimes exactly on an entry's boundary.
                ClimateEntry e = entries.get(random.nextInt(entries.size()));
                target[p] = q % 4 == 0 ? (random.nextBoolean() ? e.min(p) : e.max(p))
                    : (long) (random.nextGaussian() * spread / 2);
            }
            assertEquals(brute.closest(target), tree.closest(target), "query " + q);
        }
    }

    private static ClimateEntry coarseEntry(Random random, String biome) {
        long[] min = new long[ClimateEntry.PARAMETER_COUNT], max = new long[ClimateEntry.PARAMETER_COUNT];
        for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
            long a = GRID[random.nextInt(GRID.length)], b = GRID[random.nextInt(GRID.length)];
            min[p] = Math.min(a, b);
            max[p] = random.nextInt(3) == 0 ? min[p] : Math.max(a, b);
        }
        return new ClimateEntry(biome, min, max, random.nextInt(3) == 0 ? 2500 : 0);
    }

    private static long[] coarseTarget(Random random) {
        long[] target = new long[ClimateEntry.PARAMETER_COUNT];
        for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
            target[p] = GRID[random.nextInt(GRID.length)] + (random.nextInt(4) == 0 ? 2500 : 0);
        }
        return target;
    }
}
