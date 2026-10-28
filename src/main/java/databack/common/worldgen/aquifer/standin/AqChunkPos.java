package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a chunk position.
@ApiStatus.Internal
public final class AqChunkPos {

    private final int x, z;

    public AqChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getMinBlockX() {
        return x << 4;
    }

    public int getMaxBlockX() {
        return (x << 4) + 15;
    }

    public int getMinBlockZ() {
        return z << 4;
    }

    public int getMaxBlockZ() {
        return (z << 4) + 15;
    }
}
