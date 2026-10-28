package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.junit.jupiter.api.Test;

class ProxyBiomeResolverTest {

    @Test
    void resolvesThroughTheRegistryWithFallback() {
        AtomicInteger lookups = new AtomicInteger();
        ProxyBiomeResolver resolver = new ProxyBiomeResolver(id -> {
            lookups.incrementAndGet();
            return id.equals(new ResourceLocation("minecraft:desert")) ? BiomeGenBase.desert : null;
        }, BiomeGenBase.plains);

        assertSame(BiomeGenBase.desert, resolver.resolve("minecraft:desert"));
        assertSame(BiomeGenBase.plains, resolver.resolve("minecraft:cherry_grove"));
        assertSame(BiomeGenBase.plains, resolver.resolve("minecraft:cherry_grove"));
        assertSame(BiomeGenBase.plains, resolver.resolve(null));
        assertEquals(2, lookups.get());
    }
}
