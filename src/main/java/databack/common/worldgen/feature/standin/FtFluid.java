package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a fluid: its id.
@ApiStatus.Internal
public final class FtFluid {

    @NotNull
    public final String id;

    FtFluid(@NotNull String id) {
        this.id = id;
    }

    /// The fluid as registered under its id.
    @NotNull
    public FtHolder builtInRegistryHolder() {
        return new FtHolder(id, this);
    }

    @Override
    public String toString() {
        return id;
    }
}
