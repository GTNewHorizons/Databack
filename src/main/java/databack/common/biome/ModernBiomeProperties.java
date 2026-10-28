package databack.common.biome;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.color.RGBColor;

import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.RGBBiomeAttribute;
import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.biome.DatapackBiome.GrassColorModifier;
import databack.common.dto.worldgen.biome.DatapackBiome.TemperatureModifier;

/// Converts a datapack biome's climate and colours into [BiomeProperties]. Unsupported settings are logged once and
/// ignored.
public final class ModernBiomeProperties {

    private static final Logger LOGGER = LogManager.getLogger("databack-biomes");

    /// Below this temperature, precipitation is snow.
    public static final float SNOW_TEMPERATURE = 0.15F;

    /// Modern's usual water colour. 1.7's water texture is already this blue, so it maps to an untinted multiplier.
    private static final int DEFAULT_WATER = 0x3F76E4;

    private static final Set<String> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ModernBiomeProperties() {}

    /// The properties for datapack biome `biome` (named `id` in warnings); [BiomeProperties#DEFAULT] when null.
    @NotNull
    @Contract(pure = true)
    public static BiomeProperties of(@NotNull String id, @Nullable DatapackBiome biome) {
        if (biome == null) return BiomeProperties.DEFAULT;

        if (biome.temperature_modifier == TemperatureModifier.frozen) warnOnce(id, "temperature_modifier frozen");

        DatapackBiome.BiomeEffects effects = biome.effects;
        int water = 0xFFFFFF, grass = BiomeProperties.DERIVED, foliage = BiomeProperties.DERIVED;

        if (effects != null) {
            if (effects.water_color != null) water = waterMultiplier(resolve(effects.water_color, DEFAULT_WATER));
            if (effects.grass_color != null) grass = effects.grass_color.toIntRGB() & 0xFFFFFF;
            if (effects.foliage_color != null) foliage = effects.foliage_color.toIntRGB() & 0xFFFFFF;
            if (effects.grass_color_modifier != null && effects.grass_color_modifier != GrassColorModifier.none) {
                warnOnce(id, "grass_color_modifier " + effects.grass_color_modifier);
            }
        }

        int sky = BiomeProperties.DERIVED;
        if (biome.attributes != null && biome.attributes.sky_color != null) {
            sky = resolve(biome.attributes.sky_color, 0);
        }

        return new BiomeProperties(temperature(biome.temperature), biome.downfall, biome.has_precipitation, water,
            grass, foliage, sky);
    }

    /// 1.7 refuses temperatures strictly between 0.1 and 0.2, around its snow line; they move to the bound on the same
    /// side of [#SNOW_TEMPERATURE], so snow falls where it would in modern.
    @Contract(pure = true)
    static float temperature(float temperature) {
        if (temperature <= 0.1F || temperature >= 0.2F) return temperature;
        return temperature < SNOW_TEMPERATURE ? 0.1F : 0.2F;
    }

    /// The multiplier that turns 1.7's water colour (modern's default) into `colour`, per channel, capped at white.
    @Contract(pure = true)
    static int waterMultiplier(int colour) {
        int r = channel(colour >> 16, DEFAULT_WATER >> 16);
        int g = channel(colour >> 8, DEFAULT_WATER >> 8);
        int b = channel(colour, DEFAULT_WATER);
        return r << 16 | g << 8 | b;
    }

    private static int channel(int value, int base) {
        return Math.min(255, (value & 0xFF) * 255 / (base & 0xFF));
    }

    /// The colour `attribute` makes of `base`.
    private static int resolve(RGBBiomeAttribute attribute, int base) {
        RGBColor colour = RGBColor.fromRGB(base);
        attribute.apply(colour);
        return colour.toIntRGB() & 0xFFFFFF;
    }

    private static void warnOnce(String id, String setting) {
        if (WARNED.add(id + " " + setting)) {
            LOGGER.warn("Biome {}: {} is not supported yet and is ignored", id, setting);
        }
    }
}
