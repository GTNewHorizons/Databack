package databack.common.worldgen;

import net.minecraft.world.biome.BiomeGenBase;

import databack.common.context.WorldContext;

public interface BiomeProvider {

    void getBiomes(WorldContext context, int chunkX, int chunkZ, BiomeGenBase[] biomes);

}
