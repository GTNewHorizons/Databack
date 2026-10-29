package databack.common.dto.worldgen.block_state_provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;

public interface IBlockStateProvider {

    /// Vanilla's block state provider, or null when it cannot be built ([FeatureBuildContext#unsupported]).
    @Nullable
    Object instantiate(@NotNull FeatureBuildContext ctx);
}
