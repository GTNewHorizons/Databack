package databack.common.meta;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Holds the parsed {@code overlays} section of a {@code pack.mcmeta} file.
 * Immutable value class.
 */
public final class PackOverlays {

    @Nonnull
    private final List<OverlayEntry> entries;

    /**
     * @param entries list of overlay entries; must not be null.
     *                The constructor wraps it in an unmodifiable view.
     */
    public PackOverlays(@Nonnull List<OverlayEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    /**
     * Returns the unmodifiable ordered list of overlay entries.
     */
    @Nonnull
    public List<OverlayEntry> getEntries() {
        return entries;
    }
}
