package databack.common.dto.worldgen.placed_feature;

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

    @Override
    public boolean containsFeature(String featureId) {
        return false; // TODO: resolve tag contents and check membership
    }

}
