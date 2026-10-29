package databack.common.dto.worldgen.placed_feature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;

public interface IPlacementModifier {

    /// Vanilla's placement modifier, or null when it cannot be built ([FeatureBuildContext#unsupported]).
    @Nullable
    Object instantiate(@NotNull FeatureBuildContext ctx);
}
