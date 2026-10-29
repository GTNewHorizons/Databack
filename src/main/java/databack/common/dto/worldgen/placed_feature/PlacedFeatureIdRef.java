package databack.common.dto.worldgen.placed_feature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtHolder;

/** A placed feature reference that identifies a feature by resource location. */
public class PlacedFeatureIdRef implements IPlacedFeatureRef {

    public final String id;

    public PlacedFeatureIdRef(String id) {
        this.id = id;
    }

    @Nullable
    @Override
    public FtHolder instantiate(@NotNull FeatureBuildContext ctx) {
        return ctx.placedFeature(id);
    }
}
