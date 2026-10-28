package databack.common.biome;

import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.NotNull;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/// A biome that exists only in modern datapacks (see [ModernBiomeRegistry]). Its name is its datapack id; its climate
/// and colours come from the datapack biome ([#setProperties]).
public class ModernBiome extends BiomeGenBase {

    @NotNull
    private final String datapackId;
    @NotNull
    private volatile BiomeProperties properties = BiomeProperties.DEFAULT;

    /// Puts the biome into the global biome list at `biomeId`, which must be free.
    public ModernBiome(int biomeId, @NotNull String datapackId) {
        super(biomeId);
        this.datapackId = datapackId;
        setBiomeName(datapackId);
        setProperties(BiomeProperties.DEFAULT);
    }

    /// The biome's datapack id, e.g. `minecraft:cherry_grove`.
    @NotNull
    public String getDatapackId() {
        return datapackId;
    }

    @NotNull
    public BiomeProperties getProperties() {
        return properties;
    }

    /// Replaces the biome's properties. 1.7 reads temperature, rainfall and water colour from fields, so those are
    /// copied into them.
    public void setProperties(@NotNull BiomeProperties properties) {
        this.properties = properties;
        this.temperature = properties.temperature;
        this.rainfall = properties.rainfall;
        this.waterColorMultiplier = properties.waterColorMultiplier;
    }

    /// Whether precipitation is snow (1.7 asks this through `getEnableSnow`).
    @Override
    public boolean func_150559_j() {
        return properties.snows();
    }

    @Override
    public boolean canSpawnLightningBolt() {
        return properties.precipitation && !properties.snows();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeGrassColor(int x, int y, int z) {
        int colour = properties.grassColor;
        return colour == BiomeProperties.DERIVED ? super.getBiomeGrassColor(x, y, z) : getModdedBiomeGrassColor(colour);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeFoliageColor(int x, int y, int z) {
        int colour = properties.foliageColor;
        return colour == BiomeProperties.DERIVED ? super.getBiomeFoliageColor(x, y, z)
            : getModdedBiomeFoliageColor(colour);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getSkyColorByTemp(float temperature) {
        int colour = properties.skyColor;
        return colour == BiomeProperties.DERIVED ? super.getSkyColorByTemp(temperature) : colour;
    }
}
