package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.placed_feature.PlacedFeature;

public class PlacedFeatureList extends JsonDatapackTypeHandler<PlacedFeature> {

    public static final ResourceType<PlacedFeatureList> RT = ResourceType.withPath("worldgen/placed_feature");

    public PlacedFeatureList() {
        super(RT, PlacedFeature.class);
    }

    @Nullable
    public PlacedFeature getPlacedFeature(String id) {
        return super.getObject(id);
    }
}
