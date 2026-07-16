package databack.common.dto.worldgen.biome;

public class PlacedFeatureTag implements PlacedFeatureSet {

    /** Tag ID without the leading '#'. */
    public final String tag;

    public PlacedFeatureTag(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean containsFeature(String featureId) {
        return false; // TODO: resolve tag contents and check membership
    }

}
