package databack.common.worldgen.feature;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Added to vanilla's rewritten placed feature class ([FeatureMappings]), which already has this method, so placing
/// a feature is a plain interface call.
@ApiStatus.Internal
public interface VanillaPlacedFeature {

    boolean placeWithBiomeCheck(
        @NotNull FtLevel level, @NotNull FtChunkGenerator generator, @NotNull RandomSource random,
        @NotNull VnBlockPos origin
    );
}
