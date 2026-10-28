package databack.common.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.color.RGBColor;

import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.OverrideCBA;
import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.biome.EnvironmentEffects;

class ModernBiomePropertiesTest {

    @Test
    void missingBiomeGetsDefaults() {
        assertSame(BiomeProperties.DEFAULT, ModernBiomeProperties.of("test:missing", null));
    }

    @Test
    void climateAndColoursCarryOver() {
        DatapackBiome biome = new DatapackBiome();
        biome.temperature = 0.8F;
        biome.downfall = 0.9F;
        biome.has_precipitation = true;
        biome.effects = new DatapackBiome.BiomeEffects();
        biome.effects.water_color = override(0x3F76E4);
        biome.effects.grass_color = RGBColor.fromRGB(0xB6DB61);
        biome.effects.foliage_color = RGBColor.fromRGB(0x6A7039);
        biome.attributes = new EnvironmentEffects();
        biome.attributes.sky_color = override(0x78A7FF);

        BiomeProperties p = ModernBiomeProperties.of("test:biome", biome);

        assertEquals(0.8F, p.temperature);
        assertEquals(0.9F, p.rainfall);
        assertTrue(p.precipitation);
        assertEquals(0xFFFFFF, p.waterColorMultiplier);
        assertEquals(0xB6DB61, p.grassColor);
        assertEquals(0x6A7039, p.foliageColor);
        assertEquals(0x78A7FF, p.skyColor);
        assertFalse(p.snows());
    }

    @Test
    void unsetColoursAreDerived() {
        DatapackBiome biome = new DatapackBiome();
        biome.temperature = 0.0F;
        biome.has_precipitation = true;

        BiomeProperties p = ModernBiomeProperties.of("test:biome", biome);

        assertEquals(BiomeProperties.DERIVED, p.grassColor);
        assertEquals(BiomeProperties.DERIVED, p.skyColor);
        assertEquals(0xFFFFFF, p.waterColorMultiplier);
        assertTrue(p.snows());
    }

    /// 1.7 refuses (0.1, 0.2); values there keep their side of the 0.15 snow line.
    @Test
    void temperaturesLeave17sForbiddenBand() {
        assertEquals(0.1F, ModernBiomeProperties.temperature(0.12F));
        assertEquals(0.2F, ModernBiomeProperties.temperature(0.15F));
        assertEquals(0.2F, ModernBiomeProperties.temperature(0.19F));
        assertEquals(0.1F, ModernBiomeProperties.temperature(0.1F));
        assertEquals(-0.5F, ModernBiomeProperties.temperature(-0.5F));
        assertEquals(2.0F, ModernBiomeProperties.temperature(2.0F));
    }

    @Test
    void waterColourBecomesAMultiplierOf17sWater() {
        assertEquals(0xFFFFFF, ModernBiomeProperties.waterMultiplier(0x3F76E4));
        // About half of modern's default water colour per channel: 31 * 255 / 63, 59 * 255 / 118, 114 * 255 / 228.
        assertEquals(0x7D7F7F, ModernBiomeProperties.waterMultiplier(0x1F3B72));
        assertEquals(0xFFFFFF, ModernBiomeProperties.waterMultiplier(0xFFFFFF));
    }

    private static OverrideCBA override(int rgb) {
        OverrideCBA attribute = new OverrideCBA();
        attribute.argument = RGBColor.fromRGB(rgb);
        return attribute;
    }
}
