package databack.common.meta;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Holds the parsed {@code filter} section of a {@code pack.mcmeta} file.
 * Immutable value class.
 */
public final class PackFilter {

    @Nonnull
    private final List<ResourceLocationPattern> block;

    /**
     * @param block list of resource location patterns to block; must not be null.
     *              The constructor wraps it in an unmodifiable view.
     */
    public PackFilter(@Nonnull List<ResourceLocationPattern> block) {
        this.block = Collections.unmodifiableList(block);
    }

    /**
     * Returns the unmodifiable list of resource location patterns that this pack blocks from lower-priority packs.
     */
    @Nonnull
    public List<ResourceLocationPattern> getBlock() {
        return block;
    }
}
