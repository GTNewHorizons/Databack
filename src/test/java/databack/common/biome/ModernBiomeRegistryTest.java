package databack.common.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false, biome -> {});

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
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false, biome -> {});
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

        registry = new ModernBiomeRegistry(ModernBiomeIds.load(file), PROXY, BIOMES, 255, false, biome -> {});
        registry.activate(Arrays.asList("test:alpha", "test:beta"));
        int alpha = registry.get("test:alpha").biomeID;
        int beta = registry.get("test:beta").biomeID;
        registry.activate(Collections.emptyList());

        // A fresh registry stands in for a restart; the old instances are gone from the list.
        registry = new ModernBiomeRegistry(ModernBiomeIds.load(file), PROXY, BIOMES, 255, false, biome -> {});
        registry.activate(Arrays.asList("test:beta", "test:alpha"));
        assertEquals(alpha, registry.get("test:alpha").biomeID);
        assertEquals(beta, registry.get("test:beta").biomeID);
    }

    @Test
    void modernOnlyModeMovesTheSameNamed17BiomeAside() {
        PROXY.putBiomeReplacing(PLAINS, BiomeGenBase.plains);
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, true, biome -> {});

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
        registry = new ModernBiomeRegistry(ids, PROXY, BIOMES, 1, false, biome -> {});

        registry.activate(Collections.singletonList("test:alpha"));

        assertNull(registry.get("test:alpha"));
        assertNull(ids.get("test:alpha"));
    }

    @Test
    void clientsMirrorTheServersNumbersWithoutAssigningAny() {
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false, biome -> {});
        int free = firstFreeSlot(0);
        int otherFree = firstFreeSlot(free + 1);

        Map<String, Integer> served = new TreeMap<>();
        served.put("test:alpha", free);
        registry.synchronize(served);

        ModernBiome alpha = registry.get("test:alpha");
        assertEquals(free, alpha.biomeID);
        assertSame(alpha, PROXY.getObject(new ResourceLocation("test:alpha")));

        // Another server numbers it differently, and does not have it at all after that.
        served.put("test:alpha", otherFree);
        registry.synchronize(served);
        assertEquals(otherFree, registry.get("test:alpha").biomeID);
        assertNull(BIOMES[free]);

        registry.synchronize(Collections.emptyMap());
        assertNull(registry.get("test:alpha"));
        assertNull(BIOMES[otherFree]);
    }

    @Test
    void serverNumbersHeldByAnotherBiomeAreSkipped() {
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false, biome -> {});
        assertNotNull(BIOMES[1]);

        registry.synchronize(Collections.singletonMap("test:alpha", 1));

        assertNull(registry.get("test:alpha"));
        assertSame(BiomeGenBase.plains, BIOMES[1]);
    }

    @Test
    void everyActiveBiomeIsConfiguredAfterEachChange() {
        List<String> configured = new ArrayList<>();
        registry = new ModernBiomeRegistry(ModernBiomeIds.inMemory(), PROXY, BIOMES, 255, false,
            biome -> configured.add(biome.getDatapackId()));

        registry.activate(Arrays.asList("test:alpha", "test:beta"));
        assertEquals(Arrays.asList("test:alpha", "test:beta"), sorted(configured));

        configured.clear();
        registry.activate(Collections.singletonList("test:alpha"));
        assertEquals(Collections.singletonList("test:alpha"), configured);
        assertEquals(Collections.singletonMap("test:alpha", registry.get("test:alpha").biomeID),
            registry.activeIds());
    }

    private static int firstFreeSlot(int from) {
        for (int i = from; i < 255; i++) {
            if (BIOMES[i] == null) return i;
        }
        throw new IllegalStateException("No free biome slot");
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }
}
