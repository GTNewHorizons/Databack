package databack.common.worldgen.feature.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.worldgen.biome.BiomeTemperature;
import databack.common.worldgen.feature.standin.FtBiome;

/// The biomes features see, by namespaced id, built once each from their datapack definitions. Thread-safe.
public final class FeatureBiomes {

    /// What biomes without a definition get: 1.7's default temperature.
    private static final FtBiome UNDEFINED = new FtBiome(new BiomeTemperature(0.5F, false));

    @NotNull
    private final Function<String, @Nullable DatapackBiome> definitions;
    private final Map<String, FtBiome> built = new ConcurrentHashMap<>();

    public FeatureBiomes(@NotNull Function<String, @Nullable DatapackBiome> definitions) {
        this.definitions = definitions;
    }

    @NotNull
    public FtBiome get(@NotNull String id) {
        return built.computeIfAbsent(id, key -> {
            DatapackBiome definition = definitions.apply(key);
            return definition == null ? UNDEFINED : new FtBiome(BiomeTemperature.of(definition));
        });
    }
}
