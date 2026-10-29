package net.minecraft.world.level.levelgen;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Generates golden data for Databack's ore vein parity tests by running vanilla's noise step on lone overworld chunks
 * with ore veins on and aquifers off, as a black box. Writes into `<out>/ore_vein/`:
 *
 * - `chunk_<seed>_<cx>_<cz>_blocks.bin.gz`: every block after the noise step (the surface harness's chunk format).
 * - `chunk_<seed>_<cx>_<cz>_fields.bin.gz`: for the first chunks of each seed only, the router's `vein_toggle`,
 *   `vein_ridged` and `vein_gap` as the noise step sees them (interpolated) at every block of y [-64, 64).
 *   Format (gzip, big-endian): int minY, int layers, then layers*16*16 triples of doubles at index
 *   `((y - minY) * 16 + z) * 16 + x`.
 * - `case_<name>_<cx>_<cz>_blocks.bin.gz`: chunks of `cases/<name>.json` (`seed`, `router` as in the aquifer
 *   harness's cases, `chunks`, optional `fields: true` for a `case_..._fields.bin.gz` beside each, optional
 *   `aquifers: true`), on otherwise overworld settings with ore veins on and aquifers off unless asked.
 * - `index.tsv`: every real chunk written, and whether it has a fields file.
 */
final class OreVeinReference {

    private static final int FIELDS_MIN_Y = -64, FIELDS_LAYERS = 128;
    private static final int CHUNKS_WITH_FIELDS = 2;

    private OreVeinReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path out = root.resolve("ore_vein");
        Files.createDirectories(out);

        SurfaceReference.Context ctx = new SurfaceReference.Context(lookup);
        NoiseGeneratorSettings overworld = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        NoiseGeneratorSettings settings = SurfaceReference.withFeatures(overworld, overworld.surfaceRule(), false, true);

        long[] seeds = { 12345L, -987654321L };
        int[][] chunks = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 }, { 3, 3 }, { -40, 77 },
            { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };

        try (PrintWriter index = new PrintWriter(Files.newBufferedWriter(out.resolve("index.tsv")))) {
            index.println("seed\tchunkX\tchunkZ\tfields");
            for (long seed : seeds) {
                for (int i = 0; i < chunks.length; i++) {
                    int[] c = chunks[i];
                    String base = "chunk_" + seed + "_" + c[0] + "_" + c[1];
                    SurfaceReference.generate(ctx, settings, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD,
                        seed, c[0], c[1], out.resolve(base + "_blocks.bin.gz"), null);

                    boolean fields = i < CHUNKS_WITH_FIELDS;
                    if (fields) {
                        writeFields(out.resolve(base + "_fields.bin.gz"), settings,
                            RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed), c[0], c[1]);
                    }
                    index.println(seed + "\t" + c[0] + "\t" + c[1] + "\t" + fields);
                }
                System.out.println("ore_vein " + seed);
            }
        }

        writeCases(out, ctx, lookup, settings);
    }

    private static void writeCases(Path out, SurfaceReference.Context ctx, HolderLookup.Provider lookup,
        NoiseGeneratorSettings template) throws Exception {
        Path dir = out.resolve("cases");
        if (!Files.isDirectory(dir)) return;

        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            boolean aquifers = json.has("aquifers") && json.get("aquifers").getAsBoolean();
            NoiseGeneratorSettings settings = AquiferReference.caseSettings(name, json, lookup, template, aquifers,
                true);
            long seed = json.get("seed").getAsLong();
            boolean fields = json.has("fields") && json.get("fields").getAsBoolean();

            for (JsonElement chunk : json.getAsJsonArray("chunks")) {
                int cx = chunk.getAsJsonArray().get(0).getAsInt(), cz = chunk.getAsJsonArray().get(1).getAsInt();
                String base = "case_" + name + "_" + cx + "_" + cz;
                SurfaceReference.generate(ctx, settings, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, seed,
                    cx, cz, out.resolve(base + "_blocks.bin.gz"), null);
                if (fields) {
                    writeFields(out.resolve(base + "_fields.bin.gz"), settings,
                        RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed), cx, cz);
                }
            }
            System.out.println("ore_vein case " + name);
        }
    }

    private static void writeFields(Path file, NoiseGeneratorSettings settings, RandomState state, int chunkX,
        int chunkZ) throws IOException {
        double[] values = new double[FIELDS_LAYERS * 256 * 3];
        NoiseRouter router = state.router();

        VanillaReference.walkChunk(settings, state, chunkX, chunkZ, chunk -> {
            // Markers nested inside a field (vein_ridged's interpolated noises) need wrapping too.
            DensityFunction[] fns = { router.veinToggle().mapAll(chunk::wrap), router.veinRidged().mapAll(chunk::wrap),
                router.veinGap().mapAll(chunk::wrap) };
            return (x, y, z) -> {
                int layer = y - FIELDS_MIN_Y;
                if (layer < 0 || layer >= FIELDS_LAYERS) return;
                int index = ((layer * 16 + z) * 16 + x) * 3;
                for (int f = 0; f < 3; f++) values[index + f] = fns[f].compute(chunk);
            };
        });

        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file.toFile())))) {
            dos.writeInt(FIELDS_MIN_Y);
            dos.writeInt(FIELDS_LAYERS);
            for (double v : values) dos.writeDouble(v);
        }
    }
}
