package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for a registry holder: a value, and the namespaced id it was registered under if any.
@ApiStatus.Internal
public final class FtHolder {

    @Nullable
    public final String id;
    @NotNull
    private final Object value;

    public FtHolder(@Nullable String id, @NotNull Object value) {
        this.id = id;
        this.value = value;
    }

    @NotNull
    public Object value() {
        return value;
    }

    @Override
    public String toString() {
        return id != null ? id : "inline " + value;
    }
}
