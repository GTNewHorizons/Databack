package databack.common.worldgen.biome.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import databack.common.worldgen.biome.ClimateEntry;

/// The stand-ins' behaviour, as observed black-box on the 26.1 jar.
class PresetStandInsTest {

    @Test
    void quantizeTruncatesAfterFloatMultiply() {
        assertEquals(-7799, PresetClimate.quantizeCoord(-0.78f));
        assertEquals(10000, PresetClimate.quantizeCoord(1.0f));
        assertEquals(-12000, PresetClimate.quantizeCoord(-1.2f));
        assertEquals(0, PresetClimate.quantizeCoord(-0.00001f));
    }

    @Test
    void spanOfFloatsQuantizesBothEnds() {
        PresetParameter p = PresetParameter.span(-0.45f, 0.2f);
        assertEquals(-4500, p.min());
        assertEquals(2000, p.max());

        PresetParameter point = PresetParameter.point(0.3f);
        assertEquals(point.min(), point.max());
        assertThrows(IllegalArgumentException.class, () -> PresetParameter.span(0.5f, -0.5f));
    }

    /// Not the hull: `[from.min, to.max]`, even when `from` reaches further.
    @Test
    void spanOfParametersTakesFirstMinAndLastMax() {
        PresetParameter wide = PresetParameter.span(-1.0f, 1.0f);
        PresetParameter narrow = PresetParameter.span(-0.1f, 0.1f);

        PresetParameter span = PresetParameter.span(wide, narrow);
        assertEquals(-10000, span.min());
        assertEquals(1000, span.max());

        PresetParameter low = PresetParameter.span(-1.0f, -0.5f);
        PresetParameter high = PresetParameter.span(0.5f, 1.0f);
        assertThrows(IllegalArgumentException.class, () -> PresetParameter.span(high, low));
    }

    @Test
    void parametersKeepVanillaOrder() {
        ClimateEntry entry = PresetClimate.parameters(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f).toEntry("minecraft:x");

        assertEquals(1000, entry.min(ClimateEntry.TEMPERATURE));
        assertEquals(2000, entry.min(ClimateEntry.HUMIDITY));
        assertEquals(3000, entry.min(ClimateEntry.CONTINENTALNESS));
        assertEquals(4000, entry.min(ClimateEntry.EROSION));
        assertEquals(5000, entry.min(ClimateEntry.DEPTH));
        assertEquals(6000, entry.max(ClimateEntry.WEIRDNESS));
        assertEquals(7000, entry.offset());
    }

    @Test
    void biomeKeysBecomeLowerCaseIds() {
        assertEquals("minecraft:old_growth_pine_taiga", PresetMappings.biomeId("OLD_GROWTH_PINE_TAIGA"));
    }

    @Test
    void descriptorsMapToStandIns() {
        String parameter = "Lnet/minecraft/world/level/biome/Climate$Parameter;";
        String key = "Lnet/minecraft/resources/ResourceKey;";

        assertEquals("(IIL" + PresetMappings.mapName("net/minecraft/world/level/biome/Climate$Parameter")
            + ";)Ljava/lang/String;", PresetMappings.mapDesc("(II" + parameter + ")" + key));
        assertEquals("[[Ljava/lang/String;", PresetMappings.mapName("[[" + key));
        assertEquals("net/minecraft/Unrelated", PresetMappings.mapName("net/minecraft/Unrelated"));
    }
}
