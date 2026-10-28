package net.minecraft.world.level.levelgen;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Generates golden density-function values from the real 26.1 jar, for Databack's parity tests.
 * Lives in the vanilla package so it can reach NoiseChunk's protected members. See docs/worldgen/density-functions.md
 * (section 6) for what it writes.
 */
public final class VanillaReference {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();

        // Optional section names after the output directory; all sections run when none are given.
        Set<String> sections = new HashSet<>(Arrays.asList(args).subList(1, args.length));
        if (sections.isEmpty() || sections.contains("biome")) BiomeReference.write(out, lookup);
        if (sections.isEmpty() || sections.contains("surface")) SurfaceReference.write(out, lookup);
        if (sections.isEmpty() || sections.contains("aquifer")) AquiferReference.write(out, lookup);
        if (!sections.isEmpty() && !sections.contains("density")) return;

        long[] seeds = { 12345L, -987654321L };
        Object[][] dims = {
            { NoiseGeneratorSettings.OVERWORLD, "overworld" },
            { NoiseGeneratorSettings.AMPLIFIED, "amplified" },
            { NoiseGeneratorSettings.NETHER, "nether" },
            { NoiseGeneratorSettings.END, "end" },
        };

        for (long seed : seeds) {
            for (Object[] dim : dims) {
                @SuppressWarnings("unchecked")
                ResourceKey<NoiseGeneratorSettings> key = (ResourceKey<NoiseGeneratorSettings>) dim[0];
                String name = (String) dim[1];
                NoiseGeneratorSettings settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
                RandomState state = RandomState.create(lookup, key, seed);

                writePoints(out.resolve("points_" + name + "_" + seed + ".tsv"), settings, state, seed);
            }
        }

        // Interpolated chunks (the actual terrain path) for the overworld only - they are large.
        HolderLookup.Provider l = lookup;
        NoiseGeneratorSettings overworld = l.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        RandomState owState = RandomState.create(l, NoiseGeneratorSettings.OVERWORLD, 12345L);
        writeChunk(out.resolve("chunk_overworld_12345_0_0.bin.gz"), overworld, owState, 0, 0);
        writeChunk(out.resolve("chunk_overworld_12345_-7_13.bin.gz"), overworld, owState, -7, 13);

