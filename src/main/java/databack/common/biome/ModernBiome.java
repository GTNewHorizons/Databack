package databack.common.biome;

import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.NotNull;

/// A biome that exists only in modern datapacks (see [ModernBiomeRegistry]). Its name is its datapack id.
public class ModernBiome extends BiomeGenBase {

    @NotNull
    private final String datapackId;

    /// Puts the biome into the global biome list at `biomeId`, which must be free.
    public ModernBiome(int biomeId, @NotNull String datapackId) {
        super(biomeId);
        this.datapackId = datapackId;
        setBiomeName(datapackId);
    }

    /// The biome's datapack id, e.g. `minecraft:cherry_grove`.
    @NotNull
    public String getDatapackId() {
        return datapackId;
    }
}
