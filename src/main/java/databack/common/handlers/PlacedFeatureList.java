package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.placed_feature.PlacedFeature;

public class PlacedFeatureList extends JsonDatapackTypeHandler<PlacedFeature> {

    public static final PlacedFeatureList INSTANCE = new PlacedFeatureList();

    public PlacedFeatureList() {
        super("worldgen/placed_feature", PlacedFeature.class);
    }

    @Nullable
    public PlacedFeature getPlacedFeature(String id) {
        return super.getObject(id);
    }
}
