package net.minecraft.world.level.levelgen;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Generates golden block states for Databack's surface rule parity tests by running vanilla's chunk generation steps
 * (biomes, noise fill, surface) on a lone chunk, as a black box. Writes into `<out>/surface/`:
 *
 * - `chunk_<config>_<seed>_<cx>_<cz>_<stage>.bin.gz`: every block of a real chunk after the `noise` stage (terrain and
 *   fluids) and the `surface` stage. Configs are vanilla noise settings, plus `overworld_no_aquifers` (aquifers and
 *   ore veins disabled, which is what Databack generates so far).
 * - `case_<name>_<cx>_<cz>.bin.gz`: chunks of `cases/<name>.json`, a synthetic surface rule on otherwise vanilla
 *   terrain (aquifers and ore veins disabled); after the surface stage.
 * - `<chunk file>.columns.tsv` beside each surface-stage file: per column, the preliminary surface level the chunk's
 *   noise state reports.
 * - `index.tsv`: every file written, with its config or case, seed and chunk.
 *
 * Chunk format (gzip, big-endian): int minY, int height, int paletteSize, paletteSize UTF strings
 * (`namespace:block[prop=value,...]`, properties sorted by name), then height*16*16 shorts (palette indices) at index
 * `((y - minY) * 16 + z) * 16 + x`. Structures are not generated.
 */
final class SurfaceReference {

    private SurfaceReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path out = root.resolve("surface");
        Files.createDirectories(out);

        Context ctx = new Context(lookup);

