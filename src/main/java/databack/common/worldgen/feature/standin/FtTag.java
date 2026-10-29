package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a tag key: the tag's namespaced id, without `#`.
@ApiStatus.Internal
public final class FtTag {

    @NotNull
    public final String id;

    public FtTag(@NotNull String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "#" + id;
    }
}
