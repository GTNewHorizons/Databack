package databack.common.dto.worldgen.configured_feature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtHolder;

/// A configured feature: an inline one, or a reference to a configured_feature file.
public interface IConfiguredFeature {

    /// Vanilla's configured feature, or null when it cannot be built. Feature types not run yet are unsupported
    /// ([FeatureBuildContext#unsupported]).
    @Nullable
    default Object instantiate(@NotNull FeatureBuildContext ctx) {
        return ctx.unsupported("Feature type " + getClass().getSimpleName());
    }

    /// The configured feature as a holder: registered under its id when it is a reference, inline otherwise.
    @Nullable
    default FtHolder holder(@NotNull FeatureBuildContext ctx) {
        Object feature = instantiate(ctx);
        return feature == null ? null : new FtHolder(null, feature);
    }
}
