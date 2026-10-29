package net.minecraft.world.level.levelgen;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Generates golden data for Databack's aquifer parity tests by running vanilla's noise step (terrain and fluids) on
 * lone overworld chunks with aquifers on and ore veins off, as a black box. Writes into `<out>/aquifer/`:
 *
 * - `chunk_<seed>_<cx>_<cz>_blocks.bin.gz`: every block after the noise step (the surface harness's chunk format).
 * - `chunk_<seed>_<cx>_<cz>_density.bin.gz`: the chunk's terrain density (the density harness's chunk format), so a
 *   block's fate can be compared with whether the terrain left it open.
 * - `case_<name>_<cx>_<cz>.bin.gz`: chunks of `cases/<name>.json` (`seed`, `router`: noise router fields as in
 *   noise settings JSON, missing ones 0; `chunks`), on otherwise overworld settings; blocks after the noise step.
 * - `index.tsv`: every chunk written.
 */
final class AquiferReference {

    private AquiferReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path out = root.resolve("aquifer");
        Files.createDirectories(out);

        SurfaceReference.Context ctx = new SurfaceReference.Context(lookup);
        NoiseGeneratorSettings overworld = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        NoiseGeneratorSettings settings = SurfaceReference.withFeatures(overworld, overworld.surfaceRule(), true, false);

        long[] seeds = { 12345L, -987654321L };
        int[][] chunks = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 }, { 3, 3 }, { -40, 77 },
            { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };

        try (PrintWriter index = new PrintWriter(Files.newBufferedWriter(out.resolve("index.tsv")))) {
            index.println("seed\tchunkX\tchunkZ");
            for (long seed : seeds) {
                for (int[] c : chunks) {
                    String base = "chunk_" + seed + "_" + c[0] + "_" + c[1];
                    SurfaceReference.generate(ctx, settings, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD,
                        seed, c[0], c[1], out.resolve(base + "_blocks.bin.gz"), null);
                    VanillaReference.writeChunk(out.resolve(base + "_density.bin.gz"), settings,
                        RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed), c[0], c[1]);
                    index.println(seed + "\t" + c[0] + "\t" + c[1]);
                }
                System.out.println("aquifer " + seed);
            }
        }

        writeCases(out, ctx, lookup, settings);
    }

    /// Noise router fields in the record's order, by their noise settings JSON names.
    private static final String[] ROUTER_FIELDS = { "barrier", "fluid_level_floodedness", "fluid_level_spread", "lava",
        "temperature", "vegetation", "continents", "erosion", "depth", "ridges", "preliminary_surface_level",
        "final_density", "vein_toggle", "vein_ridged", "vein_gap" };

    /// The settings of a case file: `template` with the case's `router` (noise router fields as in noise settings
    /// JSON, missing ones 0) and the given aquifer and ore vein switches.
    static NoiseGeneratorSettings caseSettings(String name, JsonObject json, HolderLookup.Provider lookup,
        NoiseGeneratorSettings template, boolean aquifers, boolean oreVeins) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        JsonObject routerJson = json.getAsJsonObject("router");

        DensityFunction[] fields = new DensityFunction[ROUTER_FIELDS.length];
        for (int i = 0; i < fields.length; i++) {
            JsonElement field = routerJson.has(ROUTER_FIELDS[i]) ? routerJson.get(ROUTER_FIELDS[i]) : new JsonPrimitive(0);
            fields[i] = DensityFunction.DIRECT_CODEC.parse(ops, field)
                .getOrThrow(e -> new IllegalArgumentException(name + ": " + e));
        }
        NoiseRouter router = new NoiseRouter(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
            fields[6], fields[7], fields[8], fields[9], fields[10], fields[11], fields[12], fields[13], fields[14]);
        return new NoiseGeneratorSettings(template.noiseSettings(), template.defaultBlock(), template.defaultFluid(),
            router, template.surfaceRule(), List.of(), template.seaLevel(), false, aquifers, oreVeins,
            template.useLegacyRandomSource());
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
            NoiseGeneratorSettings settings = caseSettings(name, json, lookup, template, true, false);
            long seed = json.get("seed").getAsLong();

            for (JsonElement chunk : json.getAsJsonArray("chunks")) {
                int cx = chunk.getAsJsonArray().get(0).getAsInt(), cz = chunk.getAsJsonArray().get(1).getAsInt();
                SurfaceReference.generate(ctx, settings, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, seed,
                    cx, cz, out.resolve("case_" + name + "_" + cx + "_" + cz + ".bin.gz"), null);
            }
            System.out.println("aquifer case " + name);
        }
    }
}
