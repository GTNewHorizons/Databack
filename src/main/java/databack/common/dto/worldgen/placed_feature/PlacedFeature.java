package databack.common.dto.worldgen.placed_feature;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtHolder;

/** An inline placed feature definition: a configured feature paired with placement modifiers. */
public class PlacedFeature implements IPlacedFeatureRef {

    public IConfiguredFeature feature;
    public IPlacementModifier[] placement;

    /// Vanilla's placed feature, or null when its feature or a modifier cannot be built.
    @Nullable
    public Object build(@NotNull FeatureBuildContext ctx) {
        FtHolder configured = feature.holder(ctx);
        if (configured == null) return null;

        List<Object> modifiers = new ArrayList<>();
        if (placement != null) {
            for (IPlacementModifier modifier : placement) {
                Object built = modifier.instantiate(ctx);
                if (built == null) return null;
                modifiers.add(built);
            }
        }
        return ctx.vanilla.construct("placement/PlacedFeature", configured, modifiers);
    }

    @Nullable
    @Override
    public FtHolder instantiate(@NotNull FeatureBuildContext ctx) {
        Object built = build(ctx);
        return built == null ? null : new FtHolder(null, built);
    }
}
