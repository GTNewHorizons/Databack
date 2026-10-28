package net.minecraft.world.level.levelgen;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Generates golden multi-noise biome source data from the real 26.1 jar, for Databack's biome parity tests. Everything
 * goes through public API, as a black box. Writes into `<out>/biome/`:
 *
 * - `preset_<name>.tsv`: every entry of a built-in parameter list preset, in list order, as quantized longs. Test
 *   oracle only: it must never be committed or shipped (see .gitignore).
 * - `climate_<dim>_<seed>.tsv`: quart position, the sampled climate target point (quantized), and the biome picked.
 * - `zoom_seeds.tsv`: world seed and the biome zoom seed derived from it.
 * - `zoom_<seed>.tsv`: block position and the quart position(s) a block-level biome lookup reads.
 * - `ties_lists.tsv` / `ties_queries.tsv`: synthetic coarse lists where equal distances are common, and the index each
 *   lookup returns.
 */
final class BiomeReference {

    private static final String[] PARAMS = { "temperature", "humidity", "continentalness", "erosion", "depth",
        "weirdness" };

    private BiomeReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path out = root.resolve("biome");
        Files.createDirectories(out);

        HolderGetter<Biome> biomes = lookup.lookupOrThrow(Registries.BIOME);

        Map<MultiNoiseBiomeSourceParameterList.Preset, Climate.ParameterList<ResourceKey<Biome>>> presets =
            MultiNoiseBiomeSourceParameterList.knownPresets();
        writePreset(out, "overworld", MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, presets);
        writePreset(out, "nether", MultiNoiseBiomeSourceParameterList.Preset.NETHER, presets);

        long[] seeds = { 12345L, -987654321L };
        Object[][] dims = {
            { NoiseGeneratorSettings.OVERWORLD, "overworld", MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD },
            { NoiseGeneratorSettings.LARGE_BIOMES, "large_biomes", MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD },
            { NoiseGeneratorSettings.AMPLIFIED, "amplified", MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD },
            { NoiseGeneratorSettings.NETHER, "nether", MultiNoiseBiomeSourceParameterList.Preset.NETHER },
        };
        for (long seed : seeds) {
            for (Object[] dim : dims) {
                @SuppressWarnings("unchecked")
                ResourceKey<NoiseGeneratorSettings> key = (ResourceKey<NoiseGeneratorSettings>) dim[0];
                MultiNoiseBiomeSourceParameterList.Preset preset = (MultiNoiseBiomeSourceParameterList.Preset) dim[2];
                NoiseGeneratorSettings settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
                RandomState state = RandomState.create(lookup, key, seed);
                MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromList(
                    new MultiNoiseBiomeSourceParameterList(preset, biomes).parameters());
                writeClimate(out.resolve("climate_" + dim[1] + "_" + seed + ".tsv"), settings.noiseSettings(), state,
                    source, seed);
                System.out.println("biome climate " + dim[1] + " " + seed);
            }
        }

        writeZoomSeeds(out.resolve("zoom_seeds.tsv"));
        Holder<Biome> anyBiome = biomes.getOrThrow(Biomes.PLAINS);
        for (long seed : seeds) {
            writeZoom(out.resolve("zoom_" + seed + ".tsv"), BiomeManager.obfuscateSeed(seed), anyBiome, seed);
        }
        System.out.println("biome zoom");

