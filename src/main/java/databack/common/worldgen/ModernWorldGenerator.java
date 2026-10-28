package databack.common.worldgen;

import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import databack.common.context.WorldContext;
import databack.common.context.WorldContextImpl;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseDimensionGenerator;
import databack.common.dto.dimension.Dimension;
import databack.common.dto.dimension_type.DimensionType;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.world_preset.WorldPreset;
import databack.common.handlers.DimensionList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.WorldPresetList;
import databack.common.interop.ChunkBiomes;
import databack.common.worldgen.biome.ClimateSampler;
import databack.common.worldgen.biome.MultiNoiseBiomeSource;
import databack.common.worldgen.biome.MultiNoiseParameters;
import databack.common.worldgen.biome.ProxyBiomeResolver;
import databack.common.worldgen.biome.SurfaceBiomes;
import databack.common.worldgen.biome.SurfaceHeights;
import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.biome.zoom.VanillaBiomeZoom;
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

    @org.jetbrains.annotations.NotNull
    public final World world;

    public final NoiseGeneratorSettings generatorSettings;
    public final DimensionType dimensionType;

    private final SurfaceBiomes surfaceBiomes;
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
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.create(
            MultiNoiseParameters.resolve(noiseBiomes, dimensionName), ClimateSampler.of(this.router));
        BiomeZoom zoom = VanillaBiomeZoom.get();
        this.surfaceBiomes = new SurfaceBiomes(biomeSource, zoom, zoom == null ? 0 : zoom.zoomSeed(world.getSeed()));
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

        ImmutableBlockMeta main = new BlockMeta(
            this.generatorSettings.default_block.getBlock(),
            this.generatorSettings.default_block.getBlockMeta(0));

        // CPU path.
        double[] density = densityPrefetcher.get(chunkX, chunkZ, this::needsGeneration);

        int minY = densitySampler.minY();
        int maxY = minY + densitySampler.height();

        fillBiomes(chunk, density, minY);

        for (int ebsY = 0; ebsY < 16; ebsY++) {
            ExtendedBlockStorage ebs = new ExtendedBlockStorage(ebsY << 4, !world.provider.hasNoSky);
            chunk.getBlockStorageArray()[ebsY] = ebs;

            for (int y = 0; y < 16; y++) {
                int worldY = ebsY << 4 | y;
                if (worldY < minY || worldY >= maxY) continue;

                int row = (worldY - minY) * 256;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (density[row + z * 16 + x] > 0) {
                            ebs.func_150818_a(x, y, z, main.getBlock());
                            if (main.getBlockMeta() != 0) {
                                ebs.setExtBlockMetadata(x, y, z, main.getBlockMeta());
                            }
                        }
                    }
                }
            }
        }

        chunk.generateSkylightMap();

        return chunk;
    }

    /// Fills the chunk's 2D biome array with each column's surface biome (the biome at its top solid block, or at sea
    /// level for columns without one).
    private void fillBiomes(Chunk chunk, double[] density, int minY) {
        int[] surface = SurfaceHeights.fromDensity(density, minY, densitySampler.height());
        String[] ids = surfaceBiomes.columns(chunk.xPosition, chunk.zPosition, surface, generatorSettings.sea_level);

        int[] biomes = new int[256];
        for (int column = 0; column < 256; column++) biomes[column] = biomeResolver.resolve(ids[column]).biomeID;
        ChunkBiomes.current().write(chunk, biomes);
    }

    /// Whether a chunk will (probably) be generated later, i.e. it's neither loaded nor saved. Server thread only.
    private boolean needsGeneration(int chunkX, int chunkZ) {
        if (!(world.getChunkProvider() instanceof ChunkProviderServer server)) return true;
        if (server.chunkExists(chunkX, chunkZ)) return false;
        return !(server.currentChunkLoader instanceof AnvilChunkLoader loader) || !loader.chunkExists(world, chunkX, chunkZ);
    }

    @Override
    public void populate(IChunkProvider p_73153_1_, int chunkX, int chunkZ) {

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
