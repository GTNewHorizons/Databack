package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a biome's generation settings, as features use them.
@ApiStatus.Internal
public interface FtBiomeFeatures {

    /// Whether the biome places `feature`, one of vanilla's placed features.
    boolean hasFeature(@NotNull Object feature);
}
