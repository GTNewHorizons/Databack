package databack.common.dto.worldgen.placed_feature;

/**
 * A reference to a placed feature — either a resource-location string, a tag string,
 * or an inline {@link PlacedFeature} definition.
 *
 * <p>In 26.1 mcdoc this corresponds to {@code #[id="worldgen/placed_feature"] string | PlacedFeature}.
 */
public interface IPlacedFeatureRef {

    /**
     * Returns {@code true} if this reference directly identifies the given placed feature
     * resource location. Tag references and inline features always return {@code false}.
     */
    boolean containsFeature(String featureId);

}
