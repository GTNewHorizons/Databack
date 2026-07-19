package databack.common.dto.worldgen.placed_feature;

import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;

/** An inline placed feature definition: a configured feature paired with placement modifiers. */
public class PlacedFeature implements IPlacedFeatureRef {

    public IConfiguredFeature feature;
    public IPlacementModifier[] placement;

    @Override
    public boolean containsFeature(String featureId) {
        return false; // Inline features have no resource location to compare against
    }

}
