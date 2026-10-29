package databack.common.dto.worldgen.configured_feature.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;

public interface ITreeFoliagePlacer {

    /// Vanilla's foliage placer, or null when it cannot be built. Unknown types give null (they are logged when read).
    @Nullable
    default Object instantiate(@NotNull FeatureBuildContext ctx) {
        return null;
    }
}
