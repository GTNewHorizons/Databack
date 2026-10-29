package databack.common.dto.worldgen.processor_list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;

/** Processor and ore rule tests. Tagged union on {@code predicate_type}. */
public interface IRuleTest {

    /// Vanilla's rule test, or null when it cannot be built ([FeatureBuildContext#unsupported]).
    @Nullable
    Object instantiate(@NotNull FeatureBuildContext ctx);
}
