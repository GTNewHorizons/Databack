package databack.common.meta;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Holds the parsed {@code features} section of a {@code pack.mcmeta} file.
 * Immutable value class.
 */
public final class PackFeatures {

    @Nonnull
    private final List<String> enabled;

    /**
     * @param enabled list of feature flag identifiers; must not be null.
     *                The constructor wraps it in an unmodifiable view.
     */
    public PackFeatures(@Nonnull List<String> enabled) {
        this.enabled = Collections.unmodifiableList(enabled);
    }

    /**
     * Returns the unmodifiable list of enabled experimental feature flag identifiers.
     */
    @Nonnull
    public List<String> getEnabled() {
        return enabled;
    }
}
