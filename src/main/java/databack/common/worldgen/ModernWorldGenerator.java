package databack.common.worldgen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import databack.common.context.WorldContext;
import databack.common.context.WorldContextImpl;
import databack.common.dto.BlockStateDTO;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseDimensionGenerator;
import databack.common.dto.dimension.Dimension;
import databack.common.dto.dimension_type.DimensionType;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.world_preset.WorldPreset;
import databack.common.handlers.DimensionList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.BiomeList;
import databack.common.handlers.ConfiguredFeatureList;
import databack.common.handlers.PlacedFeatureList;
import databack.common.handlers.WorldPresetList;
import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.serde.MiscAdapters;
import databack.common.interop.ChunkBiomes;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.worldgen.aquifer.AquiferSystem;
import databack.common.worldgen.aquifer.VanillaAquifer;
import databack.common.worldgen.aquifer.VanillaAquifers;
import databack.common.worldgen.biome.ClimateSampler;
import databack.common.worldgen.biome.MultiNoiseBiomeSource;
import databack.common.worldgen.biome.ClimateEntry;
import databack.common.worldgen.biome.MultiNoiseParameters;
import databack.common.worldgen.biome.ProxyBiomeResolver;
import databack.common.worldgen.biome.SurfaceBiomes;
import databack.common.worldgen.biome.SurfaceHeights;
import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.biome.zoom.VanillaBiomeZoom;
import databack.common.worldgen.carver.VanillaCarvers;
import databack.common.worldgen.carver.VanillaCarversCache;
import databack.common.worldgen.carver.WorldCarvers;
import databack.common.worldgen.feature.BiomeFeatures;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.VanillaFeatures;
import databack.common.worldgen.feature.VanillaFeaturesCache;
import databack.common.worldgen.feature.population.DeferredPopulation;
import databack.common.worldgen.feature.population.FeatureDecorator;
import databack.common.worldgen.feature.population.PendingPopulationData;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.world.LegacyBlockFacts;
import databack.common.worldgen.feature.world.LegacyStates;
import databack.common.worldgen.feature.world.LegacyWorld;
import databack.common.worldgen.feature.world.FeatureBiomes;
import databack.common.worldgen.feature.world.PopulationLevel;
import databack.common.worldgen.feature.world.WorldLegacyWorld;
import databack.common.worldgen.orevein.OreVeinSystem;
import databack.common.worldgen.orevein.VeinBlock;
import databack.common.worldgen.orevein.VeinFieldSampler;
import databack.common.worldgen.surface.BlockBiomes;
import databack.common.worldgen.surface.SurfaceBuildContext;
import databack.common.worldgen.surface.SurfaceSystem;
import databack.common.worldgen.density.ChunkDensitySampler;
import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.DensityPrefetcher;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.rng.StandardRandomFactory;
import databack.common.worldgen.rng.XoroshiroRandom;
import databack.common.worldgen.rng.XoroshiroRandomFactory;

public class ModernWorldGenerator implements IChunkProvider {

    private static final Logger LOGGER = LogManager.getLogger("databack-worldgen");

    @org.jetbrains.annotations.NotNull
    public final World world;

    public final NoiseGeneratorSettings generatorSettings;
    public final DimensionType dimensionType;

    private final SurfaceBiomes surfaceBiomes;
    @Nullable
    private final SurfaceSystem surfaceSystem;
    private final AquiferSystem aquiferSystem;
    /// Both null when ore veins are off or the generated range holds no vein heights.
    @Nullable
    private final OreVeinSystem oreVeins;
    @Nullable
    private final VeinFieldSampler veinFields;
    /// By [VeinBlock] ordinal; null where the block has no 1.7 counterpart.
    private final ImmutableBlockMeta[] veinBlocks = resolveVeinBlocks();
    private static final ImmutableBlockMeta LAVA = new BlockMeta(Blocks.lava, 0);
    private static final ImmutableBlockMeta AIR = new BlockMeta(Blocks.air, 0);
    /// Null when carvers are unavailable (no vanilla classes).
    @Nullable
    private final WorldCarvers carvers;
    private final DeferredPopulation.Decorator decorator;
    /// See [#population()].
    private DeferredPopulation population;
    private final ProxyBiomeResolver biomeResolver = new ProxyBiomeResolver(BiomeGenBase.plains);

