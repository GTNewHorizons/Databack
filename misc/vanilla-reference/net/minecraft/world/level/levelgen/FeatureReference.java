package net.minecraft.world.level.levelgen;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.TrapezoidInt;

/**
 * Black-box probes of the vanilla helpers the feature and placement classes call, for Databack's stand-ins. Writes
 * into `<out>/feature/probes/`:
 *
 * - `mth.tsv`: `fn args result` for `ceil`, `clamp`, `clampedMap`, `lerp`.
 * - `between_closed.tsv`: `box index x y z`, the positions `BlockPos.betweenClosed` yields, in order.
 * - `direction.tsv`: `index name stepX stepY stepZ` for `Direction.values()`.
 * - `axis_random.tsv`: `seed draw axis` for `Direction.Axis.getRandom` on a legacy random.
 * - `section.tsv`: `fn x result` for `SectionPos.blockToSectionCoord` and `sectionRelative`.
 * - `util_random.tsv`: `seed size draw index` for `Util.getRandom` on a list of `size` elements.
 * - `weighted.tsv`: `weights seed draw index` for `WeightedList.getRandomOrThrow`.
 * - `trapezoid_int.tsv`: `size seed draw value` for `TrapezoidInt.triangle(size)`.
 * - `decoration_seed.tsv`: `base worldSeed minX minZ index step draw value`: `setDecorationSeed`'s result
 *   (`draw` -1), then the longs after `setFeatureSeed(decorationSeed, index, step)`.
 */
final class FeatureReference {

    private static final long[] SEEDS = { 0L, 1L, 12345L, -987654321L, 0x5DEECE66DL, Long.MIN_VALUE + 7 };

    private FeatureReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path dir = root.resolve("feature/probes");
        Files.createDirectories(dir);

