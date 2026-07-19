package databack.common.dto.worldgen.placed_feature;

/** A placed feature reference that identifies a feature by resource location. */
public class PlacedFeatureIdRef implements IPlacedFeatureRef {

    public final String id;

    public PlacedFeatureIdRef(String id) {
        this.id = id;
    }

    @Override
    public boolean containsFeature(String featureId) {
        return id.equals(featureId);
    }

}