        Object[][] configs = {
            { NoiseGeneratorSettings.OVERWORLD, "overworld", true },
            { NoiseGeneratorSettings.OVERWORLD, "overworld_no_aquifers", false },
            { NoiseGeneratorSettings.NETHER, "nether", true },
        };
        long[] seeds = { 12345L, -987654321L };
        int[][] chunks = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 } };

        try (PrintWriter index = new PrintWriter(Files.newBufferedWriter(out.resolve("index.tsv")))) {
            index.println("file\tconfig\tseed\tchunkX\tchunkZ\tstage");

            for (Object[] config : configs) {
                @SuppressWarnings("unchecked")
                ResourceKey<NoiseGeneratorSettings> key = (ResourceKey<NoiseGeneratorSettings>) config[0];
                NoiseGeneratorSettings settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
                if (!(Boolean) config[2]) settings = withoutAquifers(settings, settings.surfaceRule());
                MultiNoiseBiomeSourceParameterList.Preset preset = key == NoiseGeneratorSettings.NETHER
                    ? MultiNoiseBiomeSourceParameterList.Preset.NETHER
                    : MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD;

                for (long seed : seeds) {
                    for (int[] c : chunks) {
                        String base = "chunk_" + config[1] + "_" + seed + "_" + c[0] + "_" + c[1];
                        generate(ctx, settings, preset, seed, c[0], c[1], out.resolve(base + "_noise.bin.gz"),
                            out.resolve(base + "_surface.bin.gz"));
                        index.println(base + "_noise.bin.gz\t" + config[1] + "\t" + seed + "\t" + c[0] + "\t" + c[1]
                            + "\tnoise");
                        index.println(base + "_surface.bin.gz\t" + config[1] + "\t" + seed + "\t" + c[0] + "\t" + c[1]
                            + "\tsurface");
                    }
                    System.out.println("surface " + config[1] + " " + seed);
                }
            }

            writeCases(out, ctx, index);
        }
    }

    /// `cases/<name>.json`: `seed`, `surface_rule` (as in noise settings JSON) and `chunks` ([[x, z], ...]), on the
    /// overworld's terrain and biomes.
    private static void writeCases(Path out, Context ctx, PrintWriter index) throws Exception {
        Path dir = out.resolve("cases");
        if (!Files.isDirectory(dir)) return;

        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }

        NoiseGeneratorSettings overworld = ctx.lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ctx.lookup);

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            SurfaceRules.RuleSource rule = SurfaceRules.RuleSource.CODEC.parse(ops, json.get("surface_rule"))
                .getOrThrow(e -> new IllegalArgumentException(name + ": " + e));
            NoiseGeneratorSettings settings = withoutAquifers(overworld, rule);
            long seed = json.get("seed").getAsLong();

            for (JsonElement chunk : json.getAsJsonArray("chunks")) {
                int cx = chunk.getAsJsonArray().get(0).getAsInt(), cz = chunk.getAsJsonArray().get(1).getAsInt();
                String fileName = "case_" + name + "_" + cx + "_" + cz + ".bin.gz";
                generate(ctx, settings, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, seed, cx, cz, null,
                    out.resolve(fileName));
                index.println(fileName + "\t" + name + "\t" + seed + "\t" + cx + "\t" + cz + "\tsurface");
            }
            System.out.println("surface case " + name);
        }
    }

    /// A chunk after the surface step, with what later steps need.
    record Generated(ProtoChunk chunk, NoiseBasedChunkGenerator generator, RandomState state, BiomeManager biomes,
        StructureManager structures) {}

    /// Runs the biome, noise and surface steps on one chunk. `noiseFile` (if not null) gets the chunk after the noise
    /// step, `surfaceFile` (if not null; otherwise the surface step is skipped and null returned) after the surface
    /// step.
    static Generated generate(
        Context ctx, NoiseGeneratorSettings settings, MultiNoiseBiomeSourceParameterList.Preset preset, long seed,
        int chunkX, int chunkZ, Path noiseFile, Path surfaceFile
    ) throws IOException {
        RandomState state = RandomState.create(settings, ctx.lookup.lookupOrThrow(Registries.NOISE), seed);
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromList(
            new MultiNoiseBiomeSourceParameterList(preset, ctx.biomes).parameters());
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(source, Holder.direct(settings));
        NoStructures structures = new NoStructures(seed);

        NoiseSettings ns = settings.noiseSettings();
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY,
            LevelHeightAccessor.create(ns.minY(), ns.height()), ctx.containers, null);

        chunk.fillBiomesFromNoise(source, state.sampler());
        generator.fillFromNoise(Blender.empty(), state, structures, chunk).join();
        if (noiseFile != null) writeBlocks(noiseFile, chunk);
        if (surfaceFile == null) return null;

        BiomeManager biomes = new BiomeManager(
            (qx, qy, qz) -> source.getNoiseBiome(qx, qy, qz, state.sampler()), BiomeManager.obfuscateSeed(seed));
        NoiseChunk noise = chunk.getOrCreateNoiseChunk(c -> {
            throw new IllegalStateException("The noise step left no noise state");
        });
        writeColumns(Path.of(surfaceFile.toString().replace(".bin.gz", ".columns.tsv")), chunk, noise);

        generator.buildSurface(chunk, new WorldGenerationContext(generator, chunk), state, structures, biomes,
            ctx.biomeRegistry, Blender.empty());
        writeBlocks(surfaceFile, chunk);
        return new Generated(chunk, generator, state, biomes, structures);
    }

    private static void writeColumns(Path file, ProtoChunk chunk, NoiseChunk noise) throws IOException {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("x\tz\tpreliminary_surface_level");
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    w.println(x + "\t" + z + "\t" + noise.preliminarySurfaceLevel(minX + x, minZ + z));
                }
            }
        }
    }

    private static NoiseGeneratorSettings withoutAquifers(NoiseGeneratorSettings s, SurfaceRules.RuleSource rule) {
        return withFeatures(s, rule, false, false);
    }

    /// `s` with `rule` and the given aquifer and ore vein switches.
    static NoiseGeneratorSettings withFeatures(
        NoiseGeneratorSettings s, SurfaceRules.RuleSource rule, boolean aquifers, boolean oreVeins
    ) {
        return new NoiseGeneratorSettings(s.noiseSettings(), s.defaultBlock(), s.defaultFluid(), s.noiseRouter(), rule,
            s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), aquifers, oreVeins, s.useLegacyRandomSource());
    }

    static void writeBlocks(Path file, ProtoChunk chunk) throws IOException {
        int minY = chunk.getMinY(), height = chunk.getHeight();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();

        Map<String, Integer> palette = new HashMap<>();
        List<String> names = new ArrayList<>();
        short[] indices = new short[height * 256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    String name = describe(chunk.getBlockState(pos.set(minX + x, minY + y, minZ + z)));
                    Integer index = palette.get(name);
                    if (index == null) {
                        index = names.size();
                        palette.put(name, index);
                        names.add(name);
                    }
                    indices[(y * 16 + z) * 16 + x] = (short) (int) index;
                }
            }
        }

        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file.toFile())))) {
            dos.writeInt(minY);
            dos.writeInt(height);
            dos.writeInt(names.size());
            for (String name : names) dos.writeUTF(name);
            for (short index : indices) dos.writeShort(index);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static String describe(BlockState state) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        List<Property<?>> properties = new ArrayList<>(state.getProperties());
        if (properties.isEmpty()) return sb.toString();

        properties.sort((a, b) -> a.getName().compareTo(b.getName()));
        sb.append('[');
        for (int i = 0; i < properties.size(); i++) {
            Property p = properties.get(i);
            if (i > 0) sb.append(',');
            sb.append(p.getName()).append('=').append(p.getName(state.getValue(p)));
        }
        return sb.append(']').toString();
    }

    /// Registries a lone chunk needs: the vanilla biomes as a real registry, and chunk section storage over it.
    static final class Context {

        final HolderLookup.Provider lookup;
        final Registry<Biome> biomeRegistry;
        final HolderLookup.RegistryLookup<Biome> biomes;
        final PalettedContainerFactory containers;

        Context(HolderLookup.Provider lookup) {
            this.lookup = lookup;

            MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
            lookup.lookupOrThrow(Registries.BIOME).listElements()
                .forEach(h -> registry.register(h.key(), h.value(), RegistrationInfo.BUILT_IN));
            this.biomeRegistry = registry.freeze();
            this.biomes = biomeRegistry;
            this.containers = PalettedContainerFactory.create(
                new RegistryAccess.ImmutableRegistryAccess(List.of(biomeRegistry)));
        }
    }

    /// A structure manager for a world without structures: the noise step asks it which structures shape the terrain.
    private static final class NoStructures extends StructureManager {

        NoStructures(long seed) {
            super(null, new WorldOptions(seed, false, false), null);
        }

        @Override
        public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> predicate) {
            return List.of();
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
