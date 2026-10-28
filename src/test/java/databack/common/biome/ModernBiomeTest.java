package databack.common.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.biome.BiomeGenBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModernBiomeTest {

    private static final BiomeGenBase[] BIOMES = BiomeGenBase.getBiomeGenArray();

    private ModernBiome biome;

    @AfterEach
    void cleanUp() {
        if (biome != null) BIOMES[biome.biomeID] = null;
    }

    @Test
    void propertiesDriveClimateWeatherAndColours() {
        biome = new ModernBiome(freeSlot(), "test:biome");

        biome.setProperties(new BiomeProperties(0.0F, 0.4F, true, 0x7E7E7E, 0x123456, BiomeProperties.DERIVED,
            0x654321));
        assertEquals(0.0F, biome.temperature);
        assertEquals(0.4F, biome.rainfall);
        assertEquals(0x7E7E7E, biome.waterColorMultiplier);
        assertTrue(biome.getEnableSnow());
        assertFalse(biome.canSpawnLightningBolt());
        assertEquals(0x123456, biome.getBiomeGrassColor(0, 64, 0));
        assertEquals(0x654321, biome.getSkyColorByTemp(0.0F));

        biome.setProperties(new BiomeProperties(0.8F, 0.4F, true, 0xFFFFFF, BiomeProperties.DERIVED,
            BiomeProperties.DERIVED, BiomeProperties.DERIVED));
        assertFalse(biome.getEnableSnow());
        assertTrue(biome.canSpawnLightningBolt());

        biome.setProperties(new BiomeProperties(2.0F, 0.0F, false, 0xFFFFFF, BiomeProperties.DERIVED,
            BiomeProperties.DERIVED, BiomeProperties.DERIVED));
        assertFalse(biome.getEnableSnow());
        assertFalse(biome.canSpawnLightningBolt());
    }

    private static int freeSlot() {
        for (int i = 0; i < 255; i++) {
            if (BIOMES[i] == null) return i;
        }
        throw new IllegalStateException("No free biome slot");
    }
}
