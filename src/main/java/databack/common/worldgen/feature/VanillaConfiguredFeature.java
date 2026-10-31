package databack.common.worldgen.feature;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Added to vanilla's rewritten configured feature class ([FeatureMappings]), which already has this method, so
/// [FeatureBand] can place one.
@ApiStatus.Internal
public interface VanillaConfiguredFeature {

    boolean place(
        @NotNull FtLevel level, @NotNull FtChunkGenerator generator, @NotNull RandomSource random,
        @NotNull VnBlockPos origin
    );
}
