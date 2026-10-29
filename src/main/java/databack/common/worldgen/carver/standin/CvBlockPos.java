package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for block positions, mutable ones and integer vectors alike.
@ApiStatus.Internal
public final class CvBlockPos {

    private int x, y, z;

    public CvBlockPos() {}

    public CvBlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @NotNull
    public CvBlockPos set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /// This becomes `pos` moved one block towards `direction`.
    @NotNull
    public CvBlockPos setWithOffset(@NotNull CvBlockPos pos, @NotNull CvDirection direction) {
        return set(pos.x + direction.stepX, pos.y + direction.stepY, pos.z + direction.stepZ);
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
