package databack.common.worldgen.biome.preset;

import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for vanilla's preset source provider interface, which extracted preset providers implement. The function
/// maps a biome id to the list's value type.
@ApiStatus.Internal
public interface PresetSourceProvider {

    @NotNull
    <T> PresetParameterList apply(@NotNull Function<String, T> biomes);
}