        writeTies(out.resolve("ties_lists.tsv"), out.resolve("ties_queries.tsv"));
        System.out.println("biome ties");
    }

    /// One row per entry: index, biome, then min/max of each parameter and the offset, all quantized.
    private static void writePreset(
        Path out, String name, MultiNoiseBiomeSourceParameterList.Preset preset,
        Map<MultiNoiseBiomeSourceParameterList.Preset, Climate.ParameterList<ResourceKey<Biome>>> presets
    ) throws IOException {
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> entries = presets.get(preset).values();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("preset_" + name + ".tsv")))) {
            StringBuilder header = new StringBuilder("index\tbiome");
            for (String p : PARAMS) header.append('\t').append(p).append("_min\t").append(p).append("_max");
            w.println(header.append("\toffset"));
            for (int i = 0; i < entries.size(); i++) {
                Climate.ParameterPoint pt = entries.get(i).getFirst();
                StringBuilder row = new StringBuilder().append(i).append('\t')
                    .append(entries.get(i).getSecond().identifier());
                for (Climate.Parameter p : parameters(pt)) row.append('\t').append(p.min()).append('\t').append(p.max());
                w.println(row.append('\t').append(pt.offset()));
            }
        }
    }

    /// qx qy qz, the climate target point the sampler produces there, and the biome the source picks there. Points:
    /// random columns near and far from the origin (y anywhere in the dimension, plus some outside it), a dense
    /// horizontal grid, and a full vertical column.
    private static void writeClimate(
        Path file, NoiseSettings ns, RandomState state, MultiNoiseBiomeSource source, long seed
    ) throws IOException {
        Climate.Sampler sampler = state.sampler();
        int minQY = Math.floorDiv(ns.minY(), 4);
        int heightQ = ns.height() / 4;
        Random rng = new Random(seed * 17 + 3);

        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            int range = i < 1700 ? 2000 : 500_000;
            int qy = i % 10 == 0 ? minQY - 32 + rng.nextInt(heightQ + 64) : minQY + rng.nextInt(heightQ);
            points.add(new int[] { rng.nextInt(range * 2) - range, qy, rng.nextInt(range * 2) - range });
        }
        int gridQY = Math.floorDiv(64, 4);
        for (int qx = 0; qx < 32; qx++) {
            for (int qz = 0; qz < 32; qz++) points.add(new int[] { qx - 16, gridQY, qz - 16 });
        }
        for (int qy = minQY - 16; qy < minQY + heightQ + 16; qy++) points.add(new int[] { 37, qy, -91 });

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            StringBuilder header = new StringBuilder("qx\tqy\tqz");
            for (String p : PARAMS) header.append('\t').append(p);
            w.println(header.append("\tbiome"));
            for (int[] q : points) {
                Climate.TargetPoint t = sampler.sample(q[0], q[1], q[2]);
                Holder<Biome> biome = source.getNoiseBiome(q[0], q[1], q[2], sampler);
                w.println(q[0] + "\t" + q[1] + "\t" + q[2] + "\t" + t.temperature() + "\t" + t.humidity() + "\t"
                    + t.continentalness() + "\t" + t.erosion() + "\t" + t.depth() + "\t" + t.weirdness() + "\t"
                    + biome.getRegisteredName());
            }
        }
    }

    private static void writeZoomSeeds(Path file) throws IOException {
        List<Long> seeds = new ArrayList<>(List.of(0L, 1L, -1L, 12345L, -987654321L, Long.MAX_VALUE, Long.MIN_VALUE));
        Random rng = new Random(99);
        for (int i = 0; i < 50; i++) seeds.add(rng.nextLong());

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("seed\tzoom_seed");
            for (long s : seeds) w.println(s + "\t" + BiomeManager.obfuscateSeed(s));
        }
    }

    /// x y z, then every quart position the lookup asked the source for, in call order (`qx,qy,qz` separated by `;`).
    /// Points: a dense block region at the origin, random blocks near and far, and some at extreme y.
    private static void writeZoom(Path file, long zoomSeed, Holder<Biome> anyBiome, long seed) throws IOException {
        List<int[]> calls = new ArrayList<>();
        BiomeManager manager = new BiomeManager((qx, qy, qz) -> {
            calls.add(new int[] { qx, qy, qz });
            return anyBiome;
        }, zoomSeed);

        List<int[]> points = new ArrayList<>();
        for (int x = -8; x < 24; x++) {
            for (int y = 60; y < 68; y++) {
                for (int z = -8; z < 24; z++) points.add(new int[] { x, y, z });
            }
        }
        Random rng = new Random(seed * 13 + 5);
        for (int i = 0; i < 3000; i++) {
            int range = i < 2500 ? 10_000 : 30_000_000;
            int y = i % 5 == 0 ? rng.nextInt(1 << 20) - (1 << 19) : rng.nextInt(448) - 64;
            points.add(new int[] { rng.nextInt(range * 2) - range, y, rng.nextInt(range * 2) - range });
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("x\ty\tz\tquarts");
            for (int[] p : points) {
                calls.clear();
                manager.getBiome(new BlockPos(p[0], p[1], p[2]));
                StringBuilder row = new StringBuilder().append(p[0]).append('\t').append(p[1]).append('\t').append(p[2])
                    .append('\t');
                for (int i = 0; i < calls.size(); i++) {
                    if (i > 0) row.append(';');
                    int[] c = calls.get(i);
                    row.append(c[0]).append(',').append(c[1]).append(',').append(c[2]);
                }
                w.println(row);
            }
        }
    }

    /// Synthetic parameter lists with values on a coarse grid, so many targets are equally close to several entries.
    /// Databack does not reproduce vanilla's tie-breaking (it depends on earlier queries); this documents it. For each
    /// query: the index found through the list that answered every earlier query of the same list (in
    /// order), the index found through a freshly built list, and the brute-force index.
    private static void writeTies(Path listsFile, Path queriesFile) throws IOException {
        float[] grid = { -1.0f, -0.5f, 0.0f, 0.5f, 1.0f };
        float[] offsets = { 0.0f, 0.0f, 0.0f, 0.25f, 0.5f };
        Random rng = new Random(424242);

        try (PrintWriter lw = new PrintWriter(Files.newBufferedWriter(listsFile));
             PrintWriter qw = new PrintWriter(Files.newBufferedWriter(queriesFile))) {
            StringBuilder lh = new StringBuilder("list\tindex");
            for (String p : PARAMS) lh.append('\t').append(p).append("_min\t").append(p).append("_max");
            lw.println(lh.append("\toffset"));
            StringBuilder qh = new StringBuilder("list\tquery");
            for (String p : PARAMS) qh.append('\t').append(p);
            qw.println(qh.append("\tsequential\tfresh\tbrute_force"));

            for (int list = 0; list < 300; list++) {
                int size = 1 + rng.nextInt(list < 200 ? 12 : 40);
                List<Pair<Climate.ParameterPoint, Integer>> entries = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    Climate.Parameter[] ps = new Climate.Parameter[6];
                    for (int k = 0; k < 6; k++) {
                        float a = grid[rng.nextInt(grid.length)], b = grid[rng.nextInt(grid.length)];
                        ps[k] = rng.nextInt(3) == 0 ? Climate.Parameter.point(a)
                            : Climate.Parameter.span(Math.min(a, b), Math.max(a, b));
                    }
                    float offset = offsets[rng.nextInt(offsets.length)];
                    Climate.ParameterPoint pt = Climate.parameters(ps[0], ps[1], ps[2], ps[3], ps[4], ps[5], offset);
                    entries.add(Pair.of(pt, i));

                    StringBuilder row = new StringBuilder().append(list).append('\t').append(i);
                    for (Climate.Parameter p : parameters(pt)) row.append('\t').append(p.min()).append('\t').append(p.max());
                    lw.println(row.append('\t').append(pt.offset()));
                }

                Climate.ParameterList<Integer> shared = new Climate.ParameterList<>(entries);
                for (int q = 0; q < 20; q++) {
                    long[] t = new long[6];
                    for (int k = 0; k < 6; k++) {
                        // Mostly grid values (ties), sometimes halfway between them.
                        float v = grid[rng.nextInt(grid.length)];
                        if (rng.nextInt(4) == 0) v += 0.25f;
                        t[k] = Climate.quantizeCoord(v);
                    }
                    Climate.TargetPoint target = new Climate.TargetPoint(t[0], t[1], t[2], t[3], t[4], t[5]);
                    int sequential = shared.findValue(target);
                    int fresh = new Climate.ParameterList<>(entries).findValue(target);
                    int brute = shared.findValueBruteForce(target);

                    StringBuilder row = new StringBuilder().append(list).append('\t').append(q);
                    for (long v : t) row.append('\t').append(v);
                    qw.println(row.append('\t').append(sequential).append('\t').append(fresh).append('\t').append(brute));
                }
            }
        }
    }

    private static Climate.Parameter[] parameters(Climate.ParameterPoint pt) {
        return new Climate.Parameter[] { pt.temperature(), pt.humidity(), pt.continentalness(), pt.erosion(),
            pt.depth(), pt.weirdness() };
    }
}
