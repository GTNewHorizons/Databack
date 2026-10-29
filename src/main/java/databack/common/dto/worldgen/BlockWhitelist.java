package databack.common.dto.worldgen;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/// A list of blocks or fluids, as datapacks write it: one entry or an array of them, each an id or a `#tag`.
public final class BlockWhitelist {

    @NotNull
    public final List<String> entries;

    public BlockWhitelist(@NotNull List<String> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    @Override
    public String toString() {
        return entries.toString();
    }
}
