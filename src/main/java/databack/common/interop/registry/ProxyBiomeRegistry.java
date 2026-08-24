package databack.common.interop.registry;

import java.util.Iterator;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.Pair;

public class ProxyBiomeRegistry implements IProxyRegistry<BiomeGenBase> {

    public static final ProxyBiomeRegistry INSTANCE = new ProxyBiomeRegistry();

    private final BiMap<ResourceLocation, BiomeGenBase> biomes = HashBiMap.create();

    private ProxyBiomeRegistry() { }

    public void putBiome(ResourceLocation id, BiomeGenBase biome) {
        LOGGER.info("Detected biome registration: id={} biome={} ({}) numeric id={}", id, biome, biome.biomeName, biome.biomeID);
        biomes.put(id, biome);
    }

    @Override
    public BiomeGenBase getObject(ResourceLocation id) {
        return biomes.get(id);
    }

    @Override
    public ResourceLocation getIdForObject(BiomeGenBase biome) {
        return biomes.inverse().get(biome);
    }

    @Override
    public @NotNull Iterator<Pair<ResourceLocation, BiomeGenBase>> iterator() {
        return Iterators.transform(biomes.entrySet().iterator(), e -> Pair.of(e.getKey(), e.getValue()));
    }
}
