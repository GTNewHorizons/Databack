package databack.common.worldgen.biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.interop.registry.ProxyBiomeRegistry;

/// Maps modern biome ids to the 1.7 biomes stored in chunks, through [ProxyBiomeRegistry].
///
/// Every datapack biome should resolve: to a 1.7 biome (by name or [databack.common.biome.VanillaBiomeAliases]) or to
/// a [databack.common.biome.ModernBiome]. An id that does not (e.g. when biome ids ran out) gets `fallback` and a
/// warning, once per id; its biome then does not round-trip.
@ThreadSafe
public final class ProxyBiomeResolver {

    private static final Logger LOGGER = LogManager.getLogger("databack-biome-source");

    @NotNull
    private final Function<ResourceLocation, @Nullable BiomeGenBase> registry;
    @NotNull
    private final BiomeGenBase fallback;
    private final Map<String, BiomeGenBase> resolved = new ConcurrentHashMap<>();

    public ProxyBiomeResolver(@NotNull BiomeGenBase fallback) {
        this(ProxyBiomeRegistry.INSTANCE::getObject, fallback);
    }

    /// `registry` finds a 1.7 biome by id (null when unknown).
    ProxyBiomeResolver(@NotNull Function<ResourceLocation, @Nullable BiomeGenBase> registry,
        @NotNull BiomeGenBase fallback) {
        this.registry = registry;
        this.fallback = fallback;
    }

    /// The 1.7 biome for modern biome id `id`; `fallback` when there is none, or when `id` is null.
    @NotNull
    public BiomeGenBase resolve(@Nullable String id) {
        if (id == null) return fallback;
        return resolved.computeIfAbsent(id, this::lookup);
    }

    private BiomeGenBase lookup(String id) {
        BiomeGenBase biome = registry.apply(new ResourceLocation(id));
        if (biome != null) return biome;

        LOGGER.warn("No 1.7 biome for {}; using {} instead", id, fallback.biomeName);
        return fallback;
    }
}
