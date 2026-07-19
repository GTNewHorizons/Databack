package databack.common.worldgen;

import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import databack.common.context.WorldContext;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.NoiseDimensionGenerator;
import databack.common.dto.dimension.Dimension;
import databack.common.dto.dimension_type.DimensionType;
import databack.common.dto.worldgen.density_function.IDensityFunction;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.world_preset.WorldPreset;
import databack.common.handlers.DimensionList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.WorldPresetList;

public class ModernWorldGenerator implements IChunkProvider {

    @org.jetbrains.annotations.NotNull
    public final World world;

    public final NoiseGeneratorSettings generatorSettings;
    public final DimensionType dimensionType;

    private final BiomeProvider biomeProvider;

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

        if (dimensionType == null) {
            throw new IllegalStateException("Invalid dimension type: " + world.provider.dimensionId + ", " + dim.type);
        }

        biomeProvider = new NoiseBiomeProvider();
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

        IDensityFunction finalDensity = this.generatorSettings.noise_router.final_density;

        WorldContext context = WorldContext.getContext(world);
        context.resetCache();

        ImmutableBlockMeta air = new BlockMeta(Blocks.air);
        ImmutableBlockMeta main = new BlockMeta(this.generatorSettings.default_block.getBlock(), this.generatorSettings.default_block.getBlockMeta(0));

        for (int ebsY = 0; ebsY < 16; ebsY++) {
            ExtendedBlockStorage ebs = new ExtendedBlockStorage(ebsY << 4, !world.provider.hasNoSky);
            chunk.getBlockStorageArray()[ebsY] = ebs;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        float density = finalDensity.compute(context, x + (chunkX << 4), y + (ebsY << 4), z + (chunkZ << 4));

                        ImmutableBlockMeta bm = air;

                        if (density > 0) {
                            bm = main;
                        }

                        if (bm.getBlock() != Blocks.air) {
                            ebs.func_150818_a(x, y, z, bm.getBlock());
                        }

                        if (bm.getBlockMeta() != 0) {
                            ebs.setExtBlockMetadata(x, y, z, bm.getBlockMeta());
                        }
                    }
                }
            }
        }

        return chunk;
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