    private final DensityRouter router;
    private final ChunkDensitySampler densitySampler;
    private final DensityPrefetcher densityPrefetcher;

    public ModernWorldGenerator(World world, String generatorOptions) {
        this.world = world;

        Dimension dim = DimensionList.RT.getHandler().getDimension(getDimensionName(world.provider.dimensionId));

        if (dim == null) {
            WorldPreset preset = WorldPresetList.RT.getHandler().getWorldPreset("minecraft:normal");

            if (preset != null) {
                dim = preset.dimensions.get(getDimensionName(world.provider.dimensionId));
            }

            if (dim == null) {
                throw new IllegalStateException("Invalid dimension: " + world.provider.dimensionId);
            }
        }

        if (!(dim.generator instanceof NoiseDimensionGenerator noise)) {
            throw new IllegalStateException("Non-noise dimensions are current not supported: " + world.provider.dimensionId + ", " + dim.generator);
        }

        if (!(noise.biome_source instanceof MultiNoiseBiomes noiseBiomes)) {
            throw new IllegalStateException("Non-noise biomes are current not supported: " + world.provider.dimensionId + ", " + noise.biome_source);
        }

        this.dimensionType = DimensionTypeList.RT.getHandler().getDimensionType(dim.type);
        this.generatorSettings = noise.settings.get();

        NoiseGeneratorSettings.NoiseRouter router = this.generatorSettings.noise_router;

        WorldContext context = WorldContext.getContext(world);

        DensityBuildContext build = DensityBuildContext.fromDatapacks(world.getSeed(), generatorSettings.legacy_random_source);
        RandomFactory rng = build.random();
        ((WorldContextImpl) context).setRandom(rng);

        this.router = build.buildRouter(router, true);

        // 1.7 worlds span y = 0..255, so only that part of the dimension is generated. The sampler is also given the
        // dimension's range, for trees where clipping would change values (see ChunkDensitySampler).
        NoiseGeneratorSettings.NoiseSettings ns = generatorSettings.noise;
        int minY = Math.max(ns.min_y, 0);
        int maxY = Math.min(ns.min_y + ns.height, 256);
        this.densitySampler = new ChunkDensitySampler(
            this.router.finalDensity, ns.size_horizontal * 4, ns.size_vertical * 4, minY, maxY, ns.min_y,
            ns.min_y + ns.height, 4096, false);
        this.densityPrefetcher = new DensityPrefetcher(densitySampler, 1, 64);

        if (dimensionType == null) {
            throw new IllegalStateException("Invalid dimension type: " + world.provider.dimensionId + ", " + dim.type);
        }

        String dimensionName = getDimensionName(world.provider.dimensionId);
        List<ClimateEntry> climate = MultiNoiseParameters.resolve(noiseBiomes, dimensionName);
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.create(climate, ClimateSampler.of(this.router));
        BiomeZoom zoom = VanillaBiomeZoom.get();
        this.surfaceBiomes = new SurfaceBiomes(biomeSource, zoom, zoom == null ? 0 : zoom.zoomSeed(world.getSeed()));
        this.surfaceSystem = createSurfaceSystem(build, ns, world.getSeed());
        this.decorator = createDecorator(climate, ns);
        VanillaAquifer aquifer = generatorSettings.aquifers_enabled ? VanillaAquifers.get() : null;
        this.aquiferSystem = aquifer == null ? AquiferSystem.disabled(generatorSettings.sea_level)
            : AquiferSystem.enabled(generatorSettings.sea_level, aquifer, this.router, rng);

        this.veinFields = !generatorSettings.ore_veins_enabled ? null
            : VeinFieldSampler.create(this.router, ns.size_horizontal * 4, ns.size_vertical * 4, minY, maxY, ns.min_y,
                ns.min_y + ns.height);
        this.oreVeins = veinFields == null ? null : OreVeinSystem.create(rng);

        VanillaCarvers vanillaCarvers = VanillaCarversCache.get();
        this.carvers = vanillaCarvers == null ? null : new WorldCarvers(vanillaCarvers, world.getSeed(),
            (x, z) -> biomeSource.getNoiseBiome(x << 2, 0, z << 2), ns.min_y, ns.height);
    }

