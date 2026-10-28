package databack.common.biome;

import org.jetbrains.annotations.Contract;

/// What a [ModernBiome] takes from its datapack biome, already converted to 1.7's terms (see
/// [ModernBiomeProperties]). Colours are `0xRRGGBB`; [#DERIVED] means 1.7's own colour for the climate.
public final class BiomeProperties {

    public static final int DERIVED = -1;

    /// 1.7's defaults for a biome that sets nothing.
    public static final BiomeProperties DEFAULT = new BiomeProperties(0.5F, 0.5F, true, 0xFFFFFF, DERIVED, DERIVED,
        DERIVED);

    public final float temperature;
    public final float rainfall;
    public final boolean precipitation;
    /// Multiplies 1.7's (already blue) water texture.
    public final int waterColorMultiplier;
    public final int grassColor;
    public final int foliageColor;
    public final int skyColor;

    public BiomeProperties(
        float temperature, float rainfall, boolean precipitation, int waterColorMultiplier, int grassColor,
        int foliageColor, int skyColor
    ) {
        this.temperature = temperature;
        this.rainfall = rainfall;
        this.precipitation = precipitation;
        this.waterColorMultiplier = waterColorMultiplier;
        this.grassColor = grassColor;
        this.foliageColor = foliageColor;
        this.skyColor = skyColor;
    }

    /// Whether precipitation falls as snow: modern and 1.7 both snow below a temperature of 0.15.
    @Contract(pure = true)
    public boolean snows() {
        return precipitation && temperature < ModernBiomeProperties.SNOW_TEMPERATURE;
    }
}
