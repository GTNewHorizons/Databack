package databack.common.biome;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.junit.jupiter.api.Test;

import databack.common.interop.registry.ProxyBiomeRegistry;

class VanillaBiomeAliasesTest {

    @Test
    void aliased17BiomesTakeTheirModernIds() {
        ProxyBiomeRegistry proxy = ProxyBiomeRegistry.INSTANCE;
        ResourceLocation old = new ResourceLocation("minecraft:ice_plains");
        ResourceLocation modern = new ResourceLocation("minecraft:snowy_plains");
        proxy.putBiomeReplacing(old, BiomeGenBase.icePlains);

        try {
            VanillaBiomeAliases.apply(proxy);

            assertSame(BiomeGenBase.icePlains, proxy.getObject(modern));
            assertNull(proxy.getObject(old));
        } finally {
            proxy.removeBiome(modern);
            proxy.removeBiome(old);
        }
    }
}
