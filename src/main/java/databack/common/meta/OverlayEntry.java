package databack.common.meta;

import javax.annotation.Nonnull;

/**
 * Describes one overlay: the format range under which it is active, and the directory name at the pack root.
 * Immutable value class.
 */
public final class OverlayEntry {

    @Nonnull
    private final FormatRange formats;

    @Nonnull
    private final String directory;

    /**
     * @param formats   the format range for this overlay; must not be null
     * @param directory the directory name within the pack root; must not be null
     */
    public OverlayEntry(@Nonnull FormatRange formats, @Nonnull String directory) {
        this.formats = formats;
        this.directory = directory;
    }

    @Nonnull
    public FormatRange getFormats() {
        return formats;
    }

    @Nonnull
    public String getDirectory() {
        return directory;
    }
}