        writeMth(dir);
        writeBetweenClosed(dir);
        writeDirections(dir);
        writeSections(dir);
        writeDraws(dir);
        writeDecorationSeeds(dir);
        System.out.println("feature probes");
    }

    private static void writeMth(Path dir) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("mth.tsv")))) {
            w.println("fn\targs\tresult");
            java.util.Random rng = new java.util.Random(7);
            for (int i = 0; i < 2000; i++) {
                float f = (float) ((rng.nextDouble() - 0.5) * (i < 1000 ? 40 : 4e6));
                double a = (rng.nextDouble() - 0.5) * 40, b = (rng.nextDouble() - 0.5) * 40;
                double c = (rng.nextDouble() - 0.5) * 40, d = (rng.nextDouble() - 0.5) * 40;
                double t = (rng.nextDouble() - 0.25) * 2;
                double lo = Math.min(b, c), hi = Math.max(b, c);
                w.println("ceil\t" + f + "\t" + Mth.ceil(f));
                w.println("clamp\t" + a + "," + lo + "," + hi + "\t" + Mth.clamp(a, lo, hi));
                w.println("clampedMap\t" + a + "," + b + "," + c + "," + d + "," + t + "\t"
                    + Mth.clampedMap(a, b, c, d, t));
                w.println("lerp\t" + t + "," + a + "," + b + "\t" + Mth.lerp(t, a, b));
            }
            for (float f : new float[] { 0f, -0f, 1f, -1f, 0.5f, -0.5f, 2.0000002f, -2.0000002f }) {
                w.println("ceil\t" + f + "\t" + Mth.ceil(f));
            }
        }
    }

    private static void writeBetweenClosed(Path dir) throws Exception {
        int[][] boxes = { { 0, 0, 0, 1, 1, 1 }, { -2, 5, 3, 1, 6, 4 }, { 4, 0, -1, 2, -1, 1 }, { 0, 0, 0, 0, 0, 3 } };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("between_closed.tsv")))) {
            w.println("box\tindex\tx\ty\tz");
            for (int[] b : boxes) {
                String box = Arrays.toString(b).replace(" ", "");
                int i = 0;
                for (BlockPos p : BlockPos.betweenClosed(new BlockPos(b[0], b[1], b[2]), new BlockPos(b[3], b[4], b[5]))) {
                    w.println(box + "\t" + i++ + "\t" + p.getX() + "\t" + p.getY() + "\t" + p.getZ());
                }
            }
        }
    }

    private static void writeDirections(Path dir) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("direction.tsv")))) {
            w.println("index\tname\tstepX\tstepY\tstepZ");
            Direction[] values = Direction.values();
            for (int i = 0; i < values.length; i++) {
                Direction d = values[i];
                w.println(i + "\t" + d.getSerializedName() + "\t" + d.getStepX() + "\t" + d.getStepY() + "\t"
                    + d.getStepZ());
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("axis_random.tsv")))) {
            w.println("seed\tdraw\taxis");
            for (long seed : SEEDS) {
                RandomSource r = RandomSource.create(seed);
                for (int i = 0; i < 16; i++) w.println(seed + "\t" + i + "\t" + Direction.Axis.getRandom(r).getName());
            }
        }
    }

    private static void writeSections(Path dir) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("section.tsv")))) {
            w.println("fn\tx\tresult");
            for (int x = -40; x <= 40; x++) {
                w.println("blockToSectionCoord\t" + x + "\t" + SectionPos.blockToSectionCoord(x));
                w.println("sectionRelative\t" + x + "\t" + SectionPos.sectionRelative(x));
            }
        }
    }

    private static void writeDraws(Path dir) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("util_random.tsv")))) {
            w.println("seed\tsize\tdraw\tindex");
            for (long seed : SEEDS) {
                for (int size : new int[] { 1, 2, 5, 17 }) {
                    List<Integer> list = new java.util.ArrayList<>();
                    for (int i = 0; i < size; i++) list.add(i);
                    RandomSource r = RandomSource.create(seed);
                    for (int d = 0; d < 8; d++) w.println(seed + "\t" + size + "\t" + d + "\t" + Util.getRandom(list, r));
                }
            }
        }

        int[][] weightSets = { { 1 }, { 1, 3, 6 }, { 5, 0, 5 }, { 100, 1, 1, 1 } };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("weighted.tsv")))) {
            w.println("weights\tseed\tdraw\tindex");
            for (int[] weights : weightSets) {
                WeightedList.Builder<Integer> builder = WeightedList.builder();
                for (int i = 0; i < weights.length; i++) builder.add(i, weights[i]);
                WeightedList<Integer> list = builder.build();
                String key = Arrays.toString(weights).replace(" ", "");
                for (long seed : SEEDS) {
                    RandomSource r = RandomSource.create(seed);
                    for (int d = 0; d < 8; d++) w.println(key + "\t" + seed + "\t" + d + "\t" + list.getRandomOrThrow(r));
                }
            }
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("trapezoid_int.tsv")))) {
            w.println("size\tseed\tdraw\tvalue");
            for (int size : new int[] { 0, 1, 3, 8 }) {
                IntProvider provider = TrapezoidInt.triangle(size);
                for (long seed : SEEDS) {
                    RandomSource r = RandomSource.create(seed);
                    for (int d = 0; d < 8; d++) w.println(size + "\t" + seed + "\t" + d + "\t" + provider.sample(r));
                }
            }
        }
    }

    private static void writeDecorationSeeds(Path dir) throws Exception {
        int[][] origins = { { 0, 0 }, { 16, 0 }, { -112, 208 }, { 4800, -19200 } };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("decoration_seed.tsv")))) {
            w.println("base\tworldSeed\tminX\tminZ\tindex\tstep\tdraw\tvalue");
            for (String base : new String[] { "create", "xoroshiro" }) {
                for (long seed : SEEDS) {
                    for (int[] o : origins) {
                        WorldgenRandom r = new WorldgenRandom(base.equals("create") ? RandomSource.create(0)
                            : new XoroshiroRandomSource(0));
                        long decoration = r.setDecorationSeed(seed, o[0], o[1]);
                        String prefix = base + "\t" + seed + "\t" + o[0] + "\t" + o[1];
                        w.println(prefix + "\t0\t0\t-1\t" + decoration);
                        for (int[] f : new int[][] { { 0, 0 }, { 3, 6 }, { 17, 9 } }) {
                            r.setFeatureSeed(decoration, f[0], f[1]);
                            for (int d = 0; d < 4; d++) {
                                w.println(prefix + "\t" + f[0] + "\t" + f[1] + "\t" + d + "\t" + r.nextLong());
                            }
                        }
                    }
                }
            }
        }
    }
}
