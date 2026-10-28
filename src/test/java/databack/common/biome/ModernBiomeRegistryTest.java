package databack.common.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import databack.common.interop.registry.ProxyBiomeRegistry;

/// Uses the real global biome list and proxy registry (tests run without mixins, so the registry starts empty), and
/// leaves both as it found them.
class ModernBiomeRegistryTest {

    private static final ResourceLocation PLAINS = new ResourceLocation("minecraft:plains");
    private static final BiomeGenBase[] BIOMES = BiomeGenBase.getBiomeGenArray();
    private static final ProxyBiomeRegistry PROXY = ProxyBiomeRegistry.INSTANCE;

    private ModernBiomeRegistry registry;

    @AfterEach
    void cleanUp() {
        if (registry != null) registry.activate(Collections.emptyList());
        PROXY.removeBiome(PLAINS);
    }

    @Test
    void aliasModeOnlyCreatesBiomesWithoutA17Counterpart() {
        PROXY.putBiomeReplacing(PLAINS, BiomeGenBase.plains);
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false);

        registry.activate(Arrays.asList("minecraft:plains", "test:alpha", "test:beta"));

        assertNull(registry.get("minecraft:plains"));
        assertSame(BiomeGenBase.plains, PROXY.getObject(PLAINS));

        ModernBiome alpha = registry.get("test:alpha");
        assertNotNull(alpha);
        assertSame(alpha, BIOMES[alpha.biomeID]);
        assertSame(alpha, PROXY.getObject(new ResourceLocation("test:alpha")));
        assertEquals("test:alpha", alpha.biomeName);
        assertTrue(registry.get("test:beta").biomeID != alpha.biomeID);
    }

    @Test
    void unusedBiomesLeaveTheListAndComeBackUnchanged() {
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false);
        registry.activate(Collections.singletonList("test:alpha"));
        ModernBiome alpha = registry.get("test:alpha");
        int id = alpha.biomeID;

        registry.activate(Collections.singletonList("test:beta"));
        assertNull(BIOMES[id]);
        assertNull(registry.get("test:alpha"));
        assertNull(PROXY.getObject(new ResourceLocation("test:alpha")));

        registry.activate(Collections.singletonList("test:alpha"));
        assertSame(alpha, registry.get("test:alpha"));
        assertSame(alpha, BIOMES[id]);
    }

    @Test
    void idsPersistAcrossRestarts(@TempDir File dir) {
        File file = new File(dir, "ids.json");

        registry = new ModernBiomeRegistry(ModernBiomeIds.load(file), PROXY, BIOMES, 255, false);
        registry.activate(Arrays.asList("test:alpha", "test:beta"));
        int alpha = registry.get("test:alpha").biomeID;
        int beta = registry.get("test:beta").biomeID;
        registry.activate(Collections.emptyList());

        // A fresh registry stands in for a restart; the old instances are gone from the list.
        registry = new ModernBiomeRegistry(ModernBiomeIds.load(file), PROXY, BIOMES, 255, false);
        registry.activate(Arrays.asList("test:beta", "test:alpha"));
        assertEquals(alpha, registry.get("test:alpha").biomeID);
        assertEquals(beta, registry.get("test:beta").biomeID);
    }

    @Test
    void modernOnlyModeMovesTheSameNamed17BiomeAside() {
        PROXY.putBiomeReplacing(PLAINS, BiomeGenBase.plains);
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, true);

        registry.activate(Collections.singletonList("minecraft:plains"));
        ModernBiome plains = registry.get("minecraft:plains");
        assertNotNull(plains);
        assertSame(plains, PROXY.getObject(PLAINS));
        assertSame(BiomeGenBase.plains, PROXY.getObject(new ResourceLocation("minecraft:legacy/plains")));

        registry.activate(Collections.emptyList());
        assertSame(BiomeGenBase.plains, PROXY.getObject(PLAINS));
        assertNull(PROXY.getObject(new ResourceLocation("minecraft:legacy/plains")));
    }

    @Test
    void runningOutOfIdsLeavesTheBiomeUnavailable() {
        assertNotNull(BIOMES[0]);
        ModernBiomeIds ids = ModernBiomeIds.inMemory();
        registry = new ModernBiomeRegistry(ids, PROXY, BIOMES, 1, false);

        registry.activate(Collections.singletonList("test:alpha"));

        assertNull(registry.get("test:alpha"));
        assertNull(ids.get("test:alpha"));
    }
}
