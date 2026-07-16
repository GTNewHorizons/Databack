package databack.common.dto.worldgen.biome;

public class PlacedFeatureId implements PlacedFeatureSet {

    public final String id;

    public PlacedFeatureId(String id) {
        this.id = id;
    }

    @Override
    public boolean containsFeature(String featureId) {
        return id.equals(featureId);
    }

}
