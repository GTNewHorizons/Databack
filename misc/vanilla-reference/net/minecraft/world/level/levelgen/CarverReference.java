package net.minecraft.world.level.levelgen;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStep;

/**
 * Generates golden data for Databack's carver tests by running vanilla's biome, noise, surface and carver steps on
 * lone overworld chunks (aquifers on, ore veins off), as a black box. Writes into `<out>/carver/`:
 *
 * - `chunk_<seed>_<cx>_<cz>_surface.bin.gz` and `..._carved.bin.gz`: every block before and after the carver step
 *   (the surface harness's chunk format; `.columns.tsv` beside the first as in the surface harness).
 * - `case_<name>_<cx>_<cz>_{surface,carved}.bin.gz`: chunks of `cases/<name>.json` (`seed`, `router` as in the
 *   aquifer harness's cases, `chunks`, optional `aquifers: true`), on otherwise overworld settings.
 * - `index.tsv`: every real chunk written.
 *
 * The carver step needs a world generation region, which only a running server can build; a stand-in region
 * allocated without its constructor answers the calls the step makes.
 */
final class CarverReference {

    private CarverReference() {}

    static void write(Path root, HolderLookup.Provider lookup) throws Exception {
        Path out = root.resolve("carver");
        Files.createDirectories(out);
        bindBlockTags(root.resolve("../../../../build/modernData/data/minecraft/tags/block").normalize());

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
                    carve(ctx, settings, seed, c[0], c[1], out, "chunk_" + seed + "_" + c[0] + "_" + c[1]);
                    index.println(seed + "\t" + c[0] + "\t" + c[1]);
                }
                System.out.println("carver " + seed);
            }
        }

        writeCases(out, ctx, lookup, overworld);
        writeProbes(out.resolve("probes"), ctx, settings);
    }

    private static final long[] PROBE_SEEDS = { 0L, 1L, 12345L, -987654321L, 0x5DEECE66DL, Long.MIN_VALUE + 7 };

    /// Black-box probes of the vanilla helpers the carvers call, for Databack's stand-ins (`probes/*.tsv`):
    ///
    /// - `mth.tsv`: `fn x result` for `sin`, `cos`, `floor`.
    /// - `random.tsv`: `kind seed op index value`: fresh sources of each kind (`create`, `thread_local`,
    ///   `xoroshiro`) per seed and op (`long`, `int37`, `float`, `double`), eight draws each.
    /// - `large_feature_seed.tsv`: `base worldSeed chunkX chunkZ index value`: a worldgen random over a `base`
    ///   source seeded 0, after `setLargeFeatureSeed`, eight `nextLong` draws.
    /// - `providers.tsv`: `provider seed value next`: one sample from a fresh `create(seed)` source, and the
    ///   source's next float after it (to count draws).
    /// - `chunk_pos.tsv`: `chunkX chunkZ fn arg result` for `getBlockX`, `getBlockZ`, the middle and minimum blocks.
    /// - `context.tsv`: `fn result`: the overworld generation context's `getMinGenY` and `getGenDepth`.
    private static void writeProbes(Path dir, SurfaceReference.Context ctx, NoiseGeneratorSettings settings)
        throws Exception {
        Files.createDirectories(dir);

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("mth.tsv")))) {
            w.println("fn\tx\tresult");
            java.util.Random rng = new java.util.Random(42);
            for (int i = 0; i < 4000; i++) {
                double x = i < 1000 ? (i - 500) * 0.0123 : (rng.nextDouble() - 0.5) * (i < 3000 ? 20 : 2e6);
                w.println("sin\t" + x + "\t" + net.minecraft.util.Mth.sin(x));
                w.println("cos\t" + x + "\t" + net.minecraft.util.Mth.cos(x));
                w.println("floor\t" + x + "\t" + net.minecraft.util.Mth.floor(x));
            }
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("random.tsv")))) {
            w.println("kind\tseed\top\tindex\tvalue");
            String[] kinds = { "create", "thread_local", "xoroshiro" };
            String[] ops = { "long", "int37", "float", "double" };
            for (String kind : kinds) {
                for (long seed : PROBE_SEEDS) {
                    for (String op : ops) {
                        net.minecraft.util.RandomSource r = source(kind, seed);
                        for (int i = 0; i < 8; i++) {
                            Object v = switch (op) {
                                case "long" -> r.nextLong();
                                case "int37" -> r.nextInt(37);
                                case "float" -> r.nextFloat();
                                default -> r.nextDouble();
                            };
                            w.println(kind + "\t" + seed + "\t" + op + "\t" + i + "\t" + v);
                        }
                    }
                }
            }
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("large_feature_seed.tsv")))) {
            w.println("base\tworldSeed\tchunkX\tchunkZ\tindex\tvalue");
            int[][] chunks = { { 0, 0 }, { 1, 0 }, { -7, 13 }, { 300, -1200 } };
            for (String base : new String[] { "create", "xoroshiro" }) {
                for (long seed : PROBE_SEEDS) {
                    for (int[] c : chunks) {
                        WorldgenRandom r = new WorldgenRandom(source(base, 0));
                        r.setLargeFeatureSeed(seed, c[0], c[1]);
                        for (int i = 0; i < 8; i++) {
                            w.println(base + "\t" + seed + "\t" + c[0] + "\t" + c[1] + "\t" + i + "\t" + r.nextLong());
                        }
                    }
                }
            }
        }

        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromList(
            new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, ctx.biomes)
                .parameters());
        WorldGenerationContext genContext = new WorldGenerationContext(
            new NoiseBasedChunkGenerator(biomeSource, net.minecraft.core.Holder.direct(settings)),
            LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height()));
        Object[][] providers = {
            { "uniform_float(0.1,0.9)", net.minecraft.util.valueproviders.UniformFloat.of(0.1f, 0.9f) },
            { "uniform_float(-1,-0.4)", net.minecraft.util.valueproviders.UniformFloat.of(-1f, -0.4f) },
            { "trapezoid(0,6,2)", net.minecraft.util.valueproviders.TrapezoidFloat.of(0f, 6f, 2f) },
            { "constant(3)", net.minecraft.util.valueproviders.ConstantFloat.of(3f) },
            { "uniform_height(above_bottom 8,absolute 180)", net.minecraft.world.level.levelgen.heightproviders
                .UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)) },
            { "uniform_height(absolute 10,absolute 67)", net.minecraft.world.level.levelgen.heightproviders
                .UniformHeight.of(VerticalAnchor.absolute(10), VerticalAnchor.absolute(67)) },
            { "random_between(0.5,4)", null },
        };
        writeChunkPosProbes(dir, genContext);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("providers.tsv")))) {
            w.println("provider\tseed\tvalue\tnext");
            for (Object[] p : providers) {
                for (long seed = 0; seed < 200; seed++) {
                    net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed);
                    Object value = p[1] == null ? net.minecraft.util.Mth.randomBetween(r, 0.5f, 4f)
                        : p[1] instanceof net.minecraft.util.valueproviders.FloatProvider f ? f.sample(r)
                        : ((net.minecraft.world.level.levelgen.heightproviders.HeightProvider) p[1]).sample(r, genContext);
                    w.println(p[0] + "\t" + seed + "\t" + value + "\t" + r.nextFloat());
                }
            }
        }
    }

    private static void writeChunkPosProbes(Path dir, WorldGenerationContext genContext) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("chunk_pos.tsv")))) {
            w.println("chunkX\tchunkZ\tfn\targ\tresult");
            int[][] chunks = { { 0, 0 }, { -1, 1 }, { 13, -7 }, { -300, 1200 } };
            for (int[] c : chunks) {
                ChunkPos pos = new ChunkPos(c[0], c[1]);
                String at = c[0] + "\t" + c[1] + "\t";
                for (int i = -2; i <= 18; i++) {
                    w.println(at + "getBlockX\t" + i + "\t" + pos.getBlockX(i));
                    w.println(at + "getBlockZ\t" + i + "\t" + pos.getBlockZ(i));
                }
                w.println(at + "getMiddleBlockX\t\t" + pos.getMiddleBlockX());
                w.println(at + "getMiddleBlockZ\t\t" + pos.getMiddleBlockZ());
                w.println(at + "getMinBlockX\t\t" + pos.getMinBlockX());
                w.println(at + "getMinBlockZ\t\t" + pos.getMinBlockZ());
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("context.tsv")))) {
            w.println("fn\tresult");
            w.println("getMinGenY\t" + genContext.getMinGenY());
            w.println("getGenDepth\t" + genContext.getGenDepth());
        }
    }

    private static net.minecraft.util.RandomSource source(String kind, long seed) {
        return switch (kind) {
            case "create" -> net.minecraft.util.RandomSource.create(seed);
            case "thread_local" -> net.minecraft.util.RandomSource.createThreadLocalInstance(seed);
            default -> new XoroshiroRandomSource(seed);
        };
    }

    /// Carvers only replace blocks of a tag, and nothing binds tags outside a running server: binds every vanilla
    /// block tag from the extracted datapack (`tags/block/*.json`).
    @SuppressWarnings("unchecked")
    private static void bindBlockTags(Path dir) throws Exception {
        Map<String, JsonArray> raw = new HashMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path file : s.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = dir.relativize(file).toString().replace(".json", "");
                raw.put("minecraft:" + id, JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                    .getAsJsonArray("values"));
            }
        }

        Map<TagKey<Block>, List<Holder<Block>>> tags = new HashMap<>();
        for (String id : raw.keySet()) {
            List<Holder<Block>> holders = new ArrayList<>();
            resolveTag(id, raw, holders, new HashSet<>());
            tags.put(TagKey.create(Registries.BLOCK, Identifier.parse(id)), holders);
        }
        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, tags))
            .apply();
    }

    private static void resolveTag(String id, Map<String, JsonArray> raw, List<Holder<Block>> out, Set<String> seen) {
        if (!seen.add(id)) return;
        for (JsonElement value : raw.get(id)) {
            String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (entry.startsWith("#")) {
                resolveTag(entry.substring(1), raw, out, seen);
            } else {
                BuiltInRegistries.BLOCK.get(Identifier.parse(entry)).ifPresent(out::add);
            }
        }
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
                false);
            long seed = json.get("seed").getAsLong();

            for (JsonElement chunk : json.getAsJsonArray("chunks")) {
                int cx = chunk.getAsJsonArray().get(0).getAsInt(), cz = chunk.getAsJsonArray().get(1).getAsInt();
                carve(ctx, settings, seed, cx, cz, out, "case_" + name + "_" + cx + "_" + cz);
            }
            System.out.println("carver case " + name);
        }
    }

    private static void carve(SurfaceReference.Context ctx, NoiseGeneratorSettings settings, long seed, int chunkX,
        int chunkZ, Path out, String base) throws Exception {
        SurfaceReference.Generated g = SurfaceReference.generate(ctx, settings,
            MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, seed, chunkX, chunkZ, null,
            out.resolve(base + "_surface.bin.gz"));

        FakeRegion region = FakeRegion.create(ctx, g.chunk(), g.biomes(), seed);
        g.generator().applyCarvers(region, seed, g.state(), g.biomes(), g.structures(), g.chunk());
        SurfaceReference.writeBlocks(out.resolve(base + "_carved.bin.gz"), g.chunk());
    }

    /// Answers the region calls of the carver step: the chunk being carved is the centre, other chunks are empty
    /// placeholders (the step only asks them for their carver biome), and nothing is old.
    static final class FakeRegion extends WorldGenRegion {

        private SurfaceReference.Context ctx;
        private ProtoChunk center;
        private BiomeManager biomes;
        private RegistryAccess registries;
        private long seed;
        private Map<Long, ProtoChunk> others;

        /// Never called: instances come from [#create].
        private FakeRegion() {
            super((ServerLevel) null, (StaticCache2D) null, (ChunkStep) null, (ChunkAccess) null);
        }

        static FakeRegion create(SurfaceReference.Context ctx, ProtoChunk center, BiomeManager biomes, long seed)
            throws Exception {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            FakeRegion region = (FakeRegion) ((sun.misc.Unsafe) f.get(null)).allocateInstance(FakeRegion.class);
            region.ctx = ctx;
            region.center = center;
            region.biomes = biomes;
            region.registries = new RegistryAccess.ImmutableRegistryAccess(List.of(ctx.biomeRegistry));
            region.seed = seed;
            region.others = new HashMap<>();
            return region;
        }

        @Override
        public ChunkPos getCenter() {
            return center.getPos();
        }

        @Override
        public ChunkAccess getChunk(int x, int z) {
            if (center.getPos().pack() == ChunkPos.pack(x, z)) return center;
            return others.computeIfAbsent(ChunkPos.pack(x, z), k -> new ProtoChunk(new ChunkPos(x, z),
                UpgradeData.EMPTY, LevelHeightAccessor.create(getMinY(), getHeight()), ctx.containers, null));
        }

        @Override
        public BiomeManager getBiomeManager() {
            return biomes;
        }

        @Override
        public RegistryAccess registryAccess() {
            return registries;
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public int getMinY() {
            return center.getMinY();
        }

        @Override
        public int getHeight() {
            return center.getHeight();
        }

        @Override
        public boolean isOldChunkAround(ChunkPos pos, int range) {
            return false;
        }
    }
}