    /// Each vein block's 1.7 counterpart; blocks without one are logged and left as the default block.
    private static ImmutableBlockMeta[] resolveVeinBlocks() {
        ImmutableBlockMeta[] blocks = new ImmutableBlockMeta[VeinBlock.values().length];
        for (VeinBlock block : VeinBlock.values()) {
            BlockStateDTO state = new BlockStateDTO();
            state.Name = new ResourceLocation(block.id);
            if (BlockIdentityRegistry.INSTANCE.getObject(state.Name) == null) {
                LOGGER.warn("Ore vein block {} has no 1.7 counterpart; veins keep the default block there", block.id);
                continue;
            }
            blocks[block.ordinal()] = toBlockMeta(MiscAdapters.resolveBlockState(state));
        }
        return blocks;
    }

    /// Places nothing when vanilla's feature classes are unavailable (already logged).
    private DeferredPopulation.Decorator createDecorator(
        List<ClimateEntry> climate, NoiseGeneratorSettings.NoiseSettings ns
    ) {
        VanillaFeatures vanilla = VanillaFeaturesCache.get();
        if (vanilla == null) return (chunkX, chunkZ) -> {};

        LegacyStates states = new LegacyStates();
        FtBlockFacts.install(new LegacyBlockFacts(states));

        FeatureBuildContext build = new FeatureBuildContext(vanilla,
            ConfiguredFeatureList.RT.getHandler()::getConfiguredFeature,
            PlacedFeatureList.RT.getHandler()::getPlacedFeature);
        Set<String> biomes = new HashSet<>();
        for (ClimateEntry entry : climate) biomes.add(entry.biome());
        BiomeFeatures features = new BiomeFeatures(build, biomes, BiomeList.RT.getHandler()::getBiome, ns.min_y,
            ns.height);

        LegacyWorld legacy = new WorldLegacyWorld(world, densitySampler.minY(), densitySampler.height());
        RandomSource worldRandom = new StandardRandom(world.getSeed());
        FeatureBiomes biomeDefinitions = new FeatureBiomes(BiomeList.RT.getHandler()::getBiome);
        return new FeatureDecorator(world::getChunkFromChunkCoords,
            area -> new PopulationLevel(legacy, states, area, surfaceBiomes.forChunk()::biome, biomeDefinitions,
                worldRandom),
            vanilla, features, world.getSeed());
    }

    /// Null when the noise settings have no surface rule.
    @Nullable
    private SurfaceSystem createSurfaceSystem(DensityBuildContext build, NoiseGeneratorSettings.NoiseSettings ns,
        long seed) {
        if (generatorSettings.surface_rule == null) return null;

        SurfaceBuildContext ctx = new SurfaceBuildContext(build, ns.min_y, ns.height,
            state -> toBlockMeta(MiscAdapters.resolveBlockState(state)), ModernWorldGenerator::biomeTemperature);
        return new SurfaceSystem(generatorSettings.surface_rule.instantiate(ctx), ns.min_y, ns.height, seed,
            build.noise("minecraft:surface"), build.noise("minecraft:surface_secondary"),
            router.preliminarySurfaceLevel);
    }

