package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the shape constants.
@ApiStatus.Internal
public final class FtShapes {

    private FtShapes() {}

    @NotNull
    public static FtShape block() {
        return FtShape.INSTANCE;
    }
}
