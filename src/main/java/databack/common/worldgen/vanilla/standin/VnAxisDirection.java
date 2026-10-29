package databack.common.worldgen.vanilla.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for whether a direction steps towards larger coordinates.
@ApiStatus.Internal
public final class VnAxisDirection {

    public static final VnAxisDirection POSITIVE = new VnAxisDirection("POSITIVE");
    public static final VnAxisDirection NEGATIVE = new VnAxisDirection("NEGATIVE");

    @NotNull
    private final String name;

    private VnAxisDirection(@NotNull String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