        NoiseGeneratorSettings nether = l.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.NETHER).value();
        RandomState netherState = RandomState.create(l, NoiseGeneratorSettings.NETHER, 12345L);
        writeChunk(out.resolve("chunk_nether_12345_2_-3.bin.gz"), nether, netherState, 2, -3);

        // More real terrain: other dimensions, the other seed, and far out where coordinate wrapping kicks in.
        Object[][] extraChunks = {
            { NoiseGeneratorSettings.AMPLIFIED, "amplified", 12345L, 3, -2 },
            { NoiseGeneratorSettings.END, "end", 12345L, 0, 0 },
            { NoiseGeneratorSettings.END, "end", 12345L, 200, -150 },
            { NoiseGeneratorSettings.OVERWORLD, "overworld", -987654321L, 5, 5 },
            { NoiseGeneratorSettings.OVERWORLD, "overworld", 12345L, 62500, -62500 },
            { NoiseGeneratorSettings.OVERWORLD, "overworld", 12345L, -1874999, 1874999 },
        };
        for (Object[] c : extraChunks) {
            @SuppressWarnings("unchecked")
            ResourceKey<NoiseGeneratorSettings> key = (ResourceKey<NoiseGeneratorSettings>) c[0];
            long seed = (Long) c[2];
            int cx = (Integer) c[3], cz = (Integer) c[4];
            NoiseGeneratorSettings settings = l.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
            writeChunk(out.resolve("chunk_" + c[1] + "_" + seed + "_" + cx + "_" + cz + ".bin.gz"), settings,
                RandomState.create(l, key, seed), cx, cz);
        }

        writeCases(out, lookup, overworld);
    }

    /// Synthetic trees from `cases/*.json` (shared with VanillaParityTest), each used as the final density of an
    /// otherwise empty noise router. Writes `case_<name>_<chunkX>_<chunkZ>.bin.gz` per listed chunk and
    /// `case_<name>_points.tsv`, and lists every case in `cases_index.tsv`.
    private static void writeCases(Path out, HolderLookup.Provider lookup, NoiseGeneratorSettings template)
        throws Exception {
        Path dir = out.resolve("cases");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        HolderGetter<NormalNoise.NoiseParameters> noises = lookup.lookupOrThrow(Registries.NOISE);

        try (PrintWriter index = new PrintWriter(Files.newBufferedWriter(out.resolve("cases_index.tsv")))) {
            index.println("name\tchunkX\tchunkZ");
            for (Path file : files) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                DensityFunction density = DensityFunction.DIRECT_CODEC.parse(ops, json.get("density"))
                    .getOrThrow(e -> new IllegalArgumentException(name + ": " + e));
                JsonObject n = json.getAsJsonObject("noise");
                NoiseSettings noiseSettings = NoiseSettings.create(
                    n.get("min_y").getAsInt(), n.get("height").getAsInt(),
                    n.get("size_horizontal").getAsInt(), n.get("size_vertical").getAsInt());
                long seed = json.get("seed").getAsLong();

                DensityFunction zero = DensityFunctions.zero();
                NoiseRouter router = new NoiseRouter(
                    zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, density, zero, zero, zero);
                NoiseGeneratorSettings settings = new NoiseGeneratorSettings(
                    noiseSettings, template.defaultBlock(), template.defaultFluid(), router, template.surfaceRule(),
                    List.of(), template.seaLevel(), false, false, false, false);

                for (JsonElement chunk : json.getAsJsonArray("chunks")) {
                    int cx = chunk.getAsJsonArray().get(0).getAsInt(), cz = chunk.getAsJsonArray().get(1).getAsInt();
                    // A fresh RandomState per chunk, like a fresh NoiseChunk: nothing carries over between chunks.
                    writeChunk(out.resolve("case_" + name + "_" + cx + "_" + cz + ".bin.gz"), settings,
                        RandomState.create(settings, noises, seed), cx, cz);
                    index.println(name + "\t" + cx + "\t" + cz);
                }
                writeCasePoints(out.resolve("case_" + name + "_points.tsv"),
                    RandomState.create(settings, noises, seed).router().finalDensity(), noiseSettings, seed);
                System.out.println("case " + name);
            }
        }
    }

    /// x y z value for random points, evaluated with a SinglePointContext (markers are transparent).
    private static void writeCasePoints(Path file, DensityFunction fn, NoiseSettings ns, long seed) throws IOException {
        Random rng = new Random(seed * 31 + 7);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("x\ty\tz\tvalue");
            for (int i = 0; i < 300; i++) {
                int range = i < 250 ? 4000 : 2_000_000;
                int x = rng.nextInt(range * 2) - range;
                int z = rng.nextInt(range * 2) - range;
                int y = ns.minY() + rng.nextInt(ns.height());
                double v = fn.compute(new DensityFunction.SinglePointContext(x, y, z));
                w.println(x + "\t" + y + "\t" + z + "\t" + Double.toString(v));
            }
        }
    }

    /// One row per point: x y z then every NoiseRouter field, evaluated with a SinglePointContext (no interpolation or
    /// caching, so markers are transparent).
    private static void writePoints(Path file, NoiseGeneratorSettings settings, RandomState state, long seed)
        throws Exception {
        NoiseRouter router = state.router();
        RecordComponent[] comps = NoiseRouter.class.getRecordComponents();
        List<DensityFunction> fns = new ArrayList<>();
        StringBuilder header = new StringBuilder("x\ty\tz");
        for (RecordComponent c : comps) {
            fns.add((DensityFunction) c.getAccessor().invoke(router));
            header.append('\t').append(c.getName());
        }

        NoiseSettings ns = settings.noiseSettings();
        Random rng = new Random(seed ^ 0x5DEECE66DL);

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println(header);
            for (int i = 0; i < 400; i++) {
                int range = i < 350 ? 4000 : 2_000_000;
                int x = rng.nextInt(range * 2) - range;
                int z = rng.nextInt(range * 2) - range;
                int y = ns.minY() + rng.nextInt(ns.height());
                DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(x, y, z);
                StringBuilder row = new StringBuilder();
                row.append(x).append('\t').append(y).append('\t').append(z);
                for (DensityFunction fn : fns) {
                    row.append('\t').append(Double.toString(fn.compute(ctx)));
                }
                w.println(row);
            }
        }
    }

    /// Mirrors NoiseBasedChunkGenerator.doFill and records getInterpolatedDensity() (final_density + beardifier,
    /// through cache_all_in_cell) for every block of the chunk.
    /// Format (gzip, big-endian): int minY, int height, then height*16*16 doubles at index ((y-minY)*16+z)*16+x.
    static void writeChunk(Path file, NoiseGeneratorSettings settings, RandomState state, int chunkX, int chunkZ)
        throws IOException {
        NoiseSettings ns = settings.noiseSettings();
        int cellWidth = ns.getCellWidth();
        int cellHeight = ns.getCellHeight();
        int cellCountXZ = 16 / cellWidth;
        int minY = ns.minY();
        int height = ns.height();
        int cellMinY = Math.floorDiv(minY, cellHeight);
        int cellCountY = Math.floorDiv(height, cellHeight);
        int minX = chunkX * 16, minZ = chunkZ * 16;

        Aquifer.FluidPicker picker = (x, y, z) -> new Aquifer.FluidStatus(settings.seaLevel(), Blocks.WATER.defaultBlockState());
        NoiseChunk chunk = new NoiseChunk(cellCountXZ, state, minX, minZ, ns, DensityFunctions.BeardifierMarker.INSTANCE,
            settings, picker, Blender.empty());

        double[] values = new double[height * 256];

        chunk.initializeForFirstCellX();
        for (int cellX = 0; cellX < cellCountXZ; cellX++) {
            chunk.advanceCellX(cellX);
            for (int cellZ = 0; cellZ < cellCountXZ; cellZ++) {
                for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                    chunk.selectCellYZ(cellY, cellZ);
                    for (int yInCell = cellHeight - 1; yInCell >= 0; yInCell--) {
                        int posY = (cellMinY + cellY) * cellHeight + yInCell;
                        chunk.updateForY(posY, (double) yInCell / cellHeight);
                        for (int xInCell = 0; xInCell < cellWidth; xInCell++) {
                            int posX = minX + cellX * cellWidth + xInCell;
                            chunk.updateForX(posX, (double) xInCell / cellWidth);
                            for (int zInCell = 0; zInCell < cellWidth; zInCell++) {
                                int posZ = minZ + cellZ * cellWidth + zInCell;
                                chunk.updateForZ(posZ, (double) zInCell / cellWidth);
                                int lx = posX - minX, lz = posZ - minZ;
                                values[((posY - minY) * 16 + lz) * 16 + lx] = chunk.getInterpolatedDensity();
                            }
                        }
                    }
                }
            }
            chunk.swapSlices();
        }
        chunk.stopInterpolation();

        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file.toFile())))) {
            dos.writeInt(minY);
            dos.writeInt(height);
            for (double v : values) dos.writeDouble(v);
        }
    }
}
