package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Stand-in for a collision shape. Only tested against entities, of which worldgen has none ([FtLevel#isUnobstructed]),
/// so every shape is the same.
@ApiStatus.Internal
public final class FtShape {

    static final FtShape INSTANCE = new FtShape();

    private FtShape() {}

    @NotNull
    public FtShape move(@NotNull VnBlockPos offset) {
        return this;
    }
}
