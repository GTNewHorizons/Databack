package databack.common.dto.worldgen.placed_feature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtHolder;

/**
 * A reference to a placed feature — either a resource-location string, a tag string,
 * or an inline {@link PlacedFeature} definition.
 *
 * <p>In 26.1 mcdoc this corresponds to {@code #[id="worldgen/placed_feature"] string | PlacedFeature}.
 */
public interface IPlacedFeatureRef {

    /// Vanilla's placed feature as a holder (registered under its id for references), or null when it cannot be
    /// built.
    @Nullable
    FtHolder instantiate(@NotNull FeatureBuildContext ctx);
}
