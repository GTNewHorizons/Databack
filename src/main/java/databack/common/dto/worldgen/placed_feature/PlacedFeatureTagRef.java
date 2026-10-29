package databack.common.dto.worldgen.placed_feature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtHolder;

/**
 * A placed feature reference that identifies features via a tag.
 * The tag string does not include the leading {@code #}.
 */
public class PlacedFeatureTagRef implements IPlacedFeatureRef {

    /** Tag ID without the leading {@code #}. */
    public final String tag;

    public PlacedFeatureTagRef(String tag) {
        this.tag = tag;
    }

    /// Placed feature tags are not loaded.
    @Nullable
    @Override
    public FtHolder instantiate(@NotNull FeatureBuildContext ctx) {
        return ctx.unsupported("Placed feature tag #" + tag);
    }
}
