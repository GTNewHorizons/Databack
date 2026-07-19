package databack.common.worldgen;

import java.util.Arrays;

import net.minecraft.world.biome.BiomeGenBase;

import databack.common.context.WorldContext;

public class NoiseBiomeProvider implements BiomeProvider {

    @Override
    public void getBiomes(WorldContext context, int chunkX, int chunkZ, BiomeGenBase[] biomes) {
        Arrays.fill(biomes, BiomeGenBase.plains);
    }
}