    private static ImmutableBlockMeta toBlockMeta(BlockState state) {
        return new BlockMeta(state.getBlock(), state.getBlockMeta(0));
    }

    /// A biome's datapack temperature; 0.5 (1.7's default) if it has none.
    private static double biomeTemperature(String biome) {
        DatapackBiome definition = BiomeList.RT.getHandler().getBiome(biome);
        return definition == null ? 0.5 : definition.temperature;
    }

    public static String getDimensionName(int dimId) {
        return switch (dimId) {
            case 0 -> "minecraft:overworld";
            case -1 -> "minecraft:the_nether";
            case 1 -> "minecraft:the_end";
            default -> null;
        };
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        Chunk chunk = new Chunk(world, chunkX, chunkZ);

        double[] density = densityPrefetcher.get(chunkX, chunkZ, this::needsGeneration);

        int minY = densitySampler.minY();
        int layers = densitySampler.height();

        BlockBiomes biomes = surfaceBiomes.forChunk();
        fillBiomes(chunk, biomes, density, minY);

        ImmutableBlockMeta[] blocks = noiseAndSurface(chunkX, chunkZ, density, minY, layers, biomes);
        if (carvers != null) {
            carvers.carve(chunkX, chunkZ, blocks, minY, layers,
                aquiferSystem.chunkAquifer(chunkX, chunkZ, minY, layers),
                (x, y, z, underFluid) -> surfaceSystem == null ? null
                    : surfaceSystem.topMaterial(x, y, z, biomes, underFluid));
        }

        writeSections(chunk, blocks, minY, layers);
        chunk.generateSkylightMap();

        population().markGenerated(chunkX, chunkZ);
        return chunk;
    }

    /// Every block of the chunk after terrain, fluids, ore veins and surface rules, at index
    /// `((y - minY) * 16 + z) * 16 + x`.
    private ImmutableBlockMeta[] noiseAndSurface(
        int chunkX, int chunkZ, double[] density, int minY, int layers, BlockBiomes biomes
    ) {
        ImmutableBlockMeta solid = toBlockMeta(this.generatorSettings.default_block);
        ImmutableBlockMeta fluid = toBlockMeta(this.generatorSettings.default_fluid);

        byte[] kinds = aquiferSystem.kinds(chunkX, chunkZ, density, minY, layers);
        VeinBlock[] veins = oreVeins == null ? new VeinBlock[kinds.length]
            : oreVeins.place(chunkX, chunkZ, kinds, minY, layers, veinFields.sample(chunkX, chunkZ));
        ImmutableBlockMeta[] surface = surfaceSystem == null ? new ImmutableBlockMeta[kinds.length]
            : surfaceSystem.apply(chunkX, chunkZ, kinds, minY, layers, biomes);

        ImmutableBlockMeta[] blocks = new ImmutableBlockMeta[kinds.length];
        for (int index = 0; index < kinds.length; index++) {
            switch (kinds[index]) {
                case SurfaceSystem.SOLID:
                    blocks[index] = solidBlock(veins[index], surface[index], solid);
                    break;
                case SurfaceSystem.FLUID:
                    blocks[index] = fluid;
                    break;
                case SurfaceSystem.LAVA:
                    blocks[index] = LAVA;
                    break;
                default:
                    blocks[index] = AIR;
            }
        }
        return blocks;
    }

