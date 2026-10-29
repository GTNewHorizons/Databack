package databack.common.dto.worldgen.block_predicate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;

public interface IBlockPredicate {

    /// Vanilla's block predicate, or null when it cannot be built ([FeatureBuildContext#unsupported]).
    @Nullable
    Object instantiate(@NotNull FeatureBuildContext ctx);
}
