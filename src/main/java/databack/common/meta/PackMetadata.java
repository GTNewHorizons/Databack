package databack.common.meta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Root DTO for one {@code pack.mcmeta} file. Holds the complete, validated contents.
 * Immutable value class.
 */
public final class PackMetadata {

    @Nonnull
    private final PackInfo pack;

    @Nullable
    private final PackFilter filter;

    @Nullable
    private final PackOverlays overlays;

    @Nullable
    private final PackFeatures features;

    /**
     * @param pack     the mandatory pack section; must not be null
     * @param filter   the optional filter section; may be null
     * @param overlays the optional overlays section; may be null
     * @param features the optional features section; may be null
     */
    public PackMetadata(
        @Nonnull PackInfo pack,
        @Nullable PackFilter filter,
        @Nullable PackOverlays overlays,
        @Nullable PackFeatures features) {
        this.pack = pack;
        this.filter = filter;
        this.overlays = overlays;
        this.features = features;
    }

    @Nonnull
    public PackInfo getPack() {
        return pack;
    }

    @Nullable
    public PackFilter getFilter() {
        return filter;
    }

    @Nullable
    public PackOverlays getOverlays() {
        return overlays;
    }

    @Nullable
    public PackFeatures getFeatures() {
        return features;
    }
}
