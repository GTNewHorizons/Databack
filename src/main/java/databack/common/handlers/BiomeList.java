package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.biome.DatapackBiome;

public class BiomeList extends JsonDatapackTypeHandler<DatapackBiome> {

    public static final BiomeList INSTANCE = new BiomeList();

    public BiomeList() {
        super("worldgen/biome", DatapackBiome.class);
    }

    @Nullable
    public DatapackBiome getBiome(String id) {
        return super.getObject(id);
    }
}