    private void writeSections(Chunk chunk, ImmutableBlockMeta[] blocks, int minY, int layers) {
        for (int ebsY = 0; ebsY < 16; ebsY++) {
            ExtendedBlockStorage ebs = new ExtendedBlockStorage(ebsY << 4, !world.provider.hasNoSky);
            chunk.getBlockStorageArray()[ebsY] = ebs;

            for (int y = 0; y < 16; y++) {
                int layer = (ebsY << 4 | y) - minY;
                if (layer < 0 || layer >= layers) continue;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        ImmutableBlockMeta block = blocks[(layer * 16 + z) * 16 + x];
                        if (block.getBlock() != Blocks.air) setBlock(ebs, x, y, z, block);
                    }
                }
            }
        }
    }

    /// Veins take precedence: surface rules only replace the default block (docs/worldgen/ore-veins.md, section 4).
    private ImmutableBlockMeta solidBlock(
        @Nullable VeinBlock vein, @Nullable ImmutableBlockMeta surface, ImmutableBlockMeta solid
    ) {
        if (vein != null && veinBlocks[vein.ordinal()] != null) return veinBlocks[vein.ordinal()];
        return surface != null ? surface : solid;
    }

    private static void setBlock(ExtendedBlockStorage ebs, int x, int y, int z, ImmutableBlockMeta block) {
        ebs.func_150818_a(x, y, z, block.getBlock());
        if (block.getBlockMeta() != 0) ebs.setExtBlockMetadata(x, y, z, block.getBlockMeta());
    }

    /// Fills the chunk's 2D biome array with each column's surface biome (the biome at its top solid block, or at sea
    /// level for columns without one).
    private void fillBiomes(Chunk chunk, BlockBiomes biomes, double[] density, int minY) {
        int[] surface = SurfaceHeights.fromDensity(density, minY, densitySampler.height());
        String[] ids = surfaceBiomes.columns(biomes, chunk.xPosition, chunk.zPosition, surface,
            generatorSettings.sea_level);

        int[] numericIds = new int[256];
        for (int column = 0; column < 256; column++) numericIds[column] = biomeResolver.resolve(ids[column]).biomeID;
        ChunkBiomes.current().write(chunk, numericIds);
    }

    /// Whether a chunk will (probably) be generated later, i.e. it's neither loaded nor saved. Server thread only.
    private boolean needsGeneration(int chunkX, int chunkZ) {
        if (!(world.getChunkProvider() instanceof ChunkProviderServer server)) return true;
        if (server.chunkExists(chunkX, chunkZ)) return false;
        return !(server.currentChunkLoader instanceof AnvilChunkLoader loader) || !loader.chunkExists(world, chunkX, chunkZ);
    }

    /// 1.7 calls this once chunks `chunkX..chunkX + 1` by `chunkZ..chunkZ + 1` are loaded; features need the 3×3
    /// around their chunk instead, so decoration is deferred until it exists, then queued for [#tickDecoration].
    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        population().onWindowComplete(chunkX, chunkZ, this::isGenerated);
    }

    /// Decorates up to `limit` of the chunks queued by [#populate]. Server thread, once per world tick.
    public void tickDecoration(int limit) {
        population().tick(limit, this::isGenerated, decorator);
    }

    private boolean isGenerated(int chunkX, int chunkZ) {
        return !needsGeneration(chunkX, chunkZ);
    }

    /// Created on first use: the world's per-dimension storage is set up after its generator.
    private DeferredPopulation population() {
        if (population == null) {
            PendingPopulationData pending = PendingPopulationData.get(world);
            population = new DeferredPopulation(pending.chunks(), pending::markDirty);
        }
        return population;
    }

    @Override
    public List<SpawnListEntry> getPossibleCreatures(
        EnumCreatureType creatureType, int blockX, int blockY,
        int blockZ
    ) {
        return java.util.Collections.emptyList();
    }

    @Override
    public void recreateStructures(int chunkX, int chunkZ) {

    }

    // <editor-fold desc="Stubs" defaultstate="collapsed">

    @Override
    public Chunk loadChunk(int x, int z) {
        return provideChunk(x, z);
    }

    @Override
    public String makeString() {
        return "Modern";
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public boolean saveChunks(boolean p_73151_1_, IProgressUpdate p_73151_2_) {
        return false;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return false;
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structureName, int x, int y, int z) {
        // findClosestStructure(String type, int x, int y, int z)
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void saveExtraData() {

    }

    // </editor-fold>
}
